package com.hellohealth.domain.model

/**
 * The kind of workout a user can log manually (Phase A). Stored as the enum `name` string in Room
 * and on the wire — converter-free, exactly like [EmotionType] and the profile enums.
 *
 * Deliberately a flat, presentation-agnostic set. A clean mapping to Health Connect exercise-type
 * constants ([androidx.health.connect.client.records.ExerciseSessionRecord] types) is a Phase-D
 * concern (HC write-back) — Phase A only needs a safe round-trip via [fromName].
 */
enum class WorkoutActivityType {
    RUN,
    WALK,
    CYCLE,
    STRENGTH,
    YOGA,
    HIIT,
    SWIM,
    HIKE,
    OTHER;

    /** Human-facing label, e.g. "Strength", "Hiit". */
    fun displayLabel(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    /** A single emoji glyph for compact chip/row display. */
    fun emoji(): String = when (this) {
        RUN -> "🏃"
        WALK -> "🚶"
        CYCLE -> "🚴"
        STRENGTH -> "🏋️"
        YOGA -> "🧘"
        HIIT -> "🔥"
        SWIM -> "🏊"
        HIKE -> "🥾"
        OTHER -> "🤸"
    }

    companion object {
        /**
         * Parse a stored/wire `name` string back to a [WorkoutActivityType]; unknown or null values
         * fall back to [OTHER] so a row written by a newer client/server never crashes a read
         * (defensive per the roadmap guardrail — never `valueOf`, which throws).
         */
        fun fromName(value: String?): WorkoutActivityType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}
