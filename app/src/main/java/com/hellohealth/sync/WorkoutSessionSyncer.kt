package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.WorkoutSessionDao
import com.hellohealth.data.local.entities.WorkoutSessionEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `workout_sessions` table (Supabase `workout_sessions`, conflict key `id`) — the logged
 * workout actuals of F1. Multi-row per user; follows the [BodyMetricSyncer]/[VitalsSampleSyncer]
 * template exactly. Bidirectional: [push] upserts unsynced rows incl. tombstones; [pull] applies each
 * [LwwResolver] winner. Natural units on the wire (seconds, kg) — no scaling.
 */
class WorkoutSessionSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val workoutSessionDao: WorkoutSessionDao,
) : Syncer {

    @Serializable
    data class WorkoutSessionDto(
        val id: String,
        val user_id: String,
        val plan_id: String? = null,
        val day_id: String? = null,
        val title: String? = null,
        val activity_type: String,
        val start_utc: String,
        val end_utc: String? = null,
        val duration_seconds: Int? = null,
        val status: String,
        val local_date: String,
        val note: String? = null,
        val total_volume_kg: Double? = null,
        val calories_estimate: Double? = null,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.WORKOUT_SESSION

    override suspend fun push(userId: String): Int {
        val unsynced = workoutSessionDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["workout_sessions"].upsert(value = row.toDto(), onConflict = "id")
            workoutSessionDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.WORKOUT_SESSION, "pushed $pushed workout session(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["workout_sessions"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutSessionDto>()
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
        AppLogger.d(FeatureTag.WORKOUT_SESSION, "pulled $applied remote workout session(s)")
        return applied
    }

    companion object {
        /** Entity → DTO. `internal` so unit tests can verify the wire mapping without a live client. */
        internal fun WorkoutSessionEntity.toDto() = WorkoutSessionDto(
            id = id,
            user_id = userId,
            plan_id = planId,
            day_id = dayId,
            title = title,
            activity_type = activityType,
            start_utc = Timestamps.epochMsToServerTimestamp(startEpochMs),
            end_utc = endEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
            duration_seconds = durationSeconds,
            status = status,
            local_date = localDate,
            note = note,
            total_volume_kg = totalVolumeKg,
            calories_estimate = caloriesEstimate,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        /** DTO → already-synced entity, carrying the resolved remote LWW clock. */
        internal fun WorkoutSessionDto.toEntity(remoteUpdatedAt: Long?) = WorkoutSessionEntity(
            id = id,
            userId = user_id,
            planId = plan_id,
            dayId = day_id,
            title = title,
            activityType = activity_type,
            startEpochMs = Timestamps.parseServerTimestamp(start_utc) ?: Timestamps.nowEpochMs(),
            endEpochMs = Timestamps.parseServerTimestamp(end_utc),
            durationSeconds = duration_seconds,
            status = status,
            localDate = local_date,
            note = note,
            totalVolumeKg = total_volume_kg,
            caloriesEstimate = calories_estimate,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
