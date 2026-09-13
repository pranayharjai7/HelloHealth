package com.hellohealth.ui.onboarding

import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.BodyMetrics
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.model.User
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Step 6 covers the Basics step + wizard scaffold: name prefill from auth, Basics validity gating,
 * mutators, and step navigation. Persistence is Step 9, so no repo write is asserted here.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeAuthRepository(private val currentName: String?) : AuthRepository {
        var updatedName: String? = null
        override suspend fun signUp(email: String, password: String) = Result.success(Unit)
        override suspend fun signIn(email: String, password: String) = Result.success(Unit)
        override suspend fun signInWithGoogle(idToken: String, email: String?, name: String?, avatarUrl: String?) =
            Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
        override suspend fun isUserLoggedIn() = true
        override suspend fun getCurrentUser(): User? = User(email = "a@b.com", name = currentName)
        override suspend fun updateCurrentUserName(name: String): Result<User?> {
            updatedName = name
            return Result.success<User?>(null)
        }
    }

    private class FakeProfileRepository(
        private val failOnUpsert: Boolean = false,
        private val silentNoOp: Boolean = false
    ) : ProfileRepository {
        var saved: UserProfile? = null
        var upsertCount = 0
        override suspend fun getProfile(): UserProfile? = saved
        override suspend fun upsertProfile(profile: UserProfile) {
            if (failOnUpsert) throw RuntimeException("disk full")
            upsertCount++
            // silentNoOp mirrors the real repo dropping the write when no user is signed in: it
            // returns normally WITHOUT persisting, so getProfile() still yields null afterward.
            if (silentNoOp) return
            saved = profile
        }
        override suspend fun setDynamicTheme(enabled: Boolean) {
            upsertProfile((saved ?: UserProfile()).copy(isDynamicTheme = enabled))
        }
        override fun observeDynamicTheme() = flowOf(saved?.isDynamicTheme ?: true)
    }

    private class FakeGoalsRepository : GoalsRepository {
        var seeded: ActivityGoals? = null
        override fun getActivityGoals(): Flow<ActivityGoals> = flowOf(ActivityGoals())
        override suspend fun getCurrentActivityGoals(): ActivityGoals = ActivityGoals()
        override suspend fun updateActivityGoals(goals: ActivityGoals) {
            seeded = goals
        }
    }

    /**
     * Health Connect prefill stub. [metrics] is what a "read" returns; default is empty (nothing to
     * prefill), matching an unconnected device. Only [fetchLatestBodyMetrics] is exercised here.
     */
    private class FakeActivityRepository(
        private val metrics: BodyMetrics = BodyMetrics()
    ) : com.hellohealth.domain.repository.ActivityRepository {
        override suspend fun fetchSummary(
            goals: ActivityGoals,
            date: java.time.LocalDate,
            forceRefresh: Boolean
        ) = com.hellohealth.domain.model.HealthSummary()
        override suspend fun getHistoryForMonth(month: java.time.YearMonth) =
            emptyList<com.hellohealth.domain.model.DailyHealthSnapshot>()
        override suspend fun getExerciseSessionDetail(
            sessionId: String,
            startTimeHint: java.time.Instant?,
            endTimeHint: java.time.Instant?
        ): com.hellohealth.domain.model.ActivityDetail? = null
        override suspend fun fetchWeeklyStats() = com.hellohealth.domain.model.WeeklyStats()
        override suspend fun hasPermissions() = false
        override suspend fun fetchLatestBodyMetrics(): BodyMetrics = metrics
        override fun getRequiredPermissions(): Set<String> = emptySet()
        override fun getAvailability(): Int = 0
        override fun getSettingsIntent(context: android.content.Context) = android.content.Intent()
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(name: String?) =
        OnboardingViewModel(
            FakeAuthRepository(name),
            FakeProfileRepository(),
            FakeGoalsRepository(),
            FakeActivityRepository()
        )

    private fun viewModelWithMetrics(metrics: BodyMetrics) =
        OnboardingViewModel(
            FakeAuthRepository(null),
            FakeProfileRepository(),
            FakeGoalsRepository(),
            FakeActivityRepository(metrics)
        )

    /** Bundle so Step 9 tests can inspect what finish/skip actually persisted. */
    private class Harness(
        val vm: OnboardingViewModel,
        val auth: FakeAuthRepository,
        val profile: FakeProfileRepository,
        val goals: FakeGoalsRepository
    )

    private fun harness(name: String? = null, failOnUpsert: Boolean = false, silentNoOp: Boolean = false): Harness {
        val auth = FakeAuthRepository(name)
        val profile = FakeProfileRepository(failOnUpsert = failOnUpsert, silentNoOp = silentNoOp)
        val goals = FakeGoalsRepository()
        val vm = OnboardingViewModel(auth, profile, goals, FakeActivityRepository())
        return Harness(vm, auth, profile, goals)
    }

    @Test
    fun `prefills display name from the signed-in Google account`() = runTest(dispatcher) {
        val vm = viewModel("Ann Google")
        advanceUntilIdle()
        assertEquals("Ann Google", vm.uiState.value.displayName)
    }

    @Test
    fun `leaves name blank when auth user has none`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.displayName)
    }

    @Test
    fun `user typing before prefill resolves is not clobbered by the auth name`() = runTest(dispatcher) {
        // init{} queues prefillFromAuth on the StandardTestDispatcher but it hasn't run yet.
        val vm = viewModel("Jane Doe")

        // User starts typing before the async prefill resolves.
        vm.updateDisplayName("J")

        // Now let the prefill coroutine run — it must NOT overwrite the user's input.
        advanceUntilIdle()
        assertEquals("J", vm.uiState.value.displayName)
    }

    @Test
    fun `basics is invalid until name and birth date are set`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isBasicsValid)

        vm.updateDisplayName("Ann")
        assertFalse("name alone is not enough", vm.uiState.value.isBasicsValid)

        vm.updateBirthDate(LocalDate.of(1990, 1, 1).toEpochDay())
        assertTrue(vm.uiState.value.isBasicsValid)
    }

    @Test
    fun `mutators update the corresponding fields`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()

        vm.updateGender(Gender.FEMALE)
        vm.updateUnitPreference(UnitPreference.IMPERIAL)

        assertEquals(Gender.FEMALE, vm.uiState.value.gender)
        assertEquals(UnitPreference.IMPERIAL, vm.uiState.value.unitPreference)
    }

    @Test
    fun `next and previous walk the step sequence and clamp at the ends`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        assertEquals(OnboardingStep.BASICS, vm.uiState.value.step)

        // Clamps at the first step.
        vm.previousStep()
        assertEquals(OnboardingStep.BASICS, vm.uiState.value.step)

        vm.nextStep()
        assertEquals(OnboardingStep.BODY, vm.uiState.value.step)
        vm.nextStep()
        vm.nextStep()
        assertEquals(OnboardingStep.CONFIRM, vm.uiState.value.step)

        // Clamps at the last step.
        vm.nextStep()
        assertEquals(OnboardingStep.CONFIRM, vm.uiState.value.step)

        vm.previousStep()
        assertEquals(OnboardingStep.ACTIVITY, vm.uiState.value.step)
    }

    // --- Step 7: Body step ---

    @Test
    fun `body is invalid until height and weight are within bounds`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isBodyValid)

        vm.updateHeightCm(180.0)
        assertFalse("height alone is not enough", vm.uiState.value.isBodyValid)

        vm.updateWeightKg(75.0)
        assertTrue(vm.uiState.value.isBodyValid)
    }

    @Test
    fun `body rejects out-of-bounds height and weight`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()

        vm.updateHeightCm(3.0)   // absurdly short
        vm.updateWeightKg(75.0)
        assertFalse(vm.uiState.value.isBodyValid)

        vm.updateHeightCm(180.0)
        vm.updateWeightKg(5.0)   // absurdly light
        assertFalse(vm.uiState.value.isBodyValid)

        vm.updateHeightCm(180.0)
        vm.updateWeightKg(75.0)
        assertTrue(vm.uiState.value.isBodyValid)
    }

    @Test
    fun `body prefills height and weight from Health Connect`() = runTest(dispatcher) {
        val vm = viewModelWithMetrics(BodyMetrics(heightCm = 175.0, weightKg = 68.0))
        advanceUntilIdle()

        vm.prefillBodyFromHealthConnect()
        advanceUntilIdle()

        assertEquals(175.0, vm.uiState.value.heightCm!!, 0.001)
        assertEquals(68.0, vm.uiState.value.weightKg!!, 0.001)
    }

    @Test
    fun `body prefill does not clobber values the user already entered`() = runTest(dispatcher) {
        val vm = viewModelWithMetrics(BodyMetrics(heightCm = 175.0, weightKg = 68.0))
        advanceUntilIdle()

        // User types their own height before prefill runs.
        vm.updateHeightCm(190.0)
        vm.prefillBodyFromHealthConnect()
        advanceUntilIdle()

        assertEquals("manual height preserved", 190.0, vm.uiState.value.heightCm!!, 0.001)
        // Weight was untouched, so prefill fills it.
        assertEquals(68.0, vm.uiState.value.weightKg!!, 0.001)
    }

    @Test
    fun `body prefill runs at most once`() = runTest(dispatcher) {
        val vm = viewModelWithMetrics(BodyMetrics(heightCm = 175.0, weightKg = 68.0))
        advanceUntilIdle()

        vm.prefillBodyFromHealthConnect()
        advanceUntilIdle()
        // User overrides after the first prefill.
        vm.updateWeightKg(80.0)

        // A second entry to the step must NOT re-run prefill and clobber the override.
        vm.prefillBodyFromHealthConnect()
        advanceUntilIdle()
        assertEquals(80.0, vm.uiState.value.weightKg!!, 0.001)
    }

    @Test
    fun `body prefill with no Health Connect data leaves fields empty`() = runTest(dispatcher) {
        val vm = viewModel(null) // FakeActivityRepository returns empty metrics
        advanceUntilIdle()

        vm.prefillBodyFromHealthConnect()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.heightCm)
        assertEquals(null, vm.uiState.value.weightKg)
        assertFalse(vm.uiState.value.isPrefillingBody)
    }

    // --- Step 8: Activity & goal step ---

    private fun adultBirthDate() = LocalDate.now().minusYears(30).toEpochDay()

    @Test
    fun `activity is invalid until level, goal, and a valid age are set`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isActivityValid)

        vm.updateBirthDate(adultBirthDate())
        vm.updateActivityLevel(ActivityLevel.MODERATE)
        assertFalse("goal still missing", vm.uiState.value.isActivityValid)

        vm.updateGoalType(GoalType.MAINTAIN)
        assertTrue(vm.uiState.value.isActivityValid)
    }

    @Test
    fun `age outside 13 to 120 blocks the activity gate`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        vm.updateActivityLevel(ActivityLevel.MODERATE)
        vm.updateGoalType(GoalType.MAINTAIN)

        // Too young.
        vm.updateBirthDate(LocalDate.now().minusYears(10).toEpochDay())
        assertFalse("age 10 must fail", vm.uiState.value.isActivityValid)

        // Implausibly old.
        vm.updateBirthDate(LocalDate.now().minusYears(130).toEpochDay())
        assertFalse("age 130 must fail", vm.uiState.value.isActivityValid)

        // In range.
        vm.updateBirthDate(adultBirthDate())
        assertTrue(vm.uiState.value.isActivityValid)
    }

    @Test
    fun `an out-of-range weekly rate blocks the gate`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        vm.updateBirthDate(adultBirthDate())
        vm.updateActivityLevel(ActivityLevel.MODERATE)
        vm.updateGoalType(GoalType.LOSE)
        assertTrue("no target is valid (optional)", vm.uiState.value.isActivityValid)

        vm.updateTargetRateKgPerWeek(3.0) // far above the 1.0 kg/week cap
        assertFalse(vm.uiState.value.isActivityValid)

        vm.updateTargetRateKgPerWeek(0.5) // sane
        assertTrue(vm.uiState.value.isActivityValid)
    }

    @Test
    fun `switching goal to maintain clears any target weight and rate`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()
        vm.updateBirthDate(adultBirthDate())
        vm.updateActivityLevel(ActivityLevel.ACTIVE)
        vm.updateGoalType(GoalType.LOSE)
        vm.updateTargetWeightKg(70.0)
        vm.updateTargetRateKgPerWeek(0.5)

        vm.updateGoalType(GoalType.MAINTAIN)

        assertEquals(null, vm.uiState.value.targetWeightKg)
        assertEquals(null, vm.uiState.value.targetRateKgPerWeek)
        assertTrue(vm.uiState.value.isActivityValid)
    }

    @Test
    fun `activity mutators update the corresponding fields`() = runTest(dispatcher) {
        val vm = viewModel(null)
        advanceUntilIdle()

        vm.updateActivityLevel(ActivityLevel.VERY_ACTIVE)
        vm.updateGoalType(GoalType.GAIN)
        vm.updateTargetWeightKg(85.0)
        vm.updateTargetRateKgPerWeek(0.25)

        assertEquals(ActivityLevel.VERY_ACTIVE, vm.uiState.value.activityLevel)
        assertEquals(GoalType.GAIN, vm.uiState.value.goalType)
        assertEquals(85.0, vm.uiState.value.targetWeightKg!!, 0.001)
        assertEquals(0.25, vm.uiState.value.targetRateKgPerWeek!!, 0.001)
    }

    // --- Step 9: Confirm + persist + seed goals + skip ---

    /** Drives a fully-completed wizard so finish() has a real profile to persist. */
    private fun completeBasicsBodyActivity(vm: OnboardingViewModel) {
        vm.updateDisplayName("Ann")
        vm.updateGender(Gender.FEMALE)
        vm.updateBirthDate(adultBirthDate())
        vm.updateHeightCm(170.0)
        vm.updateWeightKg(65.0)
        vm.updateActivityLevel(ActivityLevel.MODERATE)
        vm.updateGoalType(GoalType.MAINTAIN)
    }

    @Test
    fun `finish persists the full profile with hasOnboarded true and seeds suggested goals`() =
        runTest(dispatcher) {
            val h = harness()
            advanceUntilIdle()
            completeBasicsBodyActivity(h.vm)

            var navigated = false
            h.vm.finish { navigated = true }
            advanceUntilIdle()

            val saved = h.profile.saved!!
            assertTrue("hasOnboarded must be set", saved.hasOnboarded)
            assertEquals("Ann", saved.displayName)
            assertEquals(Gender.FEMALE, saved.gender)
            assertEquals(170.0, saved.heightCm!!, 0.001)
            assertEquals(65.0, saved.weightKg!!, 0.001)
            // Goals seeded from the same profile via BodyEnergy.
            assertEquals(BodyEnergy.suggestedGoals(saved), h.goals.seeded)
            assertTrue("onDone runs after a successful save", navigated)
            assertFalse(h.vm.uiState.value.isSaving)
        }

    @Test
    fun `finish pushes the display name to the auth account`() = runTest(dispatcher) {
        val h = harness()
        advanceUntilIdle()
        completeBasicsBodyActivity(h.vm)

        h.vm.finish {}
        advanceUntilIdle()

        assertEquals("Ann", h.auth.updatedName)
    }

    @Test
    fun `finish does not navigate and surfaces an error when the save fails`() = runTest(dispatcher) {
        val h = harness(failOnUpsert = true)
        advanceUntilIdle()
        completeBasicsBodyActivity(h.vm)

        var navigated = false
        h.vm.finish { navigated = true }
        advanceUntilIdle()

        assertFalse("must not navigate away with nothing saved", navigated)
        assertFalse(h.vm.uiState.value.isSaving)
        assertTrue("an error is surfaced", h.vm.uiState.value.error != null)
    }

    @Test
    fun `finish does not navigate when the write silently no-ops with no session`() = runTest(dispatcher) {
        // The real repos log-and-return (no throw) when no user is signed in, so "no exception" does
        // not prove a row persisted. The read-back guard must catch this and refuse to navigate.
        val h = harness(silentNoOp = true)
        advanceUntilIdle()
        completeBasicsBodyActivity(h.vm)

        var navigated = false
        h.vm.finish { navigated = true }
        advanceUntilIdle()

        assertFalse("silent no-op must not be treated as success", navigated)
        assertEquals("nothing persisted", null, h.profile.saved)
        assertTrue("an error is surfaced", h.vm.uiState.value.error != null)
        assertFalse(h.vm.uiState.value.isSaving)
    }

    @Test
    fun `skip persists an explicit row with hasOnboarded true and seeds default goals with no vitals`() =
        runTest(dispatcher) {
            val h = harness()
            advanceUntilIdle()

            var navigated = false
            h.vm.skip { navigated = true }
            advanceUntilIdle()

            val saved = h.profile.saved!!
            assertTrue("skip still writes an explicit onboarded row", saved.hasOnboarded)
            assertEquals("no vitals were entered", null, saved.heightCm)
            assertEquals(null, saved.weightKg)
            // With no activity level or goal, suggestedGoals falls back to sedentary defaults.
            assertEquals(BodyEnergy.suggestedGoals(saved), h.goals.seeded)
            assertTrue(navigated)
        }

    @Test
    fun `finish is a no-op while a save is already in flight`() = runTest(dispatcher) {
        val h = harness()
        advanceUntilIdle()
        completeBasicsBodyActivity(h.vm)

        // First finish sets isSaving=true; a second call before the coroutine drains must be ignored.
        var navCount = 0
        h.vm.finish { navCount++ }
        h.vm.finish { navCount++ }
        advanceUntilIdle()

        assertEquals("only one persist runs", 1, h.profile.upsertCount)
        assertEquals("only one navigation", 1, navCount)
    }

    @Test
    fun `calorie budget preview is null without vitals and a sane number with a full profile`() =
        runTest(dispatcher) {
            val h = harness()
            advanceUntilIdle()
            assertEquals("no vitals yet", null, h.vm.calorieBudgetPreview)

            completeBasicsBodyActivity(h.vm)
            val budget = h.vm.calorieBudgetPreview!!
            // MAINTAIN → TDEE unchanged; well above the 1200 floor for a moderately-active adult.
            assertTrue("budget should be a plausible adult maintenance figure, was $budget", budget in 1500..3500)
        }
}
