package com.hellohealth.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DayKeyTest {

    @Test
    fun `asString combines user and date`() {
        val key = DayKey("user-123", LocalDate.of(2026, 9, 12))
        assertEquals("user-123|2026-09-12", key.asString())
    }

    @Test
    fun `from resolves the local day for the given zone`() {
        // 2026-09-12T23:30:00Z is still the 12th in UTC but the 13th in IST (+05:30).
        val instant = Instant.parse("2026-09-12T23:30:00Z")

        val utcKey = DayKey.from("u", instant, ZoneOffset.UTC)
        val istKey = DayKey.from("u", instant, ZoneId.of("Asia/Kolkata"))

        assertEquals(LocalDate.of(2026, 9, 12), utcKey.localDate)
        assertEquals(LocalDate.of(2026, 9, 13), istKey.localDate)
    }

    @Test
    fun `equality is value-based`() {
        val a = DayKey("u", LocalDate.of(2026, 1, 1))
        val b = DayKey("u", LocalDate.of(2026, 1, 1))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
