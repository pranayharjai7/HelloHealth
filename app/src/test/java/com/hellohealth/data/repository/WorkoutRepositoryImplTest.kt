package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.WorkoutSessionDao
import com.hellohealth.domain.model.WorkoutActivityType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutSessionDao
    private var syncRequests = 0

    private val syncScheduler = object : com.hellohealth.sync.SyncScheduler(
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
        dao = db.workoutSessionDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String?) = WorkoutRepositoryImpl(
        workoutSessionDao = dao,
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler
    )

    private suspend fun WorkoutRepositoryImpl.saveRun(note: String? = null) = saveWorkout(
        activityType = WorkoutActivityType.RUN,
        title = "Morning run",
        startTimeUtcEpochMs = 1_000L,
        endTimeUtcEpochMs = 2_800_000L,
        durationMinutes = 46L,
        calories = 320.0,
        distanceKm = 8.2,
        note = note
    )

    @Test
    fun `no user reads empty and drops writes without scheduling sync`() = runTest {
        val repo = repo(null)

        assertTrue(repo.observeWorkouts().first().isEmpty())

        val id = repo.saveRun()
        assertNull(id)
        assertEquals(0, syncRequests)
        assertTrue(dao.getUnsynced().isEmpty())
    }

    @Test
    fun `saveWorkout mints a UUID id, writes an unsynced row, and requests sync`() = runTest {
        val repo = repo("u1")

        val id = repo.saveRun(note = "felt great")
        assertNotNull(id)

        val saved = repo.observeWorkouts().first()
        assertEquals(1, saved.size)
        assertEquals(id, saved[0].id)
        assertEquals(WorkoutActivityType.RUN, saved[0].activityType)
        assertEquals("felt great", saved[0].note)
        assertEquals(320.0, saved[0].calories!!, 0.0001)

        val unsynced = dao.getUnsynced()
        assertEquals(1, unsynced.size)
        assertFalse(unsynced[0].isSynced)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `two saves in the same instant produce two distinct rows (no silent overwrite)`() = runTest {
        val repo = repo("u1")

        val id1 = repo.saveRun()
        val id2 = repo.saveRun()

        assertNotNull(id1)
        assertNotNull(id2)
        assertTrue("UUIDs must differ", id1 != id2)
        assertEquals(2, repo.observeWorkouts().first().size)
    }

    @Test
    fun `observeWorkouts surfaces the saved workout`() = runTest {
        val repo = repo("u1")
        repo.saveRun()

        repo.observeWorkouts().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(WorkoutActivityType.RUN, list[0].activityType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete tombstones the row so live reads drop it but push still sees it`() = runTest {
        val repo = repo("u1")
        val id = repo.saveRun()!!

        repo.delete(id)

        // Live reads exclude tombstones...
        assertTrue(repo.observeWorkouts().first().isEmpty())
        // ...but the tombstone is still unsynced so the delete propagates on push.
        val unsynced = dao.getUnsynced()
        assertEquals(1, unsynced.size)
        assertNotNull("tombstone carries a deletedAt", unsynced[0].deletedAtEpochMs)
        assertFalse(unsynced[0].isSynced)
    }
}
