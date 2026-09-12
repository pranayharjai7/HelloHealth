package com.hellohealth.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.User
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.GoalsRepository
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
import java.time.YearMonth

/**
 * Focused tests for the connected-vs-no-data decision. The dashboard must distinguish
 * "not connected" (no Health Connect permissions) from "connected but no data for this date",
 * and must refresh permission state when the screen resumes after the user grants access.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * Configurable fake. `permissionsGranted` can flip between calls to emulate the user granting
     * access in the Health Connect UI and returning to the app.
     */
    private class FakeActivityRepository(
        var available: Int = HealthConnectClient.SDK_AVAILABLE,
        var permissionsGranted: Boolean = false,
        var summaryForDate: (LocalDate) -> HealthSummary = { HealthSummary(lastUpdated = 0L) }
    ) : ActivityRepository {
        override suspend fun fetchSummary(goals: ActivityGoals, date: LocalDate, forceRefresh: Boolean): HealthSummary =
            summaryForDate(date)
        override suspend fun getHistoryForMonth(month: YearMonth): List<DailyHealthSnapshot> = emptyList()
        override suspend fun getExerciseSessionDetail(
            sessionId: String,
            startTimeHint: java.time.Instant?,
            endTimeHint: java.time.Instant?
        ) = null
        override suspend fun fetchWeeklyStats() = com.hellohealth.domain.model.WeeklyStats()
        override suspend fun hasPermissions(): Boolean = permissionsGranted
        override fun getRequiredPermissions(): Set<String> = setOf("perm")
        override fun getAvailability(): Int = available
        override fun getSettingsIntent(context: Context): Intent = Intent()
    }

    private class FakeAuthRepository : AuthRepository {
        override suspend fun signUp(email: String, password: String) = Result.success(Unit)
        override suspend fun signIn(email: String, password: String) = Result.success(Unit)
        override suspend fun signInWithGoogle(idToken: String, email: String?, name: String?, avatarUrl: String?) =
            Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
        override suspend fun isUserLoggedIn() = true
        override suspend fun getCurrentUser(): User? = null
        override suspend fun updateCurrentUserName(name: String) = Result.success<User?>(null)
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

    private fun viewModel(repo: FakeActivityRepository) =
        DashboardViewModel(repo, FakeAuthRepository(), FakeGoalsRepository())

    @Test
    fun `no permissions leaves dashboard disconnected`() = runTest(dispatcher) {
        val vm = viewModel(FakeActivityRepository(permissionsGranted = false))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasHealthPermissions)
    }

    @Test
    fun `granted permissions with data marks connected`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeActivityRepository(
                permissionsGranted = true,
                summaryForDate = { HealthSummary(steps = 4200, lastUpdated = 123L) }
            )
        )
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasHealthPermissions)
        assertEquals(4200L, vm.uiState.value.healthSummary.steps)
    }

    @Test
    fun `connected but empty past date stays connected and not a connect-prompt`() = runTest(dispatcher) {
        // Permissions granted, but the selected past date has no data (lastUpdated == 0).
        val repo = FakeActivityRepository(
            permissionsGranted = true,
            summaryForDate = { HealthSummary(steps = 0, lastUpdated = 0L) }
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.selectDate(LocalDate.now().minusDays(5))
        advanceUntilIdle()

        // Dataless != disconnected: the connect prompt is gated on hasHealthPermissions,
        // which must remain true here so the UI shows a zero/no-data state instead.
        assertTrue(vm.uiState.value.hasHealthPermissions)
        assertEquals(0L, vm.uiState.value.healthSummary.steps)
    }

    @Test
    fun `refreshPermissionState flips to connected after user grants access`() = runTest(dispatcher) {
        // Starts disconnected (mirrors first launch before the user grants access).
        val repo = FakeActivityRepository(
            permissionsGranted = false,
            summaryForDate = { HealthSummary(steps = 900, lastUpdated = 55L) }
        )
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasHealthPermissions)

        // User grants permissions in Health Connect and returns -> ON_RESUME re-check.
        repo.permissionsGranted = true
        vm.refreshPermissionState()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasHealthPermissions)
        assertEquals(900L, vm.uiState.value.healthSummary.steps)
    }
}
