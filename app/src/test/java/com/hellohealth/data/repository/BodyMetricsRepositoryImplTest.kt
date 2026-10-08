package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.BodyMetricDao
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
class BodyMetricsRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: BodyMetricDao
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
        dao = db.bodyMetricDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String?) = BodyMetricsRepositoryImpl(
        bodyMetricDao = dao,
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler,
    )

    @Test
    fun `logWeight persists a manual row and requests sync`() = runTest {
        val r = repo("u1")
        r.logWeight("2026-09-30", 80.0, waistCm = 85.0)
        val row = dao.getForDate("u1", "2026-09-30")!!
        assertEquals(80.0, row.weightKg!!, 0.001)
        assertEquals(85.0, row.waistCm!!, 0.001)
        assertEquals("manual", row.source)
        assertEquals("u1|body|2026-09-30", row.id)
        assertTrue(syncRequests > 0)
    }

    @Test
    fun `no signed-in user drops the write`() = runTest {
        repo(null).logWeight("2026-09-30", 80.0)
        // Nothing persisted; and observeRecent emits empty for no user.
        assertTrue(repo(null).observeRecentBodyMetrics(7).first().isEmpty())
    }

    @Test
    fun `HealthConnect capture is idempotent per day and merges with a manual waist`() = runTest {
        val r = repo("u1")
        // Manual waist first, then a HC daily capture for the same day.
        r.logWeight("2026-09-30", 79.0, waistCm = 84.0)
        r.upsertFromHealthConnect(
            localDate = "2026-09-30", weightKg = 80.0, heightCm = 180.0, bodyFatPct = 20.0,
            leanMassKg = 64.0, fatMassKg = 16.0, bodyWaterKg = 42.0, boneMassKg = 3.2,
            bmr = 1780.0, bmi = 24.69, vo2max = 48.0,
        )
        // Same deterministic id → one row, HC values applied, manual waist preserved.
        assertEquals(1, dao.observeRecentForUser("u1", "2026-09-01", "2026-09-30").first().size)
        val row = dao.getForDate("u1", "2026-09-30")!!
        assertEquals(80.0, row.weightKg!!, 0.001)
        assertEquals("health_connect", row.source)
        assertEquals(84.0, row.waistCm!!, 0.001) // preserved from the manual log
        assertEquals(24.69, row.bmi!!, 0.001)
    }

    @Test
    fun `HealthConnect capture with all-null body fields writes no row`() = runTest {
        val r = repo("u1")
        r.upsertFromHealthConnect(
            localDate = "2026-09-30", weightKg = null, heightCm = null, bodyFatPct = null,
            leanMassKg = null, fatMassKg = null, bodyWaterKg = null, boneMassKg = null,
            bmr = null, bmi = null, vo2max = null,
        )
        assertNull(dao.getForDate("u1", "2026-09-30"))
    }

    @Test
    fun `observeRecent returns rows ascending by date`() = runTest {
        val r = repo("u1")
        // Seed relative to today so the rows always fall inside observeRecent's trailing window
        // (the window is computed from LocalDate.now(); fixed past literals would age out).
        val older = java.time.LocalDate.now().minusDays(5).toString()
        val newer = java.time.LocalDate.now().minusDays(2).toString()
        r.logWeight(older, 82.0)
        r.logWeight(newer, 80.0)
        val rows = r.observeRecentBodyMetrics(7).first()
        assertEquals(listOf(older, newer), rows.map { it.localDate })
    }
}
