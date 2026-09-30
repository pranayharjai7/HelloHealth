package com.hellohealth.domain.vitals

import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.model.vitals.ReadinessScoreDebug
import com.hellohealth.domain.model.vitals.ReadinessStatus
import kotlin.time.Duration.Companion.days
import javax.inject.Inject

/**
 * Pure analytic module for computing daily readiness. Ported from TrackMe's
 * `com.trackme.domain.analytics.recovery.ReadinessScoreCalculator`.
 *
 * Requirements:
 * - Handle < 7 days of data gracefully (Establishing Baseline).
 * - Safely handle missing HRV / RHR / Sleep data on any given day.
 * - Outlier mitigation via baseline averages.
 *
 * **Fix vs TrackMe:** TrackMe averaged the ENTIRE history list to form the baseline, so stale data
 * from months ago biased today's deviation. Here the baseline is bounded to the most recent
 * [BASELINE_WINDOW_DAYS] before today; older rows are excluded. The `< 7 days` establishing gate is
 * unchanged, and the recent-value fallbacks (HRV over last 3 days, RHR over last 2) are preserved.
 */
class ReadinessScoreCalculator @Inject constructor() {

    companion object {
        /**
         * How far back the HRV/RHR baseline averages look. 28 days = ~4 weeks: long enough to
         * smooth day-to-day noise and absorb a couple of missing nights, short enough that a
         * genuine fitness change moves the baseline within a month. Data older than this is
         * excluded so a stale historical outlier can't skew today's deviation.
         */
        const val BASELINE_WINDOW_DAYS = 28
    }

    fun calculate(
        historicalMetrics: List<HealthMetricsData>,
        todayMetric: HealthMetricsData?
    ): ReadinessScore {
        // Edge Case: No data for today
        if (todayMetric == null) {
            return ReadinessScore(
                score = 0,
                status = ReadinessStatus.INSUFFICIENT_DATA,
                debug = ReadinessScoreDebug(0f, 0f, 0f, listOf("todayMetric is null"))
            )
        }

        // Edge Case: Users with < 7 days history
        if (historicalMetrics.size < 7) {
            return ReadinessScore(
                score = 0,
                status = ReadinessStatus.INSUFFICIENT_DATA,
                isEstablishingBaseline = true,
                establishingDayCount = historicalMetrics.size,
                debug = ReadinessScoreDebug(0f, 0f, 0f, listOf("historicalMetrics < 7 days"))
            )
        }

        val missingFlags = mutableListOf<String>()

        // 1. Calculate Baselines (Averages over the recent window only — the fix vs TrackMe).
        val windowStart = todayMetric.dateMillis - BASELINE_WINDOW_DAYS.days.inWholeMilliseconds
        val windowedHistory = historicalMetrics.filter { it.dateMillis >= windowStart }

        val validHrvs = windowedHistory.mapNotNull { it.hrvRmssd }.filter { it > 0 }
        val validRhrs = windowedHistory.mapNotNull { it.restingHeartRate }.filter { it > 0 }

        val baselineHrv = if (validHrvs.isNotEmpty()) validHrvs.average().toFloat() else null
        val baselineRhr = if (validRhrs.isNotEmpty()) validRhrs.average().toFloat() else null

        // HRV is typically recorded overnight (during sleep) and may not appear in today's snapshot
        // until after the sleep session is processed. Fall back to the most recent HRV from the
        // last 3 days of history before flagging it as missing.
        val recentHrv: Float? = (todayMetric.hrvRmssd?.takeIf { it > 0 }?.toFloat())
            ?: historicalMetrics
                .sortedByDescending { it.dateMillis }
                .take(3)
                .mapNotNull { it.hrvRmssd }
                .firstOrNull { it > 0 }
                ?.toFloat()

        // RHR follows the same pattern — measured during resting periods and may lag behind.
        val recentRhr: Float? = (todayMetric.restingHeartRate?.takeIf { it > 0 }?.toFloat())
            ?: historicalMetrics
                .sortedByDescending { it.dateMillis }
                .take(2)
                .mapNotNull { it.restingHeartRate }
                .firstOrNull { it > 0 }
                ?.toFloat()

        // 2. Compute Deviations
        var hrvDeviation = 0f
        var hasHrv = false
        if (baselineHrv != null && recentHrv != null) {
            hrvDeviation = (recentHrv - baselineHrv) / baselineHrv
            hasHrv = true
        } else {
            missingFlags.add("Missing HRV data")
        }

        var rhrDeviation = 0f
        var hasRhr = false
        if (baselineRhr != null && recentRhr != null) {
            rhrDeviation = (baselineRhr - recentRhr) / baselineRhr
            hasRhr = true
        } else {
            missingFlags.add("Missing RHR data")
        }

        var sleepPenalty = 0f
        var hasSleep = false
        val sleepMinutes = todayMetric.sleepDurationMinutes
        if (sleepMinutes != null && sleepMinutes > 0) {
            val sleepHours = sleepMinutes / 60f
            if (sleepHours < 6.0f) {
                sleepPenalty = ((6.0f - sleepHours) / 6.0f) * 0.3f
            } else if (sleepHours >= 7.5f) {
                sleepPenalty = -0.1f
            }
            hasSleep = true
        } else {
            missingFlags.add("Missing Sleep data")
        }

        // 3. Dynamic Weight Redistribution
        val baselineWeights = mutableMapOf<String, Float>()
        if (hasHrv) baselineWeights["HRV"] = 0.40f
        if (hasRhr) baselineWeights["RHR"] = 0.30f
        if (hasSleep) baselineWeights["Sleep"] = 0.30f

        val totalAvailableWeight = baselineWeights.values.sum()
        if (totalAvailableWeight == 0f) {
            return ReadinessScore(
                score = 0,
                status = ReadinessStatus.INSUFFICIENT_DATA,
                debug = ReadinessScoreDebug(0f, 0f, 0f, missingFlags)
            )
        }

        // Normalize weights to sum to 1.0
        val normalizedWeights = baselineWeights.mapValues { it.value / totalAvailableWeight }

        // Compute normalized impacts scaled to a standard 0-100 range
        val rawHrvImpact = (hrvDeviation * 100f).coerceIn(-20f, 20f)
        val rawRhrImpact = (rhrDeviation * 100f).coerceIn(-15f, 15f)
        val rawSleepImpact = -(sleepPenalty * 100f)

        // Apply redistributed weights
        val hrvImpact = if (hasHrv) rawHrvImpact * (normalizedWeights["HRV"] ?: 0f) / 0.40f else 0f
        val rhrImpact = if (hasRhr) rawRhrImpact * (normalizedWeights["RHR"] ?: 0f) / 0.30f else 0f
        val sleepImpact = if (hasSleep) rawSleepImpact * (normalizedWeights["Sleep"] ?: 0f) / 0.30f else 0f

        val baseScore = 80f
        val finalScore = (baseScore + hrvImpact + rhrImpact + sleepImpact).toInt().coerceIn(0, 100)

        val status = when {
            finalScore >= 85 -> ReadinessStatus.OPTIMAL
            finalScore >= 65 -> ReadinessStatus.GOOD
            finalScore >= 40 -> ReadinessStatus.MODERATE
            else -> ReadinessStatus.NEEDS_RECOVERY
        }

        return ReadinessScore(
            score = finalScore,
            status = status,
            debug = ReadinessScoreDebug(
                hrvDeviationPercentage = hrvDeviation,
                rhrDeviationPercentage = rhrDeviation,
                sleepScorePenalty = sleepPenalty,
                missingDataFlags = missingFlags
            )
        )
    }
}
