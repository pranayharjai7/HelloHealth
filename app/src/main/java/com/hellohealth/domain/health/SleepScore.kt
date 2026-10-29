package com.hellohealth.domain.health

/**
 * Pure 0–100 sleep score + per-component ratings from a [SleepStageSummary]. Transparent, documented
 * weights (Fitbit-class breakdown) against AASM / National Sleep Foundation reference ranges — not a
 * black box. Each component scores 0..1, blended by weight; the total maps to a [SleepBand]. Degrades
 * gracefully: a duration-only night ([SleepStageSummary.hasStages] = false) scores on duration alone and
 * sets [SleepScore.isEstimate]. Mirrors ReadinessScoreCalculator's shape; fully unit-tested.
 */
object SleepScore {

    // Component weights (sum to 1.0 when all present).
    const val W_DURATION = 0.40
    const val W_DEEP = 0.15
    const val W_REM = 0.15
    const val W_EFFICIENCY = 0.15
    const val W_RESTFULNESS = 0.10
    const val W_LATENCY = 0.05

    data class Component(val label: String, val score01: Double, val band: SleepBand, val detail: String)

    fun compute(summary: SleepStageSummary): SleepScoreResult {
        val components = mutableListOf<Component>()
        var weighted = 0.0
        var weightUsed = 0.0

        // Duration — always present (TST = asleep minutes).
        val tstHours = summary.asleepMinutes / 60.0
        val durationScore = durationScore(tstHours)
        components += Component("Duration", durationScore, bandOf(durationScore), "${fmtHm(summary.asleepMinutes)} asleep")
        weighted += durationScore * W_DURATION; weightUsed += W_DURATION

        if (summary.hasStages) {
            val tst = summary.asleepMinutes.coerceAtLeast(1L).toDouble()
            summary.deepMinutes?.let {
                val pct = it / tst * 100.0
                val s = rangeScore(pct, idealLo = 13.0, idealHi = 23.0, hardLo = 5.0, hardHi = 35.0)
                components += Component("Deep", s, bandOf(s), "${it}m (${pct.toInt()}%)")
                weighted += s * W_DEEP; weightUsed += W_DEEP
            }
            summary.remMinutes?.let {
                val pct = it / tst * 100.0
                val s = rangeScore(pct, idealLo = 20.0, idealHi = 25.0, hardLo = 10.0, hardHi = 40.0)
                components += Component("REM", s, bandOf(s), "${it}m (${pct.toInt()}%)")
                weighted += s * W_REM; weightUsed += W_REM
            }
            summary.efficiencyPercent?.let {
                val s = when { it >= 90.0 -> 1.0; it >= 85.0 -> 0.85; it >= 75.0 -> 0.6; else -> (it / 75.0).coerceIn(0.0, 0.5) }
                components += Component("Efficiency", s, bandOf(s), "${it.toInt()}%")
                weighted += s * W_EFFICIENCY; weightUsed += W_EFFICIENCY
            }
            summary.wasoMinutes?.let {
                val s = when { it <= 20L -> 1.0; it <= 40L -> 0.75; it <= 60L -> 0.5; else -> 0.25 }
                components += Component("Restfulness", s, bandOf(s), "${it}m awake")
                weighted += s * W_RESTFULNESS; weightUsed += W_RESTFULNESS
            }
            summary.latencyMinutes?.let {
                val s = when { it <= 20L -> 1.0; it <= 30L -> 0.8; it <= 45L -> 0.6; else -> 0.3 }
                components += Component("Latency", s, bandOf(s), "${it}m to fall asleep")
                weighted += s * W_LATENCY; weightUsed += W_LATENCY
            }
        }

        // Renormalize over the weights actually used (missing components don't penalize).
        val score = if (weightUsed > 0) ((weighted / weightUsed) * 100.0).toInt().coerceIn(0, 100) else 0
        return SleepScoreResult(
            score = score,
            band = bandOf(score / 100.0),
            components = components,
            isEstimate = !summary.hasStages,
        )
    }

    /** TST → 0..1: full credit 7–9h, tapering below 6h and above 10h. */
    private fun durationScore(hours: Double): Double = when {
        hours in 7.0..9.0 -> 1.0
        hours in 6.0..7.0 -> 0.6 + (hours - 6.0) * 0.4
        hours in 9.0..10.0 -> 1.0 - (hours - 9.0) * 0.3
        hours < 6.0 -> (hours / 6.0 * 0.6).coerceIn(0.0, 0.6)
        else -> 0.6 // >10h oversleep
    }

    /** A value inside [idealLo]..[idealHi] = 1.0; linearly down to 0 at the hard bounds. */
    private fun rangeScore(v: Double, idealLo: Double, idealHi: Double, hardLo: Double, hardHi: Double): Double = when {
        v in idealLo..idealHi -> 1.0
        v < idealLo -> ((v - hardLo) / (idealLo - hardLo)).coerceIn(0.0, 1.0)
        else -> ((hardHi - v) / (hardHi - idealHi)).coerceIn(0.0, 1.0)
    }

    private fun bandOf(score01: Double): SleepBand = when {
        score01 >= 0.85 -> SleepBand.EXCELLENT
        score01 >= 0.70 -> SleepBand.GOOD
        score01 >= 0.50 -> SleepBand.FAIR
        else -> SleepBand.NEEDS_ATTENTION
    }

    private fun fmtHm(minutes: Long): String = "${minutes / 60}h ${minutes % 60}m"
}

enum class SleepBand(val label: String) {
    EXCELLENT("Excellent"), GOOD("Good"), FAIR("Fair"), NEEDS_ATTENTION("Needs attention")
}

data class SleepScoreResult(
    val score: Int,
    val band: SleepBand,
    val components: List<SleepScore.Component>,
    val isEstimate: Boolean,
)
