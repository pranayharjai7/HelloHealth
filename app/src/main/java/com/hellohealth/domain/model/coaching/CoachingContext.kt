package com.hellohealth.domain.model.coaching

/**
 * A pure, provider-agnostic snapshot of the user's four health dimensions for "today", assembled by
 * the coaching repository from the activity / emotions / vitals / nutrition repos. It is the ONLY
 * thing sent to the LLM (as compact text) and the ONLY input to the rule-based fallback coach, so it
 * is deliberately small and free of PII beyond the health metrics the user already entered.
 *
 * Every field is nullable — a dimension with no data for today is simply absent from the prompt, and
 * both the LLM path and the rule-based path handle absence gracefully (never fabricate a zero).
 */
data class CoachingContext(
    // Energy balance (the app's flagship number).
    val caloriesConsumed: Int?,
    val calorieBudget: Int?,
    val caloriesOut: Int?,
    val netKcal: Int?,            // caloriesOut - caloriesConsumed; positive = deficit
    // Macros consumed today (grams).
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?,
    val waterMl: Int?,
    // Recovery.
    val readinessScore: Int?,     // 0-100
    val readinessStatus: String?, // e.g. "GOOD"
    val restingHeartRate: Int?,
    val hrvRmssd: Int?,
    val sleepHours: Double?,
    // Activity.
    val steps: Int?,
    // Mood.
    val dominantMoodToday: String?, // e.g. "HAPPINESS"
    val moodCountToday: Int?,
) {
    /** True when at least one dimension has data — the coach only runs when there is something to say. */
    val hasAnyData: Boolean
        get() = listOf(
            caloriesConsumed, calorieBudget, caloriesOut, proteinG, waterMl,
            readinessScore, restingHeartRate, steps, moodCountToday,
        ).any { it != null }

    companion object {
        val EMPTY = CoachingContext(
            caloriesConsumed = null, calorieBudget = null, caloriesOut = null, netKcal = null,
            proteinG = null, carbsG = null, fatG = null, waterMl = null,
            readinessScore = null, readinessStatus = null, restingHeartRate = null, hrvRmssd = null,
            sleepHours = null, steps = null, dominantMoodToday = null, moodCountToday = null,
        )
    }
}
