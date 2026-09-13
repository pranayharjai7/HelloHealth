package com.hellohealth.ui.goals

import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.repository.GoalsRepository
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the Daily Goals editor after the goal-direction fields moved to the Preferences screen.
 * Goals now owns ONLY the three activity-ring targets: it persists them exactly as edited (no profile
 * write, no goal-type-driven calorie re-derivation — that logic lives in PreferencesViewModel now).
 *
 * Runs under Robolectric so [com.hellohealth.core.logging.AppLogger]'s `android.util.Log` calls in
 * the write-failure path resolve rather than throwing "not mocked".
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeGoalsRepository(
        initial: ActivityGoals = ActivityGoals(),
        var throwOnWrite: Boolean = false
    ) : GoalsRepository {
        val flow = MutableStateFlow(initial)
        var goals: ActivityGoals
            get() = flow.value
            set(value) { flow.value = value }
        override fun getActivityGoals(): Flow<ActivityGoals> = flow
        override suspend fun getCurrentActivityGoals(): ActivityGoals = flow.value
        override suspend fun updateActivityGoals(goals: ActivityGoals) {
            if (throwOnWrite) error("write failed")
            flow.value = goals
        }
        /** Simulate a background sync write re-emitting on the observed Flow. */
        fun emitFromBackground(goals: ActivityGoals) { flow.value = goals }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(goals: FakeGoalsRepository) = GoalsViewModel(goals)

    @Test
    fun `seeds the ring targets from the store`() = runTest(dispatcher) {
        val goalsRepo = FakeGoalsRepository(ActivityGoals(steps = 9000, activeCalories = 500, activeMinutes = 55))
        val vm = viewModel(goalsRepo)
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(9000, s.goals.steps)
        assertEquals(500, s.goals.activeCalories)
        assertEquals(55, s.goals.activeMinutes)
    }

    @Test
    fun `save persists the three ring targets exactly as edited`() = runTest(dispatcher) {
        val goalsRepo = FakeGoalsRepository(ActivityGoals(steps = 10000, activeCalories = 600, activeMinutes = 60))
        val vm = viewModel(goalsRepo)
        advanceUntilIdle()

        vm.updateStepsGoal(12000)
        vm.updateCaloriesGoal(750)
        vm.updateMinutesGoal(45)
        vm.saveGoals()
        advanceUntilIdle()

        assertEquals("steps persisted", 12000, goalsRepo.goals.steps)
        assertEquals("calories persisted as edited (no re-derivation)", 750, goalsRepo.goals.activeCalories)
        assertEquals("minutes persisted", 45, goalsRepo.goals.activeMinutes)
        assertEquals("success message set", "Daily goals saved.", vm.uiState.value.successMessage)
    }

    @Test
    fun `save surfaces an error and does not clear on write failure`() = runTest(dispatcher) {
        val goalsRepo = FakeGoalsRepository(ActivityGoals(activeCalories = 500), throwOnWrite = true)
        val vm = viewModel(goalsRepo)
        advanceUntilIdle()

        vm.updateCaloriesGoal(650)
        vm.saveGoals()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(false, s.isSaving)
        assertNull(s.successMessage)
        assertEquals("an error must surface", true, s.error != null)
    }

    @Test
    fun `a background goals emission does not clear a save-owned error`() = runTest(dispatcher) {
        // A write failure sets an error; a subsequent goals Flow refresh must NOT wipe that banner.
        val goalsRepo = FakeGoalsRepository(ActivityGoals(activeCalories = 600), throwOnWrite = true)
        val vm = viewModel(goalsRepo)
        advanceUntilIdle()

        vm.updateCaloriesGoal(700)
        vm.saveGoals()
        advanceUntilIdle()
        assertEquals("error set by the failed save", true, vm.uiState.value.error != null)

        // A background sync write re-emits on the observed goals Flow — the refresh must NOT wipe the
        // save-owned error banner (the bug: loadGoals() used to set error=null on every emission).
        goalsRepo.emitFromBackground(ActivityGoals(steps = 11000, activeCalories = 600, activeMinutes = 60))
        advanceUntilIdle()
        assertEquals("the save error must survive a background goals emission", true, vm.uiState.value.error != null)
        assertEquals("the fresh goals value is still applied", 11000, vm.uiState.value.goals.steps)
    }
}
