package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.PointsLedgerDao
import com.hellohealth.data.local.entities.PointsLedgerEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `points_ledger` table (Supabase `points_ledger`, conflict key `id`) — the append-only
 * points ledger. Multi-row per user; follows the [BodyMetricSyncer] template exactly. Bidirectional:
 * [push] upserts unsynced rows incl. tombstones; [pull] applies each [LwwResolver] winner.
 */
class PointsLedgerSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val pointsLedgerDao: PointsLedgerDao,
) : Syncer {

    @Serializable
    data class PointsLedgerDto(
        val id: String,
        val user_id: String,
        val local_date: String,
        val source: String,
        val points: Int,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.POINTS_LEDGER

    override suspend fun push(userId: String): Int {
        val unsynced = pointsLedgerDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["points_ledger"].upsert(value = row.toDto(), onConflict = "id")
            pointsLedgerDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.POINTS_LEDGER, "pushed $pushed points row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["points_ledger"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<PointsLedgerDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = pointsLedgerDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            pointsLedgerDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.POINTS_LEDGER, "pulled $applied remote points row(s)")
        return applied
    }

    companion object {
        internal fun PointsLedgerEntity.toDto() = PointsLedgerDto(
            id = id,
            user_id = userId,
            local_date = localDate,
            source = source,
            points = points,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        internal fun PointsLedgerDto.toEntity(remoteUpdatedAt: Long?) = PointsLedgerEntity(
            id = id,
            userId = user_id,
            localDate = local_date,
            source = source,
            points = points,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
