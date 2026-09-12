package com.hellohealth.ui.onboarding

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
        override suspend fun signUp(email: String, password: String) = Result.success(Unit)
        override suspend fun signIn(email: String, password: String) = Result.success(Unit)
        override suspend fun signInWithGoogle(idToken: String, email: String?, name: String?, avatarUrl: String?) =
            Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
        override suspend fun isUserLoggedIn() = true
        override suspend fun getCurrentUser(): User? = User(email = "a@b.com", name = currentName)
        override suspend fun updateCurrentUserName(name: String) = Result.success<User?>(null)
    }

    private class FakeProfileRepository : ProfileRepository {
        var saved: UserProfile? = null
        override suspend fun getProfile(): UserProfile? = saved
        override suspend fun upsertProfile(profile: UserProfile) { saved = profile }
    }

    private class FakeGoalsRepository : GoalsRepository {
        override fun getActivityGoals(): Flow<ActivityGoals> = flowOf(ActivityGoals())
        override suspend fun getCurrentActivityGoals(): ActivityGoals = ActivityGoals()
        override suspend fun updateActivityGoals(goals: ActivityGoals) {}
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(name: String?) =
        OnboardingViewModel(FakeAuthRepository(name), FakeProfileRepository(), FakeGoalsRepository())

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
}
