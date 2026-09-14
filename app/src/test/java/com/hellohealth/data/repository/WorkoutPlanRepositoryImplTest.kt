package com.hellohealth.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.domain.model.Exercise
import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.repository.ExerciseRepository
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
 * Repository tests for the planning hierarchy (plans → days → planned exercises). House style: a real
 * in-memory Room DB (so the cascade transaction and every DAO query run against real SQL), a
 * [SupabaseSessionManager] fake overriding the current user id, a [SyncScheduler] fake counting
 * requestSync, and a hand-written [ExerciseRepository] fake for the catalog join. No mockk.
 *
 * Coverage: user-gating (reads empty / writes dropped, no sync), create + list, the single-active
 * invariant on create and setActive, rename, add/reorder/update-targets, the joined observe with a
 * missing-catalog fallback, and the atomic cascade delete tombstoning parent + all descendants.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutPlanRepositoryImplTest {

    private lateinit var db: AppDatabase
    private var syncRequests = 0

    private val syncScheduler = object : SyncScheduler(
        ApplicationProvider.getApplicationContext<Context>()
    ) {
        override fun requestSync() { syncRequests++ }
    }

    /** Catalog fake: returns [Exercise]s for the ids it was seeded with; misses are simply absent. */
    private class FakeExerciseRepository(
        private val catalog: Map<String, Exercise> = emptyMap(),
    ) : ExerciseRepository {
        override suspend fun seedIfEmpty(): Int = catalog.size
        override suspend fun count(): Int = catalog.size
        override suspend fun search(query: String, limit: Int): List<Exercise> =
            catalog.values.filter { it.name.contains(query, ignoreCase = true) }.take(limit)
        override suspend fun getById(id: String): Exercise? = catalog[id]
        override suspend fun getByIds(ids: Collection<String>): Map<String, Exercise> =
            ids.mapNotNull { id -> catalog[id]?.let { id to it } }.toMap()
        override suspend fun getAllCategories(): List<String> =
            catalog.values.map { it.category }.distinct().sorted()
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

    private fun repo(
        userId: String?,
        catalog: Map<String, Exercise> = emptyMap(),
    ) = WorkoutPlanRepositoryImpl(
        db = db,
        planDao = db.workoutPlanDao(),
        dayDao = db.workoutDayDao(),
        plannedDao = db.plannedExerciseDao(),
        exerciseRepository = FakeExerciseRepository(catalog),
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler,
    )

    private fun exercise(id: String, name: String, category: String = "strength") = Exercise(
        id = id,
        name = name,
        category = category,
        primaryMuscles = listOf("chest"),
        secondaryMuscles = emptyList(),
        equipment = "barbell",
        instructions = listOf("do it"),
        gifUrl = "https://example/$id.jpg",
        youtubeQuery = "$name tutorial",
    )

    // ---------------------------------------------------------------- Gating

    @Test
    fun `no user reads empty and drops writes without scheduling sync`() = runTest {
        val repo = repo(null)

        assertTrue(repo.observePlans().first().isEmpty())
        assertNull(repo.observeActivePlan().first())

        val planId = repo.createPlan("Push/Pull", PlanType.WEEKLY)
        assertNull("createPlan returns null when signed out", planId)
        assertNull(repo.addDay("nope", "MONDAY", "Day"))
        assertNull(repo.addExercise("nope", "bench"))

        assertEquals(0, syncRequests)
        assertTrue(db.workoutPlanDao().getUnsynced().isEmpty())
        assertTrue(db.workoutDayDao().getUnsynced().isEmpty())
        assertTrue(db.plannedExerciseDao().getUnsynced().isEmpty())
    }

    // ---------------------------------------------------------------- Plans

    @Test
    fun `createPlan writes an active unsynced plan, lists it, and requests sync`() = runTest {
        val repo = repo("u1")

        val id = repo.createPlan("Hypertrophy", PlanType.WEEKLY)
        assertNotNull(id)

        val plans = repo.observePlans().first()
        assertEquals(1, plans.size)
        assertEquals("Hypertrophy", plans[0].name)
        assertEquals(PlanType.WEEKLY, plans[0].planType)
        assertTrue(plans[0].isActive)

        assertEquals(id, repo.observeActivePlan().first()?.id)

        val unsynced = db.workoutPlanDao().getUnsynced()
        assertEquals(1, unsynced.size)
        assertFalse(unsynced[0].isSynced)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `createPlan with makeActive false does not become active`() = runTest {
        val repo = repo("u1")

        repo.createPlan("First", PlanType.WEEKLY, makeActive = true)
        val secondId = repo.createPlan("Second", PlanType.MONTHLY, makeActive = false)

        assertEquals(2, repo.observePlans().first().size)
        // The first plan stays the sole active one.
        val active = repo.observeActivePlan().first()
        assertNotNull(active)
        assertEquals("First", active!!.name)
        assertFalse(repo.getPlan(secondId!!)!!.isActive)
    }

    @Test
    fun `creating a second active plan clears the first - single-active invariant`() = runTest {
        val repo = repo("u1")

        val firstId = repo.createPlan("First", PlanType.WEEKLY)!!
        val secondId = repo.createPlan("Second", PlanType.CUSTOM)!!

        assertFalse("first plan deactivated", repo.getPlan(firstId)!!.isActive)
        assertTrue("second plan is active", repo.getPlan(secondId)!!.isActive)
        assertEquals(secondId, repo.observeActivePlan().first()?.id)
    }

    @Test
    fun `setActivePlan flips the active flag to exactly one plan`() = runTest {
        val repo = repo("u1")
        val firstId = repo.createPlan("First", PlanType.WEEKLY)!!
        val secondId = repo.createPlan("Second", PlanType.WEEKLY)!! // now the active one

        repo.setActivePlan(firstId)

        assertTrue(repo.getPlan(firstId)!!.isActive)
        assertFalse(repo.getPlan(secondId)!!.isActive)
        assertEquals(firstId, repo.observeActivePlan().first()?.id)
    }

    @Test
    fun `renamePlan updates the name and re-marks unsynced`() = runTest {
        val repo = repo("u1")
        val id = repo.createPlan("Old", PlanType.WEEKLY)!!
        db.workoutPlanDao().markSynced(id, repo.getPlan(id)!!.updatedAt)
        assertTrue(db.workoutPlanDao().getUnsynced().isEmpty())

        repo.renamePlan(id, "New")

        assertEquals("New", repo.getPlan(id)!!.name)
        assertEquals(1, db.workoutPlanDao().getUnsynced().size)
    }

    @Test
    fun `renamePlan on unknown id is a no-op`() = runTest {
        val repo = repo("u1")
        repo.renamePlan("ghost", "New")
        assertTrue(repo.observePlans().first().isEmpty())
    }

    // ---------------------------------------------------------------- Days

    @Test
    fun `addDay appends a day observable under its plan`() = runTest {
        val repo = repo("u1")
        val planId = repo.createPlan("Split", PlanType.WEEKLY)!!

        val dayId = repo.addDay(planId, "MONDAY", "Push")
        assertNotNull(dayId)

        val days = repo.observeDays(planId).first()
        assertEquals(1, days.size)
        assertEquals("Push", days[0].name)
        assertEquals("MONDAY", days[0].slotKey)
        assertEquals(planId, days[0].planId)
    }

    // ------------------------------------------------------ Planned exercises

    @Test
    fun `addExercise assigns sequential order indices and default target sets`() = runTest {
        val repo = repo("u1", catalog = mapOf("bench" to exercise("bench", "Bench Press")))
        val planId = repo.createPlan("Split", PlanType.WEEKLY)!!
        val dayId = repo.addDay(planId, "MONDAY", "Push")!!

        val firstId = repo.addExercise(dayId, "bench")!!
        val secondId = repo.addExercise(dayId, "bench")!!

        val rows = repo.observePlannedExercises(dayId).first()
        assertEquals(2, rows.size)
        assertEquals(0, rows[0].planned.orderIndex)
        assertEquals(1, rows[1].planned.orderIndex)
        assertEquals(3, rows[0].planned.targetSets)
        // The catalog join resolved the name.
        assertEquals("Bench Press", rows[0].exercise?.name)
        assertNotNull(firstId)
        assertNotNull(secondId)
    }

    @Test
    fun `observePlannedExercises keeps a row whose catalog entry is missing`() = runTest {
        // No catalog entry for the added exercise id → exercise is null, row still present.
        val repo = repo("u1")
        val planId = repo.createPlan("Split", PlanType.WEEKLY)!!
        val dayId = repo.addDay(planId, "MONDAY", "Push")!!
        repo.addExercise(dayId, "unknown-id")

        val rows = repo.observePlannedExercises(dayId).first()
        assertEquals(1, rows.size)
        assertNull(rows[0].exercise)
        assertEquals("unknown-id", rows[0].planned.exerciseId)
    }

    @Test
    fun `updateTargets persists the edited target vocabulary`() = runTest {
        val repo = repo("u1", catalog = mapOf("bench" to exercise("bench", "Bench Press")))
        val planId = repo.createPlan("Split", PlanType.WEEKLY)!!
        val dayId = repo.addDay(planId, "MONDAY", "Push")!!
        repo.addExercise(dayId, "bench")
        val planned = repo.observePlannedExercises(dayId).first()[0].planned

        repo.updateTargets(planned.copy(targetSets = 5, targetReps = 8, targetWeightKg = 80f))

        val updated = repo.observePlannedExercises(dayId).first()[0].planned
        assertEquals(5, updated.targetSets)
        assertEquals(8, updated.targetReps)
        assertEquals(80f, updated.targetWeightKg)
    }

    @Test
    fun `reorderExercises rewrites order indices to match the given order`() = runTest {
        val repo = repo("u1", catalog = mapOf("bench" to exercise("bench", "Bench Press")))
        val planId = repo.createPlan("Split", PlanType.WEEKLY)!!
        val dayId = repo.addDay(planId, "MONDAY", "Push")!!
        val a = repo.addExercise(dayId, "bench")!!
        val b = repo.addExercise(dayId, "bench")!!
        val c = repo.addExercise(dayId, "bench")!!

        repo.reorderExercises(dayId, listOf(c, a, b))

        val order = repo.observePlannedExercises(dayId).first().map { it.planned.id }
        assertEquals(listOf(c, a, b), order)
    }

    // ------------------------------------------------------ Cascade delete

    @Test
    fun `deletePlan tombstones the plan and all its days and planned exercises atomically`() = runTest {
        val repo = repo("u1", catalog = mapOf("bench" to exercise("bench", "Bench Press")))
        val planId = repo.createPlan("Split", PlanType.WEEKLY)!!
        val dayId = repo.addDay(planId, "MONDAY", "Push")!!
        repo.addExercise(dayId, "bench")
        syncRequests = 0

        repo.deletePlan(planId)

        // Live reads drop everything...
        assertTrue(repo.observePlans().first().isEmpty())
        assertTrue(repo.observeDays(planId).first().isEmpty())
        assertTrue(repo.observePlannedExercises(dayId).first().isEmpty())

        // ...but every tombstone is unsynced so the deletes propagate, and only one sync was requested.
        assertEquals(1, db.workoutPlanDao().getUnsynced().size)
        assertEquals(1, db.workoutDayDao().getUnsynced().size)
        assertEquals(1, db.plannedExerciseDao().getUnsynced().size)
        assertNotNull(db.workoutPlanDao().getUnsynced()[0].deletedAtEpochMs)
        assertNotNull(db.workoutDayDao().getUnsynced()[0].deletedAtEpochMs)
        assertNotNull(db.plannedExerciseDao().getUnsynced()[0].deletedAtEpochMs)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `deleteDay tombstones the day and its planned exercises but leaves the plan`() = runTest {
        val repo = repo("u1", catalog = mapOf("bench" to exercise("bench", "Bench Press")))
        val planId = repo.createPlan("Split", PlanType.WEEKLY)!!
        val dayId = repo.addDay(planId, "MONDAY", "Push")!!
        repo.addExercise(dayId, "bench")

        repo.deleteDay(dayId)

        assertTrue(repo.observeDays(planId).first().isEmpty())
        assertTrue(repo.observePlannedExercises(dayId).first().isEmpty())
        // The parent plan survives.
        assertEquals(1, repo.observePlans().first().size)
    }
}
