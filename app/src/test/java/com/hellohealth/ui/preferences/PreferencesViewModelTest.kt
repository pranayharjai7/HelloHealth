package com.hellohealth.ui.preferences

import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.UserRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for the Preferences screen's ViewModel, which now owns the weight-goal (goal-direction)
 * fields moved off the Daily Goals screen — seeded from the profile, persisted back via
 * load-then-`copy()`, MAINTAIN clears targets, and the locked re-derive rule (active-calories is
 * recomputed from the goal type ONLY when the type changed this session) — PLUS the existing food
 * preferences. Both cards save together on one action.
 *
 * Runs under Robolectric so [com.hellohealth.core.logging.AppLogger]'s `android.util.Log` calls in
 * the write-failure path resolve rather than throwing "not mocked".
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeUserRepository(
        initial: FoodPreferences = FoodPreferences(),
        var throwOnWrite: Boolean = false
    ) : UserRepository {
        val flow = MutableStateFlow(initial)
        var saved: FoodPreferences = initial
        override fun getFoodPreferences(): Flow<FoodPreferences> = flow
        override suspend fun getCurrentFoodPreferences(): FoodPreferences = flow.value
        override suspend fun updateFoodPreferences(preferences: FoodPreferences) {
            if (throwOnWrite) error("food write failed")
            saved = preferences
            flow.value = preferences
        }
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
        override suspend fun setDynamicTheme(enabled: Boolean) {
            profile = (profile ?: UserProfile()).copy(isDynamicTheme = enabled)
        }
    }

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
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        user: FakeUserRepository = FakeUserRepository(),
        profile: FakeProfileRepository,
        goals: FakeGoalsRepository = FakeGoalsRepository()
    ) = PreferencesViewModel(user, profile, goals)

    // --- Weight preferences (goal direction) ---

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
        val vm = viewModel(profile = profileRepo)
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
        val vm = viewModel(profile = profileRepo)
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
        val vm = viewModel(profile = profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.LOSE)
        vm.updateTargetWeightKg(62.0)
        vm.updateTargetRateKgPerWeek(0.4)
        vm.savePreferences()
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
        val vm = viewModel(profile = profileRepo, goals = goalsRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.LOSE)
        vm.savePreferences()
        advanceUntilIdle()

        assertEquals("active-calories re-derived from LOSE", 600, goalsRepo.goals.activeCalories)
        assertEquals("steps preserved", 9000, goalsRepo.goals.steps)
        assertEquals("minutes preserved", 55, goalsRepo.goals.activeMinutes)
    }

    @Test
    fun `save does NOT touch the calorie ring when goal type is unchanged`() = runTest(dispatcher) {
        // Goal type stays LOSE; the ring must be left exactly as-is (Preferences never drives the
        // slider — it only re-derives on a type change).
        val profileRepo = FakeProfileRepository(UserProfile(goalType = GoalType.LOSE, hasOnboarded = true))
        val goalsRepo = FakeGoalsRepository(ActivityGoals(steps = 10000, activeCalories = 750, activeMinutes = 60))
        val vm = viewModel(profile = profileRepo, goals = goalsRepo)
        advanceUntilIdle()

        vm.updateTargetWeightKg(65.0) // a weight edit, but goal type unchanged
        vm.savePreferences()
        advanceUntilIdle()

        assertEquals("ring untouched when goal type stable", 750, goalsRepo.goals.activeCalories)
    }

    @Test
    fun `a second save with a stable type does not re-derive - persistedGoalType advanced`() = runTest(dispatcher) {
        // First save changes MAINTAIN→LOSE (re-derives to 600 and advances the persisted snapshot).
        // A follow-up manual ring change + second save must then stick, proving the snapshot advanced
        // (guards the persistedGoalType write — a mutation dropping it would re-fire the derivation).
        val profileRepo = FakeProfileRepository(UserProfile(goalType = GoalType.MAINTAIN, hasOnboarded = true))
        val goalsRepo = FakeGoalsRepository(ActivityGoals(activeCalories = 500))
        val vm = viewModel(profile = profileRepo, goals = goalsRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.LOSE)
        vm.savePreferences()
        advanceUntilIdle()
        assertEquals("first save re-derives from LOSE", 600, goalsRepo.goals.activeCalories)

        // Simulate the Daily Goals screen having moved the ring to 720, then save Preferences again
        // WITHOUT changing the goal type. The derivation must NOT re-fire and clobber it back to 600.
        goalsRepo.goals = goalsRepo.goals.copy(activeCalories = 720)
        vm.savePreferences()
        advanceUntilIdle()
        assertEquals("second save keeps the ring value (type stable since last save)", 720, goalsRepo.goals.activeCalories)
    }

    @Test
    fun `save persists cleared targets as null onto the profile when switching to MAINTAIN`() = runTest(dispatcher) {
        // Seed LOSE + concrete targets, switch to MAINTAIN (clears them in UI state), then save.
        // The null targets must actually reach the persisted profile — not just the UI state.
        val profileRepo = FakeProfileRepository(
            UserProfile(goalType = GoalType.LOSE, targetWeightKg = 60.0, targetRateKgPerWeek = 0.5, hasOnboarded = true)
        )
        val vm = viewModel(profile = profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.MAINTAIN)
        vm.savePreferences()
        advanceUntilIdle()

        val saved = profileRepo.profile!!
        assertEquals(GoalType.MAINTAIN, saved.goalType)
        assertNull("target weight must persist as null on MAINTAIN", saved.targetWeightKg)
        assertNull("rate must persist as null on MAINTAIN", saved.targetRateKgPerWeek)
    }

    @Test
    fun `out-of-range target blocks save`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(UserProfile(goalType = GoalType.LOSE, hasOnboarded = true))
        val vm = viewModel(profile = profileRepo)
        advanceUntilIdle()

        vm.updateTargetWeightKg(5.0) // below MIN_WEIGHT_KG
        assertEquals("canSave must be false with an out-of-range target", false, vm.uiState.value.canSave)

        vm.savePreferences()
        advanceUntilIdle()
        assertNull("no save should occur", vm.uiState.value.successMessage)
    }

    @Test
    fun `save is blocked and no-op before the profile snapshot has loaded`() = runTest(dispatcher) {
        val stored = UserProfile(goalType = GoalType.LOSE, targetWeightKg = 70.0, targetRateKgPerWeek = 0.5, hasOnboarded = true)
        val profileRepo = FakeProfileRepository(stored)
        val vm = viewModel(profile = profileRepo)
        // NOTE: no advanceUntilIdle — the seed coroutine is still pending, profileLoaded is false.

        assertEquals("Save must be disabled until the profile loads", false, vm.uiState.value.canSave)
        vm.savePreferences() // must be a guarded no-op
        advanceUntilIdle()

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
        val vm = viewModel(profile = profileRepo)
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals("a failed profile load must not enable Save", false, s.canSave)
        assertTrue("an error must surface", s.error != null)

        vm.savePreferences()
        advanceUntilIdle()
        assertNull(vm.uiState.value.successMessage)
        assertEquals(GoalType.LOSE, profileRepo.profile!!.goalType)
    }

    @Test
    fun `save persists goal-direction fields onto a null profile without wiping food prefs`() = runTest(dispatcher) {
        // A partial (null goalType) profile that still loaded successfully. profileLoaded=true, so Save
        // is enabled; the food prefs and the (null) goal must both persist cleanly.
        val profileRepo = FakeProfileRepository(UserProfile(hasOnboarded = true))
        val userRepo = FakeUserRepository()
        val vm = viewModel(user = userRepo, profile = profileRepo)
        advanceUntilIdle()

        vm.updateDietType("vegan")
        vm.updateGoalType(GoalType.GAIN)
        vm.updateTargetWeightKg(80.0)
        vm.savePreferences()
        advanceUntilIdle()

        assertEquals("food prefs persisted", "vegan", userRepo.saved.dietType)
        assertEquals("goal persisted", GoalType.GAIN, profileRepo.profile!!.goalType)
        assertEquals("hasOnboarded preserved", true, profileRepo.profile!!.hasOnboarded)
        assertEquals("success message set", "Preferences saved.", vm.uiState.value.successMessage)
    }

    // --- Food preferences ---

    @Test
    fun `toggles allergies and cuisines`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(UserProfile(hasOnboarded = true))
        val vm = viewModel(profile = profileRepo)
        advanceUntilIdle()

        vm.toggleAllergy("nuts")
        vm.toggleCuisine("Indian")
        vm.toggleAllergy("nuts") // toggle off again

        val prefs = vm.uiState.value.preferences
        assertEquals("nuts removed after double toggle", false, prefs.allergies.contains("nuts"))
        assertEquals("cuisine added", true, prefs.cuisinePreferences.contains("Indian"))
    }

    @Test
    fun `save surfaces an error and does not clear on profile write failure`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            UserProfile(goalType = GoalType.LOSE, hasOnboarded = true),
            throwOnWrite = true
        )
        val vm = viewModel(profile = profileRepo)
        advanceUntilIdle()

        vm.updateGoalType(GoalType.GAIN)
        vm.savePreferences()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(false, s.isSaving)
        assertNull(s.successMessage)
        assertTrue("an error must surface", s.error != null)
    }
}
