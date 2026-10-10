package com.hellohealth.domain.model

/**
 * A day's intraday time-of-day series read from Health Connect (Phase 1). Device-local; feeds the
 * "through the day" graphs and (later) the real active-minutes derivation + cardio-load scores.
 *
 * Each list is ordered by [IntradayBucket.startEpochMs]. [steps] and [activeCalories] are hourly
 * buckets (bucketMinutes 60); [heartRate] is 5-minute averaged buckets. Any list may be empty when the
 * provider does not expose that signal sub-daily (e.g. a phone-only source with no watch gives no
 * [heartRate]) — callers degrade gracefully.
 */
data class IntradayDay(
    val steps: List<IntradayBucket> = emptyList(),
    val activeCalories: List<IntradayBucket> = emptyList(),
    val heartRate: List<IntradayBucket> = emptyList(),
) {
    val isEmpty: Boolean get() = steps.isEmpty() && activeCalories.isEmpty() && heartRate.isEmpty()
}

/** One time-of-day bucket: a value over [bucketMinutes] starting at [startEpochMs] (UTC epoch ms). */
data class IntradayBucket(
    val startEpochMs: Long,
    val bucketMinutes: Int,
    val value: Double,
)
