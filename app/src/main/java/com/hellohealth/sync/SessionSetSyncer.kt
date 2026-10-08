package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.SessionSetDao
import com.hellohealth.data.local.entities.SessionSetEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `session_sets` table (Supabase `session_sets`, conflict key `id`) — the logged sets of
 * F1 workout sessions. Multi-row per user; follows the [WorkoutSessionSyncer] template exactly.
 * Bidirectional: [push] upserts unsynced rows incl. tombstones; [pull] applies each [LwwResolver]
 * winner. Natural units on the wire (kg, seconds, km) — no scaling.
 */
class SessionSetSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionSetDao: SessionSetDao,
) : Syncer {

    @Serializable
    data class SessionSetDto(
        val id: String,
        val session_id: String,
        val user_id: String,
        val planned_exercise_id: String? = null,
        val exercise_id: String,
        val order_index: Int,
        val set_number: Int,
        val reps: Int? = null,
        val weight_kg: Double? = null,
        val duration_seconds: Int? = null,
        val distance_km: Double? = null,
        val rpe: Double? = null,
        val is_warmup: Boolean,
        val is_completed: Boolean,
        val is_skipped: Boolean,
        val logged_at_utc: String,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.SESSION_SET

    override suspend fun push(userId: String): Int {
        val unsynced = sessionSetDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["session_sets"].upsert(value = row.toDto(), onConflict = "id")
            sessionSetDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.SESSION_SET, "pushed $pushed session set(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["session_sets"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<SessionSetDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = sessionSetDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            sessionSetDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.SESSION_SET, "pulled $applied remote session set(s)")
        return applied
    }

    companion object {
        /** Entity → DTO. `internal` so unit tests can verify the wire mapping without a live client. */
        internal fun SessionSetEntity.toDto() = SessionSetDto(
            id = id,
            session_id = sessionId,
            user_id = userId,
            planned_exercise_id = plannedExerciseId,
            exercise_id = exerciseId,
            order_index = orderIndex,
            set_number = setNumber,
            reps = reps,
            weight_kg = weightKg,
            duration_seconds = durationSeconds,
            distance_km = distanceKm,
            rpe = rpe,
            is_warmup = isWarmup,
            is_completed = isCompleted,
            is_skipped = isSkipped,
            logged_at_utc = Timestamps.epochMsToServerTimestamp(loggedAtEpochMs),
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        /** DTO → already-synced entity, carrying the resolved remote LWW clock. */
        internal fun SessionSetDto.toEntity(remoteUpdatedAt: Long?) = SessionSetEntity(
            id = id,
            sessionId = session_id,
            userId = user_id,
            plannedExerciseId = planned_exercise_id,
            exerciseId = exercise_id,
            orderIndex = order_index,
            setNumber = set_number,
            reps = reps,
            weightKg = weight_kg,
            durationSeconds = duration_seconds,
            distanceKm = distance_km,
            rpe = rpe,
            isWarmup = is_warmup,
            isCompleted = is_completed,
            isSkipped = is_skipped,
            loggedAtEpochMs = Timestamps.parseServerTimestamp(logged_at_utc) ?: Timestamps.nowEpochMs(),
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
