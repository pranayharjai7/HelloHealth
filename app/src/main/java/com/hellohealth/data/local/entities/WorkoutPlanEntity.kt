package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `workout_plans` (Supabase conflict key: `id`) — the top of the planning hierarchy
 * ([WorkoutDayEntity] → [PlannedExerciseEntity]). Multiple rows per user; at most one [isActive] at
 * a time (a repository invariant, not a schema constraint, so it stays easy to relax).
 *
 * [planType] is stored as the [com.hellohealth.domain.model.PlanType] `name` string (converter-free,
 * like the profile/emotion enums); decode defensively via `PlanType.fromName`. [createdAt] is set
 * once; [updatedAtEpochMs] is the LWW clock bumped on every write.
 *
 * The four [Syncable] sync-meta columns are declared LAST so Room appends them after the feature
 * columns — matching the MIGRATION_8_9 `CREATE TABLE` column order (see Migrations.kt). Indices on
 * `userId` (per-user pull) and `isSynced` (unsynced scan) match the migration's CREATE INDEX set.
 */
@Entity(
    tableName = "workout_plans",
    indices = [
        Index(value = ["userId"], name = "idx_workout_plans_userId"),
        Index(value = ["isSynced"], name = "idx_workout_plans_isSynced"),
    ],
)
data class WorkoutPlanEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val isActive: Boolean,
    val planType: String,
    val createdAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
