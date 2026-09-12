package com.hellohealth.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.SyncLogDao
import com.hellohealth.data.repository.SupabaseSessionManager
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
class SyncOrchestratorTest {

    private lateinit var db: AppDatabase
    private lateinit var syncLogDao: SyncLogDao

    /** Records call order across all syncers so we can assert push-before-pull. */
    private val callOrder = mutableListOf<String>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        syncLogDao = db.syncLogDao()
    }

    @After
    fun tearDown() = db.close()

    /** getCurrentUserId() is overridden, so the null client path is never taken. */
    private fun sessionManager(userId: String?) =
        object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        }
    private inner class FakeSyncer(
        override val featureTag: FeatureTag,
        private val pushCount: Int,
        private val pullCount: Int,
        private val failOnPush: Boolean = false
    ) : Syncer {
        override suspend fun push(userId: String): Int {
            callOrder.add("${featureTag.tag}:push")
            if (failOnPush) throw RuntimeException("boom")
            return pushCount
        }

        override suspend fun pull(userId: String): Int {
            callOrder.add("${featureTag.tag}:pull")
            return pullCount
        }
    }

    @Test
    fun `no user is a clean no-op with no log rows`() = runTest {
        val orch = SyncOrchestrator(emptySet(), sessionManager(null), syncLogDao)

        val result = orch.syncAll()

        assertFalse(result.ranForUser)
        assertFalse(result.hadFailure)
        assertTrue(syncLogDao.observeRecent(10).first().isEmpty())
    }

    @Test
    fun `push runs before pull for each syncer and counts land in sync_log`() = runTest {
        val syncer = FakeSyncer(FeatureTag.GOALS, pushCount = 2, pullCount = 3)
        val orch = SyncOrchestrator(setOf(syncer), sessionManager("u1"), syncLogDao)

        val result = orch.syncAll()

        assertTrue(result.ranForUser)
        assertFalse(result.hadFailure)
        assertEquals(listOf("HH.Goals:push", "HH.Goals:pull"), callOrder)

        val logs = syncLogDao.observeRecent(10).first()
        assertEquals(1, logs.size)
        assertEquals(2, logs[0].pushed)
        assertEquals(3, logs[0].pulled)
        assertEquals(0, logs[0].failures)
        assertEquals("ok", logs[0].resultLabel)
    }

    @Test
    fun `a failing syncer is isolated but reports hadFailure and logs the failure`() = runTest {
        val ok = FakeSyncer(FeatureTag.GOALS, pushCount = 1, pullCount = 1)
        val bad = FakeSyncer(FeatureTag.PROFILE, pushCount = 0, pullCount = 0, failOnPush = true)
        val orch = SyncOrchestrator(setOf(ok, bad), sessionManager("u1"), syncLogDao)

        val result = orch.syncAll()

        assertTrue(result.ranForUser)
        assertTrue("any syncer failing must trigger retry", result.hadFailure)

        val logs = syncLogDao.observeRecent(10).first()
        // Both syncers produced a log row (failure is isolated, others still run).
        assertEquals(2, logs.size)
        val badLog = logs.first { it.featureTag == FeatureTag.PROFILE.tag }
        assertEquals(1, badLog.failures)
        assertTrue(badLog.resultLabel.startsWith("failed:"))
    }
}
