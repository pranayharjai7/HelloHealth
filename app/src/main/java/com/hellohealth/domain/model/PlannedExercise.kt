package com.hellohealth.domain.model

/**
 * One exercise placed on a [WorkoutDay], with its planned targets — the leaf of the planning
 * hierarchy ([WorkoutPlan] → [WorkoutDay] → [PlannedExercise]).
 *
 * [exerciseId] references a row in the read-only global [Exercise] catalog (not a foreign key in
 * the schema — the catalog is seeded per-device and never user-owned). [orderIndex] fixes the
 * exercise's position within the day so reordering is a cheap index rewrite.
 *
 * The `target*` fields are the full vocabulary of what a set of any [LoggingType] can specify;
 * which subset is meaningful is derived at runtime from the referenced exercise (see
 * [deriveLoggingType]) rather than stored, so no target column ever needs migrating when logging
 * rules evolve. All are nullable except [targetSets] (defaults to 3, matching TrackMe). [updatedAt]
 * is the UTC epoch-ms LWW clock; other sync bookkeeping lives on the entity.
 */
data class PlannedExercise(
    val id: String,
    val dayId: String,
    val userId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val updatedAt: Long,
    val targetSets: Int = 3,
    val targetReps: Int? = null,
    val targetWeightKg: Float? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceKm: Float? = null,
    val targetSpeedKmh: Float? = null,
    val targetIncline: Float? = null,
)
