package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.WorkoutPlanDao
import com.hellohealth.data.local.entities.WorkoutPlanEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `workout_plans` table (Supabase `workout_plans`, conflict key `id`) — the top of the
 * planning hierarchy. Multi-row per user; follows the [EmotionsSyncer] template exactly.
 *
 * **Bidirectional**: [push] upserts local unsynced rows *including tombstones* (so a soft-delete
 * propagates), acking each at its exact version; [pull] fetches remote rows and applies each
 * [LwwResolver] winner, adopting remote tombstones as local deletes.
 *
 * The DTO carries `updated_at`/`deleted_at` as ISO strings. Until the Supabase columns exist
 * ([run_sql_in_supabase.sql], Step 10), the server omits them, [Timestamps.parseServerTimestamp]
 * yields null and [LwwResolver] never lets remote win — sync degrades to push-only, losslessly.
 */
class WorkoutPlanSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val workoutPlanDao: WorkoutPlanDao,
) : Syncer {

    @Serializable
    data class WorkoutPlanDto(
        val id: String,
        val user_id: String,
        val name: String,
        val is_active: Boolean,
        val plan_type: String,
        val created_at: String,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.WORKOUT_PLAN

    override suspend fun push(userId: String): Int {
        val unsynced = workoutPlanDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["workout_plans"].upsert(value = row.toDto(), onConflict = "id")
            workoutPlanDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.WORKOUT_PLAN, "pushed $pushed workout plan row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["workout_plans"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<WorkoutPlanDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = workoutPlanDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            workoutPlanDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.WORKOUT_PLAN, "pulled $applied remote workout plan row(s)")
        return applied
    }

    companion object {
        /** Entity → DTO. `internal` so unit tests can verify the wire mapping without a live client. */
        internal fun WorkoutPlanEntity.toDto() = WorkoutPlanDto(
            id = id,
            user_id = userId,
            name = name,
            is_active = isActive,
            plan_type = planType,
            created_at = Timestamps.epochMsToServerTimestamp(createdAtEpochMs),
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        /** DTO → already-synced entity, carrying the resolved remote LWW clock. */
        internal fun WorkoutPlanDto.toEntity(remoteUpdatedAt: Long?) = WorkoutPlanEntity(
            id = id,
            userId = user_id,
            name = name,
            isActive = is_active,
            planType = plan_type,
            createdAtEpochMs = Timestamps.parseServerTimestamp(created_at) ?: Timestamps.nowEpochMs(),
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
