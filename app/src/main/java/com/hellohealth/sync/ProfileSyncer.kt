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
                isSynced = true
            )
        )
        AppLogger.d(FeatureTag.PROFILE, "pulled remote profile (remote won LWW)")
        return 1
    }

    private fun ProfileEntity.toDto() = ProfileSyncDto(
        id = userId,
        display_name = displayName,
        updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
        deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) }
    )
}
