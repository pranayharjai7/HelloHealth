package com.hellohealth.domain.wellness

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Tests for the pure [StreakCalculator]: per-pillar walk-back boundaries (a gap on `asOf` breaks it;
 * an older gap bounds it) and the composite Balanced-Day streak (every tracked pillar hit, an
 * untracked day breaks it).
 */
class StreakCalculatorTest {

    private val calc = StreakCalculator()
    private val asOf = LocalDate.of(2026, 10, 8)

    @Test
    fun `current streak counts consecutive hit days ending at asOf`() {
        val hits = setOf(asOf, asOf.minusDays(1), asOf.minusDays(2))
        assertEquals(3, calc.currentStreak(hits, asOf))
    }

    @Test
    fun `a gap on asOf yields a zero streak`() {
        val hits = setOf(asOf.minusDays(1), asOf.minusDays(2))
        assertEquals(0, calc.currentStreak(hits, asOf))
    }

    @Test
    fun `an older gap bounds the streak`() {
        // Hit today and yesterday, miss two days ago, hit three days ago → streak is 2.
        val hits = setOf(asOf, asOf.minusDays(1), asOf.minusDays(3))
        assertEquals(2, calc.currentStreak(hits, asOf))
    }

    @Test
    fun `empty hit set is a zero streak`() {
        assertEquals(0, calc.currentStreak(emptySet(), asOf))
    }

    @Test
    fun `balanced-day streak counts days where every tracked pillar was hit`() {
        val tracked = mapOf(
            asOf to setOf(Pillar.ACTIVITY, Pillar.MOOD),
            asOf.minusDays(1) to setOf(Pillar.ACTIVITY),
        )
        val hit = mapOf(
            asOf to setOf(Pillar.ACTIVITY, Pillar.MOOD), // both tracked hit
            asOf.minusDays(1) to setOf(Pillar.ACTIVITY), // the one tracked hit
        )
        assertEquals(2, calc.balancedDayStreak(tracked, hit, asOf))
    }

    @Test
    fun `a day with a tracked-but-unhit pillar breaks the balanced streak`() {
        val tracked = mapOf(asOf to setOf(Pillar.ACTIVITY, Pillar.NUTRITION))
        val hit = mapOf(asOf to setOf(Pillar.ACTIVITY)) // nutrition tracked but missed
        assertEquals(0, calc.balancedDayStreak(tracked, hit, asOf))
    }

    @Test
    fun `a day with nothing tracked breaks the balanced streak`() {
        val tracked = mapOf(
            asOf to setOf(Pillar.ACTIVITY),
            // asOf-1 has no tracked pillars at all → nothing to balance → breaks.
            asOf.minusDays(2) to setOf(Pillar.ACTIVITY),
        )
        val hit = mapOf(
            asOf to setOf(Pillar.ACTIVITY),
            asOf.minusDays(2) to setOf(Pillar.ACTIVITY),
        )
        assertEquals(1, calc.balancedDayStreak(tracked, hit, asOf))
    }
}
