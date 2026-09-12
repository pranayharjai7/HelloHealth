package com.hellohealth.ui.auth

import com.hellohealth.domain.model.User
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Focused tests for the onboarding gate decision. After auth succeeds the ViewModel must route a
 * new/incomplete account to [AuthState.NeedsOnboarding] and a completed account to
 * [AuthState.Authenticated] — the branch Step 5 later wires into navigation.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeAuthRepository(
        var loggedIn: Boolean = true,
        val user: User? = User(email = "a@b.com", name = "Ann")
    ) : AuthRepository {
        override suspend fun signUp(email: String, password: String) = Result.success(Unit)
        override suspend fun signIn(email: String, password: String) = Result.success(Unit)
        override suspend fun signInWithGoogle(idToken: String, email: String?, name: String?, avatarUrl: String?) =
            Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
        override suspend fun isUserLoggedIn() = loggedIn
        override suspend fun getCurrentUser(): User? = user
        override suspend fun updateCurrentUserName(name: String) = Result.success<User?>(null)
    }

    private class FakeProfileRepository(
        var profile: UserProfile?,
        var throwOnRead: Boolean = false
    ) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? {
            if (throwOnRead) error("boom")
            return profile
        }
        override suspend fun upsertProfile(profile: UserProfile) { this.profile = profile }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(auth: FakeAuthRepository, profile: FakeProfileRepository) =
        AuthViewModel(auth, profile)

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
}
