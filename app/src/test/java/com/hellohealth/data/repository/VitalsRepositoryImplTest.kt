package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.VitalsSampleDao
import com.hellohealth.domain.vitals.ReadinessScoreCalculator
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VitalsRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: VitalsSampleDao
    private var syncRequests = 0

    private val syncScheduler = object : SyncScheduler(
        ApplicationProvider.getApplicationContext<Context>()
    ) {
        override fun requestSync() { syncRequests++ }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.vitalsSampleDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String?) = VitalsRepositoryImpl(
        vitalsSampleDao = dao,
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler,
        readinessCalculator = ReadinessScoreCalculator()
    )

    @Test
    fun `no user reads null-slash-empty and drops writes without scheduling sync`() = runTest {
        val repo = repo(null)

        assertNull(repo.observeReadiness().first())
        assertTrue(repo.observeRecentRollups(30).first().isEmpty())

        repo.upsertRollup(
            localDate = "2026-09-30", timestampUtcEpochMs = 1000L,
            restingHeartRate = 58.0, hrvRmssd = 65.0, respiratoryRate = 14.0,
            bodyTemperature = 36.6, hydrationMl = 500.0, spo2 = 98.0,
            sleepDurationMinutes = 420, deepSleepMinutes = 90
        )
        assertEquals(0, syncRequests)
        assertTrue(dao.getUnsynced().isEmpty())
    }

    @Test
    fun `re-upserting the same day upserts one row via the deterministic id`() = runTest {
        val repo = repo("u1")

        repo.upsertRollup(
            localDate = "2026-09-30", timestampUtcEpochMs = 1000L,
            restingHeartRate = 58.0, hrvRmssd = 65.0, respiratoryRate = 14.0,
            bodyTemperature = 36.6, hydrationMl = 500.0, spo2 = 98.0,
            sleepDurationMinutes = 420, deepSleepMinutes = 90
        )
        // Same local day, a re-read of Health Connect with slightly different values.
        repo.upsertRollup(
            localDate = "2026-09-30", timestampUtcEpochMs = 2000L,
            restingHeartRate = 60.0, hrvRmssd = 62.0, respiratoryRate = 15.0,
            bodyTemperature = 36.7, hydrationMl = 900.0, spo2 = 97.0,
            sleepDurationMinutes = 430, deepSleepMinutes = 95
        )

        val row = dao.getRollupForDate("u1", "2026-09-30")
        assertEquals("one rollup row per user per day", "u1|rollup|2026-09-30", row!!.id)
        assertEquals(1, dao.countRollups("u1"))
        // The upsert overwrote with the latest values.
        assertEquals(60.0, row.restingHeartRate!!, 0.0001)
        assertEquals(900.0, row.hydrationMl!!, 0.0001)
        assertEquals(2, syncRequests)
    }
}
