package com.hellohealth.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Pure unit tests for [relativeLastRecorded]: boundary buckets, null-safety, and the future guard. */
class RelativeDateTest {

    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun `null or blank or unparseable returns null`() {
        assertNull(relativeLastRecorded(null, today))
        assertNull(relativeLastRecorded("", today))
        assertNull(relativeLastRecorded("   ", today))
        assertNull(relativeLastRecorded("not-a-date", today))
    }

    @Test
    fun `a future date returns null`() {
        assertNull(relativeLastRecorded("2026-10-10", today))
        assertNull(relativeLastRecorded("2027-01-01", today))
    }

    @Test
    fun `today and yesterday`() {
        assertEquals("today", relativeLastRecorded("2026-10-09", today))
        assertEquals("yesterday", relativeLastRecorded("2026-10-08", today))
    }

    @Test
    fun `days bucket under a week`() {
        assertEquals("2 days ago", relativeLastRecorded("2026-10-07", today))
        assertEquals("6 days ago", relativeLastRecorded("2026-10-03", today))
    }

    @Test
    fun `weeks bucket`() {
        assertEquals("1 week ago", relativeLastRecorded("2026-10-02", today)) // 7 days
        assertEquals("2 weeks ago", relativeLastRecorded("2026-09-25", today)) // 14 days
        assertEquals("4 weeks ago", relativeLastRecorded("2026-09-10", today)) // 29 days
    }

    @Test
    fun `months bucket`() {
        assertEquals("1 month ago", relativeLastRecorded("2026-09-09", today)) // 30 days
        assertEquals("2 months ago", relativeLastRecorded("2026-08-01", today)) // 69 days
    }

    @Test
    fun `years bucket`() {
        assertEquals("1 year ago", relativeLastRecorded("2025-10-09", today)) // 365 days
        assertEquals("2 years ago", relativeLastRecorded("2024-10-09", today)) // 730 days
    }
}
