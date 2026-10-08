package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.StreakDao
import com.hellohealth.data.local.entities.StreakEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `streaks` table (Supabase `streaks`, conflict key `id`) — one row per user per pillar.
 * Multi-row per user; follows the [BodyMetricSyncer] template exactly. Bidirectional: [push] upserts
 * unsynced rows incl. tombstones; [pull] applies each [LwwResolver] winner.
 */
class StreakSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val streakDao: StreakDao,
) : Syncer {

    @Serializable
    data class StreakDto(
        val id: String,
        val user_id: String,
        val pillar: String,
        val current_count: Int,
        val longest_count: Int,
        val last_hit_local_date: String? = null,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.WELLNESS

    override suspend fun push(userId: String): Int {
        val unsynced = streakDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["streaks"].upsert(value = row.toDto(), onConflict = "id")
            streakDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.WELLNESS, "pushed $pushed streak row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["streaks"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<StreakDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = streakDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            streakDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.WELLNESS, "pulled $applied remote streak row(s)")
        return applied
    }

    companion object {
        internal fun StreakEntity.toDto() = StreakDto(
            id = id,
            user_id = userId,
            pillar = pillar,
            current_count = currentCount,
            longest_count = longestCount,
            last_hit_local_date = lastHitLocalDate,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        internal fun StreakDto.toEntity(remoteUpdatedAt: Long?) = StreakEntity(
            id = id,
            userId = user_id,
            pillar = pillar,
            currentCount = current_count,
            longestCount = longest_count,
            lastHitLocalDate = last_hit_local_date,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
