package com.hellohealth.ui.dashboard

import app.cash.turbine.test
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.model.nutrition.FoodEntry
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.VitalsRepository
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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Verifies [InsightsPreviewViewModel] counts how many of the four dimensions have data today, for
 * the dashboard entry card's one-line summary. Cheap today-only reads; missing dimension not counted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InsightsPreviewViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate.now(ZoneId.systemDefault()).toString()

    private class FakeNutrition(private val summary: NutritionDaySummary) : NutritionRepository {
        override fun observeDaySummary(localDate: String): Flow<NutritionDaySummary> = flowOf(summary)
        override fun observeEntries(localDate: String): Flow<List<FoodEntry>> = flowOf(emptyList())
        override suspend fun addQuickAdd(localDate: String, mealCategory: MealCategory, foodName: String, quantity: Double, unit: String, calories: Double, proteinG: Double?, carbsG: Double?, fatG: Double?, fibreG: Double?) = Unit
        override suspend fun addFromFood(localDate: String, mealCategory: MealCategory, foodId: String, quantity: Double, unit: String) = Unit
        override suspend fun addWater(localDate: String, waterMl: Double) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun searchFoods(query: String) = emptyList<com.hellohealth.data.local.entities.CachedFoodEntity>()
        override suspend fun resolveBarcode(barcode: String): com.hellohealth.data.local.entities.CachedFoodEntity? = null
        override suspend fun seedCatalogIfEmpty() = Unit
    }

    private class FakeEmotions(private val today: List<EmotionRecord>) : EmotionsRepository {
        override fun observeToday(): Flow<List<EmotionRecord>> = flowOf(today)
        override fun observeLatest(): Flow<EmotionRecord?> = flowOf(today.firstOrNull())
        override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = flowOf(emptyList())
        override suspend fun logEmotion(emotion: EmotionType, confidence: Double, source: String, note: String?, visibility: String): String? = null
        override suspend fun delete(id: String) = Unit
    }

    private class FakeVitals(private val latest: LatestVitals?) : VitalsRepository {
        override fun observeReadiness(): Flow<ReadinessScore?> = flowOf(null)
        override fun observeReadinessAsOf(date: java.time.LocalDate): Flow<ReadinessScore?> = flowOf(null)
        override fun observeRecentRollups(days: Int): Flow<List<HealthMetricsData>> = flowOf(emptyList())
        override fun observeRecentVitals(days: Int): Flow<List<LatestVitals>> = flowOf(emptyList())
        override fun observeLatestVitals(): Flow<LatestVitals?> = flowOf(latest)
        override fun observeVitalsForDay(localDate: String): Flow<LatestVitals?> = flowOf(latest)
        override suspend fun upsertRollup(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?, sleepDurationMinutes: Int?, deepSleepMinutes: Int?) = Unit
        override suspend fun upsertSample(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?) = Unit
    }

    private class FakeActivity(private val caloriesOut: Double) : ActivityRepository {
        override suspend fun fetchSummary(goals: ActivityGoals, date: LocalDate, forceRefresh: Boolean) = com.hellohealth.domain.model.HealthSummary()
        override suspend fun getHistoryForMonth(month: YearMonth) = emptyList<com.hellohealth.domain.model.DailyHealthSnapshot>()
        override suspend fun getExerciseSessionDetail(sessionId: String, startTimeHint: java.time.Instant?, endTimeHint: java.time.Instant?) = null
        override suspend fun fetchWeeklyStats() = com.hellohealth.domain.model.WeeklyStats()
        override suspend fun hasPermissions() = true
        override suspend fun fetchLatestBodyMetrics() = com.hellohealth.domain.model.BodyMetrics()
        override fun observeTodayCaloriesOut(): Flow<Double> = flowOf(caloriesOut)
        override fun observeCaloriesOutForDay(localDate: String): Flow<Double?> = flowOf(caloriesOut)
        override fun getRequiredPermissions() = emptySet<String>()
        override fun getAvailability() = 0
        override fun getSettingsIntent(context: android.content.Context) = android.content.Intent()
    }

    private fun emptySummary() = NutritionDaySummary.empty(today)
    private fun nutritionSummary(kcal: Double) = NutritionDaySummary(today, kcal, 0.0, 0.0, 0.0, 0.0, 0.0, emptyMap())
    private fun latestVitals() = LatestVitals(today, 58.0, 60.0, null, null, null, null)
    private fun mood() = EmotionRecord("m1", "u1", 1L, 0, EmotionType.HAPPINESS)

    @Test
    fun `counts zero when nothing is tracked today`() = runTest {
        val vm = InsightsPreviewViewModel(FakeNutrition(emptySummary()), FakeEmotions(emptyList()), FakeVitals(null), FakeActivity(0.0))
        vm.uiState.test {
            var s = awaitItem()
            while (s.isLoading) s = awaitItem()
            assertEquals(0, s.dimensionsTracked)
            assertEquals(4, s.totalDimensions)
            assertFalse(s.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `counts each dimension that has data today`() = runTest {
        // Nutrition (kcal>0) + mood + vitals + activity(caloriesOut>0) = 4.
        val vm = InsightsPreviewViewModel(
            FakeNutrition(nutritionSummary(1200.0)),
            FakeEmotions(listOf(mood())),
            FakeVitals(latestVitals()),
            FakeActivity(500.0),
        )
        vm.uiState.test {
            var s = awaitItem()
            while (s.isLoading) s = awaitItem()
            assertEquals(4, s.dimensionsTracked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `counts a partial day correctly`() = runTest {
        // Only mood + nutrition water -> 2.
        val vm = InsightsPreviewViewModel(
            FakeNutrition(NutritionDaySummary(today, 0.0, 0.0, 0.0, 0.0, 0.0, 500.0, emptyMap())),
            FakeEmotions(listOf(mood())),
            FakeVitals(null),
            FakeActivity(0.0),
        )
        vm.uiState.test {
            var s = awaitItem()
            while (s.isLoading) s = awaitItem()
            assertEquals(2, s.dimensionsTracked)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
