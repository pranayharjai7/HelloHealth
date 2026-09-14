package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.WorkoutDayDao
import com.hellohealth.data.local.entities.WorkoutDayEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `workout_days` table (Supabase `workout_days`, conflict key `id`) — the middle of the
 * planning hierarchy. Multi-row per plan; identical shape to [WorkoutPlanSyncer]/[EmotionsSyncer].
 *
 * Bidirectional, tombstone-carrying, version-exact ack, LWW pull. The syncers push independently;
 * because rows are id-based and reads filter `deletedAtEpochMs IS NULL`, the intended
 * plans→days→planned push order is not required for correctness.
 */
class WorkoutDaySyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val workoutDayDao: WorkoutDayDao,
) : Syncer {

    @Serializable
    data class WorkoutDayDto(
        val id: String,
        val plan_id: String,
        val user_id: String,
        val slot_key: String,
        val name: String,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.WORKOUT_DAY

    override suspend fun push(userId: String): Int {
        val unsynced = workoutDayDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["workout_days"].upsert(value = row.toDto(), onConflict = "id")
            workoutDayDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.WORKOUT_DAY, "pushed $pushed workout day row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["workout_days"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutDayDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = workoutDayDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            workoutDayDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.WORKOUT_DAY, "pulled $applied remote workout day row(s)")
        return applied
    }

    companion object {
        internal fun WorkoutDayEntity.toDto() = WorkoutDayDto(
            id = id,
            plan_id = planId,
            user_id = userId,
            slot_key = slotKey,
            name = name,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        internal fun WorkoutDayDto.toEntity(remoteUpdatedAt: Long?) = WorkoutDayEntity(
            id = id,
            planId = plan_id,
            userId = user_id,
            slotKey = slot_key,
            name = name,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
