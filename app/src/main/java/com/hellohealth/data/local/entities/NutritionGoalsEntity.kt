package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `nutrition_goals` (Supabase conflict key: `user_id`). One row per user — holds the
 * user's optional overrides for the otherwise-derived nutrition targets (calorie budget, macro grams,
 * fiber, and which micronutrients to track). Mirrors [GoalsEntity].
 *
 * `trackedMicros` is persisted as a comma-joined list of [com.hellohealth.domain.health.MicronutrientTargets.Micro]
 * names (Room has no Set column type); `macroMode` as the enum name. Nullable override columns mean
 * "no override for that value". The 4 [Syncable] columns come LAST, per the project convention.
 */
@Entity(tableName = "nutrition_goals")
data class NutritionGoalsEntity(
    @PrimaryKey val userId: String,
    val useCustomCalories: Boolean,
    val calorieBudget: Int?,
    val useCustomMacros: Boolean,
    val macroMode: String,
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?,
    val fiberG: Int?,
    val trackedMicros: String,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
