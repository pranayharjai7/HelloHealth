package com.hellohealth.domain.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive tests for the pure [WellnessScoreUseCase]. The behaviour that carries the risk is
 * SKIP-MISSING RENORMALIZATION: a gap in a pillar must never be scored as a zero — the present
 * pillars are reweighted to sum to 1. Also covers the all-missing → null (empty state, never a fake
 * 0), clamping, and bands.
 */
class WellnessScoreUseCaseTest {

    private val useCase = WellnessScoreUseCase()

    @Test
    fun `all four pillars at 100 scores 100`() {
        val result = useCase(activity = 100, nutrition = 100, recovery = 100, mood = 100)
        assertEquals(100, result.score)
        assertEquals(4, result.trackedCount)
        assertEquals(WellnessBand.THRIVING, result.band)
    }

    @Test
    fun `a missing pillar is skipped and the rest are renormalized, not penalized as zero`() {
        // Only activity + mood tracked, both perfect. Must be 100 (renormalized over the two present),
        // NOT 50 (which is what averaging in two zeros for the gaps would give).
        val result = useCase(activity = 100, nutrition = null, recovery = null, mood = 100)
        assertEquals("gaps must not drag the score down", 100, result.score)
        assertEquals(2, result.trackedCount)
        assertTrue(result.pillars.map { it.pillar }.containsAll(listOf(Pillar.ACTIVITY, Pillar.MOOD)))
    }

    @Test
    fun `renormalization weights the present pillars by their base ratio`() {
        // Activity (base 0.30) = 90, recovery (base 0.30) = 50 → equal weights → 70.
        val equal = useCase(activity = 90, nutrition = null, recovery = 50, mood = null)
        assertEquals(70, equal.score)

        // Activity (0.30) = 100, nutrition (0.20) = 0 → weights 0.6/0.4 → 60.
        val skewed = useCase(activity = 100, nutrition = 0, recovery = null, mood = null)
        assertEquals(60, skewed.score)
    }

    @Test
    fun `no pillars tracked yields a null score, never a fake zero`() {
        val result = useCase(activity = null, nutrition = null, recovery = null, mood = null)
        assertNull("empty state, not 0", result.score)
        assertEquals(0, result.trackedCount)
        assertNull(result.band)
    }

    @Test
    fun `a single tracked pillar scores exactly that pillar`() {
        val result = useCase(activity = null, nutrition = 73, recovery = null, mood = null)
        assertEquals(73, result.score)
        assertEquals(1, result.trackedCount)
    }

    @Test
    fun `out-of-range inputs are clamped to 0-100`() {
        val result = useCase(activity = 150, nutrition = -20, recovery = null, mood = null)
        // activity clamps to 100 (base 0.30), nutrition to 0 (base 0.20) → weights 0.6/0.4 → 60.
        assertEquals(60, result.score)
    }

    @Test
    fun `bands map at their boundaries`() {
        assertEquals(WellnessBand.THRIVING, WellnessBand.of(80))
        assertEquals(WellnessBand.STEADY, WellnessBand.of(60))
        assertEquals(WellnessBand.BUILDING, WellnessBand.of(40))
        assertEquals(WellnessBand.LOW, WellnessBand.of(39))
    }
}
