package com.hellohealth.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class TimestampsTest {

    @Test
    fun `parseServerTimestamp matches OffsetDateTime epoch millis`() {
        val value = "2026-09-12T10:15:30+05:30"
        val expected = OffsetDateTime.parse(value).toInstant().toEpochMilli()

        assertEquals(expected, Timestamps.parseServerTimestamp(value))
    }

    @Test
    fun `parseServerTimestamp handles UTC Z offset`() {
        val value = "2026-09-12T04:45:30Z"
        val expected = OffsetDateTime.parse(value).toInstant().toEpochMilli()

        assertEquals(expected, Timestamps.parseServerTimestamp(value))
    }

    @Test
    fun `parseServerTimestamp returns null for null blank or garbage`() {
        assertNull(Timestamps.parseServerTimestamp(null))
        assertNull(Timestamps.parseServerTimestamp(""))
        assertNull(Timestamps.parseServerTimestamp("   "))
        assertNull(Timestamps.parseServerTimestamp("not-a-timestamp"))
    }

    @Test
    fun `epochMs round-trips through server timestamp string`() {
        val epochMs = OffsetDateTime.parse("2026-09-12T04:45:30Z").toInstant().toEpochMilli()
        val serialized = Timestamps.epochMsToServerTimestamp(epochMs)

        assertEquals(epochMs, Timestamps.parseServerTimestamp(serialized))
    }

    @Test
    fun `instant round-trips through epoch millis`() {
        val epochMs = 1_757_649_930_000L
        assertEquals(epochMs, Timestamps.instantToEpochMs(Timestamps.epochMsToInstant(epochMs)))
    }
}
