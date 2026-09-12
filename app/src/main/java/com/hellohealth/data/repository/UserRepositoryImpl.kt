package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.FoodPrefsDao
import com.hellohealth.data.local.entities.FoodPrefsEntity
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.repository.UserRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first food-preferences repository (Supabase `food_preferences`, conflict key `user_id`).
 * Reads from Room, writes hit Room (`isSynced=false`) then poke [SyncScheduler];
 * [com.hellohealth.sync.FoodPrefsSyncer] owns the Supabase push/pull. Missing user/row falls back
 * to [FoodPreferences] defaults, matching prior behavior.
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val foodPrefsDao: FoodPrefsDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler
) : UserRepository {

    override fun getFoodPreferences(): Flow<FoodPreferences> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(FoodPreferences())
            return@flow
        }
        emitAll(foodPrefsDao.observe(userId).map { it?.toDomain() ?: FoodPreferences() })
    }.flowOn(Dispatchers.IO)

    override suspend fun getCurrentFoodPreferences(): FoodPreferences {
        val userId = sessionManager.getCurrentUserId() ?: return FoodPreferences()
        return foodPrefsDao.get(userId)?.toDomain() ?: FoodPreferences()
    }

    override suspend fun updateFoodPreferences(preferences: FoodPreferences) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.FOODPREFS, "updateFoodPreferences with no signed-in user; dropping write")
            return
        }

        foodPrefsDao.upsert(
            FoodPrefsEntity(
                userId = userId,
                dietType = preferences.dietType,
                allergies = preferences.allergies,
                cuisinePreferences = preferences.cuisinePreferences,
                updatedAtEpochMs = Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.FOODPREFS, "food prefs written locally; requesting sync")
        syncScheduler.requestSync()
    }

    private fun FoodPrefsEntity.toDomain() = FoodPreferences(
        dietType = dietType,
        allergies = allergies,
        cuisinePreferences = cuisinePreferences
    )
}
