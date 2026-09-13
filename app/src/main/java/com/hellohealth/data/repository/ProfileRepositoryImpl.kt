package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.ProfileDao
import com.hellohealth.data.local.entities.ProfileEntity
import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.sync.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first profile repository (Supabase `profiles`, conflict key `id`). Reads come from Room;
 * writes hit Room (`isSynced=false`) then poke [SyncScheduler]. [com.hellohealth.sync.ProfileSyncer]
 * owns the Supabase push/pull.
 *
 * Enum vitals are persisted as their `name` string in [ProfileEntity] and mapped back to enums here;
 * an unrecognized stored value degrades to null rather than crashing.
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler
) : ProfileRepository {

    override suspend fun getProfile(): UserProfile? {
        val userId = sessionManager.getCurrentUserId() ?: return null
        return profileDao.get(userId)?.toDomain()
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.PROFILE, "upsertProfile with no signed-in user; dropping write")
            return
        }

        profileDao.upsert(
            ProfileEntity(
                userId = userId,
                displayName = profile.displayName?.trim().orEmpty().ifBlank { null },
                updatedAtEpochMs = Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = null,
                isSynced = false,
                gender = profile.gender?.name,
                birthDateEpochDay = profile.birthDateEpochDay,
                heightCm = profile.heightCm,
                weightKg = profile.weightKg,
                activityLevel = profile.activityLevel?.name,
                goalType = profile.goalType?.name,
                targetWeightKg = profile.targetWeightKg,
                targetRateKgPerWeek = profile.targetRateKgPerWeek,
                unitPreference = profile.unitPreference.name,
                hasOnboarded = profile.hasOnboarded
            )
        )
        AppLogger.d(FeatureTag.PROFILE, "profile written locally; requesting sync")
        syncScheduler.requestSync()
    }

    private fun ProfileEntity.toDomain() = UserProfile(
        displayName = displayName,
        gender = gender.toEnumOrNull<Gender>(),
        birthDateEpochDay = birthDateEpochDay,
        heightCm = heightCm,
        weightKg = weightKg,
        activityLevel = activityLevel.toEnumOrNull<ActivityLevel>(),
        goalType = goalType.toEnumOrNull<GoalType>(),
        targetWeightKg = targetWeightKg,
        targetRateKgPerWeek = targetRateKgPerWeek,
        unitPreference = unitPreference.toEnumOrNull<UnitPreference>() ?: UnitPreference.METRIC,
        hasOnboarded = hasOnboarded
    )
}

/** Parse an enum by name, degrading an unknown/null stored value to null instead of throwing. */
private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
