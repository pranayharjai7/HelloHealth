package com.hellohealth.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.hellohealth.data.local.dao.GoalsDao
import com.hellohealth.data.local.entities.GoalsEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * In-memory Room tests for the sync-metadata contract, exercised through GoalsDao (representative
 * of every per-user Syncable table). Verifies the invariants every Syncer depends on:
 *  - a fresh local write defaults to isSynced = false and surfaces in getUnsynced()
 *  - observe()/get() hide tombstones (deletedAtEpochMs != null)
 *  - markSynced() is guarded by the row's updatedAtEpochMs, so it can't ack a newer local edit
 */
@RunWith(RobolectricTestRunner::class)
class SyncableDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: GoalsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.goalsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun goals(
        userId: String = "u1",
        updatedAt: Long = 1_000L,
        deletedAt: Long? = null,
        isSynced: Boolean = false
    ) = GoalsEntity(
        userId = userId,
        steps = 10_000,
        activeCalories = 500,
        activeMinutes = 30,
        updatedAtEpochMs = updatedAt,
        updatedAtTzOffsetMinutes = 0,
        deletedAtEpochMs = deletedAt,
        isSynced = isSynced
    )

    @Test
    fun `fresh write is unsynced and readable`() = runTest {
        dao.upsert(goals())

        val row = dao.get("u1")
        assertEquals(10_000, row?.steps)
        assertTrue("a fresh local write must be unsynced", row?.isSynced == false)

        assertEquals(1, dao.getUnsynced().size)
    }

    @Test
    fun `observe excludes tombstoned rows`() = runTest {
        dao.upsert(goals(deletedAt = null))

        dao.observe("u1").test {
            assertEquals(10_000, awaitItem()?.steps)

            // Tombstone the row — observers should see it disappear.
            dao.upsert(goals(updatedAt = 2_000L, deletedAt = 2_000L))
            assertNull("observe() must hide tombstones", awaitItem())
        }
    }

    @Test
    fun `get returns null for tombstoned row but getUnsynced still includes it`() = runTest {
        dao.upsert(goals(deletedAt = 2_000L, isSynced = false))

        assertNull("get() must hide tombstones", dao.get("u1"))
        assertEquals("tombstones must still push to remote", 1, dao.getUnsynced().size)
    }

    @Test
    fun `markSynced acks only the matching timestamp`() = runTest {
        dao.upsert(goals(updatedAt = 1_000L, isSynced = false))

        // A stale ack (older timestamp than the row) must NOT flip isSynced.
        dao.markSynced("u1", updatedAtEpochMs = 999L)
        assertEquals(1, dao.getUnsynced().size)

        // The matching ack flips it.
        dao.markSynced("u1", updatedAtEpochMs = 1_000L)
        assertTrue(dao.getUnsynced().isEmpty())
        assertTrue(dao.get("u1")?.isSynced == true)
    }

    @Test
    fun `local edit after push keeps row unsynced when ack is for old timestamp`() = runTest {
        dao.upsert(goals(updatedAt = 1_000L, isSynced = false))
        // Sync starts, then the user edits again before the ack lands (updatedAt bumps to 2000).
        dao.upsert(goals(updatedAt = 2_000L, isSynced = false))

        // The in-flight push acks the OLD timestamp — must not mark the newer edit synced.
        dao.markSynced("u1", updatedAtEpochMs = 1_000L)

        assertEquals("newer local edit must survive a stale ack", 1, dao.getUnsynced().size)
        assertEquals(2_000L, dao.get("u1")?.updatedAtEpochMs)
    }
}
