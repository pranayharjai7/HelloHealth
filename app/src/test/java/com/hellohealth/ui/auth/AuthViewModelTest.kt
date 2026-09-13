package com.hellohealth.ui.auth

import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.Gender
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Focused tests for the onboarding gate decision. After auth succeeds the ViewModel must route a
 * new/incomplete account to [AuthState.NeedsOnboarding] and a completed account to
 * [AuthState.Authenticated] — the branch Step 5 later wires into navigation.
 *
 * Step 10b adds coverage for the profile vitals editor's save paths: load-then-`copy()` (no
 * full-row wipe), `hasOnboarded`/untouched-field preservation, and active-calories-only re-derive.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeAuthRepository(
        var loggedIn: Boolean = true,
        val user: User? = User(email = "a@b.com", name = "Ann")
    ) : AuthRepository {
        var lastNameUpdate: String? = null
        override suspend fun signUp(email: String, password: String) = Result.success(Unit)
        override suspend fun signIn(email: String, password: String) = Result.success(Unit)
        override suspend fun signInWithGoogle(idToken: String, email: String?, name: String?, avatarUrl: String?) =
            Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
        override suspend fun isUserLoggedIn() = loggedIn
        override suspend fun getCurrentUser(): User? = user
        override suspend fun updateCurrentUserName(name: String): Result<User?> {
            lastNameUpdate = name
            return Result.success(null)
        }
    }

    private class FakeProfileRepository(
        var profile: UserProfile?,
        var throwOnRead: Boolean = false,
        var throwOnWrite: Boolean = false
    ) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? {
            if (throwOnRead) error("boom")
            return profile
        }
        override suspend fun upsertProfile(profile: UserProfile) {
            if (throwOnWrite) error("write failed")
            this.profile = profile
        }
    }

    private class FakeGoalsRepository(
        var goals: ActivityGoals = ActivityGoals()
    ) : GoalsRepository {
        override fun getActivityGoals(): Flow<ActivityGoals> = flowOf(goals)
        override suspend fun getCurrentActivityGoals(): ActivityGoals = goals
        override suspend fun updateActivityGoals(goals: ActivityGoals) { this.goals = goals }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        auth: FakeAuthRepository,
        profile: FakeProfileRepository,
        goals: FakeGoalsRepository = FakeGoalsRepository()
    ) = AuthViewModel(auth, profile, goals)

    @Test
    fun `null profile gates a brand-new account to onboarding`() = runTest(dispatcher) {
        val vm = viewModel(FakeAuthRepository(), FakeProfileRepository(profile = null))
        advanceUntilIdle()
        assertEquals(AuthState.NeedsOnboarding, vm.authState.value)
    }

    @Test
    fun `profile with hasOnboarded false still gates to onboarding`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeAuthRepository(),
            FakeProfileRepository(profile = UserProfile(displayName = "Ann", hasOnboarded = false))
        )
        advanceUntilIdle()
        assertEquals(AuthState.NeedsOnboarding, vm.authState.value)
    }

    @Test
    fun `returning onboarded user goes straight to authenticated`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeAuthRepository(),
            FakeProfileRepository(profile = UserProfile(displayName = "Ann", hasOnboarded = true))
        )
        advanceUntilIdle()
        assertEquals(AuthState.Authenticated, vm.authState.value)
    }

    @Test
    fun `logged-out user resolves to unauthenticated, not onboarding`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeAuthRepository(loggedIn = false),
            FakeProfileRepository(profile = UserProfile(hasOnboarded = true))
        )
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, vm.authState.value)
    }

    @Test
    fun `profile read failure degrades to onboarding rather than crashing`() = runTest(dispatcher) {
        val vm = viewModel(FakeAuthRepository(), FakeProfileRepository(profile = null, throwOnRead = true))
        advanceUntilIdle()
        assertEquals(AuthState.NeedsOnboarding, vm.authState.value)
    }

    @Test
    fun `sign-in of a new account gates to onboarding`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(profile = null)
        val vm = viewModel(FakeAuthRepository(loggedIn = false), profileRepo)
        advanceUntilIdle()
        assertEquals(AuthState.Unauthenticated, vm.authState.value)

        vm.signIn("a@b.com", "pw")
        advanceUntilIdle()
        assertEquals(AuthState.NeedsOnboarding, vm.authState.value)
    }

    // --- Step 10b: profile vitals editor save paths ---

    /** The name-only dialog save must load-then-copy so vitals + hasOnboarded survive. */
    @Test
    fun `saveProfile preserves existing vitals and hasOnboarded`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            profile = UserProfile(
                displayName = "Ann",
                heightCm = 170.0,
                weightKg = 65.0,
                hasOnboarded = true
            )
        )
        val vm = viewModel(FakeAuthRepository(), profileRepo)
        advanceUntilIdle()

        vm.saveProfile("Annie")
        advanceUntilIdle()

        val saved = profileRepo.profile!!
        assertEquals("Annie", saved.displayName)
        assertEquals(170.0, saved.heightCm!!, 0.0001)
        assertEquals(65.0, saved.weightKg!!, 0.0001)
        assertTrue("hasOnboarded must survive a name-only save", saved.hasOnboarded)
    }

    @Test
    fun `saveVitals loads existing row and preserves hasOnboarded and untouched fields`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            profile = UserProfile(
                displayName = "Ann",
                goalType = com.hellohealth.domain.model.GoalType.LOSE,
                targetWeightKg = 60.0,
                targetRateKgPerWeek = 0.5,
                heightCm = 160.0,
                weightKg = 70.0,
                hasOnboarded = true
            )
        )
        val vm = viewModel(FakeAuthRepository(), profileRepo)
        advanceUntilIdle()

        vm.saveVitals(
            displayName = "Ann",
            gender = Gender.FEMALE,
            birthDateEpochDay = 0L,
            heightCm = 165.0,
            weightKg = 68.0,
            unitPreference = UnitPreference.IMPERIAL
        )
        advanceUntilIdle()

        val saved = profileRepo.profile!!
        assertEquals(165.0, saved.heightCm!!, 0.0001)
        assertEquals(68.0, saved.weightKg!!, 0.0001)
        assertEquals(Gender.FEMALE, saved.gender)
        assertEquals(UnitPreference.IMPERIAL, saved.unitPreference)
        assertTrue("hasOnboarded must survive a vitals save", saved.hasOnboarded)
        // Goal fields aren't edited here — they must survive untouched.
        assertEquals(com.hellohealth.domain.model.GoalType.LOSE, saved.goalType)
        assertEquals(60.0, saved.targetWeightKg!!, 0.0001)
        assertEquals(0.5, saved.targetRateKgPerWeek!!, 0.0001)
    }

    @Test
    fun `saveVitals re-derives only active-calories preserving manual steps and minutes`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            profile = UserProfile(hasOnboarded = true, goalType = com.hellohealth.domain.model.GoalType.LOSE)
        )
        // User has hand-tuned steps/minutes away from the seeded defaults.
        val goalsRepo = FakeGoalsRepository(ActivityGoals(steps = 12345, activeCalories = 500, activeMinutes = 42))
        val vm = viewModel(FakeAuthRepository(), profileRepo, goalsRepo)
        advanceUntilIdle()

        vm.saveVitals(
            displayName = "Ann",
            gender = Gender.FEMALE,
            birthDateEpochDay = 0L,
            heightCm = 165.0,
            weightKg = 68.0,
            unitPreference = UnitPreference.METRIC
        )
        advanceUntilIdle()

        // goalType=LOSE + null activityLevel → suggestedGoals.activeCalories == 600 (a fixed literal,
        // so this also fails if a regression wiped goalType to null and derived 500).
        assertEquals("steps must not be touched", 12345, goalsRepo.goals.steps)
        assertEquals("minutes must not be touched", 42, goalsRepo.goals.activeMinutes)
        assertEquals("active-calories re-derived", 600, goalsRepo.goals.activeCalories)
    }

    /** A blank name field in the vitals editor must NOT erase the stored name — it falls back. */
    @Test
    fun `saveVitals with blank name keeps the existing stored name`() = runTest(dispatcher) {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(
            profile = UserProfile(displayName = "Ann", hasOnboarded = true)
        )
        val vm = viewModel(authRepo, profileRepo)
        advanceUntilIdle()

        vm.saveVitals("   ", Gender.FEMALE, 0L, 165.0, 68.0, UnitPreference.METRIC)
        advanceUntilIdle()

        assertEquals("blank name must not wipe the stored name", "Ann", profileRepo.profile!!.displayName)
    }

    @Test
    fun `saveVitals pushes display name to auth`() = runTest(dispatcher) {
        val authRepo = FakeAuthRepository()
        val vm = viewModel(authRepo, FakeProfileRepository(profile = UserProfile(hasOnboarded = true)))
        advanceUntilIdle()

        vm.saveVitals("Annie", null, null, null, null, UnitPreference.METRIC)
        advanceUntilIdle()

        assertEquals("Annie", authRepo.lastNameUpdate)
    }

    @Test
    fun `saveVitals surfaces error and does not clear on write failure`() = runTest(dispatcher) {
        val profileRepo = FakeProfileRepository(
            profile = UserProfile(displayName = "Ann", hasOnboarded = true, heightCm = 170.0),
            throwOnWrite = true
        )
        val vm = viewModel(FakeAuthRepository(), profileRepo)
        advanceUntilIdle()

        vm.saveVitals("Ann", null, null, 165.0, null, UnitPreference.METRIC)
        advanceUntilIdle()

        val editor = vm.profileEditorState.value
        assertFalse(editor.isSaving)
        assertNull(editor.successMessage)
        assertTrue("an error must surface", editor.error != null)
        // The stored row is untouched (write threw before mutation in the fake).
        assertEquals(170.0, profileRepo.profile!!.heightCm!!, 0.0001)
    }

    @Test
    fun `loadProfileForEditing exposes the stored profile for seeding`() = runTest(dispatcher) {
        val stored = UserProfile(displayName = "Ann", heightCm = 170.0, hasOnboarded = true)
        val vm = viewModel(FakeAuthRepository(), FakeProfileRepository(profile = stored))
        advanceUntilIdle()

        vm.loadProfileForEditing()
        advanceUntilIdle()

        val editor = vm.profileEditorState.value
        assertEquals(stored, editor.profile)
        assertTrue("a successful load marks loaded=true", editor.loaded)
        assertFalse(editor.isLoading)
    }

    /**
     * A failed load must NOT mark `loaded` — the editor keys its Save gate on `loaded`, so a failed
     * load keeps Save disabled and an empty draft can never clobber a live row.
     */
    @Test
    fun `loadProfileForEditing failure leaves loaded false and surfaces an error`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeAuthRepository(),
            FakeProfileRepository(profile = UserProfile(hasOnboarded = true), throwOnRead = true)
        )
        advanceUntilIdle()

        vm.loadProfileForEditing()
        advanceUntilIdle()

        val editor = vm.profileEditorState.value
        assertFalse("a failed load must not mark loaded", editor.loaded)
        assertNull(editor.profile)
        assertTrue("an error must surface", editor.error != null)
    }
}
