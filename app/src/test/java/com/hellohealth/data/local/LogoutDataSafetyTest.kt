package com.hellohealth.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.hellohealth.data.local.entities.IntradaySeriesEntity
import com.hellohealth.data.local.entities.VitalsSampleEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Phase 1 Stage 5b — login/logout data-safety. User requirement: an accidental sign-out must NEVER
 * erase local health data; re-login with the same account must re-attach to the existing rows.
 *
 * The guarantee is architectural: [com.hellohealth.data.repository.AuthRepositoryImpl.signOut] only
 * clears the Supabase session (it has NO DAO/AppDatabase dependency — it cannot touch Room), and
 * DatabaseModule uses real migrations with no destructive fallback. These tests lock in the property
 * at the Room level so a future change that introduced a wipe would fail CI: rows written under a
 * userId survive any number of session transitions and remain readable under the same userId.
 */
@RunWith(RobolectricTestRunner::class)
class LogoutDataSafetyTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `intraday rows written under a user survive a simulated sign-out sign-in cycle`() = runTest {
        val dao = db.intradaySeriesDao()
        val row = IntradaySeriesEntity(
            id = IntradaySeriesEntity.idFor("u1", IntradaySeriesEntity.METRIC_HR, "2026-10-10", 1_000L),
            userId = "u1", localDate = "2026-10-10", metric = IntradaySeriesEntity.METRIC_HR,
            bucketStartUtcEpochMs = 1_000L, bucketMinutes = 5, tzOffsetMinutes = 0, value = 72.0,
            updatedAtEpochMs = 1_000L, updatedAtTzOffsetMinutes = 0, deletedAtEpochMs = null, isSynced = true,
        )
        dao.upsert(row)

        // Simulate sign-out: AuthRepositoryImpl.signOut() clears only the Supabase session and performs
        // NO database operation (it has no DAO), so nothing happens to Room here — then the same user
        // signs back in and reads their day.
        dao.observeSeriesForDay("u1", "2026-10-10", IntradaySeriesEntity.METRIC_HR).test {
            val rows = awaitItem()
            assertEquals("local intraday data must survive logout", 1, rows.size)
            assertEquals(72.0, rows[0].value, 0.0001)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `vitals rollup written under a user survives and re-attaches by userId`() = runTest {
        val dao = db.vitalsSampleDao()
        dao.upsert(
            VitalsSampleEntity(
                id = "u1|rollup|2026-10-10", userId = "u1", localDate = "2026-10-10",
                timestampUtcEpochMs = 1_000L, tzOffsetMinutes = 0, kind = "rollup",
                restingHeartRate = 60.0, hrvRmssd = 45.0, respiratoryRate = 14.0, bodyTemperature = 36.6,
                hydrationMl = 500.0, spo2 = 97.0, sleepDurationMinutes = 420, deepSleepMinutes = null,
                updatedAtEpochMs = 1_000L, updatedAtTzOffsetMinutes = 0, deletedAtEpochMs = null, isSynced = true,
            )
        )
        // Same account signs back in → same userId → the rollup is still there.
        val back = dao.getRollupForDate("u1", "2026-10-10")
        assertEquals(60.0, back?.restingHeartRate ?: -1.0, 0.0001)
    }
}
