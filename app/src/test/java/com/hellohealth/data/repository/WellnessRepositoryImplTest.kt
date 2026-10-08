package com.hellohealth.data.repository

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyStat
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.model.WeeklyStats
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.model.vitals.ReadinessStatus
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Repository tests for the wellness score + gamification state. Real in-memory Room for the three
 * gamification DAOs; hand-written fakes for the four pillar repositories (only the observe/fetch
 * methods the repo calls carry real behaviour — the rest are minimal stubs). No mockk.
 *
 * Covers: signed-out → empty snapshot (no writes); live score fusion from the present pillars;
 * persist-TODAY writes the wellness ledger row idempotently (recompute never double-counts); a PAST
 * day computes the score read-only (no ledger write).
 */
@RunWith(RobolectricTestRunner::class)
class WellnessRepositoryImplTest {

    private lateinit var db: AppDatabase
    private var syncRequests = 0

    private val syncScheduler = object : SyncScheduler(
        ApplicationProvider.getApplicationContext<Context>()
    ) {
        override fun requestSync() { syncRequests++ }
    }

    private val today = LocalDate.now()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    // ---- Pillar fakes (only the called methods are meaningful) ----

    private class FakeGoals(private val goals: ActivityGoals) : GoalsRepository {
        override fun getActivityGoals(): Flow<ActivityGoals> = flowOf(goals)
        override suspend fun getCurrentActivityGoals(): ActivityGoals = goals
        override suspend fun updateActivityGoals(goals: ActivityGoals) {}
    }

    private class FakeActivity(private val stat: DailyStat?) : ActivityRepository {
        override suspend fun fetchSummary(goals: ActivityGoals, date: LocalDate, forceRefresh: Boolean) =
            throw UnsupportedOperationException()
        override suspend fun getHistoryForMonth(month: java.time.YearMonth) = emptyList<com.hellohealth.domain.model.DailyHealthSnapshot>()
        override suspend fun getExerciseSessionDetail(sessionId: String, startTimeHint: java.time.Instant?, endTimeHint: java.time.Instant?) = null
        override suspend fun fetchWeeklyStats(): WeeklyStats = WeeklyStats(dailyStats = listOfNotNull(stat))
        override suspend fun hasPermissions(): Boolean = true
        override suspend fun fetchLatestBodyMetrics() = com.hellohealth.domain.model.BodyMetrics()
        override fun observeTodayCaloriesOut(): Flow<Double> = flowOf(0.0)
        override fun observeCaloriesOutForDay(localDate: String): Flow<Double?> = flowOf(null)
        override fun getRequiredPermissions(): Set<String> = emptySet()
        override fun getAvailability(): Int = 0
        override fun getSettingsIntent(context: Context): Intent = Intent()
    }

    private class FakeNutrition(private val summary: NutritionDaySummary) : NutritionRepository {
        override fun observeDaySummary(localDate: String): Flow<NutritionDaySummary> = flowOf(summary)
        override fun observeEntries(localDate: String) = flowOf(emptyList<com.hellohealth.domain.model.nutrition.FoodEntry>())
        override suspend fun addQuickAdd(localDate: String, mealCategory: com.hellohealth.domain.model.nutrition.MealCategory, foodName: String, quantity: Double, unit: String, calories: Double, proteinG: Double?, carbsG: Double?, fatG: Double?, fibreG: Double?) {}
        override suspend fun addFromFood(localDate: String, mealCategory: com.hellohealth.domain.model.nutrition.MealCategory, foodId: String, quantity: Double, unit: String) {}
        override suspend fun addWater(localDate: String, waterMl: Double) {}
        override suspend fun deleteEntry(id: String) {}
        override suspend fun searchFoods(query: String) = emptyList<com.hellohealth.data.local.entities.CachedFoodEntity>()
        override suspend fun resolveBarcode(barcode: String): com.hellohealth.data.local.entities.CachedFoodEntity? = null
        override suspend fun seedCatalogIfEmpty() {}
    }

    private class FakeVitals(private val readiness: ReadinessScore?) : VitalsRepository {
        override fun observeReadiness(): Flow<ReadinessScore?> = flowOf(readiness)
        override fun observeReadinessAsOf(date: LocalDate): Flow<ReadinessScore?> = flowOf(readiness)
        override fun observeVitalsForDay(localDate: String) = flowOf<com.hellohealth.domain.model.vitals.LatestVitals?>(null)
        override fun observeRecentRollups(days: Int) = flowOf(emptyList<com.hellohealth.domain.model.vitals.HealthMetricsData>())
        override fun observeRecentVitals(days: Int) = flowOf(emptyList<com.hellohealth.domain.model.vitals.LatestVitals>())
        override fun observeLatestVitals() = flowOf<com.hellohealth.domain.model.vitals.LatestVitals?>(null)
        override suspend fun upsertRollup(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?, sleepDurationMinutes: Int?, deepSleepMinutes: Int?) {}
        override suspend fun upsertSample(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?) {}
    }

