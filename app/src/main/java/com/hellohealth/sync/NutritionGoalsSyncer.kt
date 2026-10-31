package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.NutritionGoalsDao
import com.hellohealth.data.local.entities.NutritionGoalsEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `nutrition_goals` table (Supabase conflict key `user_id`). Mirrors [GoalsSyncer] — push
 * unsynced rows, pull with last-write-wins. One row per user.
 */
class NutritionGoalsSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val dao: NutritionGoalsDao,
) : Syncer {

    @Serializable
    data class NutritionGoalsDto(
        val user_id: String,
        val use_custom_calories: Boolean,
        val calorie_budget: Int? = null,
        val use_custom_macros: Boolean,
        val macro_mode: String,
        val protein_g: Int? = null,
        val carbs_g: Int? = null,
        val fat_g: Int? = null,
        val fiber_g: Int? = null,
        val tracked_micros: String,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.NUTRITION

    override suspend fun push(userId: String): Int {
        val unsynced = dao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0
        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["nutrition_goals"].upsert(value = row.toDto(), onConflict = "user_id")
            dao.markSynced(row.userId, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.NUTRITION, "pushed $pushed nutrition-goals row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["nutrition_goals"]
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<NutritionGoalsDto>()
            ?: return 0

        val remoteUpdatedAt = Timestamps.parseServerTimestamp(remote.updated_at)
        val local = dao.get(userId)
        if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
            return 0
        }
        dao.upsert(remote.toEntity(remoteUpdatedAt ?: Timestamps.nowEpochMs()))
        AppLogger.d(FeatureTag.NUTRITION, "pulled remote nutrition goals (remote won LWW)")
        return 1
    }

    companion object {
        internal fun NutritionGoalsEntity.toDto() = NutritionGoalsDto(
            user_id = userId,
            use_custom_calories = useCustomCalories,
            calorie_budget = calorieBudget,
            use_custom_macros = useCustomMacros,
            macro_mode = macroMode,
            protein_g = proteinG,
            carbs_g = carbsG,
            fat_g = fatG,
            fiber_g = fiberG,
            tracked_micros = trackedMicros,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        internal fun NutritionGoalsDto.toEntity(resolvedUpdatedAt: Long) = NutritionGoalsEntity(
            userId = user_id,
            useCustomCalories = use_custom_calories,
            calorieBudget = calorie_budget,
            useCustomMacros = use_custom_macros,
            macroMode = macro_mode,
            proteinG = protein_g,
            carbsG = carbs_g,
            fatG = fat_g,
            fiberG = fiber_g,
            trackedMicros = tracked_micros,
            updatedAtEpochMs = resolvedUpdatedAt,
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
