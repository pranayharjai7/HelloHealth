package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.WorkoutSessionDao
import com.hellohealth.data.local.entities.WorkoutSessionEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `workout_sessions` table (Supabase `workout_sessions`, conflict key `id`). Multi-row per
 * user — follows the [EmotionsSyncer] template.
 *
 * **Bidirectional:** [push] upserts local unsynced rows *including tombstones*, so a soft-delete
 * propagates; [pull] fetches remote rows and applies each LWW winner, adopting remote tombstones as
 * local deletes.
 *
 * The DTO carries `updated_at`/`deleted_at` as ISO strings, declared `String? = null`. Until the
 * Supabase table + those columns exist ([run_sql_in_supabase.sql]), the server omits them,
 * [Timestamps.parseServerTimestamp] yields null and [LwwResolver] never lets remote win — sync
 * degrades to push-only, losslessly, and `decodeList` does not throw on the missing columns.
 */
class WorkoutSessionSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val workoutSessionDao: WorkoutSessionDao
) : Syncer {

    @Serializable
    data class WorkoutSyncDto(
        val id: String,
        val user_id: String,
        val activity_type: String,
        val title: String? = null,
        val start_time_utc: String,
        val end_time_utc: String,
        val duration_minutes: Long,
        val calories: Double? = null,
        val distance_km: Double? = null,
        val note: String? = null,
        val local_date: String,
        val updated_at: String? = null,
        val deleted_at: String? = null
    )

    override val featureTag = FeatureTag.WORKOUT

    override suspend fun push(userId: String): Int {
        val unsynced = workoutSessionDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["workout_sessions"].upsert(value = row.toDto(), onConflict = "id")
            // Ack only this exact version — a concurrent local edit (newer updatedAt) stays unsynced.
            workoutSessionDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.WORKOUT, "pushed $pushed workout row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["workout_sessions"]
            .select {
                filter { eq("user_id", userId) }
                order("start_time_utc", Order.ASCENDING)
            }
            .decodeList<WorkoutSyncDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = workoutSessionDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            workoutSessionDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.WORKOUT, "pulled $applied remote workout row(s)")
        return applied
    }

    companion object {
        /** entity → wire DTO. Exposed for unit testing the round-trip mapping. */
        fun toDto(entity: WorkoutSessionEntity) = WorkoutSyncDto(
            id = entity.id,
            user_id = entity.userId,
            activity_type = entity.activityType,
            title = entity.title,
            start_time_utc = Timestamps.epochMsToServerTimestamp(entity.startTimeUtcEpochMs),
            end_time_utc = Timestamps.epochMsToServerTimestamp(entity.endTimeUtcEpochMs),
            duration_minutes = entity.durationMinutes,
            calories = entity.calories,
            distance_km = entity.distanceKm,
            note = entity.note,
            local_date = entity.localDate,
            updated_at = Timestamps.epochMsToServerTimestamp(entity.updatedAtEpochMs),
            deleted_at = entity.deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) }
        )

        /** wire DTO → entity. [remoteUpdatedAt] is the parsed `updated_at` (null → now, degrade-safe). */
        fun toEntity(dto: WorkoutSyncDto, remoteUpdatedAt: Long?): WorkoutSessionEntity {
            val startMs = Timestamps.parseServerTimestamp(dto.start_time_utc) ?: Timestamps.nowEpochMs()
            val endMs = Timestamps.parseServerTimestamp(dto.end_time_utc) ?: startMs
            return WorkoutSessionEntity(
                id = dto.id,
                userId = dto.user_id,
                activityType = dto.activity_type,
                title = dto.title,
                startTimeUtcEpochMs = startMs,
                endTimeUtcEpochMs = endMs,
                durationMinutes = dto.duration_minutes,
                calories = dto.calories,
                distanceKm = dto.distance_km,
                note = dto.note,
                localDate = dto.local_date,
                updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = Timestamps.parseServerTimestamp(dto.deleted_at),
                isSynced = true
            )
        }
    }

    private fun WorkoutSessionEntity.toDto() = Companion.toDto(this)

    private fun WorkoutSyncDto.toEntity(remoteUpdatedAt: Long?) = Companion.toEntity(this, remoteUpdatedAt)
}
