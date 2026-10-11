package com.hellohealth.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for [ActiveMinutesEstimator] — HR-elevated minutes, session union/de-dup, degradation. */
class ActiveMinutesEstimatorTest {

    private fun hr(startMin: Int, bpm: Double) =
        ActiveMinutesEstimator.HrBucket(startEpochMs = startMin * 60_000L, bucketMinutes = 5, avgBpm = bpm)

    @Test
    fun `counts 5-min HR buckets at or above the active threshold`() {
        // restingHr 60 → threshold = max(60+25, 90) = 90. Buckets ≥90 → counted at 5 min each.
        val buckets = listOf(hr(0, 70.0), hr(5, 92.0), hr(10, 120.0), hr(15, 80.0), hr(20, 110.0), hr(25, 90.0))
        val r = ActiveMinutesEstimator.estimate(
            exerciseMinutes = 0, sessionWindows = emptyList(), hrBuckets = buckets, restingHr = 60.0, steps = 9000,
        )
        assertEquals("hr_elevated", r.method)
        assertEquals(20L, r.activeMinutes) // 92,120,110,90 qualify (4 * 5)
        assertFalse(r.isEstimate)
    }

    @Test
    fun `HR-active unions with exercise minutes and de-dups buckets inside a session`() {
        // A 30-min session from min 10..40; a HR bucket at min 10 (inside) must NOT be re-counted.
        val buckets = listOf(hr(10, 130.0), hr(45, 100.0)) // min10 inside session, min45 outside
        val sessionWindows = listOf(10 * 60_000L to 40 * 60_000L)
        val r = ActiveMinutesEstimator.estimate(
            exerciseMinutes = 30, sessionWindows = sessionWindows, hrBuckets = buckets, restingHr = 55.0, steps = 5000,
        )
        // 30 session + 5 (only the min45 bucket) = 35; the inside-session bucket is de-duped.
        assertEquals(35L, r.activeMinutes)
    }

    @Test
    fun `uses the MIN_ACTIVE_BPM floor when resting HR is unknown`() {
        val buckets = listOf(hr(0, 92.0), hr(5, 88.0)) // floor 90 → only the first qualifies
        val r = ActiveMinutesEstimator.estimate(
            exerciseMinutes = 0, sessionWindows = emptyList(), hrBuckets = buckets, restingHr = null, steps = 0,
        )
        assertEquals(5L, r.activeMinutes)
    }

    @Test
    fun `falls back to exercise sessions when no intraday HR`() {
        val r = ActiveMinutesEstimator.estimate(
            exerciseMinutes = 45, sessionWindows = listOf(0L to 1L), hrBuckets = emptyList(), restingHr = 60.0, steps = 8000,
        )
        assertEquals("sessions_only", r.method)
        assertEquals(45L, r.activeMinutes)
        assertFalse(r.isEstimate)
    }

    @Test
    fun `step proxy is uncapped and flagged estimate when nothing better exists`() {
        // 9094 steps / 100 = 90 — NOT capped at 60 (the old bug). Flagged as an estimate.
        val r = ActiveMinutesEstimator.estimate(
            exerciseMinutes = 0, sessionWindows = emptyList(), hrBuckets = emptyList(), restingHr = null, steps = 9094,
        )
        assertEquals("step_proxy", r.method)
        assertEquals(90L, r.activeMinutes)
        assertTrue(r.isEstimate)
    }

    @Test
    fun `no data yields zero`() {
        val r = ActiveMinutesEstimator.estimate(
            exerciseMinutes = 0, sessionWindows = emptyList(), hrBuckets = emptyList(), restingHr = null, steps = 0,
        )
        assertEquals(0L, r.activeMinutes)
        assertEquals("none", r.method)
    }
}
