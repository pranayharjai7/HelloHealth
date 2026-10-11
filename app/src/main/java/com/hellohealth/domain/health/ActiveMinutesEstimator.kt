package com.hellohealth.domain.health

/**
 * Derives a day's **active minutes** — the headline "Active time" metric — replacing the old
 * fabrication `(steps / 100).coerceAtMost(60)` that capped non-exercise movement at 60 min (Samsung
 * reported 100 → the app showed 60).
 *
 * "Active minutes" across the industry (Google Fit Move Minutes, WHO moderate-activity minutes) counts
 * time spent at ≥ moderate intensity, best detected from heart rate. Tiered, best-available-wins:
 *
 *  - **Tier A — HR-elevated minutes (primary when a wearable feeds intraday HR).** Count the intraday
 *    HR buckets whose average bpm is at/above an "active" threshold, each worth its bucket width, then
 *    union with logged exercise-session minutes (de-duplicated so a session isn't double-counted).
 *  - **Tier B — exercise sessions only** when no intraday HR exists but sessions do.
 *  - **Tier C — step proxy** `steps / STEPS_PER_ACTIVE_MIN`, UNCAPPED, flagged `isEstimate`, when
 *    nothing better exists.
 *
 * Pure/deterministic; unit-tested.
 */
object ActiveMinutesEstimator {

    /**
     * Floor for the "active" HR threshold (bpm) — the low end of moderate activity for a typical adult.
     * Calibrated on-device against Samsung Health's own "active minutes": a 90 bpm floor reproduced
     * ~95 min vs Samsung's ~100 for a 9k-step day (a 95 bpm floor undercounted at 60; 85 overcounted).
     */
    const val MIN_ACTIVE_BPM = 90.0

    /** Active threshold = restingHr + this margin, floored at [MIN_ACTIVE_BPM] (so fitter, higher-RHR
     *  users still get a sensible moderate-activity bar rather than the floor). */
    const val ACTIVE_MARGIN_OVER_RESTING_BPM = 25.0

    /** Tier-C step proxy denominator (≈ a brisk cadence of 100 steps/active-min). */
    const val STEPS_PER_ACTIVE_MIN = 100L

    data class Result(val activeMinutes: Long, val isEstimate: Boolean, val method: String)

    /**
     * @param exerciseMinutes summed logged exercise-session minutes (authoritative).
     * @param sessionWindows (startEpochMs, endEpochMs) of logged sessions, to de-dup HR minutes.
     * @param hrBuckets intraday HR buckets (startEpochMs, bucketMinutes, avg bpm) — empty if no watch.
     * @param restingHr resting heart rate (bpm) if known, for a personalized threshold.
     * @param steps the day's steps, for the Tier-C fallback.
     */
    fun estimate(
        exerciseMinutes: Long,
        sessionWindows: List<Pair<Long, Long>>,
        hrBuckets: List<HrBucket>,
        restingHr: Double?,
        steps: Long,
    ): Result {
        if (hrBuckets.isNotEmpty()) {
            val threshold = activeThreshold(restingHr)
            // HR buckets at/above threshold, excluding any bucket whose start lies inside a logged
            // session window (those minutes are already in exerciseMinutes).
            val hrActiveMin = hrBuckets
                .filter { it.avgBpm >= threshold }
                .filterNot { b -> sessionWindows.any { (s, e) -> b.startEpochMs in s until e } }
                .sumOf { it.bucketMinutes.toLong() }
            return Result(exerciseMinutes + hrActiveMin, isEstimate = false, method = "hr_elevated")
        }
        if (exerciseMinutes > 0L) {
            return Result(exerciseMinutes, isEstimate = false, method = "sessions_only")
        }
        // Tier C — uncapped step proxy, clearly an estimate.
        val proxy = if (steps > 0L) steps / STEPS_PER_ACTIVE_MIN else 0L
        return Result(proxy, isEstimate = proxy > 0L, method = if (proxy > 0L) "step_proxy" else "none")
    }

    private fun activeThreshold(restingHr: Double?): Double =
        if (restingHr != null && restingHr > 0) maxOf(restingHr + ACTIVE_MARGIN_OVER_RESTING_BPM, MIN_ACTIVE_BPM)
        else MIN_ACTIVE_BPM

    /** Minimal HR bucket shape (decouples the estimator from the data-layer IntradayBucket). */
    data class HrBucket(val startEpochMs: Long, val bucketMinutes: Int, val avgBpm: Double)
}
