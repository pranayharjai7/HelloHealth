package com.hellohealth.domain.model

/**
 * A single manually-logged workout session (Phase A of the native workout system).
 *
 * Plain domain model — no sync metadata (that lives on
 * [com.hellohealth.data.local.entities.WorkoutSessionEntity]). Mirrors the [EmotionRecord] shape:
 * a stable string [id] (a random UUID minted at save time so two distinct workouts in the same
 * instant never collide), a UTC-epoch time range, and a denormalized [localDate] for cheap
 * day-scoped / windowed reads.
 *
 * [durationMinutes] is stored explicitly rather than derived from the range so a user can log a
 * duration without pinning exact start/end wall-clock times. [calories] and [distanceKm] are
 * optional (null = not entered). This is a *manual* session — it is never sourced from, nor written
 * back to, Health Connect in Phase A (HC write-back is Phase D); the existing read-only HC activity
 * path is untouched.
 */
data class WorkoutSession(
    val id: String,
    val userId: String,
    val activityType: WorkoutActivityType,
    val title: String?,
    val startTimeUtcEpochMs: Long,
    val endTimeUtcEpochMs: Long,
    val durationMinutes: Long,
    val calories: Double? = null,
    val distanceKm: Double? = null,
    val note: String? = null,
    val localDate: String
)
