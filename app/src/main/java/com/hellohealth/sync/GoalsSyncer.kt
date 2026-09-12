package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.GoalsDao
import com.hellohealth.data.local.entities.GoalsEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `goals` table (Supabase `activity_goals`, conflict key `user_id`).
 *
 * The DTO carries `updated_at`/`deleted_at` so LWW works once the Supabase migration (Step 11)
 * adds those columns. Before that migration the server omits them, [Timestamps.parseServerTimestamp]
 * yields null, and [LwwResolver] never lets remote win — sync degrades to push-only, losslessly.
 */
class GoalsSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val goalsDao: GoalsDao
) : Syncer {

    @Serializable
    data class GoalsSyncDto(
        val user_id: String,
        val steps_goal: Int,
        val calories_goal: Int,
        val active_minutes_goal: Int,
        val updated_at: String? = null,
        val deleted_at: String? = null
    )

    override val featureTag = FeatureTag.GOALS

    override suspend fun push(userId: String): Int {
        val unsynced = goalsDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["activity_goals"].upsert(
                value = row.toDto(),
                onConflict = "user_id"
            )
            // Ack only this exact version — a concurrent local edit (newer updatedAt) stays unsynced.
            goalsDao.markSynced(row.userId, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.GOALS, "pushed $pushed goals row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["activity_goals"]
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<GoalsSyncDto>()
            ?: return 0

        val remoteUpdatedAt = Timestamps.parseServerTimestamp(remote.updated_at)
        val local = goalsDao.get(userId)

        // Compare against the local row's clock (null if no local row → remote wins if it has one).
        if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
            return 0
        }

        val remoteDeletedAt = Timestamps.parseServerTimestamp(remote.deleted_at)
        goalsDao.upsert(
            GoalsEntity(
                userId = userId,
                steps = remote.steps_goal,
                activeCalories = remote.calories_goal,
                activeMinutes = remote.active_minutes_goal,
                // Remote is the winner — adopt its clock, or now() if the server has no column yet.
                updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
                deletedAtEpochMs = remoteDeletedAt,
                isSynced = true
            )
        )
        AppLogger.d(FeatureTag.GOALS, "pulled remote goals (remote won LWW)")
        return 1
    }

    private fun GoalsEntity.toDto() = GoalsSyncDto(
        user_id = userId,
        steps_goal = steps,
        calories_goal = activeCalories,
        active_minutes_goal = activeMinutes,
        updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
        deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) }
    )
}
