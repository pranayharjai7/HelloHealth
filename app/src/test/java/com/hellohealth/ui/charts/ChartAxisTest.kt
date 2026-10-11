package com.hellohealth.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for [niceAxis] / [hourAxis] — clean tick values, unit formatting, degenerate input. */
class ChartAxisTest {

    @Test
    fun `niceAxis produces clean round ticks covering the range`() {
        val a = niceAxis(52f, 71f, "bpm")
        assertTrue("min at or below data", a.min <= 52f)
        assertTrue("max at or above data", a.max >= 71f)
        // ticks are evenly spaced, round, ascending
        val step = a.ticks[1] - a.ticks[0]
        a.ticks.zipWithNext().forEach { (x, y) -> assertEquals(step, y - x, 0.001f) }
        assertTrue("step is a clean 1/2/5 x10^n", step in listOf(1f, 2f, 5f, 10f, 20f, 25f, 50f))
    }

    @Test
    fun `niceAxis formats ticks with the unit and thousands separators`() {
        val a = niceAxis(0f, 9000f, "steps")
        assertTrue(a.format(2000f).startsWith("2,000"))
        assertTrue(a.format(2000f).endsWith("steps"))
    }

    @Test
    fun `niceAxis gives clean ticks for a small-range unit`() {
        val a = niceAxis(72.1f, 76.4f, "kg")
        // ~4 kg span → whole-kg steps are clean and correct; ticks cover the data and are round.
        assertTrue(a.min <= 72.1f && a.max >= 76.4f)
        val step = a.ticks[1] - a.ticks[0]
        assertTrue("clean step", step in listOf(1f, 2f, 5f))
    }

    @Test
    fun `niceAxis handles a flat series without crashing`() {
        val a = niceAxis(97f, 97f, "%")
        assertTrue(a.ticks.isNotEmpty())
        assertTrue(a.max > a.min)
    }

    @Test
    fun `hourAxis has five clock ticks from midnight to midnight`() {
        val h = hourAxis()
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), h.ticks)
        assertEquals("00", h.format(0f))
        assertEquals("06", h.format(0.25f))
        assertEquals("12", h.format(0.5f))
        assertEquals("18", h.format(0.75f))
        assertEquals("24", h.format(1f))
    }
}
