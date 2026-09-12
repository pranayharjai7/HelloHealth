package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.GoalsDao
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoalsRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var goalsDao: GoalsDao
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
        goalsDao = db.goalsDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String?) = GoalsRepositoryImpl(
        goalsDao = goalsDao,
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler
    )

    @Test
    fun `no user reads defaults and drops writes without scheduling sync`() = runTest {
        val repo = repo(null)

        assertEquals(ActivityGoals(), repo.getCurrentActivityGoals())
        assertEquals(ActivityGoals(), repo.getActivityGoals().first())

        repo.updateActivityGoals(ActivityGoals(steps = 1))
        assertEquals(0, syncRequests)
        assertTrue(goalsDao.getUnsynced().isEmpty())
    }

    @Test
    fun `update writes unsynced row, reads back, and requests sync`() = runTest {
        val repo = repo("u1")

        repo.updateActivityGoals(ActivityGoals(steps = 12000, activeCalories = 700, activeMinutes = 45))

        // Read back through the domain surface — round-trips entity <-> domain.
        assertEquals(
            ActivityGoals(steps = 12000, activeCalories = 700, activeMinutes = 45),
            repo.getCurrentActivityGoals()
        )

        // Persisted as an unsynced local write, and a sync was requested.
        val unsynced = goalsDao.getUnsynced()
        assertEquals(1, unsynced.size)
        assertFalse(unsynced[0].isSynced)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `observe emits current goals from Room`() = runTest {
        val repo = repo("u1")
        repo.updateActivityGoals(ActivityGoals(steps = 8000))

        repo.getActivityGoals().test {
            assertEquals(8000, awaitItem().steps)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
