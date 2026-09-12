package com.hellohealth.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.SnapshotMapper
import com.hellohealth.data.local.dao.SnapshotDao
import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.SnapshotDataSource
import com.hellohealth.domain.model.SnapshotSyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SnapshotSyncerTest {

    private lateinit var db: AppDatabase
    private lateinit var snapshotDao: SnapshotDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        snapshotDao = db.snapshotDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `snapshot round-trips through Room including exercise sessions`() = runTest {
        val date = LocalDate.of(2026, 9, 12)
        val summary = HealthSummary(
            steps = 8234,
            stepsGoal = 10000,
            activeCalories = 412.0,
            caloriesGoal = 500.0,
            activeTimeMinutes = 47,
            activeTimeGoal = 60,
            distanceKm = 6.1,
            totalCalories = 1900.0,
            basalMetabolicRate = 1500.0,
            weight = 72.5,
            heartRateAvg = 68,
            sleepDurationMinutes = 430,
            sleepStartTime = Instant.parse("2026-09-11T23:10:00Z"),
            sleepEndTime = Instant.parse("2026-09-12T06:20:00Z"),
            exerciseSessions = listOf(
                ExerciseSession(
                    id = "sess-1",
                    title = "Morning run",
                    type = 56,
                    typeLabel = "Running",
                    startTime = Instant.parse("2026-09-12T06:30:00Z"),
                    endTime = Instant.parse("2026-09-12T07:05:00Z"),
                    durationMinutes = 35,
                    calories = 320.0,
                    distanceKm = 5.2,
                    sourcePackageName = "com.google.android.apps.fitness",
                    sourceAppName = "Fit"
                )
            ),
            lastUpdated = 1_757_000_000_000L
        )
        val now = Timestamps.nowEpochMs()

        snapshotDao.upsert(
            SnapshotMapper.toEntity(
                userId = "u1",
                date = date,
                summary = summary,
                syncStatus = SnapshotSyncStatus.COMPLETE,
                dataSource = SnapshotDataSource.HEALTH_CONNECT,
                lastSyncedAtEpochMs = now,
                updatedAtEpochMs = now,
                isSynced = false
            )
        )

        val loaded = SnapshotMapper.toDomain(snapshotDao.get("u1", date.toString())!!)

        assertEquals(date, loaded.date)
        assertEquals(SnapshotSyncStatus.COMPLETE, loaded.syncStatus)
        assertEquals(SnapshotDataSource.HEALTH_CONNECT, loaded.dataSource)
        assertEquals(summary.steps, loaded.summary.steps)
        assertEquals(summary.sleepStartTime, loaded.summary.sleepStartTime)
        assertEquals(1, loaded.summary.exerciseSessions.size)
        val session = loaded.summary.exerciseSessions.first()
        assertEquals("sess-1", session.id)
        assertEquals("Running", session.typeLabel)
        assertEquals(Instant.parse("2026-09-12T06:30:00Z"), session.startTime)
        assertEquals(5.2, session.distanceKm!!, 0.0001)
        assertEquals("Fit", session.sourceAppName)
    }

    @Test
    fun `today-guard skips pulling today when local is PARTIAL`() {
        val today = "2026-09-12"
        assertTrue(SnapshotSyncer.shouldSkipTodayPull(today, today, "partial"))
        assertTrue(SnapshotSyncer.shouldSkipTodayPull(today, today, "PARTIAL"))
    }

    @Test
    fun `today-guard allows pull for today when local is COMPLETE or absent`() {
        val today = "2026-09-12"
        assertFalse(SnapshotSyncer.shouldSkipTodayPull(today, today, "complete"))
        assertFalse(SnapshotSyncer.shouldSkipTodayPull(today, today, null))
    }

    @Test
    fun `today-guard never blocks a past day even if PARTIAL`() {
        assertFalse(SnapshotSyncer.shouldSkipTodayPull("2026-09-10", "2026-09-12", "partial"))
    }
}
