package com.hellohealth.domain.model

/**
 * A [PlannedExercise] joined with its catalog [Exercise] for display on the Day screen. The
 * repository resolves [exercise] by [PlannedExercise.exerciseId] against the read-only catalog.
 *
 * [exercise] is nullable so a planned row whose catalog entry is missing (e.g. seeding hasn't
 * completed, or a future catalog dropped an id) still renders with a graceful "unknown exercise"
 * fallback instead of being silently dropped. [loggingType] is the runtime-derived classification
 * (null when [exercise] is null) so the UI can pick which targets to show without re-deriving.
 *
 * A data class rather than a bare Pair so each field is named — deliberate, so this is easy to read
 * and extend later (e.g. adding last-session stats) without touching call sites positionally.
 */
data class PlannedExerciseWithDetails(
    val planned: PlannedExercise,
    val exercise: Exercise?,
) {
    val loggingType: LoggingType? = exercise?.loggingType()
}
