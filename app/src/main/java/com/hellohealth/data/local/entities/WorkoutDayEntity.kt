package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `workout_days` (Supabase conflict key: `id`) — the middle of the planning
 * hierarchy ([WorkoutPlanEntity] → this → [PlannedExerciseEntity]). Multiple rows per plan.
 *
 * [slotKey] is an opaque, stable String locating the day within its plan's schedule; its allowed
 * values depend on the parent plan's type (see `PlanType.slotKeysFor`). [userId] is denormalized
 * onto the row so the per-user syncer can filter without a join. [planId] is a logical parent
 * reference, NOT a Room foreign key — cascade delete is done explicitly in a repository
 * `@Transaction` (soft tombstones), never by the schema.
 *
 * Syncable columns LAST (matches MIGRATION_8_9). The `(planId, deletedAtEpochMs)` composite index
 * serves the "live days of a plan" read; `isSynced` serves the unsynced scan.
 */
@Entity(
    tableName = "workout_days",
    indices = [
        Index(value = ["planId", "deletedAtEpochMs"], name = "idx_workout_days_planId_deletedAt"),
        Index(value = ["userId"], name = "idx_workout_days_userId"),
        Index(value = ["isSynced"], name = "idx_workout_days_isSynced"),
    ],
)
data class WorkoutDayEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val userId: String,
    val slotKey: String,
    val name: String,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
