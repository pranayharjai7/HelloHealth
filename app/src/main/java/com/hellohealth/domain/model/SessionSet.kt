package com.hellohealth.domain.model

/**
 * One logged set within a [WorkoutSession]. [sessionId] is the logical parent reference.
 *
 * [plannedExerciseId] optionally links the set back to the plan row it was prefilled from;
 * [exerciseId] references the read-only global [Exercise] catalog. [orderIndex] + [setNumber] fix
 * display order within the session. The measurement fields are the full logging vocabulary; which
 * subset is meaningful is derived at runtime from the exercise's [LoggingType], never stored.
 * [isWarmup]/[isCompleted]/[isSkipped] are the set's state.
 *
 * Natural units (kg, seconds, km). [loggedAt]/[updatedAt] are UTC epoch millis; [updatedAt] is the
 * LWW clock. Sync bookkeeping lives on the entity.
 */
data class SessionSet(
    val id: String,
    val sessionId: String,
    val userId: String,
    val plannedExerciseId: String?,
    val exerciseId: String,
    val orderIndex: Int,
    val setNumber: Int,
    val reps: Int?,
    val weightKg: Double?,
    val durationSeconds: Int?,
    val distanceKm: Double?,
    val rpe: Double?,
    val isWarmup: Boolean,
    val isCompleted: Boolean,
    val isSkipped: Boolean,
    val loggedAt: Long,
    val updatedAt: Long,
) {
    /** Volume contribution (reps × weight) of this set, or null when it isn't a weighted-reps set. */
    val volumeKg: Double?
        get() = if (isCompleted && !isSkipped && reps != null && weightKg != null) {
            reps * weightKg
        } else {
            null
        }
}
