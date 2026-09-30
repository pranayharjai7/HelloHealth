package com.hellohealth.data.repository

import com.hellohealth.data.ai.ChatRequest
import com.hellohealth.data.ai.ChatResult
import com.hellohealth.data.ai.CoachingProxyDataSource
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.BodyMetrics
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.model.WeeklyStats
import com.hellohealth.domain.model.coaching.CoachingInsight
import com.hellohealth.domain.model.nutrition.FoodEntry
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.VitalsRepository
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tests [CoachingRepositoryImpl]: the cross-dimension context assembly, the consent gate, and the
 * LLM -> rule-based fallback. Robolectric so the impl's `AppLogger.w` failure-path logging resolves.
 */
@RunWith(RobolectricTestRunner::class)
class CoachingRepositoryImplTest {

    // --- Fakes -----------------------------------------------------------------------------------

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

    private class FakeActivity(private val summary: HealthSummary) : ActivityRepository {
        override suspend fun fetchSummary(goals: ActivityGoals, date: LocalDate, forceRefresh: Boolean) = summary
        override suspend fun getHistoryForMonth(month: YearMonth) = emptyList<DailyHealthSnapshot>()
        override suspend fun getExerciseSessionDetail(sessionId: String, startTimeHint: java.time.Instant?, endTimeHint: java.time.Instant?): ActivityDetail? = null
        override suspend fun fetchWeeklyStats() = WeeklyStats()
        override suspend fun hasPermissions() = true
        override suspend fun fetchLatestBodyMetrics() = BodyMetrics()
        override fun observeTodayCaloriesOut(): Flow<Double> = flowOf(0.0)
        override fun getRequiredPermissions() = emptySet<String>()
        override fun getAvailability() = 0
        override fun getSettingsIntent(context: android.content.Context) = android.content.Intent()
    }

    private class FakeVitals(
        private val readiness: ReadinessScore?,
        private val latest: LatestVitals?,
    ) : VitalsRepository {
        override fun observeReadiness(): Flow<ReadinessScore?> = flowOf(readiness)
        override fun observeRecentRollups(days: Int): Flow<List<com.hellohealth.domain.model.vitals.HealthMetricsData>> = flowOf(emptyList())
        override fun observeRecentVitals(days: Int): Flow<List<LatestVitals>> = flowOf(emptyList())
        override fun observeLatestVitals(): Flow<LatestVitals?> = flowOf(latest)
        override suspend fun upsertRollup(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?, sleepDurationMinutes: Int?, deepSleepMinutes: Int?) = Unit
        override suspend fun upsertSample(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?) = Unit
    }

    private class FakeEmotions(private val today: List<EmotionRecord>) : EmotionsRepository {
        override fun observeToday(): Flow<List<EmotionRecord>> = flowOf(today)
        override fun observeLatest(): Flow<EmotionRecord?> = flowOf(today.firstOrNull())
        override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = flowOf(emptyList())
        override suspend fun logEmotion(emotion: EmotionType, confidence: Double, source: String, note: String?, visibility: String): String? = null
        override suspend fun delete(id: String) = Unit
    }

