package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.EmotionRecordsDao
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.sync.SyncScheduler
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
class EmotionsRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EmotionRecordsDao
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
        dao = db.emotionRecordsDao()
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String?) = EmotionsRepositoryImpl(
        emotionRecordsDao = dao,
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler
    )

    @Test
    fun `no user reads empty and drops writes without scheduling sync`() = runTest {
        val repo = repo(null)

        assertTrue(repo.observeToday().first().isEmpty())
        assertNull(repo.observeLatest().first())

        repo.logEmotion(EmotionType.HAPPINESS)
        assertEquals(0, syncRequests)
        assertTrue(dao.getUnsynced().isEmpty())
    }

    @Test
    fun `logEmotion writes an unsynced row, exposes it as latest, and requests sync`() = runTest {
        val repo = repo("u1")

        repo.logEmotion(EmotionType.SADNESS, note = "rough day")

        val latest = repo.observeLatest().first()
        assertNotNull(latest)
        assertEquals(EmotionType.SADNESS, latest!!.emotion)
        assertEquals("rough day", latest.note)
        assertEquals("manual", latest.source)

        val unsynced = dao.getUnsynced()
        assertEquals(1, unsynced.size)
        assertFalse(unsynced[0].isSynced)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `observeToday surfaces the logged mood`() = runTest {
        val repo = repo("u1")
        repo.logEmotion(EmotionType.CALM)

        repo.observeToday().test {
            val today = awaitItem()
            assertEquals(1, today.size)
            assertEquals(EmotionType.CALM, today[0].emotion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete tombstones the row so live reads drop it but push still sees it`() = runTest {
        val repo = repo("u1")
        repo.logEmotion(EmotionType.FEAR)
        val id = repo.observeLatest().first()!!.id

        repo.delete(id)

        // Live reads exclude tombstones...
        assertNull(repo.observeLatest().first())
        assertTrue(repo.observeToday().first().isEmpty())
        // ...but the tombstone is still unsynced so the delete propagates on push.
        val unsynced = dao.getUnsynced()
        assertEquals(1, unsynced.size)
        assertNotNull("tombstone carries a deletedAt", unsynced[0].deletedAtEpochMs)
        assertFalse(unsynced[0].isSynced)
    }
}
