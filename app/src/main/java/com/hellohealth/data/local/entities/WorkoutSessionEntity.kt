package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `workout_sessions` (Supabase conflict key: `id`) — one row per logged workout
 * session (the ACTUALS of F1 live logging, distinct from the [WorkoutPlanEntity] planning hierarchy).
 * Its sets live in [SessionSetEntity] (logical parent reference by [id]; cascade delete is a
 * repository `@Transaction`, not a schema FK).
 *
 * [planId]/[dayId] optionally anchor a session to the planned day it was started from (both nullable
 * for an ad-hoc session). [status] is `active` | `completed` | `abandoned` — the single-active-session
 * invariant is enforced in the repository. [localDate] buckets the session into a calendar day for the
 * date-aware Health screen. [totalVolumeKg]/[caloriesEstimate] are derived at finish, nullable until.
 *
 * All in natural units (kg, seconds). Syncable columns LAST (matches the project convention). The
 * `userId` index serves per-user sync filtering; `(userId, status)` serves the active-session lookup;
 * `isSynced` the unsynced push scan.
 */
@Entity(
    tableName = "workout_sessions",
    indices = [
        Index(value = ["userId"], name = "idx_workout_sessions_userId"),
        Index(value = ["userId", "status"], name = "idx_workout_sessions_userId_status"),
        Index(value = ["isSynced"], name = "idx_workout_sessions_isSynced"),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val planId: String? = null,
    val dayId: String? = null,
    val title: String? = null,
    val activityType: String,
    val startEpochMs: Long,
    val endEpochMs: Long? = null,
    val durationSeconds: Int? = null,
    val status: String,
    val localDate: String,
    val note: String? = null,
    val totalVolumeKg: Double? = null,
    val caloriesEstimate: Double? = null,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
