package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `session_sets` (Supabase conflict key: `id`) — one row per logged set within a
 * [WorkoutSessionEntity]. [sessionId] is a logical parent reference (cascade delete is a repository
 * `@Transaction`, not a schema FK, mirroring [PlannedExerciseEntity]).
 *
 * [plannedExerciseId] optionally links back to the plan the set was prefilled from; [exerciseId]
 * references the read-only global `exercises` catalog by id (NOT a Room FK — the catalog is
 * device-seeded, not user-owned). [orderIndex] + [setNumber] fix display order within the session.
 * The measurement columns are the full logging vocabulary; which subset is meaningful is derived from
 * the exercise type at runtime, never stored. [isWarmup]/[isCompleted]/[isSkipped] are the set's state.
 *
 * Natural units (kg, seconds, km). Syncable columns LAST. The `sessionId` index serves the "sets of a
 * session" read; `userId` per-user sync filtering; `isSynced` the unsynced scan.
 */
@Entity(
    tableName = "session_sets",
    indices = [
        Index(value = ["sessionId"], name = "idx_session_sets_sessionId"),
        Index(value = ["userId"], name = "idx_session_sets_userId"),
        Index(value = ["isSynced"], name = "idx_session_sets_isSynced"),
    ],
)
data class SessionSetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val userId: String,
    val plannedExerciseId: String? = null,
    val exerciseId: String,
    val orderIndex: Int,
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceKm: Double? = null,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val isCompleted: Boolean = true,
    val isSkipped: Boolean = false,
    val loggedAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
