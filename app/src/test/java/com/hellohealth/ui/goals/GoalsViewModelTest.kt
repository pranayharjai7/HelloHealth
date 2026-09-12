package com.hellohealth.ui.goals

import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
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
 * Tests for the Goals editor's new goal-direction handling (P0.5 Step 10c): goal-type/target/rate
 * seed from the profile and persist back via load-then-`copy()`, MAINTAIN clears targets, and the
 * locked re-derive rule — active-calories is recomputed from the goal type ONLY when the type
 * changed this session; a pure slider tweak persists as-is.
 *
 * Runs under Robolectric so [com.hellohealth.core.logging.AppLogger]'s `android.util.Log` calls in
 * the write-failure path resolve rather than throwing "not mocked".
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeGoalsRepository(
        initial: ActivityGoals = ActivityGoals()
    ) : GoalsRepository {
        val flow = MutableStateFlow(initial)
        var goals: ActivityGoals
            get() = flow.value
            set(value) { flow.value = value }
        override fun getActivityGoals(): Flow<ActivityGoals> = flow
        override suspend fun getCurrentActivityGoals(): ActivityGoals = flow.value
        override suspend fun updateActivityGoals(goals: ActivityGoals) { flow.value = goals }
        /** Simulate a background sync write re-emitting on the observed Flow. */
        fun emitFromBackground(goals: ActivityGoals) { flow.value = goals }
    }

    private class FakeProfileRepository(
        var profile: UserProfile?,
        var throwOnRead: Boolean = false,
        var throwOnWrite: Boolean = false
    ) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? {
            if (throwOnRead) error("read failed")
            return profile
        }
        override suspend fun upsertProfile(profile: UserProfile) {
            if (throwOnWrite) error("write failed")
            this.profile = profile
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        goals: FakeGoalsRepository,
        profile: FakeProfileRepository
    ) = GoalsViewModel(goals, profile)

    @Test
    fun `seeds goal-direction fields from the stored profile`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            UserProfile(
                goalType = GoalType.LOSE,
                targetWeightKg = 60.0,
                targetRateKgPerWeek = 0.5,
                unitPreference = UnitPreference.IMPERIAL,
                hasOnboarded = true
            )
        )
        val vm = viewModel(FakeGoalsRepository(), profileRepo)
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(GoalType.LOSE, s.goalType)
        assertEquals(60.0, s.targetWeightKg!!, 0.0001)
        assertEquals(0.5, s.targetRateKgPerWeek!!, 0.0001)
        assertEquals(UnitPreference.IMPERIAL, s.unitPreference)
    }

    @Test
    fun `MAINTAIN clears target weight and rate`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            UserProfile(goalType = GoalType.LOSE, targetWeightKg = 60.0, targetRateKgPerWeek = 0.5, hasOnboarded = true)
        )
        val vm = viewModel(FakeGoalsRepository(), profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.MAINTAIN)

        val s = vm.uiState.value
        assertEquals(GoalType.MAINTAIN, s.goalType)
        assertNull("target weight must clear on MAINTAIN", s.targetWeightKg)
        assertNull("rate must clear on MAINTAIN", s.targetRateKgPerWeek)
    }

    @Test
    fun `save persists goal-direction fields onto the profile preserving hasOnboarded`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            UserProfile(displayName = "Ann", heightCm = 170.0, goalType = GoalType.MAINTAIN, hasOnboarded = true)
        )
        val vm = viewModel(FakeGoalsRepository(), profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.LOSE)
        vm.updateTargetWeightKg(62.0)
        vm.updateTargetRateKgPerWeek(0.4)
        vm.saveGoals()
        advanceUntilIdle()

        val saved = profileRepo.profile!!
        assertEquals(GoalType.LOSE, saved.goalType)
        assertEquals(62.0, saved.targetWeightKg!!, 0.0001)
        assertEquals(0.4, saved.targetRateKgPerWeek!!, 0.0001)
        assertEquals("untouched vitals survive", 170.0, saved.heightCm!!, 0.0001)
        assertEquals("hasOnboarded survives", true, saved.hasOnboarded)
    }

    @Test
    fun `save re-derives active-calories when goal type changed`() = runTest(dispatcher) {
        // Starts MAINTAIN (500-cal ring). Switching to LOSE must re-derive to 600.
        val profileRepo = FakeProfileRepository(UserProfile(goalType = GoalType.MAINTAIN, hasOnboarded = true))
        val goalsRepo = FakeGoalsRepository(ActivityGoals(steps = 9000, activeCalories = 500, activeMinutes = 55))
        val vm = viewModel(goalsRepo, profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.LOSE)
        vm.saveGoals()
        advanceUntilIdle()

        assertEquals("active-calories re-derived from LOSE", 600, goalsRepo.goals.activeCalories)
        assertEquals("steps preserved", 9000, goalsRepo.goals.steps)
        assertEquals("minutes preserved", 55, goalsRepo.goals.activeMinutes)
    }

    @Test
    fun `save keeps the manual slider value when goal type is unchanged`() = runTest(dispatcher) {
        // Goal type stays LOSE; a manual active-cal slider tweak must persist as-is (NOT re-derived
        // back to LOSE's 600) — this is the locked decision.
        val profileRepo = FakeProfileRepository(UserProfile(goalType = GoalType.LOSE, hasOnboarded = true))
        val goalsRepo = FakeGoalsRepository(ActivityGoals(steps = 10000, activeCalories = 600, activeMinutes = 60))
        val vm = viewModel(goalsRepo, profileRepo)
        advanceUntilIdle()

        vm.updateCaloriesGoal(750) // user drags the slider
        vm.saveGoals()
        advanceUntilIdle()

        assertEquals("manual slider value must persist untouched", 750, goalsRepo.goals.activeCalories)
    }

    @Test
    fun `a second save after a type change does not re-derive when type is stable`() = runTest(dispatcher) {
        // First save changes MAINTAIN→LOSE (re-derives to 600 and advances the persisted snapshot).
        // A follow-up manual slider tweak + save must then stick, proving the snapshot advanced.
        val profileRepo = FakeProfileRepository(UserProfile(goalType = GoalType.MAINTAIN, hasOnboarded = true))
        val goalsRepo = FakeGoalsRepository(ActivityGoals(activeCalories = 500))
        val vm = viewModel(goalsRepo, profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.LOSE)
        vm.saveGoals()
        advanceUntilIdle()
        assertEquals(600, goalsRepo.goals.activeCalories)

        vm.updateCaloriesGoal(720)
        vm.saveGoals()
        advanceUntilIdle()
        assertEquals("second save keeps the slider value (type stable since last save)", 720, goalsRepo.goals.activeCalories)
    }

    @Test
    fun `save surfaces an error and does not clear on write failure`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            UserProfile(goalType = GoalType.LOSE, hasOnboarded = true),
            throwOnWrite = true
        )
        val vm = viewModel(FakeGoalsRepository(), profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.GAIN)
        vm.saveGoals()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(false, s.isSaving)
        assertNull(s.successMessage)
        assertEquals("an error must surface", true, s.error != null)
    }

    @Test
    fun `out-of-range target blocks save`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(UserProfile(goalType = GoalType.LOSE, hasOnboarded = true))
        val vm = viewModel(FakeGoalsRepository(), profileRepo)
        advanceUntilIdle()

        vm.updateTargetWeightKg(5.0) // below MIN_WEIGHT_KG
        assertEquals("canSave must be false with an out-of-range target", false, vm.uiState.value.canSave)

        vm.saveGoals()
        advanceUntilIdle()
        assertNull("no save should occur", vm.uiState.value.successMessage)
    }

    // --- Regression: seed-vs-save race / load-failure clobber (10c adversarial review) ---

    @Test
    fun `save is blocked and no-op before the profile snapshot has loaded`() = runTest(dispatcher) {
        // Stored directional goal + targets. If a Save lands before the seed coroutine resolves, the
        // null-default goal fields must NOT be projected over the stored row.
        val stored = UserProfile(goalType = GoalType.LOSE, targetWeightKg = 70.0, targetRateKgPerWeek = 0.5, hasOnboarded = true)
        val profileRepo = FakeProfileRepository(stored)
        val vm = viewModel(FakeGoalsRepository(), profileRepo)
        // NOTE: no advanceUntilIdle — the seed coroutine is still pending, profileLoaded is false.

        assertEquals("Save must be disabled until the profile loads", false, vm.uiState.value.canSave)
        vm.saveGoals() // must be a guarded no-op
        advanceUntilIdle()

        // The stored goal survives untouched (the null-default state was never written).
        val saved = profileRepo.profile!!
        assertEquals(GoalType.LOSE, saved.goalType)
        assertEquals(70.0, saved.targetWeightKg!!, 0.0001)
        assertEquals(0.5, saved.targetRateKgPerWeek!!, 0.0001)
    }

    @Test
    fun `a failed profile load leaves save disabled and surfaces an error`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            UserProfile(goalType = GoalType.LOSE, targetWeightKg = 70.0, hasOnboarded = true),
            throwOnRead = true
        )
        val vm = viewModel(FakeGoalsRepository(), profileRepo)
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("a failed profile load must not enable Save", false, s.canSave)
        assertEquals("an error must surface", true, s.error != null)

        // Even a forced save must be a no-op — the stored goal survives.
        vm.saveGoals()
        advanceUntilIdle()
        assertNull(vm.uiState.value.successMessage)
        assertEquals(GoalType.LOSE, profileRepo.profile!!.goalType)
    }

    @Test
    fun `a background goals emission does not clear a save-owned error`() = runTest(dispatcher) {
        // A write failure sets an error; a subsequent goals Flow refresh must NOT wipe that banner.
        val profileRepo = FakeProfileRepository(
            UserProfile(goalType = GoalType.LOSE, hasOnboarded = true),
            throwOnWrite = true
        )
        val goalsRepo = FakeGoalsRepository(ActivityGoals(activeCalories = 600))
        val vm = viewModel(goalsRepo, profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.GAIN)
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
