package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.ProfileDao
import com.hellohealth.data.local.entities.ProfileEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `profile` table (Supabase `profiles`, conflict key `id`).
 *
 * Like [GoalsSyncer], the DTO carries `updated_at`/`deleted_at` for LWW; before the Step 11 server
 * migration those are absent and [LwwResolver] never lets remote win (push-only, lossless).
 */
class ProfileSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val profileDao: ProfileDao
) : Syncer {

    @Serializable
    data class ProfileSyncDto(
        val id: String,
        val display_name: String? = null,
        val gender: String? = null,
        val birth_date_epoch_day: Long? = null,
        val height_cm: Double? = null,
        val weight_kg: Double? = null,
        val activity_level: String? = null,
        val goal_type: String? = null,
        val target_weight_kg: Double? = null,
        val target_rate_kg_per_week: Double? = null,
        val unit_preference: String? = null,
        val has_onboarded: Boolean? = null,
        val updated_at: String? = null,
        val deleted_at: String? = null
    )

    override val featureTag = FeatureTag.PROFILE

    override suspend fun push(userId: String): Int {
        val unsynced = profileDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["profiles"].upsert(value = row.toDto(), onConflict = "id")
            profileDao.markSynced(row.userId, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.PROFILE, "pushed $pushed profile row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["profiles"]
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<ProfileSyncDto>()
            ?: return 0

        val remoteUpdatedAt = Timestamps.parseServerTimestamp(remote.updated_at)
        val local = profileDao.get(userId)

        if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
            return 0
        }

        profileDao.upsert(
            ProfileEntity(
                userId = userId,
                displayName = remote.display_name,
                updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = Timestamps.parseServerTimestamp(remote.deleted_at),
                isSynced = true,
                gender = remote.gender,
                birthDateEpochDay = remote.birth_date_epoch_day,
                heightCm = remote.height_cm,
                weightKg = remote.weight_kg,
                activityLevel = remote.activity_level,
                goalType = remote.goal_type,
                targetWeightKg = remote.target_weight_kg,
                targetRateKgPerWeek = remote.target_rate_kg_per_week,
                unitPreference = remote.unit_preference ?: "METRIC",
                hasOnboarded = remote.has_onboarded ?: false
            )
        )
        AppLogger.d(FeatureTag.PROFILE, "pulled remote profile (remote won LWW)")
        return 1
    }

    private fun ProfileEntity.toDto() = ProfileSyncDto(
        id = userId,
        display_name = displayName,
        gender = gender,
        birth_date_epoch_day = birthDateEpochDay,
        height_cm = heightCm,
        weight_kg = weightKg,
        activity_level = activityLevel,
        goal_type = goalType,
        target_weight_kg = targetWeightKg,
        target_rate_kg_per_week = targetRateKgPerWeek,
        unit_preference = unitPreference,
        has_onboarded = hasOnboarded,
        updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
        deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) }
    )
}
