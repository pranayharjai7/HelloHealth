package com.hellohealth.ui.workoutplan

import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.PlannedExerciseWithDetails
import com.hellohealth.domain.model.WorkoutDay
import com.hellohealth.domain.model.WorkoutPlan
import com.hellohealth.domain.repository.WorkoutPlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests [RoutinesViewModel]'s write passthroughs — focus on the Stage-8 addition `renamePlan` (trim +
 * blank guard + repo forward), alongside create/setActive/delete. Fakes only (no mockk); the recording
 * fake captures the exact (id, name) forwarded. StandardTestDispatcher + advanceUntilIdle drive the
 * viewModelScope.launch writes.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Recording fake: observe-flows are controllable; write-methods capture their last call. */
    private class RecordingPlanRepo : WorkoutPlanRepository {
        val plans = MutableStateFlow<List<WorkoutPlan>>(emptyList())
        val activePlan = MutableStateFlow<WorkoutPlan?>(null)

        var renamed: Pair<String, String>? = null
        var created: Triple<String, PlanType, Boolean>? = null
        var setActiveId: String? = null
        var deletedId: String? = null

        override fun observePlans() = plans
        override fun observeActivePlan() = activePlan
        override suspend fun getPlan(id: String): WorkoutPlan? = null
        override suspend fun createPlan(name: String, planType: PlanType, makeActive: Boolean): String? {
            created = Triple(name, planType, makeActive); return "newId"
        }
        override suspend fun renamePlan(id: String, name: String) { renamed = id to name }
        override suspend fun setActivePlan(id: String) { setActiveId = id }
        override suspend fun deletePlan(id: String) { deletedId = id }
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

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `renamePlan trims and forwards to the repository`() = runTest(dispatcher) {
        val repo = RecordingPlanRepo()
        val vm = RoutinesViewModel(repo)
        vm.renamePlan("plan1", "  Push / Pull  ")
        advanceUntilIdle()
        assertEquals("plan1" to "Push / Pull", repo.renamed)
    }

    @Test
    fun `renamePlan ignores a blank name`() = runTest(dispatcher) {
        val repo = RecordingPlanRepo()
        val vm = RoutinesViewModel(repo)
        vm.renamePlan("plan1", "   ")
        advanceUntilIdle()
        assertNull(repo.renamed)
    }

    @Test
    fun `createPlan forwards trimmed name and makes it active`() = runTest(dispatcher) {
        val repo = RecordingPlanRepo()
        val vm = RoutinesViewModel(repo)
        vm.createPlan("  Split  ", PlanType.CUSTOM)
        advanceUntilIdle()
        assertEquals(Triple("Split", PlanType.CUSTOM, true), repo.created)
    }

    @Test
    fun `setActive and deletePlan forward the id`() = runTest(dispatcher) {
        val repo = RecordingPlanRepo()
        val vm = RoutinesViewModel(repo)
        vm.setActive("p2")
        vm.deletePlan("p3")
        advanceUntilIdle()
        assertEquals("p2", repo.setActiveId)
        assertEquals("p3", repo.deletedId)
    }
}
