package com.hellohealth.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for [HealthConnectManager.bucketHeartRate] — day-aligned, fixed-width averaging of raw HR
 * samples into intraday buckets. No Health Connect client involved.
 */
class HealthConnectManagerHrBucketTest {

    private val dayStart = 1_000_000_000_000L // arbitrary local-midnight epoch ms
    private val fiveMinMs = 5 * 60_000L

    @Test
    fun `empty samples yield empty buckets`() {
        assertTrue(HealthConnectManager.bucketHeartRate(emptyList(), dayStart, 5).isEmpty())
    }

    @Test
    fun `samples in the same bucket are averaged`() {
        val samples = listOf(
            dayStart + 10_000L to 60.0,
            dayStart + 20_000L to 80.0, // same first 5-min bucket
        )
        val buckets = HealthConnectManager.bucketHeartRate(samples, dayStart, 5)
        assertEquals(1, buckets.size)
        assertEquals(dayStart, buckets[0].startEpochMs)
        assertEquals(5, buckets[0].bucketMinutes)
        assertEquals(70.0, buckets[0].value, 0.0001)
    }

    @Test
    fun `samples land in the correct day-aligned bucket and are sorted`() {
        val samples = listOf(
            dayStart + fiveMinMs + 1_000L to 100.0, // 2nd bucket
            dayStart + 1_000L to 50.0,              // 1st bucket
        )
        val buckets = HealthConnectManager.bucketHeartRate(samples, dayStart, 5)
        assertEquals(2, buckets.size)
        assertEquals(dayStart, buckets[0].startEpochMs)
        assertEquals(50.0, buckets[0].value, 0.0001)
        assertEquals(dayStart + fiveMinMs, buckets[1].startEpochMs)
        assertEquals(100.0, buckets[1].value, 0.0001)
    }

    @Test
    fun `a sample exactly on a bucket boundary belongs to the later bucket`() {
        val samples = listOf(dayStart + fiveMinMs to 90.0) // exactly the 2nd bucket start
        val buckets = HealthConnectManager.bucketHeartRate(samples, dayStart, 5)
        assertEquals(1, buckets.size)
        assertEquals(dayStart + fiveMinMs, buckets[0].startEpochMs)
    }

    @Test
    fun `gaps produce no empty buckets (gaps stay gaps)`() {
        val samples = listOf(
            dayStart + 1_000L to 60.0,                   // bucket 0
            dayStart + 3 * fiveMinMs + 1_000L to 70.0,   // bucket 3 (buckets 1,2 skipped)
        )
        val buckets = HealthConnectManager.bucketHeartRate(samples, dayStart, 5)
        assertEquals(2, buckets.size) // only the two populated buckets, no filler
    }

    private fun ib(start: Long, value: Double) =
        com.hellohealth.domain.model.IntradayBucket(start, 60, value)

    @Test
    fun `isDegenerate flags a uniform spread of a daily total`() {
        // Samsung shape: one daily StepsRecord smeared to 24 identical hourly buckets.
        val uniform = (0 until 24).map { ib(it * 3_600_000L, 379.0) }
        assertTrue(HealthConnectManager.isDegenerate(uniform))
    }

    @Test
    fun `isDegenerate is false for a real varying series`() {
        val varying = listOf(ib(0, 120.0), ib(1, 450.0), ib(2, 0.0), ib(3, 900.0))
        assertFalse(HealthConnectManager.isDegenerate(varying))
    }

    @Test
    fun `isDegenerate is false for too-few or all-zero buckets`() {
        assertFalse(HealthConnectManager.isDegenerate(listOf(ib(0, 379.0), ib(1, 379.0)))) // <3
        assertFalse(HealthConnectManager.isDegenerate((0 until 5).map { ib(it.toLong(), 0.0) })) // all zero
    }
}
