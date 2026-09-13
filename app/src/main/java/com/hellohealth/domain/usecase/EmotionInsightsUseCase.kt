package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.model.Valence
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Emotional-balance summary over a set of logged moods. Pure and deterministic — takes an already
 * loaded list of records so it can be unit-tested without a repository, and holds no time/IO.
 *
 * Balance Score = PositiveRatio*50 + Stability*30 + Diversity*20, so a perfect score (100) means
 * every mood was positive, there were no swings, and a healthy spread of moods was expressed. Each
 * sub-metric is a 0..1 fraction; the weights sum to 100.
 *
 * All three sub-metrics are defined so an empty or single-record window never divides by zero and
 * never crashes — an empty window returns [EmotionInsights.EMPTY] (score 0, no dominant mood).
 */
class EmotionInsightsUseCase @Inject constructor() {

    operator fun invoke(records: List<EmotionRecord>): EmotionInsights {
        // Only records that actually carry a mood contribute; an empty window is a valid,
        // non-crashing "nothing logged yet" state rather than a zeroed score with fake structure.
        if (records.isEmpty()) return EmotionInsights.EMPTY

        val positiveRatio = positiveRatioOf(records)
        val stability = stabilityOf(records)
        val diversity = diversityOf(records)

        val score = (positiveRatio * 50 + stability * 30 + diversity * 20).roundToInt()
            .coerceIn(0, 100)

        val counts = records.groupingBy { it.emotion }.eachCount()
        val maxCount = counts.values.max()
        // Tie broken by the most recently logged of the tied moods (last in input order).
        val dominant = records.last { counts[it.emotion] == maxCount }.emotion

        return EmotionInsights(
            score = score,
            positiveRatio = positiveRatio,
            stability = stability,
            diversity = diversity,
            dominant = dominant,
            counts = counts,
            total = records.size
        )
    }

    /** Fraction of records whose emotion carries a POSITIVE valence (0..1). */
    private fun positiveRatioOf(records: List<EmotionRecord>): Double =
        records.count { it.emotion.valence == Valence.POSITIVE }.toDouble() / records.size

    /**
     * 1 − (number of mood *changes* between consecutive records / number of adjacent pairs).
     * A single record has no pairs → perfectly stable (1.0). Fewer swings → closer to 1.
     */
    private fun stabilityOf(records: List<EmotionRecord>): Double {
        val pairs = records.size - 1
        if (pairs <= 0) return 1.0
        val changes = records.zipWithNext().count { (a, b) -> a.emotion != b.emotion }
        return 1.0 - changes.toDouble() / pairs
    }

    /**
     * Distinct moods expressed / distinct moods available. Bounded to 1.0 defensively so it can
     * never overshoot even if the enum shrinks below the number of distinct logged moods.
     */
    private fun diversityOf(records: List<EmotionRecord>): Double {
        val distinct = records.map { it.emotion }.toSet().size
        val available = EmotionType.entries.size
        return (distinct.toDouble() / available).coerceIn(0.0, 1.0)
    }
}

/**
 * Result of [EmotionInsightsUseCase]. [score] is 0..100; the three fractions are the un-weighted
 * 0..1 sub-metrics behind it (useful for a breakdown UI). [dominant] is null only for [EMPTY].
 */
data class EmotionInsights(
    val score: Int,
    val positiveRatio: Double,
    val stability: Double,
    val diversity: Double,
    val dominant: EmotionType?,
    val counts: Map<EmotionType, Int>,
    val total: Int
) {
    companion object {
        val EMPTY = EmotionInsights(
            score = 0,
            positiveRatio = 0.0,
            stability = 0.0,
            diversity = 0.0,
            dominant = null,
            counts = emptyMap(),
            total = 0
        )
    }
}