    private class FakeEmotions(private val logs: List<EmotionRecord>) : EmotionsRepository {
        override fun observeToday(): Flow<List<EmotionRecord>> = flowOf(logs)
        override fun observeLatest(): Flow<EmotionRecord?> = flowOf(logs.firstOrNull())
        override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = flowOf(logs)
        override suspend fun logEmotion(emotion: EmotionType, confidence: Double, source: String, note: String?, visibility: String): String? = null
        override suspend fun delete(id: String) {}
    }

    private fun repo(
        userId: String?,
        goals: ActivityGoals = ActivityGoals(steps = 10000, activeCalories = 500, activeMinutes = 60),
        stat: DailyStat? = null,
        nutrition: NutritionDaySummary = emptyNutrition(today.toString()),
        readiness: ReadinessScore? = null,
        moods: List<EmotionRecord> = emptyList(),
    ) = WellnessRepositoryImpl(
        goalsRepository = FakeGoals(goals),
        activityRepository = FakeActivity(stat),
        nutritionRepository = FakeNutrition(nutrition),
        vitalsRepository = FakeVitals(readiness),
        emotionsRepository = FakeEmotions(moods),
        streakDao = db.streakDao(),
        pointsLedgerDao = db.pointsLedgerDao(),
        achievementDao = db.achievementDao(),
        sessionManager = object : SupabaseSessionManager(null) {
            override suspend fun getCurrentUserId(): String? = userId
        },
        syncScheduler = syncScheduler,
    )

    private fun emptyNutrition(date: String) = NutritionDaySummary(
        localDate = date, caloriesConsumed = 0.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0,
        fibreG = 0.0, waterMl = 0.0, entriesByMeal = emptyMap(),
    )

    private fun positiveMood() = EmotionRecord(
        id = "m1", userId = "u1",
        timestampUtcEpochMs = 1_000L, tzOffsetMinutes = 0,
        emotion = EmotionType.entries.first { it.valence == com.hellohealth.domain.model.Valence.POSITIVE },
    )

    // ---------------------------------------------------------------- Tests

    @Test
    fun `signed out emits an empty snapshot and writes nothing`() = runTest {
        val snapshot = repo(null).observeSnapshot(today).first()
        assertNull(snapshot.score)
        assertTrue(snapshot.pillars.isEmpty())
        assertEquals(0, snapshot.totalPoints)
        assertEquals(0, syncRequests)
        assertTrue(db.pointsLedgerDao().getUnsynced().isEmpty())
    }

    @Test
    fun `score fuses the present pillars live`() = runTest {
        // Activity: all 3 goals met → 100. Mood: 1 positive log → 100. Nutrition/recovery absent.
        val stat = DailyStat(date = today, steps = 12000, calories = 600.0, activeMinutes = 70, sleepMinutes = 0, avgHeartRate = 0)
        val snapshot = repo("u1", stat = stat, moods = listOf(positiveMood())).observeSnapshot(today).first()
        assertEquals(100, snapshot.score)
        assertEquals(2, snapshot.pillars.size) // only activity + mood present
    }

    @Test
    fun `persisting today writes an idempotent wellness ledger row`() = runTest {
        val stat = DailyStat(date = today, steps = 12000, calories = 600.0, activeMinutes = 70, sleepMinutes = 0, avgHeartRate = 0)
        val r = repo("u1", stat = stat, moods = listOf(positiveMood()))

        // Observe twice (simulating two recomputes) — the (day, "wellness") row must not duplicate.
        r.observeSnapshot(today).first()
        r.observeSnapshot(today).first()

        val rows = db.pointsLedgerDao().getUnsynced().filter { it.source == "wellness" && it.localDate == today.toString() }
        assertEquals("exactly one wellness ledger row for today", 1, rows.size)
        assertEquals(100, rows.first().points)
    }

    @Test
    fun `a past day computes the score read-only and writes no ledger row`() = runTest {
        val past = today.minusDays(3)
        val stat = DailyStat(date = past, steps = 12000, calories = 600.0, activeMinutes = 70, sleepMinutes = 0, avgHeartRate = 0)
        // Nutrition/mood/readiness empty; activity present for the past day.
        val snapshot = repo("u1", stat = stat, nutrition = emptyNutrition(past.toString()))
            .observeSnapshot(past).first()

        assertNotNull("score still computed for a past day", snapshot.score)
        assertTrue("no ledger write for a past day", db.pointsLedgerDao().getUnsynced().isEmpty())
    }
}
