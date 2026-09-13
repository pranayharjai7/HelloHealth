package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `planned_exercises` (Supabase conflict key: `id`) — the leaf of the planning
 * hierarchy ([WorkoutPlanEntity] → [WorkoutDayEntity] → this). Multiple rows per day.
 *
 * [exerciseId] references the read-only global `exercises` catalog by id (NOT a Room foreign key —
 * the catalog is seeded per-device, not user-owned). [dayId] is a logical parent reference (cascade
 * delete is an explicit repository `@Transaction`, not a schema FK). [userId] is denormalized for
 * per-user sync filtering. [orderIndex] fixes display order within the day.
 *
 * The `target*` columns are the full target vocabulary; all nullable except [targetSets]. Which
 * subset is meaningful is derived at runtime (`deriveLoggingType`), never stored — so no target
 * column ever needs migrating. `Int?` → INTEGER nullable, `Float?` → REAL nullable.
 *
 * Syncable columns LAST (matches MIGRATION_8_9). The `(dayId, deletedAtEpochMs, orderIndex)`
 * composite index serves the ordered "live exercises of a day" read; `isSynced` the unsynced scan.
 */
@Entity(
    tableName = "planned_exercises",
    indices = [
        Index(
            value = ["dayId", "deletedAtEpochMs", "orderIndex"],
            name = "idx_planned_exercises_dayId_deletedAt_order",
        ),
        Index(value = ["userId"], name = "idx_planned_exercises_userId"),
        Index(value = ["isSynced"], name = "idx_planned_exercises_isSynced"),
    ],
)
data class PlannedExerciseEntity(
    @PrimaryKey val id: String,
    val dayId: String,
    val userId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: Int? = null,
    val targetWeightKg: Float? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Float? = null,
    val targetSpeedKmh: Float? = null,
    val targetIncline: Float? = null,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
