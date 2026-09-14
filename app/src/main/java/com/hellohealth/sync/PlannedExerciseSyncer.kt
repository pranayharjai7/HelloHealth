package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.PlannedExerciseDao
import com.hellohealth.data.local.entities.PlannedExerciseEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `planned_exercises` table (Supabase `planned_exercises`, conflict key `id`) — the leaf of
 * the planning hierarchy. Multi-row per day; identical shape to its parent syncers.
 *
 * Carries the full nullable target vocabulary on the wire. `exercise_id` references the read-only
 * catalog by id — the catalog itself is seeded per-device and never synced, so no catalog data
 * crosses here. Bidirectional, tombstone-carrying, version-exact ack, LWW pull.
 */
class PlannedExerciseSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val plannedExerciseDao: PlannedExerciseDao,
) : Syncer {

    @Serializable
    data class PlannedExerciseDto(
        val id: String,
        val day_id: String,
        val user_id: String,
        val exercise_id: String,
        val order_index: Int,
        val target_sets: Int,
        val target_reps: Int? = null,
        val target_weight_kg: Float? = null,
        val target_duration_seconds: Int? = null,
        val target_distance_km: Float? = null,
        val target_speed_kmh: Float? = null,
        val target_incline: Float? = null,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.PLANNED_EXERCISE

    override suspend fun push(userId: String): Int {
        val unsynced = plannedExerciseDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["planned_exercises"].upsert(value = row.toDto(), onConflict = "id")
            plannedExerciseDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.PLANNED_EXERCISE, "pushed $pushed planned exercise row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["planned_exercises"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<PlannedExerciseDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = plannedExerciseDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            plannedExerciseDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.PLANNED_EXERCISE, "pulled $applied remote planned exercise row(s)")
        return applied
    }

    companion object {
        internal fun PlannedExerciseEntity.toDto() = PlannedExerciseDto(
            id = id,
            day_id = dayId,
            user_id = userId,
            exercise_id = exerciseId,
            order_index = orderIndex,
            target_sets = targetSets,
            target_reps = targetReps,
            target_weight_kg = targetWeightKg,
            target_duration_seconds = targetDurationSeconds,
            target_distance_km = targetDistanceKm,
            target_speed_kmh = targetSpeedKmh,
            target_incline = targetIncline,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        internal fun PlannedExerciseDto.toEntity(remoteUpdatedAt: Long?) = PlannedExerciseEntity(
            id = id,
            dayId = day_id,
            userId = user_id,
            exerciseId = exercise_id,
            orderIndex = order_index,
            targetSets = target_sets,
            targetReps = target_reps,
            targetWeightKg = target_weight_kg,
            targetDurationSeconds = target_duration_seconds,
            targetDistanceKm = target_distance_km,
            targetSpeedKmh = target_speed_kmh,
            targetIncline = target_incline,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
