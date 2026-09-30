package com.hellohealth.ui.insights

import app.cash.turbine.test
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyStat
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.model.WeeklyStats
import com.hellohealth.domain.model.nutrition.FoodEntry
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.UserRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.domain.usecase.BuildCrossInsightsUseCase
import com.hellohealth.domain.usecase.BuildWeeklyInsightsUseCase
import com.hellohealth.domain.vitals.ReadinessScoreCalculator
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
import java.time.ZoneId

/**
 * Verifies [InsightsViewModel] folds the three extra dimensions into [InsightsUiState.crossInsights]
 * on load, alongside the existing activity weekly insights. Uses the real pure use-cases so the
 * wiring (which repo feeds which field) is what's under test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)

    private class FakeActivity(private val stats: WeeklyStats) : ActivityRepository {
        override suspend fun fetchSummary(goals: ActivityGoals, date: LocalDate, forceRefresh: Boolean) = com.hellohealth.domain.model.HealthSummary()
        override suspend fun getHistoryForMonth(month: YearMonth) = emptyList<com.hellohealth.domain.model.DailyHealthSnapshot>()
        override suspend fun getExerciseSessionDetail(sessionId: String, startTimeHint: java.time.Instant?, endTimeHint: java.time.Instant?) = null
        override suspend fun fetchWeeklyStats() = stats
        override suspend fun hasPermissions() = true
        override suspend fun fetchLatestBodyMetrics() = com.hellohealth.domain.model.BodyMetrics()
        override fun observeTodayCaloriesOut(): Flow<Double> = flowOf(0.0)
        override fun observeCaloriesOutForDay(localDate: String): Flow<Double?> = flowOf(null)
        override fun getRequiredPermissions() = emptySet<String>()
        override fun getAvailability() = 0
        override fun getSettingsIntent(context: android.content.Context) = android.content.Intent()
    }

    private class FakeGoals(private val goals: ActivityGoals) : GoalsRepository {
        override fun getActivityGoals(): Flow<ActivityGoals> = flowOf(goals)
        override suspend fun getCurrentActivityGoals() = goals
        override suspend fun updateActivityGoals(goals: ActivityGoals) = Unit
    }

    private class FakeUser(private val prefs: FoodPreferences) : UserRepository {
        override fun getFoodPreferences(): Flow<FoodPreferences> = flowOf(prefs)
        override suspend fun getCurrentFoodPreferences() = prefs
        override suspend fun updateFoodPreferences(preferences: FoodPreferences) = Unit
    }

    private class FakeEmotions(private val window: List<EmotionRecord>) : EmotionsRepository {
        override fun observeToday(): Flow<List<EmotionRecord>> = flowOf(emptyList())
        override fun observeLatest(): Flow<EmotionRecord?> = flowOf(null)
        override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = flowOf(window)
        override suspend fun logEmotion(emotion: EmotionType, confidence: Double, source: String, note: String?, visibility: String): String? = null
        override suspend fun delete(id: String) = Unit
    }

    private class FakeVitals(private val rollups: List<HealthMetricsData>) : VitalsRepository {
        override fun observeReadiness(): Flow<ReadinessScore?> = flowOf(null)
        override fun observeReadinessAsOf(date: java.time.LocalDate): Flow<ReadinessScore?> = flowOf(null)
        override fun observeRecentRollups(days: Int): Flow<List<HealthMetricsData>> = flowOf(rollups)
        override fun observeRecentVitals(days: Int): Flow<List<LatestVitals>> = flowOf(emptyList())
        override fun observeLatestVitals(): Flow<LatestVitals?> = flowOf(null)
        override fun observeVitalsForDay(localDate: String): Flow<LatestVitals?> = flowOf(null)
        override suspend fun upsertRollup(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?, sleepDurationMinutes: Int?, deepSleepMinutes: Int?) = Unit
        override suspend fun upsertSample(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?) = Unit
    }

    private class FakeNutrition(private val byDay: Map<String, NutritionDaySummary>) : NutritionRepository {
        override fun observeDaySummary(localDate: String): Flow<NutritionDaySummary> = flowOf(byDay[localDate] ?: NutritionDaySummary.empty(localDate))
        override fun observeEntries(localDate: String): Flow<List<FoodEntry>> = flowOf(emptyList())
        override suspend fun addQuickAdd(localDate: String, mealCategory: MealCategory, foodName: String, quantity: Double, unit: String, calories: Double, proteinG: Double?, carbsG: Double?, fatG: Double?, fibreG: Double?) = Unit
        override suspend fun addFromFood(localDate: String, mealCategory: MealCategory, foodId: String, quantity: Double, unit: String) = Unit
        override suspend fun addWater(localDate: String, waterMl: Double) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun searchFoods(query: String) = emptyList<com.hellohealth.data.local.entities.CachedFoodEntity>()
        override suspend fun resolveBarcode(barcode: String): com.hellohealth.data.local.entities.CachedFoodEntity? = null
        override suspend fun seedCatalogIfEmpty() = Unit
    }

    private fun vm(
        stats: WeeklyStats = WeeklyStats(),
        emotions: List<EmotionRecord> = emptyList(),
        rollups: List<HealthMetricsData> = emptyList(),
        nutrition: Map<String, NutritionDaySummary> = emptyMap(),
    ) = InsightsViewModel(
        FakeActivity(stats), FakeGoals(ActivityGoals()), FakeUser(FoodPreferences()),
        FakeEmotions(emotions), FakeVitals(rollups), FakeNutrition(nutrition),
        BuildWeeklyInsightsUseCase(), BuildCrossInsightsUseCase(ReadinessScoreCalculator()),
    )

    @Test
    fun `load folds mood and nutrition into the cross-dimension series`() = runTest {
        val moodMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val emotions = listOf(
            EmotionRecord("m1", "u1", moodMs, 0, EmotionType.HAPPINESS),
            EmotionRecord("m2", "u1", moodMs, 0, EmotionType.ANGER),
        )
        val nutrition = mapOf(today.toString() to NutritionDaySummary(today.toString(), 1800.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyMap()))
        val stats = WeeklyStats(listOf(DailyStat(today, steps = 9000, calories = 500.0, activeMinutes = 40, sleepMinutes = 420, avgHeartRate = 60)))

        vm(stats = stats, emotions = emotions, nutrition = nutrition).uiState.test {
            var s = awaitItem()
            while (s.crossInsights.days.isEmpty()) s = awaitItem()
            val last = s.crossInsights.days.last()
            assertEquals(today, last.date)
            assertEquals(0.5, last.moodPositivity!!, 0.0001) // 1 of 2 positive
            assertEquals(1800.0, last.caloriesIn!!, 0.01)
            assertEquals(500.0, last.caloriesOut!!, 0.01)    // from WeeklyStats active calories
            assertTrue(s.crossInsights.hasAnyData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load still produces the activity weekly insights`() = runTest {
        val stats = WeeklyStats((0..6).map {
            DailyStat(today.minusDays(it.toLong()), steps = 10000, calories = 600.0, activeMinutes = 60, sleepMinutes = 450, avgHeartRate = 62)
        })
        vm(stats = stats).uiState.test {
            var s = awaitItem()
            while (s.weeklyStats.dailyStats.isEmpty()) s = awaitItem()
            assertTrue(s.weeklyInsights.averageSteps > 0)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
