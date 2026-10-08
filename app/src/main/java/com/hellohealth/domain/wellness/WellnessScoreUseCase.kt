package com.hellohealth.domain.wellness

/**
 * A 0–100 daily wellness score fusing the four currently-tracked pillars (activity, nutrition,
 * recovery/vitals, mood). PURE — no I/O, no clock; the caller supplies each pillar's sub-score (also
 * 0–100) or null when that pillar has no data for the day.
 *
 * Core rule — **skip missing pillars and renormalize over the present ones**: a day that only logged
 * activity + mood is scored over those two weights (not penalized for the two gaps). A day with no
 * pillars at all has a null score (the UI shows an empty state, never a fake 0). This mirrors
 * [com.hellohealth.domain.vitals.ReadinessScoreCalculator]'s dynamic weight redistribution so the two
 * scores behave consistently.
 *
 * The design is additive: later pillars (sleep quality, workout adherence, …) slot in as new weighted
 * inputs without changing the renormalization math.
 */
class WellnessScoreUseCase {

    /**
     * Fuse the four pillar sub-scores into one 0–100 score, or null when every pillar is absent.
     * Each input is a 0–100 "how well did this pillar go today" value; null = not tracked today.
     */
    operator fun invoke(
        activity: Int?,
        nutrition: Int?,
        recovery: Int?,
        mood: Int?,
    ): WellnessScore {
        val present = buildList {
            activity?.let { add(Pillar.ACTIVITY to it.coerceIn(0, 100)) }
            nutrition?.let { add(Pillar.NUTRITION to it.coerceIn(0, 100)) }
            recovery?.let { add(Pillar.RECOVERY to it.coerceIn(0, 100)) }
            mood?.let { add(Pillar.MOOD to it.coerceIn(0, 100)) }
        }
        if (present.isEmpty()) {
            return WellnessScore(score = null, pillars = emptyList(), trackedCount = 0)
        }
        // Renormalize the base weights over only the present pillars.
        val totalWeight = present.sumOf { BASE_WEIGHTS.getValue(it.first).toDouble() }
        val weighted = present.sumOf { (pillar, value) ->
            value * (BASE_WEIGHTS.getValue(pillar) / totalWeight)
        }
        return WellnessScore(
            score = weighted.toInt().coerceIn(0, 100),
            pillars = present.map { PillarScore(it.first, it.second) },
            trackedCount = present.size,
        )
    }

    private companion object {
        /** Base weights; renormalized over whichever pillars are present. Activity & recovery lead. */
        val BASE_WEIGHTS = mapOf(
            Pillar.ACTIVITY to 0.30f,
            Pillar.NUTRITION to 0.20f,
            Pillar.RECOVERY to 0.30f,
            Pillar.MOOD to 0.20f,
        )
    }
}

/** The four pillars the launch wellness score fuses. */
enum class Pillar(val displayName: String) {
    ACTIVITY("Activity"),
    NUTRITION("Nutrition"),
    RECOVERY("Recovery"),
    MOOD("Mood"),
}

/** One pillar's contribution to a day's [WellnessScore]. */
data class PillarScore(val pillar: Pillar, val value: Int)

/**
 * The result of [WellnessScoreUseCase]. [score] is null when no pillar was tracked (empty state —
 * never rendered as 0). [pillars] are the present pillars with their sub-scores, for the breakdown.
 */
data class WellnessScore(
    val score: Int?,
    val pillars: List<PillarScore>,
    val trackedCount: Int,
) {
    val band: WellnessBand? get() = score?.let { WellnessBand.of(it) }
}

/** Qualitative band for a wellness score, for copy + color. */
enum class WellnessBand(val label: String) {
    THRIVING("Thriving"),
    STEADY("Steady"),
    BUILDING("Building"),
    LOW("Low");

    companion object {
        fun of(score: Int): WellnessBand = when {
            score >= 80 -> THRIVING
            score >= 60 -> STEADY
            score >= 40 -> BUILDING
            else -> LOW
        }
    }
}
