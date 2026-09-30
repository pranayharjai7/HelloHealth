package com.hellohealth.ui.health

import app.cash.turbine.test
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.BodyMetric
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.model.vitals.ReadinessStatus
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.BodyMetricsRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.domain.usecase.BodyAnalyticsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Verifies [HealthViewModel] reconciles activity + vitals + body into one date-aware [HealthUiState]:
 * today's sections populate, and switching the date re-drives all sections to the selected day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeActivity(private val byDay: Map<String, HealthSummary>) : ActivityRepository {
        override suspend fun fetchSummary(goals: ActivityGoals, date: LocalDate, forceRefresh: Boolean) =
            byDay[date.toString()] ?: HealthSummary()
        override suspend fun getHistoryForMonth(month: YearMonth) = emptyList<com.hellohealth.domain.model.DailyHealthSnapshot>()
        override suspend fun getExerciseSessionDetail(sessionId: String, startTimeHint: java.time.Instant?, endTimeHint: java.time.Instant?) = null
        override suspend fun fetchWeeklyStats() = com.hellohealth.domain.model.WeeklyStats()
        override suspend fun hasPermissions() = true
        override suspend fun fetchLatestBodyMetrics() = com.hellohealth.domain.model.BodyMetrics()
        override fun observeTodayCaloriesOut(): Flow<Double> = flowOf(0.0)
        override fun observeCaloriesOutForDay(localDate: String): Flow<Double?> = flowOf(null)
        override fun getRequiredPermissions() = emptySet<String>()
        override fun getAvailability() = 0
        override fun getSettingsIntent(context: android.content.Context) = android.content.Intent()
    }

    private class FakeVitals(
        private val readinessByDay: Map<String, ReadinessScore>,
        private val vitalsByDay: Map<String, LatestVitals>,
    ) : VitalsRepository {
        override fun observeReadiness(): Flow<ReadinessScore?> = flowOf(null)
        override fun observeReadinessAsOf(date: LocalDate): Flow<ReadinessScore?> =
            flowOf(readinessByDay[date.toString()] ?: ReadinessScore(0, ReadinessStatus.INSUFFICIENT_DATA))
        override fun observeVitalsForDay(localDate: String): Flow<LatestVitals?> = flowOf(vitalsByDay[localDate])
        override fun observeRecentRollups(days: Int): Flow<List<HealthMetricsData>> = flowOf(emptyList())
        override fun observeRecentVitals(days: Int): Flow<List<LatestVitals>> = flowOf(vitalsByDay.values.toList())
        override fun observeLatestVitals(): Flow<LatestVitals?> = flowOf(null)
        override suspend fun upsertRollup(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?, sleepDurationMinutes: Int?, deepSleepMinutes: Int?) = Unit
        override suspend fun upsertSample(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?) = Unit
    }

    private class FakeBody(private val rows: List<BodyMetric>) : BodyMetricsRepository {
        override fun observeRecentBodyMetrics(days: Int): Flow<List<BodyMetric>> = flowOf(rows)
        override fun observeLatest(): Flow<BodyMetric?> = flowOf(rows.lastOrNull())
        override suspend fun logWeight(localDate: String, weightKg: Double, waistCm: Double?) = Unit
        override suspend fun upsertFromHealthConnect(localDate: String, weightKg: Double?, heightCm: Double?, bodyFatPct: Double?, leanMassKg: Double?, fatMassKg: Double?, bodyWaterKg: Double?, boneMassKg: Double?, bmr: Double?, bmi: Double?, vo2max: Double?) = Unit
    }

    private class FakeGoals : GoalsRepository {
        override fun getActivityGoals(): Flow<ActivityGoals> = flowOf(ActivityGoals())
        override suspend fun getCurrentActivityGoals() = ActivityGoals()
        override suspend fun updateActivityGoals(goals: ActivityGoals) = Unit
    }

    private class FakeProfile : ProfileRepository {
        override suspend fun getProfile(): UserProfile? = null
        override suspend fun upsertProfile(profile: UserProfile) = Unit
        override suspend fun setDynamicTheme(enabled: Boolean) = Unit
        override fun observeDynamicTheme(): Flow<Boolean> = flowOf(true)
        override suspend fun setAiCoachingEnabled(enabled: Boolean) = Unit
        override fun observeAiCoachingEnabled(): Flow<Boolean> = flowOf(false)
    }

    private fun bodyMetric(date: String, weightKg: Double) = BodyMetric(
        localDate = date, weightKg = weightKg, heightCm = 180.0, bodyFatPct = null, leanMassKg = null,
        fatMassKg = null, bodyWaterKg = null, boneMassKg = null, bmr = null, bmi = null, waistCm = null,
        vo2max = null, source = "health_connect",
    )

    private fun vm(
        activity: Map<String, HealthSummary> = emptyMap(),
        readiness: Map<String, ReadinessScore> = emptyMap(),
        vitals: Map<String, LatestVitals> = emptyMap(),
        body: List<BodyMetric> = emptyList(),
        holder: SelectedDateHolder = SelectedDateHolder(),
    ) = HealthViewModel(
        FakeActivity(activity), FakeVitals(readiness, vitals), FakeBody(body),
        FakeGoals(), FakeProfile(), BodyAnalyticsUseCase(), holder,
    )

    @Test
    fun `today populates activity, vitals and body sections`() = runTest {
        val today = LocalDate.now().toString()
        val vm = vm(
            activity = mapOf(today to HealthSummary(steps = 9000, activeCalories = 500.0)),
            readiness = mapOf(today to ReadinessScore(82, ReadinessStatus.GOOD)),
            vitals = mapOf(today to LatestVitals(today, 58.0, 60.0, null, null, null, 97.0)),
            body = listOf(bodyMetric(today, 80.0)),
        )
        vm.uiState.test {
            var s = awaitItem()
            while (s.isLoading) s = awaitItem()
            assertEquals(9000, s.summary.steps)
            assertEquals(82, s.readiness?.score)
            assertEquals(58.0, s.latestVitals?.restingHeartRate!!, 0.001)
            assertEquals(80.0, s.body.latestWeightKg!!, 0.001)
            assertTrue(s.body.hasAnyData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching the date re-drives all sections to the selected day`() = runTest {
        val today = LocalDate.now()
        val past = today.minusDays(3)
        val holder = SelectedDateHolder()
        val vm = vm(
            activity = mapOf(
                today.toString() to HealthSummary(steps = 9000),
                past.toString() to HealthSummary(steps = 2000),
            ),
            vitals = mapOf(today.toString() to LatestVitals(today.toString(), 58.0, null, null, null, null, null)),
            holder = holder,
        )
        vm.uiState.test {
            var s = awaitItem()
            while (s.summary.steps != 9000L) s = awaitItem()

            holder.set(past)
            while (s.summary.steps != 2000L) s = awaitItem()
            assertEquals(past, s.selectedDate)
            // No vitals rollup for the past day → dashed (null) latestVitals.
            assertEquals(null, s.latestVitals)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
