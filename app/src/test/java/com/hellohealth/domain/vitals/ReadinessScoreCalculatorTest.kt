package com.hellohealth.domain.vitals

import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.ReadinessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days

class ReadinessScoreCalculatorTest {

    private val calculator = ReadinessScoreCalculator()

    private val dayMs = 1.days.inWholeMilliseconds

    @Test
    fun `returns Establishing Baseline if less than 7 days of history`() {
        val history = List(5) { HealthMetricsData(0L, 50.0, 60.0, 480, 100) }
        val today = HealthMetricsData(0L, 50.0, 60.0, 480, 100)

        val result = calculator.calculate(history, today)

        assertEquals(0, result.score)
        assertTrue(result.isEstablishingBaseline)
        assertEquals(5, result.establishingDayCount)
    }

    @Test
    fun `calculates optimal score with great sleep and improved HRV`() {
        val history = List(10) { HealthMetricsData(0L, 50.0, 65.0, 420, 100) } // Baseline HRV 50, RHR 65
        val today = HealthMetricsData(0L, 60.0, 60.0, 500, 150) // HRV up, RHR down, long sleep

        val result = calculator.calculate(history, today)

        assertTrue("Score should be very high", result.score >= 85)
        assertEquals(ReadinessStatus.OPTIMAL, result.status)
        assertEquals(-0.1f, result.debug?.sleepScorePenalty ?: 0f, 0.01f)
    }

    @Test
    fun `penalizes score heavily for terrible sleep`() {
        val history = List(10) { HealthMetricsData(0L, 50.0, 65.0, 420, 100) }
        val today = HealthMetricsData(0L, 50.0, 65.0, 180, 50) // Only 3 hours of sleep!

        val result = calculator.calculate(history, today)

        assertTrue("Score should be penalized for 3 hours of sleep", result.score < 80)
        assertTrue((result.debug?.sleepScorePenalty ?: 0f) > 0.1f)
    }

    @Test
    fun `gracefully handles completely missing HRV and RHR data`() {
        // Neither today NOR history carries HRV/RHR — the watch was never worn during sleep, only
        // sleep duration was manually logged. With nothing for the 3/2-day fallbacks to find, the
        // "missing" flags must be raised and the score should degrade gracefully (sleep-only).
        val history = List(10) { HealthMetricsData(0L, null, null, 420, null) }
        val today = HealthMetricsData(0L, null, null, 420, null)

        val result = calculator.calculate(history, today)

        // Should not crash; sleep-only 7h is neutral so the score stays near the base.
        assertTrue("Should compute a fallback score", result.score in 60..80)
        assertTrue("Should flag missing data", result.debug?.missingDataFlags?.contains("Missing HRV data") == true)
        assertTrue("Should flag missing data", result.debug?.missingDataFlags?.contains("Missing RHR data") == true)
    }

    @Test
    fun `rebalances weights when sleep is missing`() {
        val history = List(10) { HealthMetricsData(0L, 50.0, 65.0, 420, 100) }
        // Sleep is missing today, but HRV and RHR are present and improved
        val today = HealthMetricsData(0L, 60.0, 60.0, null, null)

        val result = calculator.calculate(history, today)

        // The improved HRV/RHR should have higher influence due to redistribution
        assertTrue("Redistribution should elevate score beyond default weight boundaries", result.score >= 85)
        assertTrue(result.debug?.missingDataFlags?.contains("Missing Sleep data") == true)
    }

    @Test
    fun `baseline ignores data older than the window`() {
        // "today" is a fixed reference point.
        val today = HealthMetricsData(dateMillis = 100 * dayMs, hrvRmssd = 60.0, restingHeartRate = 60.0, sleepDurationMinutes = 420, deepSleepMinutes = 100)

        // In-window recent history: healthy baseline HRV 50 / RHR 65 over the last 10 days.
        val inWindow = (1..10).map { d ->
            HealthMetricsData(dateMillis = (100 - d) * dayMs, hrvRmssd = 50.0, restingHeartRate = 65.0, sleepDurationMinutes = 420, deepSleepMinutes = 100)
        }
        // Poison rows dated BASELINE_WINDOW_DAYS + 10 days ago: absurd HRV/RHR that WOULD wreck the
        // baseline if the window filter were absent. They must be excluded.
        val poisonAgeDays = ReadinessScoreCalculator.BASELINE_WINDOW_DAYS + 10
        val outOfWindow = (0..9).map { i ->
            HealthMetricsData(dateMillis = (100 - poisonAgeDays - i) * dayMs, hrvRmssd = 500.0, restingHeartRate = 200.0, sleepDurationMinutes = 420, deepSleepMinutes = 100)
        }

        val withPoison = calculator.calculate(inWindow + outOfWindow, today)
        val withoutPoison = calculator.calculate(inWindow, today)

        // Identical baseline → identical score/status: the out-of-window rows had zero effect.
        assertEquals(withoutPoison.score, withPoison.score)
        assertEquals(withoutPoison.status, withPoison.status)
        // And the HRV deviation (today 60 vs baseline 50 = +0.2) is computed from the in-window
        // baseline, not the poison average of ~275.
        assertEquals(0.2f, withPoison.debug?.hrvDeviationPercentage ?: 0f, 0.001f)
    }
}