    private class FakeProfile(private val profile: UserProfile?, private val enabled: Boolean) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? = profile
        override suspend fun upsertProfile(profile: UserProfile) = Unit
        override suspend fun setDynamicTheme(enabled: Boolean) = Unit
        override fun observeDynamicTheme(): Flow<Boolean> = flowOf(true)
        override suspend fun setAiCoachingEnabled(enabled: Boolean) = Unit
        override fun observeAiCoachingEnabled(): Flow<Boolean> = flowOf(enabled)
    }

    private class FakeProxy(private val result: ChatResult, val onCall: () -> Unit = {}) :
        CoachingProxyDataSource(CoachingRepositoryImplTest.stubSupabase()) {
        override suspend fun generate(request: ChatRequest): ChatResult { onCall(); return result }
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun summary(cal: Double = 0.0, p: Double = 0.0, c: Double = 0.0, f: Double = 0.0, water: Double = 0.0) =
        NutritionDaySummary(
            localDate = LocalDate.now().toString(),
            caloriesConsumed = cal, proteinG = p, carbsG = c, fatG = f, fibreG = 0.0, waterMl = water,
            entriesByMeal = emptyMap(),
        )

    private fun health(steps: Long = 0, active: Double = 0.0, bmr: Double = 0.0, sleepMin: Long = 0) =
        HealthSummary(steps = steps, activeCalories = active, basalMetabolicRate = bmr, sleepDurationMinutes = sleepMin)

    private fun repo(
        summary: NutritionDaySummary = summary(),
        health: HealthSummary = health(),
        readiness: ReadinessScore? = null,
        latest: LatestVitals? = null,
        moods: List<EmotionRecord> = emptyList(),
        profile: UserProfile? = UserProfile(),
        enabled: Boolean = false,
        proxy: FakeProxy = FakeProxy(ChatResult.Failure("no-op")),
    ) = CoachingRepositoryImpl(
        FakeNutrition(summary), FakeActivity(health), FakeVitals(readiness, latest),
        FakeEmotions(moods), FakeProfile(profile, enabled), proxy,
    )

    private fun mood(emotion: EmotionType) = EmotionRecord(
        id = "e-${emotion.name}", userId = "u1", timestampUtcEpochMs = 1, tzOffsetMinutes = 0, emotion = emotion
    )

    // --- Tests -----------------------------------------------------------------------------------

    @Test
    fun `buildTodayContext computes net energy balance and folds all dimensions`() = runTest {
        val ctx = repo(
            summary = summary(cal = 1500.0, p = 100.0, c = 150.0, f = 50.0, water = 750.0),
            health = health(steps = 8200, active = 400.0, bmr = 1600.0, sleepMin = 450),
            readiness = ReadinessScore(score = 82, status = com.hellohealth.domain.model.vitals.ReadinessStatus.GOOD),
            latest = LatestVitals("2026-09-30", restingHeartRate = 58.0, hrvRmssd = 65.0, respiratoryRate = null, bodyTemperature = null, hydrationMl = null, spo2 = null),
            moods = listOf(mood(EmotionType.HAPPINESS), mood(EmotionType.HAPPINESS), mood(EmotionType.SADNESS)),
        ).buildTodayContext()

        assertEquals(1500, ctx.caloriesConsumed)
        assertEquals(2000, ctx.caloriesOut)          // 400 + 1600
        assertEquals(500, ctx.netKcal)               // 2000 - 1500 (deficit)
        assertEquals(100, ctx.proteinG)
        assertEquals(750, ctx.waterMl)
        assertEquals(8200, ctx.steps)
        assertEquals(7.5, ctx.sleepHours!!, 0.01)     // 450 min
        assertEquals(82, ctx.readinessScore)
        assertEquals(58, ctx.restingHeartRate)
        assertEquals("HAPPINESS", ctx.dominantMoodToday)
        assertEquals(3, ctx.moodCountToday)
    }

    @Test
    fun `an empty day yields an all-null context with hasAnyData false`() = runTest {
        val ctx = repo(profile = null).buildTodayContext()
        assertEquals(false, ctx.hasAnyData)
        assertEquals(null, ctx.caloriesConsumed)
        assertEquals(null, ctx.netKcal)
    }

    @Test
    fun `dailyInsight uses the LLM when enabled and a provider succeeds`() = runTest {
        val insight = repo(
            summary = summary(cal = 1500.0),
            enabled = true,
            proxy = FakeProxy(ChatResult.Success("Great deficit today!")),
        ).dailyInsight()
        assertEquals("Great deficit today!", insight.text)
        assertEquals(CoachingInsight.Source.LLM, insight.source)
    }

    @Test
    fun `dailyInsight falls back to rule-based when the LLM fails`() = runTest {
        val insight = repo(
            summary = summary(cal = 1500.0),
            health = health(active = 500.0, bmr = 1600.0),
            profile = UserProfile(
                gender = com.hellohealth.domain.model.Gender.MALE,
                birthDateEpochDay = LocalDate.now().minusYears(30).toEpochDay(),
                heightCm = 180.0, weightKg = 80.0,
                activityLevel = com.hellohealth.domain.model.ActivityLevel.MODERATE,
                goalType = com.hellohealth.domain.model.GoalType.MAINTAIN,
            ),
            enabled = true,
            proxy = FakeProxy(ChatResult.Failure("both down")),
        ).dailyInsight()
        assertEquals(CoachingInsight.Source.RULE_BASED, insight.source)
        assertTrue(insight.text.isNotBlank())
    }

    @Test
    fun `dailyInsight never calls the LLM when consent is off`() = runTest {
        var called = false
        val insight = repo(
            summary = summary(cal = 1500.0),
            enabled = false,
            proxy = FakeProxy(ChatResult.Success("should not be used")) { called = true },
        ).dailyInsight()
        assertEquals(false, called)
        assertEquals(CoachingInsight.Source.RULE_BASED, insight.source)
    }

    @Test
    fun `ask with a blank question returns a gentle rule-based prompt without calling the LLM`() = runTest {
        var called = false
        val insight = repo(enabled = true, proxy = FakeProxy(ChatResult.Success("x")) { called = true }).ask("   ")
        assertEquals(false, called)
        assertEquals(CoachingInsight.Source.RULE_BASED, insight.source)
    }

    @Test
    fun `ask uses the LLM when enabled and successful`() = runTest {
        val insight = repo(enabled = true, proxy = FakeProxy(ChatResult.Success("Because your HRV dipped."))).ask("Why is readiness low?")
        assertEquals("Because your HRV dipped.", insight.text)
        assertEquals(CoachingInsight.Source.LLM, insight.source)
    }

    companion object {
        /** A throwaway Supabase client so FakeProxy can call its super constructor (never used — generate is overridden). */
        fun stubSupabase() = createSupabaseClient(supabaseUrl = "https://stub.supabase.co", supabaseKey = "stub") {}
    }
}
