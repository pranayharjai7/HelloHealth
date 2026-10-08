package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.AchievementDao
import com.hellohealth.data.local.entities.AchievementEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `achievements` table (Supabase `achievements`, conflict key `id`) — one row per user per
 * earned achievement. Multi-row per user; follows the [BodyMetricSyncer] template exactly.
 * Bidirectional: [push] upserts unsynced rows incl. tombstones; [pull] applies each [LwwResolver]
 * winner. `unlocked_at` rides the wire as an ISO timestamp.
 */
class AchievementSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val achievementDao: AchievementDao,
) : Syncer {

    @Serializable
    data class AchievementDto(
        val id: String,
        val user_id: String,
        val code: String,
        val unlocked_at: String,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.ACHIEVEMENT

    override suspend fun push(userId: String): Int {
        val unsynced = achievementDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["achievements"].upsert(value = row.toDto(), onConflict = "id")
            achievementDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.ACHIEVEMENT, "pushed $pushed achievement row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["achievements"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<AchievementDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = achievementDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            achievementDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.ACHIEVEMENT, "pulled $applied remote achievement row(s)")
        return applied
    }

    companion object {
        internal fun AchievementEntity.toDto() = AchievementDto(
            id = id,
            user_id = userId,
            code = code,
            unlocked_at = Timestamps.epochMsToServerTimestamp(unlockedAtEpochMs),
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        internal fun AchievementDto.toEntity(remoteUpdatedAt: Long?) = AchievementEntity(
            id = id,
            userId = user_id,
            code = code,
            unlockedAtEpochMs = Timestamps.parseServerTimestamp(unlocked_at) ?: Timestamps.nowEpochMs(),
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
