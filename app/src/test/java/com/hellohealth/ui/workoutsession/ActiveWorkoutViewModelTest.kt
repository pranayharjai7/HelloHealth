package com.hellohealth.ui.workoutsession

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.domain.model.PlannedExerciseWithDetails
import com.hellohealth.domain.model.SessionSet
import com.hellohealth.domain.model.SessionStatus
import com.hellohealth.domain.model.WorkoutSession
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.ui.navigation.Screen
import com.hellohealth.workoutsession.RestState
import com.hellohealth.workoutsession.WorkoutSessionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Turbine-free VM tests for [ActiveWorkoutViewModel], driven through its [kotlinx.coroutines.flow.StateFlow]
 * uiState (StandardTestDispatcher + advanceUntilIdle). Uses a fake [WorkoutSessionRepository] backed by
 * plain StateFlows and a REAL [WorkoutSessionController] (Robolectric SharedPreferences) so the rest
 * timer path is exercised end to end. [WorkoutPlanRepository] is stubbed to a no-op (prefill only).
 *
 * Covers: ad-hoc start opens exactly one active session; logging a set with a rest starts the rest
 * timer; finish completes the session + clears the rest + flips finished; the single-active guard
 * (start is a no-op when a session is already active).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** In-memory session repo: a StateFlow of sessions + a StateFlow of sets, mutated by the actions. */
    private class FakeSessionRepo : com.hellohealth.domain.repository.WorkoutSessionRepository {
        val sessions = MutableStateFlow<List<WorkoutSession>>(emptyList())
        val sets = MutableStateFlow<List<SessionSet>>(emptyList())
        var nextId = 0
        var finishedCalls = 0

        private fun active() = sessions.value.firstOrNull { it.status == SessionStatus.ACTIVE }

        override fun observeActiveSession(): Flow<WorkoutSession?> =
            sessions.map { it.firstOrNull { s -> s.status == SessionStatus.ACTIVE } }
        override fun observeSessionsForDay(localDate: String): Flow<List<WorkoutSession>> = sessions
        override fun observeRecentSessions(startDate: String, endDate: String): Flow<List<WorkoutSession>> = sessions
        override fun observeSets(sessionId: String): Flow<List<SessionSet>> =
            sets.map { all -> all.filter { it.sessionId == sessionId } }
        override suspend fun getSession(id: String): WorkoutSession? = sessions.value.firstOrNull { it.id == id }
        override suspend fun activeSessionId(): String? = active()?.id

        override suspend fun startSession(activityType: String, planId: String?, dayId: String?, title: String?): String {
            val id = "s${nextId++}"
            // Abandon any existing active (single-active) then add the new ACTIVE one.
            sessions.value = sessions.value.map {
                if (it.status == SessionStatus.ACTIVE) it.copy(status = SessionStatus.ABANDONED) else it
            } + WorkoutSession(
                id = id, userId = "u1", planId = planId, dayId = dayId, title = title,
                activityType = activityType, startEpochMs = 1_000L, endEpochMs = null, durationSeconds = null,
                status = SessionStatus.ACTIVE, localDate = "2026-10-08", note = null,
                totalVolumeKg = null, caloriesEstimate = null, updatedAt = 1_000L,
            )
            return id
        }

        override suspend fun logSet(
            sessionId: String, exerciseId: String, plannedExerciseId: String?, reps: Int?,
            weightKg: Double?, durationSeconds: Int?, distanceKm: Double?, rpe: Double?, isWarmup: Boolean,
        ): String {
            val id = "set${nextId++}"
            val n = sets.value.count { it.sessionId == sessionId && it.exerciseId == exerciseId } + 1
            sets.value = sets.value + SessionSet(
                id = id, sessionId = sessionId, userId = "u1", plannedExerciseId = plannedExerciseId,
                exerciseId = exerciseId, orderIndex = 0, setNumber = n, reps = reps, weightKg = weightKg,
                durationSeconds = durationSeconds, distanceKm = distanceKm, rpe = rpe, isWarmup = isWarmup,
                isCompleted = true, isSkipped = false, loggedAt = 2_000L, updatedAt = 2_000L,
            )
            return id
        }

        override suspend fun editSet(set: SessionSet) {
            sets.value = sets.value.map { if (it.id == set.id) set else it }
        }
        override suspend fun skipSet(id: String) {
            sets.value = sets.value.map { if (it.id == id) it.copy(isSkipped = true, isCompleted = false) else it }
        }
        override suspend fun deleteSet(id: String) { sets.value = sets.value.filterNot { it.id == id } }

        override suspend fun finishSession(sessionId: String, caloriesEstimate: Double?) {
            finishedCalls++
            sessions.value = sessions.value.map {
                if (it.id == sessionId) it.copy(status = SessionStatus.COMPLETED, endEpochMs = 9_000L, durationSeconds = 8) else it
            }
        }
        override suspend fun abandonSession(sessionId: String) {
            sessions.value = sessions.value.map {
                if (it.id == sessionId) it.copy(status = SessionStatus.ABANDONED) else it
            }
        }
        override suspend fun deleteSession(id: String) { sessions.value = sessions.value.filterNot { it.id == id } }
        override suspend fun lastCompletedSet(exerciseId: String): SessionSet? =
            sets.value.lastOrNull { it.exerciseId == exerciseId && it.isCompleted && !it.isSkipped && !it.isWarmup }
    }

    /** No-op plan repo — the active-workout VM only reads planned exercises for prefill. */
    private class StubPlanRepo : WorkoutPlanRepository {
        override fun observePlans() = MutableStateFlow(emptyList<com.hellohealth.domain.model.WorkoutPlan>())
        override fun observeActivePlan() = MutableStateFlow<com.hellohealth.domain.model.WorkoutPlan?>(null)
        override suspend fun getPlan(id: String) = null
        override suspend fun createPlan(name: String, planType: com.hellohealth.domain.model.PlanType, makeActive: Boolean): String? = null
        override suspend fun renamePlan(id: String, name: String) {}
        override suspend fun setActivePlan(id: String) {}
        override suspend fun deletePlan(id: String) {}
        override fun observeDays(planId: String) = MutableStateFlow(emptyList<com.hellohealth.domain.model.WorkoutDay>())
        override suspend fun getDay(id: String): com.hellohealth.domain.model.WorkoutDay? = null
        override suspend fun addDay(planId: String, slotKey: String, name: String): String? = null
        override suspend fun renameDay(id: String, name: String) {}
        override suspend fun deleteDay(id: String) {}
        override fun observePlannedExercises(dayId: String): Flow<List<PlannedExerciseWithDetails>> =
            MutableStateFlow(emptyList())
        override suspend fun getPlannedExercise(id: String): PlannedExerciseWithDetails? = null
        override suspend fun addExercise(dayId: String, exerciseId: String): String? = null
        override suspend fun updateTargets(planned: com.hellohealth.domain.model.PlannedExercise) {}
        override suspend fun reorderExercises(dayId: String, orderedIds: List<String>) {}
        override suspend fun deleteExercise(id: String) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context.getSharedPreferences("workout_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        context.getSharedPreferences("workout_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun viewModel(
        sessionRepo: FakeSessionRepo,
        dayId: String? = null,
    ): ActiveWorkoutViewModel {
        val handle = SavedStateHandle(mapOf(Screen.ActiveWorkout.dayIdArg to (dayId ?: "none")))
        return ActiveWorkoutViewModel(
            sessionRepository = sessionRepo,
            planRepository = StubPlanRepo(),
            controller = WorkoutSessionController(context),
            savedStateHandle = handle,
        )
    }

    @Test
    fun `startAdHoc opens exactly one active session`() = runTest(dispatcher) {
        val repo = FakeSessionRepo()
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} } // keep uiState hot (WhileSubscribed)

        vm.startAdHoc()
        advanceUntilIdle()
        // Calling again must NOT open a second (single-active guard).
        vm.startAdHoc()
        advanceUntilIdle()

        assertEquals(1, repo.sessions.value.count { it.status == SessionStatus.ACTIVE })
        assertTrue(vm.uiState.value.isActive)
        job.cancel()
    }

    @Test
    fun `logging a set with a rest starts the rest timer`() = runTest(dispatcher) {
        val repo = FakeSessionRepo()
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        vm.startAdHoc()
        advanceUntilIdle()

        vm.logSet(exerciseId = "bench", reps = 8, weightKg = 100.0, restSeconds = 90)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.sets.size)
        assertTrue("rest timer is running", vm.uiState.value.restState is RestState.Resting)
        job.cancel()
    }

    @Test
    fun `finish completes the session, clears rest, and flips finished`() = runTest(dispatcher) {
        val repo = FakeSessionRepo()
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        vm.startAdHoc()
        advanceUntilIdle()
        vm.logSet(exerciseId = "bench", reps = 8, weightKg = 100.0, restSeconds = 90)
        advanceUntilIdle()

        vm.finish()
        advanceUntilIdle()

        assertEquals(1, repo.finishedCalls)
        assertTrue("finished flag flips", vm.uiState.value.finished)
        assertEquals(RestState.Idle, vm.uiState.value.restState)
        assertNull("no active session remains", vm.uiState.value.session)
        job.cancel()
    }

    @Test
    fun `skip excludes a set from the live volume total`() = runTest(dispatcher) {
        val repo = FakeSessionRepo()
        val vm = viewModel(repo)
        val job = launch { vm.uiState.collect {} }
        vm.startAdHoc()
        advanceUntilIdle()
        vm.logSet(exerciseId = "bench", reps = 8, weightKg = 100.0)
        advanceUntilIdle()
        val skipId = vm.uiState.value.sets.last().id
        vm.logSet(exerciseId = "bench", reps = 8, weightKg = 100.0)
        advanceUntilIdle()

        assertEquals(1600.0, vm.uiState.value.totalVolumeKg, 0.0001)

        vm.skipSet(skipId)
        advanceUntilIdle()
        assertEquals("skipped set drops out of volume", 800.0, vm.uiState.value.totalVolumeKg, 0.0001)
        assertTrue(vm.uiState.value.sets.first { it.id == skipId }.isSkipped)
        job.cancel()
    }

    @Test
    fun `resuming (dayId none) re-attaches to a live session without starting a new one`() = runTest(dispatcher) {
        // Simulate an already-running session (e.g. opened via the resume affordance / notification
        // deep link while a workout is in progress). The deep link carries dayId=none.
        val repo = FakeSessionRepo()
        repo.sessions.value = listOf(
            WorkoutSession(
                id = "existing", userId = "u1", planId = null, dayId = null, title = "Push day",
                activityType = "strength_training", startEpochMs = 1_000L, endEpochMs = null,
                durationSeconds = null, status = SessionStatus.ACTIVE, localDate = "2026-10-08",
                note = null, totalVolumeKg = null, caloriesEstimate = null, updatedAt = 1_000L,
            )
        )
        val vm = viewModel(repo, dayId = null) // null → "none" sentinel in SavedStateHandle
        val job = launch { vm.uiState.collect {} }

        // The screen calls startAdHoc() on entry; it must NO-OP because a session is already active.
        vm.startAdHoc()
        advanceUntilIdle()

        assertEquals("no second session started", 1, repo.sessions.value.count { it.status == SessionStatus.ACTIVE })
        assertEquals("re-attached to the existing session", "existing", vm.uiState.value.session?.id)
        job.cancel()
    }
}
