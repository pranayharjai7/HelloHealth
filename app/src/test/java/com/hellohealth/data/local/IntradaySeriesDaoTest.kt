package com.hellohealth.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.hellohealth.data.local.dao.IntradaySeriesDao
import com.hellohealth.data.local.entities.IntradaySeriesEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * In-memory Room tests for IntradaySeriesDao — the partial-day → full-day reconciliation guarantee.
 * The deterministic bucket id means re-reading Health Connect for the same bucket UPSERTS in place
 * (latest value wins) rather than duplicating, so a morning fetch + an evening fetch converge.
 */
@RunWith(RobolectricTestRunner::class)
class IntradaySeriesDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: IntradaySeriesDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.intradaySeriesDao()
    }

    @After
    fun tearDown() = db.close()

    private fun bucket(
        metric: String = IntradaySeriesEntity.METRIC_STEPS,
        start: Long,
        value: Double,
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null,
    ) = IntradaySeriesEntity(
        id = IntradaySeriesEntity.idFor("u1", metric, "2026-10-10", start),
        userId = "u1",
        localDate = "2026-10-10",
        metric = metric,
        bucketStartUtcEpochMs = start,
        bucketMinutes = 60,
        tzOffsetMinutes = 0,
        value = value,
        updatedAtEpochMs = updatedAt,
        updatedAtTzOffsetMinutes = 0,
        deletedAtEpochMs = deletedAt,
        isSynced = true,
    )

    @Test
    fun `re-reading the same bucket upserts in place with the latest value`() = runTest {
        // Morning fetch: hour-9 bucket = 300 steps.
        dao.upsert(bucket(start = 9_000L, value = 300.0, updatedAt = 1_000L))
        // Evening re-fetch of the FULL day: hour-9 now reads 520 (more steps accrued that hour).
        dao.upsert(bucket(start = 9_000L, value = 520.0, updatedAt = 2_000L))

        dao.observeSeriesForDay("u1", "2026-10-10", IntradaySeriesEntity.METRIC_STEPS).test {
            val rows = awaitItem()
            assertEquals("one row, not two — reconciled in place", 1, rows.size)
            assertEquals(520.0, rows[0].value, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `partial morning plus evening full day converge with no double-count`() = runTest {
        // Morning: hours 7,8,9 present.
        dao.upsertAll(listOf(
            bucket(start = 7_000L, value = 100.0),
            bucket(start = 8_000L, value = 150.0),
            bucket(start = 9_000L, value = 200.0),
        ))
        // Evening full re-read: 7,8,9 unchanged + new hours 10,11.
        dao.upsertAll(listOf(
            bucket(start = 7_000L, value = 100.0),
            bucket(start = 8_000L, value = 150.0),
            bucket(start = 9_000L, value = 200.0),
            bucket(start = 10_000L, value = 180.0),
            bucket(start = 11_000L, value = 90.0),
        ))

        dao.observeSeriesForDay("u1", "2026-10-10", IntradaySeriesEntity.METRIC_STEPS).test {
            val rows = awaitItem()
            assertEquals("5 distinct hour buckets, morning not duplicated", 5, rows.size)
            assertEquals(720.0, rows.sumOf { it.value }, 0.0001) // 100+150+200+180+90
            // ordered ascending by bucket start
            assertEquals(listOf(7_000L, 8_000L, 9_000L, 10_000L, 11_000L), rows.map { it.bucketStartUtcEpochMs })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe filters by metric and tombstones`() = runTest {
        dao.upsert(bucket(metric = IntradaySeriesEntity.METRIC_STEPS, start = 1_000L, value = 100.0))
        dao.upsert(bucket(metric = IntradaySeriesEntity.METRIC_HR, start = 1_000L, value = 70.0))
        dao.upsert(bucket(metric = IntradaySeriesEntity.METRIC_STEPS, start = 2_000L, value = 50.0, deletedAt = 5_000L))

        dao.observeSeriesForDay("u1", "2026-10-10", IntradaySeriesEntity.METRIC_STEPS).test {
            val rows = awaitItem()
            assertEquals(1, rows.size) // only the live steps bucket; HR excluded, tombstone excluded
            assertEquals(100.0, rows[0].value, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
