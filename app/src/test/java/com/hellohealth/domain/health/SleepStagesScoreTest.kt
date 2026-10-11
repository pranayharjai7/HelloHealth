package com.hellohealth.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for [SleepStages.summarize] — per-stage minutes, latency, efficiency, WASO, degrade. */
class SleepStagesTest {

    private val base = 1_700_000_000_000L // arbitrary night start
    private fun min(n: Long) = n * 60_000L
    private fun seg(fromMin: Long, toMin: Long, stage: Int) =
        SleepStageSegment(base + min(fromMin), base + min(toMin), stage)

    @Test
    fun `sums per-stage minutes and computes efficiency, latency, WASO`() {
        // 0-10 awake (latency 10), 10-70 light, 70-100 deep, 100-110 awake (WASO 10), 110-160 rem. inBed 160m.
        val segs = listOf(
            seg(0, 10, SleepStages.AWAKE),
            seg(10, 70, SleepStages.LIGHT),
            seg(70, 100, SleepStages.DEEP),
            seg(100, 110, SleepStages.AWAKE_IN_BED),
            seg(110, 160, SleepStages.REM),
        )
        val s = SleepStages.summarize(base, base + min(160), segs)
        assertTrue(s.hasStages)
        assertEquals(30L, s.deepMinutes)
        assertEquals(50L, s.remMinutes)
        assertEquals(60L, s.lightMinutes)
        assertEquals(20L, s.awakeMinutes) // 10 + 10
        assertEquals(140L, s.asleepMinutes) // 60+30+50
        assertEquals(160L, s.inBedMinutes)
        assertEquals(10L, s.latencyMinutes) // first asleep at min 10
        assertEquals(10L, s.wasoMinutes) // the awake-in-bed between first and last asleep
        assertEquals(87.5, s.efficiencyPercent!!, 0.1) // 140/160
    }

    @Test
    fun `no stages degrades to a duration-only summary`() {
        val s = SleepStages.summarize(base, base + min(300), emptyList())
        assertFalse(s.hasStages)
        assertEquals(300L, s.asleepMinutes)
        assertEquals(300L, s.inBedMinutes)
        assertNull(s.deepMinutes)
        assertNull(s.latencyMinutes)
    }

    @Test
    fun `unsorted segments are handled`() {
        val segs = listOf(seg(70, 100, SleepStages.DEEP), seg(10, 70, SleepStages.LIGHT), seg(0, 10, SleepStages.AWAKE))
        val s = SleepStages.summarize(base, base + min(100), segs)
        assertEquals(10L, s.latencyMinutes)
        assertEquals(30L, s.deepMinutes)
    }
}

/** Pure tests for [SleepScore.compute] — weights, bands, degrade, Samsung-shaped night. */
class SleepScoreTest {

    private fun summary(
        deep: Long?, rem: Long?, light: Long?, awake: Long?, asleep: Long, inBed: Long,
        latency: Long?, efficiency: Double?, waso: Long?, hasStages: Boolean = true,
    ) = SleepStageSummary(deep, rem, light, awake, asleep, inBed, latency, efficiency, waso, hasStages)

    @Test
    fun `a textbook night scores excellent`() {
        // 8h asleep, deep 18%, REM 22%, efficiency 92%, low WASO, short latency.
        val s = summary(deep = 86, rem = 106, light = 288, awake = 15, asleep = 480, inBed = 522,
            latency = 10, efficiency = 92.0, waso = 15)
        val r = SleepScore.compute(s)
        assertEquals(SleepBand.EXCELLENT, r.band)
        assertTrue(r.score >= 85)
        assertFalse(r.isEstimate)
    }

    @Test
    fun `a short fragmented night scores low (Samsung-shaped)`() {
        // ~4h24m asleep, deep 25m, REM 38m, awake 14m, latency 9m → should land fair/needs-attention.
        val asleep = 264L // 4h24m
        val s = summary(deep = 25, rem = 38, light = 201, awake = 14, asleep = asleep, inBed = 278,
            latency = 9, efficiency = (asleep.toDouble() / 278 * 100), waso = 14)
        val r = SleepScore.compute(s)
        assertTrue("short night should not be good/excellent, got ${r.score}", r.score < 70)
        assertTrue(r.components.any { it.label == "Deep" })
    }

    @Test
    fun `duration-only night scores on duration alone and flags estimate`() {
        val s = summary(null, null, null, null, asleep = 450, inBed = 450, null, null, null, hasStages = false)
        val r = SleepScore.compute(s)
        assertTrue(r.isEstimate)
        assertEquals(1, r.components.size) // only Duration
        assertTrue(r.score >= 85) // 7.5h is in the ideal band
    }

    @Test
    fun `missing components do not penalize via renormalization`() {
        // Only duration + deep available; score reflects those two, not zeros for the rest.
        val s = summary(deep = 90, rem = null, light = null, awake = null, asleep = 480, inBed = 480,
            latency = null, efficiency = null, waso = null)
        val r = SleepScore.compute(s)
        assertTrue(r.score >= 85) // both present components are strong
    }
}
