package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.FoodPrefsDao
import com.hellohealth.data.local.entities.FoodPrefsEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `food_prefs` table (Supabase `food_preferences`, conflict key `user_id`).
 * Note the server column for cuisines is `cuisines`, not `cuisine_preferences`.
 *
 * DTO carries `updated_at`/`deleted_at` for LWW; pre-Step-11 the server omits them and remote
 * never wins ([LwwResolver]), so sync degrades to push-only losslessly.
 */
class FoodPrefsSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val foodPrefsDao: FoodPrefsDao
) : Syncer {

    @Serializable
    data class FoodPrefsSyncDto(
        val user_id: String,
        val diet_type: String,
        val allergies: List<String>,
        val cuisines: List<String>,
        val updated_at: String? = null,
        val deleted_at: String? = null
    )

    override val featureTag = FeatureTag.FOODPREFS

    override suspend fun push(userId: String): Int {
        val unsynced = foodPrefsDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["food_preferences"].upsert(value = row.toDto(), onConflict = "user_id")
            foodPrefsDao.markSynced(row.userId, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.FOODPREFS, "pushed $pushed food-prefs row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["food_preferences"]
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<FoodPrefsSyncDto>()
            ?: return 0

        val remoteUpdatedAt = Timestamps.parseServerTimestamp(remote.updated_at)
        val local = foodPrefsDao.get(userId)

        if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
            return 0
        }

        foodPrefsDao.upsert(
            FoodPrefsEntity(
                userId = userId,
                dietType = remote.diet_type,
                allergies = remote.allergies,
                cuisinePreferences = remote.cuisines,
                updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = Timestamps.parseServerTimestamp(remote.deleted_at),
                isSynced = true
            )
        )
        AppLogger.d(FeatureTag.FOODPREFS, "pulled remote food prefs (remote won LWW)")
        return 1
    }

    private fun FoodPrefsEntity.toDto() = FoodPrefsSyncDto(
        user_id = userId,
        diet_type = dietType,
        allergies = allergies,
        cuisines = cuisinePreferences,
        updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
        deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) }
    )
}
