package com.hellohealth.ui.workoutplan

import androidx.lifecycle.SavedStateHandle
import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.PlannedExerciseWithDetails
import com.hellohealth.domain.model.WorkoutDay
import com.hellohealth.domain.model.WorkoutPlan
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.ui.navigation.Screen
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
 * Tests [DayDetailViewModel.reorder] — the drag-to-reorder passthrough added in Stage 9. Asserts the
 * exact (dayId, orderedIds) forwarded to the repository, and that an empty list is a no-op. Fakes only
 * (no mockk); dayId is supplied via SavedStateHandle like production.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DayDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class RecordingPlanRepo : WorkoutPlanRepository {
        var reordered: Pair<String, List<String>>? = null

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
        override suspend fun reorderExercises(dayId: String, orderedIds: List<String>) { reordered = dayId to orderedIds }
        override suspend fun deleteExercise(id: String) {}
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: WorkoutPlanRepository) =
        DayDetailViewModel(repo, SavedStateHandle(mapOf(Screen.DayDetail.dayIdArg to "day1")))

    @Test
    fun `reorder forwards the day id and exact new order to the repository`() = runTest(dispatcher) {
        val repo = RecordingPlanRepo()
        val vm = vm(repo)
        vm.reorder(listOf("e3", "e1", "e2"))
        advanceUntilIdle()
        assertEquals("day1" to listOf("e3", "e1", "e2"), repo.reordered)
    }

    @Test
    fun `reorder with an empty list is a no-op`() = runTest(dispatcher) {
        val repo = RecordingPlanRepo()
        val vm = vm(repo)
        vm.reorder(emptyList())
        advanceUntilIdle()
        assertNull(repo.reordered)
    }
}
