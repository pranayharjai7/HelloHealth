package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.domain.model.SessionStatus
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

/**
 * Repository tests for live workout logging (sessions → sets). House style: a real in-memory Room DB
 * (so the single-active transaction, cascade delete, and volume re-derivation run against real SQL),
 * a [SupabaseSessionManager] fake overriding the current user id, and a [SyncScheduler] fake counting
 * requestSync. No mockk.
 *
 * Coverage: user-gating; start→log→finish lifecycle with volume derivation; the single-active
 * invariant (a second start abandons the first); edit re-derives volume at finish; skip excludes
 * from volume; the atomic cascade delete; and prefill-from-last-set.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutSessionRepositoryImplTest {

    private lateinit var db: AppDatabase
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
    }

    @After
    fun tearDown() = db.close()

    private fun repo(userId: String?) = WorkoutSessionRepositoryImpl(
        db = db,
        sessionDao = db.workoutSessionDao(),
        setDao = db.sessionSetDao(),
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler,
    )

    // ---------------------------------------------------------------- Gating

    @Test
    fun `no user reads empty and drops writes without scheduling sync`() = runTest {
        val repo = repo(null)

        assertNull(repo.observeActiveSession().first())
        assertTrue(repo.observeSessionsForDay("2026-10-08").first().isEmpty())

        assertNull("startSession returns null when signed out", repo.startSession("strength_training"))
        assertNull(repo.logSet("nope", "bench"))
        assertNull(repo.lastCompletedSet("bench"))

        assertEquals(0, syncRequests)
        assertTrue(db.workoutSessionDao().getUnsynced().isEmpty())
        assertTrue(db.sessionSetDao().getUnsynced().isEmpty())
    }

    // ------------------------------------------------------------- Lifecycle

    @Test
    fun `start then log then finish derives volume and completes the session`() = runTest {
        val repo = repo("u1")

        val sessionId = repo.startSession("strength_training", title = "Push day")!!
        assertEquals(sessionId, repo.observeActiveSession().first()?.id)

        repo.logSet(sessionId, "bench", reps = 8, weightKg = 100.0)
        repo.logSet(sessionId, "bench", reps = 8, weightKg = 100.0)

        val sets = repo.observeSets(sessionId).first()
        assertEquals(2, sets.size)
        assertEquals(1, sets[0].setNumber)
        assertEquals(2, sets[1].setNumber)

        repo.finishSession(sessionId, caloriesEstimate = 320.0)

        val finished = repo.getSession(sessionId)!!
        assertEquals(SessionStatus.COMPLETED, finished.status)
        assertNotNull(finished.endEpochMs)
        assertNotNull(finished.durationSeconds)
        assertEquals(1600.0, finished.totalVolumeKg!!, 0.0001) // 2 × (8 × 100)
        assertEquals(320.0, finished.caloriesEstimate!!, 0.0001)
        // The active-session flow now emits null (session is completed).
        assertNull(repo.observeActiveSession().first())
    }

    @Test
    fun `finish on an unknown or already-finished session is a no-op`() = runTest {
        val repo = repo("u1")
        repo.finishSession("ghost") // unknown — no throw

        val sessionId = repo.startSession("running")!!
        repo.finishSession(sessionId)
        val firstEnd = repo.getSession(sessionId)!!.endEpochMs
        repo.finishSession(sessionId) // second finish ignored
        assertEquals(firstEnd, repo.getSession(sessionId)!!.endEpochMs)
    }

    // ------------------------------------------------- Single-active invariant

    @Test
    fun `starting a second session abandons the first`() = runTest {
        val repo = repo("u1")

        val first = repo.startSession("strength_training")!!
        val second = repo.startSession("running")!!

        assertEquals(SessionStatus.ABANDONED, repo.getSession(first)!!.status)
        assertEquals(SessionStatus.ACTIVE, repo.getSession(second)!!.status)
        assertEquals(second, repo.observeActiveSession().first()?.id)
    }

    // ---------------------------------------------------- Volume on edit/skip

    @Test
    fun `editing a set before finish changes the derived volume`() = runTest {
        val repo = repo("u1")
        val sessionId = repo.startSession("strength_training")!!
        val setId = repo.logSet(sessionId, "bench", reps = 5, weightKg = 100.0)!!

        val set = repo.observeSets(sessionId).first().first { it.id == setId }
        repo.editSet(set.copy(reps = 10, weightKg = 100.0))

        repo.finishSession(sessionId)
        assertEquals(1000.0, repo.getSession(sessionId)!!.totalVolumeKg!!, 0.0001) // 10 × 100
    }

    @Test
    fun `a skipped set is excluded from volume`() = runTest {
        val repo = repo("u1")
        val sessionId = repo.startSession("strength_training")!!
        repo.logSet(sessionId, "bench", reps = 8, weightKg = 100.0)
        val skipId = repo.logSet(sessionId, "bench", reps = 8, weightKg = 100.0)!!

        repo.skipSet(skipId)
        val skipped = repo.observeSets(sessionId).first().first { it.id == skipId }
        assertTrue(skipped.isSkipped)
        assertFalse(skipped.isCompleted)

        repo.finishSession(sessionId)
        assertEquals(800.0, repo.getSession(sessionId)!!.totalVolumeKg!!, 0.0001) // only the 1 kept set
    }

    @Test
    fun `logSet is dropped for a non-active session`() = runTest {
        val repo = repo("u1")
        val sessionId = repo.startSession("strength_training")!!
        repo.finishSession(sessionId)

        assertNull("cannot log into a finished session", repo.logSet(sessionId, "bench", reps = 8, weightKg = 50.0))
    }

    // ------------------------------------------------------ Cascade delete

    @Test
    fun `deleteSession tombstones the session and all its sets atomically`() = runTest {
        val repo = repo("u1")
        val sessionId = repo.startSession("strength_training")!!
        repo.logSet(sessionId, "bench", reps = 8, weightKg = 100.0)
        repo.logSet(sessionId, "bench", reps = 8, weightKg = 100.0)
        syncRequests = 0

        repo.deleteSession(sessionId)

        assertNull(repo.getSession(sessionId))
        assertTrue(repo.observeSets(sessionId).first().isEmpty())
        // Tombstones are unsynced so the deletes propagate; one sync requested for the whole cascade.
        val unsyncedSets = db.sessionSetDao().getUnsynced()
        assertEquals(2, unsyncedSets.size)
        assertTrue(unsyncedSets.all { it.deletedAtEpochMs != null })
        val unsyncedSession = db.workoutSessionDao().getUnsynced().first { it.id == sessionId }
        assertNotNull(unsyncedSession.deletedAtEpochMs)
        assertEquals(1, syncRequests)
    }

    // --------------------------------------------------------------- Prefill

    @Test
    fun `lastCompletedSet returns the most recent working set for an exercise`() = runTest {
        val repo = repo("u1")
        val s1 = repo.startSession("strength_training")!!
        repo.logSet(s1, "bench", reps = 8, weightKg = 100.0)
        repo.finishSession(s1)

        val prefill = repo.lastCompletedSet("bench")
        assertNotNull(prefill)
        assertEquals(8, prefill!!.reps)
        assertEquals(100.0, prefill.weightKg!!, 0.0001)

        // A different exercise has no prior set.
        assertNull(repo.lastCompletedSet("squat"))
    }
}
