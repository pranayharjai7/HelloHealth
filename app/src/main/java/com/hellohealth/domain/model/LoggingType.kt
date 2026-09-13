package com.hellohealth.domain.model

/**
 * How a given exercise is logged / what targets are meaningful for it. DERIVED at runtime from the
 * catalog exercise's category + equipment — never stored — so the rules can evolve without a schema
 * migration (see [PlannedExercise]).
 */
enum class LoggingType(val displayName: String) {
    WEIGHTED_REPS("Weighted · Sets · Reps"),
    BODYWEIGHT_REPS("Bodyweight · Sets · Reps"),
    TIMED("Timed · Sets · Duration"),
    CARDIO("Cardio · Duration · Speed"),
}

/**
 * Pure classifier: maps a catalog exercise's [category] + [equipment] to its [LoggingType].
 *
 * Mirrors TrackMe's ruleset exactly (order matters — the first matching branch wins):
 *  - `Cardio` category            → [LoggingType.CARDIO]
 *  - `Stretching` category        → [LoggingType.TIMED]
 *  - `Plyometrics` category       → [LoggingType.BODYWEIGHT_REPS]
 *  - `body only` equipment        → [LoggingType.BODYWEIGHT_REPS]
 *  - everything else              → [LoggingType.WEIGHTED_REPS]
 *
 * Case-insensitive and null-tolerant, so a missing/odd catalog value degrades to the sensible
 * [LoggingType.WEIGHTED_REPS] default rather than throwing.
 */
fun deriveLoggingType(category: String?, equipment: String?): LoggingType = when {
    category.equals("Cardio", ignoreCase = true) -> LoggingType.CARDIO
    category.equals("Stretching", ignoreCase = true) -> LoggingType.TIMED
    category.equals("Plyometrics", ignoreCase = true) -> LoggingType.BODYWEIGHT_REPS
    equipment.equals("body only", ignoreCase = true) -> LoggingType.BODYWEIGHT_REPS
    else -> LoggingType.WEIGHTED_REPS
}

/** Convenience: the [LoggingType] for this catalog exercise. */
fun Exercise.loggingType(): LoggingType = deriveLoggingType(category, equipment)
