package com.hellohealth.ui.dashboard

import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.PlannedExerciseWithDetails
import com.hellohealth.domain.model.SessionSet
import com.hellohealth.domain.model.SessionStatus
import com.hellohealth.domain.model.WorkoutDay
import com.hellohealth.domain.model.WorkoutPlan
import com.hellohealth.domain.model.WorkoutSession
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.domain.repository.WorkoutSessionRepository
import com.hellohealth.domain.usecase.ResolveSmartStartDayUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the dashboard Training-card VM's [WorkoutPlanViewModel.hasActiveSession] flag — the signal
 * that gates the "Resume workout" affordance. StandardTestDispatcher + a background collector keep
 * the WhileSubscribed flow hot. Fakes only (no mockk).
 *
 * Cases: no active session → false; active → true; active→null (finished while backgrounded) → false;
 * signed-out (session flow emits null) → false, no crash.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutPlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Minimal session repo: a single MutableStateFlow<WorkoutSession?> for the active session. */
    private class FakeSessionRepo : WorkoutSessionRepository {
        val active = MutableStateFlow<WorkoutSession?>(null)
        val recent = MutableStateFlow<List<WorkoutSession>>(emptyList())
        override fun observeActiveSession(): Flow<WorkoutSession?> = active
        override fun observeSessionsForDay(localDate: String): Flow<List<WorkoutSession>> = MutableStateFlow(emptyList())
        override fun observeRecentSessions(startDate: String, endDate: String): Flow<List<WorkoutSession>> = recent
        override fun observeSets(sessionId: String): Flow<List<SessionSet>> = MutableStateFlow(emptyList())
        override suspend fun getSession(id: String): WorkoutSession? = active.value?.takeIf { it.id == id }
        override suspend fun activeSessionId(): String? = active.value?.id
        override suspend fun startSession(activityType: String, planId: String?, dayId: String?, title: String?): String? = null
        override suspend fun logSet(sessionId: String, exerciseId: String, plannedExerciseId: String?, reps: Int?, weightKg: Double?, durationSeconds: Int?, distanceKm: Double?, rpe: Double?, isWarmup: Boolean): String? = null
        override suspend fun editSet(set: SessionSet) {}
        override suspend fun skipSet(id: String) {}
        override suspend fun deleteSet(id: String) {}
        override suspend fun finishSession(sessionId: String, caloriesEstimate: Double?) {}
        override suspend fun abandonSession(sessionId: String) {}
        override suspend fun deleteSession(id: String) {}
        override suspend fun lastCompletedSet(exerciseId: String): SessionSet? = null
    }

    /** No-op plan repo — the VM's summary chain isn't under test here. */
    private class StubPlanRepo : WorkoutPlanRepository {
        override fun observePlans() = MutableStateFlow(emptyList<WorkoutPlan>())
        override fun observeActivePlan() = MutableStateFlow<WorkoutPlan?>(null)
        override suspend fun getPlan(id: String): WorkoutPlan? = null
        override suspend fun createPlan(name: String, planType: PlanType, makeActive: Boolean): String? = null
        override suspend fun renamePlan(id: String, name: String) {}
        override suspend fun setActivePlan(id: String) {}
        override suspend fun deletePlan(id: String) {}
        override fun observeDays(planId: String) = MutableStateFlow(emptyList<WorkoutDay>())
        override suspend fun getDay(id: String): WorkoutDay? = null
        override suspend fun addDay(planId: String, slotKey: String, name: String): String? = null
        override suspend fun renameDay(id: String, name: String) {}
        override suspend fun deleteDay(id: String) {}
        override fun observePlannedExercises(dayId: String): Flow<List<PlannedExerciseWithDetails>> = MutableStateFlow(emptyList())
        override suspend fun getPlannedExercise(id: String): PlannedExerciseWithDetails? = null
        override suspend fun addExercise(dayId: String, exerciseId: String): String? = null
        override suspend fun updateTargets(planned: com.hellohealth.domain.model.PlannedExercise) {}
        override suspend fun reorderExercises(dayId: String, orderedIds: List<String>) {}
        override suspend fun deleteExercise(id: String) {}
    }

    /**
     * A plan repo with one active CUSTOM plan and a fixed day list — enough to exercise the summary
     * chain's Smart-Start resolution. CUSTOM is used because next-in-sequence is date-independent (so
     * the test doesn't depend on today's weekday/day-of-month).
     */
    private class CustomPlanRepo(private val days: List<WorkoutDay>) : WorkoutPlanRepository {
        private val plan = WorkoutPlan(
            id = "plan1", userId = "u1", name = "My Split", isActive = true,
            planType = PlanType.CUSTOM, createdAt = 0L, updatedAt = 0L,
        )
        override fun observePlans() = MutableStateFlow(listOf(plan))
        override fun observeActivePlan() = MutableStateFlow<WorkoutPlan?>(plan)
        override suspend fun getPlan(id: String): WorkoutPlan? = plan.takeIf { it.id == id }
        override suspend fun createPlan(name: String, planType: PlanType, makeActive: Boolean): String? = null
        override suspend fun renamePlan(id: String, name: String) {}
        override suspend fun setActivePlan(id: String) {}
        override suspend fun deletePlan(id: String) {}
        override fun observeDays(planId: String) = MutableStateFlow(days)
        override suspend fun getDay(id: String): WorkoutDay? = days.firstOrNull { it.id == id }
        override suspend fun addDay(planId: String, slotKey: String, name: String): String? = null
        override suspend fun renameDay(id: String, name: String) {}
        override suspend fun deleteDay(id: String) {}
        override fun observePlannedExercises(dayId: String): Flow<List<PlannedExerciseWithDetails>> = MutableStateFlow(emptyList())
        override suspend fun getPlannedExercise(id: String): PlannedExerciseWithDetails? = null
        override suspend fun addExercise(dayId: String, exerciseId: String): String? = null
        override suspend fun updateTargets(planned: com.hellohealth.domain.model.PlannedExercise) {}
        override suspend fun reorderExercises(dayId: String, orderedIds: List<String>) {}
        override suspend fun deleteExercise(id: String) {}
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun session() = WorkoutSession(
        id = "s1", userId = "u1", planId = null, dayId = null, title = "Push day",
        activityType = "strength_training", startEpochMs = 1_000L, endEpochMs = null,
        durationSeconds = null, status = SessionStatus.ACTIVE, localDate = "2026-10-09",
        note = null, totalVolumeKg = null, caloriesEstimate = null, updatedAt = 1_000L,
    )

    @Test
    fun `no active session reports false`() = runTest(dispatcher) {
        val repo = FakeSessionRepo()
        val vm = WorkoutPlanViewModel(StubPlanRepo(), repo, ResolveSmartStartDayUseCase())
        val job = launch { vm.hasActiveSession.collect {} }
        advanceUntilIdle()
        assertFalse(vm.hasActiveSession.value)
        job.cancel()
    }

    @Test
    fun `an active session reports true`() = runTest(dispatcher) {
        val repo = FakeSessionRepo().apply { active.value = session() }
        val vm = WorkoutPlanViewModel(StubPlanRepo(), repo, ResolveSmartStartDayUseCase())
        val job = launch { vm.hasActiveSession.collect {} }
        advanceUntilIdle()
        assertTrue(vm.hasActiveSession.value)
        job.cancel()
    }

    @Test
    fun `finishing a session (active to null) flips back to false`() = runTest(dispatcher) {
        val repo = FakeSessionRepo().apply { active.value = session() }
        val vm = WorkoutPlanViewModel(StubPlanRepo(), repo, ResolveSmartStartDayUseCase())
        val job = launch { vm.hasActiveSession.collect {} }
        advanceUntilIdle()
        assertTrue(vm.hasActiveSession.value)

        repo.active.value = null // session finished/abandoned while backgrounded
        advanceUntilIdle()
        assertFalse(vm.hasActiveSession.value)
        job.cancel()
    }

    @Test
    fun `signed out (null session) reports false without crashing`() = runTest(dispatcher) {
        val repo = FakeSessionRepo() // stays null
        val vm = WorkoutPlanViewModel(StubPlanRepo(), repo, ResolveSmartStartDayUseCase())
        val job = launch { vm.hasActiveSession.collect {} }
        advanceUntilIdle()
        assertFalse(vm.hasActiveSession.value)
        assertEquals(false, vm.hasActiveSession.value)
        job.cancel()
    }

    private fun customDay(id: String, slotKey: String) =
        WorkoutDay(id = id, planId = "plan1", userId = "u1", slotKey = slotKey, name = id, updatedAt = 0L)

    private fun sessionForDay(id: String, dayId: String) = WorkoutSession(
        id = id, userId = "u1", planId = "plan1", dayId = dayId, title = null,
        activityType = "strength_training", startEpochMs = 1_000L, endEpochMs = 2_000L,
        durationSeconds = 60, status = SessionStatus.COMPLETED, localDate = "2026-10-08",
        note = null, totalVolumeKg = null, caloriesEstimate = null, updatedAt = 2_000L,
    )

    @Test
    fun `summary surfaces the next CUSTOM day as the smart-start suggestion`() = runTest(dispatcher) {
        val days = listOf(customDay("c1", "C01"), customDay("c2", "C02"), customDay("c3", "C03"))
        val sessionRepo = FakeSessionRepo().apply {
            recent.value = listOf(sessionForDay("s2", "c1")) // last trained c1 → next is c2
        }
        val vm = WorkoutPlanViewModel(CustomPlanRepo(days), sessionRepo, ResolveSmartStartDayUseCase())
        val job = launch { vm.summary.collect {} }
        advanceUntilIdle()

        val summary = vm.summary.value
        assertEquals("My Split", summary.activePlanName)
        assertEquals(3, summary.dayCount)
        assertEquals("c2", summary.suggestedDayId)
        assertEquals("Custom 2", summary.suggestedDayLabel)
        job.cancel()
    }

    @Test
    fun `summary with no training history suggests the first CUSTOM day`() = runTest(dispatcher) {
        val days = listOf(customDay("c1", "C01"), customDay("c2", "C02"))
        val sessionRepo = FakeSessionRepo() // no recent sessions
        val vm = WorkoutPlanViewModel(CustomPlanRepo(days), sessionRepo, ResolveSmartStartDayUseCase())
        val job = launch { vm.summary.collect {} }
        advanceUntilIdle()

        assertEquals("c1", vm.summary.value.suggestedDayId)
        assertEquals("Custom 1", vm.summary.value.suggestedDayLabel)
        job.cancel()
    }
}
