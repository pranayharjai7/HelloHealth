package com.hellohealth.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Pure tests for [timeOfDayFraction] — wall-clock placement of a point within its local day. */
class TimeOfDayChartTest {

    private val utc = ZoneId.of("UTC")
    private fun atUtc(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDate.of(y, m, d).atTime(h, min).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun `midnight maps to zero`() {
        assertEquals(0.0f, timeOfDayFraction(atUtc(2026, 10, 10, 0, 0), utc), 0.0001f)
    }

    @Test
    fun `noon maps to one half`() {
        assertEquals(0.5f, timeOfDayFraction(atUtc(2026, 10, 10, 12, 0), utc), 0.0001f)
    }

    @Test
    fun `six am maps to a quarter`() {
        assertEquals(0.25f, timeOfDayFraction(atUtc(2026, 10, 10, 6, 0), utc), 0.0001f)
    }

    @Test
    fun `late evening is near one`() {
        val f = timeOfDayFraction(atUtc(2026, 10, 10, 23, 45), utc)
        assertEquals(0.9896f, f, 0.001f)
    }

    @Test
    fun `fraction stays within range regardless of input`() {
        val f = timeOfDayFraction(atUtc(2026, 10, 10, 18, 30), utc)
        assertEquals(true, f in 0.0f..1.0f)
    }
}
