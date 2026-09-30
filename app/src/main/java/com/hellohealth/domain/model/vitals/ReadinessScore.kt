package com.hellohealth.domain.model.vitals

/**
 * Result of a readiness computation. Ported from TrackMe's `ReadinessScore`, restructured so the
 * status is a typed enum (drives card colour + label) and the "establishing baseline" state is an
 * explicit flag rather than a magic string the UI has to parse.
 */
data class ReadinessScore(
    val score: Int,               // 0-100
    val status: ReadinessStatus,
    val isEstablishingBaseline: Boolean = false,
    val establishingDayCount: Int = 0,   // n in "n/7 days" while establishing
    val debug: ReadinessScoreDebug? = null
)

/**
 * Recovery bands. TrackMe used raw strings ("Optimal"/"Good"/"Moderate"/"Needs Recovery" plus
 * "Insufficient Data"/"Establishing Baseline"). Here the four scoring bands are the enum; the two
 * pre-score states are carried by [ReadinessScore.isEstablishingBaseline] / score==0.
 */
enum class ReadinessStatus {
    OPTIMAL,
    GOOD,
    MODERATE,
    NEEDS_RECOVERY,
    INSUFFICIENT_DATA
}

/**
 * Diagnostic detail for a readiness computation. Mirrors TrackMe's `ReadinessScoreDebug`.
 */
data class ReadinessScoreDebug(
    val hrvDeviationPercentage: Float,
    val rhrDeviationPercentage: Float,
    val sleepScorePenalty: Float,
    val missingDataFlags: List<String>
)
