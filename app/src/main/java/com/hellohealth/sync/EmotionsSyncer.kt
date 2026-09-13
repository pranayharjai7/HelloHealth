package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.EmotionRecordsDao
import com.hellohealth.data.local.entities.EmotionRecordEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Syncs the `emotion_records` table (Supabase `emotion_records`, conflict key `id`). Multi-row per
 * user — follows the [SnapshotSyncer] template rather than the single-row goals/profile syncers.
 *
 * **Bidirectional** (fixes the MyEmotions "insert-only" defect noted in docs/integration/05):
 * [push] upserts local unsynced rows *including tombstones*, so a soft-delete propagates; [pull]
 * fetches remote rows and applies each LWW winner, adopting remote tombstones as local deletes.
 *
 * The DTO carries `updated_at`/`deleted_at` as ISO strings. Until the Supabase table + those
 * columns exist ([run_sql_in_supabase.sql], Step 2), the server omits them,
 * [Timestamps.parseServerTimestamp] yields null and [LwwResolver] never lets remote win — sync
 * degrades to push-only, losslessly.
 */
class EmotionsSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val emotionRecordsDao: EmotionRecordsDao
) : Syncer {

    @Serializable
    data class EmotionSyncDto(
        val id: String,
        val user_id: String,
        val timestamp_utc: String,
        val tz_offset: Int,
        val local_date: String,
        val emotion: String,
        val confidence: Double,
        val source: String,
        val note: String? = null,
        val visibility: String,
        val updated_at: String? = null,
        val deleted_at: String? = null
    )

    override val featureTag = FeatureTag.EMOTIONS

    override suspend fun push(userId: String): Int {
        val unsynced = emotionRecordsDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["emotion_records"].upsert(value = row.toDto(), onConflict = "id")
            // Ack only this exact version — a concurrent local edit (newer updatedAt) stays unsynced.
            emotionRecordsDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.EMOTIONS, "pushed $pushed emotion row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["emotion_records"]
            .select {
                filter { eq("user_id", userId) }
                order("timestamp_utc", Order.ASCENDING)
            }
            .decodeList<EmotionSyncDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = emotionRecordsDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            emotionRecordsDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.EMOTIONS, "pulled $applied remote emotion row(s)")
        return applied
    }

    private fun EmotionRecordEntity.toDto() = EmotionSyncDto(
        id = id,
        user_id = userId,
        timestamp_utc = Timestamps.epochMsToServerTimestamp(timestampUtcEpochMs),
        tz_offset = tzOffsetMinutes,
        local_date = localDate,
        emotion = emotion,
        confidence = confidence,
        source = source,
        note = note,
        visibility = visibility,
        updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
        deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) }
    )

    private fun EmotionSyncDto.toEntity(remoteUpdatedAt: Long?): EmotionRecordEntity {
        val timestampMs = Timestamps.parseServerTimestamp(timestamp_utc) ?: Timestamps.nowEpochMs()
        // Prefer the server's local_date; fall back to deriving it from the UTC instant if absent.
        val resolvedLocalDate = local_date.ifBlank {
            Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }
        return EmotionRecordEntity(
            id = id,
            userId = user_id,
            timestampUtcEpochMs = timestampMs,
            tzOffsetMinutes = tz_offset,
            localDate = resolvedLocalDate,
            emotion = emotion,
            confidence = confidence,
            source = source,
            note = note,
            visibility = visibility,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true
        )
    }
}
