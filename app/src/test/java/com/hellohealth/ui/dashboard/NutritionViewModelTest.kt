package com.hellohealth.ui.dashboard

import app.cash.turbine.test
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.health.MacroTargets
import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.model.nutrition.FoodEntry
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.ProfileRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Verifies the flagship read-time energy-balance join in [NutritionViewModel]: the nutrition day
 * summary (calories in) combined with the activity snapshot's calories out (active + BMR) and the
 * profile-derived budget/macro targets, with `net = caloriesOut − caloriesIn` computed in the VM and
 * never persisted. Plain-JVM (no Robolectric) — the happy paths here never touch android.util.Log.
 *
 * A concrete full profile is used so the budget is deterministic; the test derives the EXPECTED
 * budget/macros from the same pure [BodyEnergy]/[MacroTargets] functions the VM uses, so it pins the
 * wiring (which numbers flow where) rather than re-hardcoding the arithmetic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val today: String = LocalDate.now().toString()

    private class FakeNutrition(private val summary: NutritionDaySummary) : NutritionRepository {
        override fun observeDaySummary(localDate: String): Flow<NutritionDaySummary> = flowOf(summary)
        override fun observeEntries(localDate: String): Flow<List<FoodEntry>> = flowOf(emptyList())
        override suspend fun addQuickAdd(
            localDate: String, mealCategory: MealCategory, foodName: String, quantity: Double,
            unit: String, calories: Double, proteinG: Double?, carbsG: Double?, fatG: Double?, fibreG: Double?,
        ) = Unit
        override suspend fun addFromFood(
            localDate: String, mealCategory: MealCategory, foodId: String, quantity: Double, unit: String,
        ) = Unit
        override suspend fun addWater(localDate: String, waterMl: Double) = Unit
        override suspend fun deleteEntry(id: String) = Unit
        override suspend fun searchFoods(query: String) =
            emptyList<com.hellohealth.data.local.entities.CachedFoodEntity>()
        override suspend fun resolveBarcode(barcode: String): com.hellohealth.data.local.entities.CachedFoodEntity? = null
        override suspend fun seedCatalogIfEmpty() = Unit
    }

    /** Only [observeTodayCaloriesOut] matters here; the rest is unused by the VM. */
    private class FakeActivity(private val caloriesOut: Double) : ActivityRepository {
        override suspend fun fetchSummary(
            goals: com.hellohealth.domain.model.ActivityGoals, date: LocalDate, forceRefresh: Boolean,
        ) = com.hellohealth.domain.model.HealthSummary()
        override suspend fun getHistoryForMonth(month: java.time.YearMonth) = emptyList<com.hellohealth.domain.model.DailyHealthSnapshot>()
        override suspend fun getExerciseSessionDetail(sessionId: String, startTimeHint: java.time.Instant?, endTimeHint: java.time.Instant?) = null
        override suspend fun fetchWeeklyStats() = com.hellohealth.domain.model.WeeklyStats()
        override suspend fun hasPermissions() = false
        override suspend fun fetchLatestBodyMetrics() = com.hellohealth.domain.model.BodyMetrics()
        override fun observeTodayCaloriesOut(): Flow<Double> = flowOf(caloriesOut)
        override fun getRequiredPermissions() = emptySet<String>()
        override fun getAvailability() = 0
        override fun getSettingsIntent(context: android.content.Context) =
            android.content.Intent()
    }

    private class FakeProfile(private val profile: UserProfile?) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? = profile
        override suspend fun upsertProfile(profile: UserProfile) = Unit
        override suspend fun setDynamicTheme(enabled: Boolean) = Unit
        override fun observeDynamicTheme(): Flow<Boolean> = flowOf(true)
        override suspend fun setAiCoachingEnabled(enabled: Boolean) = Unit
        override fun observeAiCoachingEnabled(): Flow<Boolean> = flowOf(false)
    }

    /** A complete profile whose budget/macros are computable (drives the non-DASH path). */
    private fun fullProfile() = UserProfile(
        gender = Gender.MALE,
        birthDateEpochDay = LocalDate.now().minusYears(30).toEpochDay(),
        heightCm = 180.0,
        weightKg = 80.0,
        activityLevel = ActivityLevel.MODERATE,
        goalType = GoalType.LOSE,
        hasOnboarded = true,
    )

    private fun daySummary(calories: Double, protein: Double, carbs: Double, fat: Double, waterMl: Double) =
        NutritionDaySummary(
            localDate = today,
            caloriesConsumed = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fibreG = 0.0,
            waterMl = waterMl,
            entriesByMeal = emptyMap(),
        )

    @Test
    fun `net is caloriesOut minus caloriesIn as a deficit when burn exceeds intake`() = runTest {
        // Intake 1500, burn (active + BMR) 2600 → net +1100 deficit.
        val vm = NutritionViewModel(
            FakeNutrition(daySummary(1500.0, 100.0, 150.0, 50.0, waterMl = 500.0)),
            FakeActivity(caloriesOut = 2600.0),
            FakeProfile(fullProfile()),
        )
        vm.uiState.test {
            // WhileSubscribed replays the initial default first; take the first populated emission.
            var state = awaitItem()
            while (!state.hasData) state = awaitItem()

            assertEquals(1500, state.caloriesConsumed)
            assertTrue(state.isDeficit)
            assertEquals("1100 kcal deficit", state.netLabel)
            assertEquals(100, state.proteinG)
            assertEquals(150, state.carbsG)
            assertEquals(50, state.fatG)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `net flips to a surplus when intake exceeds burn`() = runTest {
        // Intake 3000, burn 2000 → net −1000 surplus.
        val vm = NutritionViewModel(
            FakeNutrition(daySummary(3000.0, 0.0, 0.0, 0.0, waterMl = 0.0)),
            FakeActivity(caloriesOut = 2000.0),
            FakeProfile(fullProfile()),
        )
        vm.uiState.test {
            var state = awaitItem()
            while (!state.hasData) state = awaitItem()

            assertFalse(state.isDeficit)
            assertEquals("1000 kcal surplus", state.netLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `budget and macro targets mirror the pure profile math`() = runTest {
        val profile = fullProfile()
        val expectedBudget = BodyEnergy.calorieBudget(profile)
        val expectedMacros = MacroTargets.split(expectedBudget, profile.goalType)

        val vm = NutritionViewModel(
            FakeNutrition(daySummary(1500.0, 0.0, 0.0, 0.0, waterMl = 0.0)),
            FakeActivity(caloriesOut = 2000.0),
            FakeProfile(profile),
        )
        vm.uiState.test {
            var state = awaitItem()
            while (state.calorieBudget == null) state = awaitItem()

            assertEquals(expectedBudget, state.calorieBudget)
            assertEquals(expectedMacros?.proteinG, state.proteinTargetG)
            assertEquals(expectedMacros?.carbsG, state.carbsTargetG)
            assertEquals(expectedMacros?.fatG, state.fatTargetG)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a sparse profile hides the budget and targets as DASH rather than zero`() = runTest {
        // No vitals → BodyEnergy.calorieBudget returns null → targets are null (hidden), not 0.
        val vm = NutritionViewModel(
            FakeNutrition(daySummary(1200.0, 20.0, 30.0, 10.0, waterMl = 250.0)),
            FakeActivity(caloriesOut = 1800.0),
            FakeProfile(UserProfile(hasOnboarded = true)),
        )
        vm.uiState.test {
            var state = awaitItem()
            // hasData is still true here (intake + burn present), so wait on that.
            while (!state.hasData) state = awaitItem()

            assertEquals(null, state.calorieBudget)
            assertEquals(null, state.proteinTargetG)
            assertEquals(NutritionUiState.DASH, state.budgetLabel)
            // Consumed grams still surface even with no target.
            assertEquals(20, state.proteinG)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an empty day with no signed-in profile collapses to the zero state`() = runTest {
        val vm = NutritionViewModel(
            FakeNutrition(daySummary(0.0, 0.0, 0.0, 0.0, waterMl = 0.0)),
            FakeActivity(caloriesOut = 0.0),
            FakeProfile(null),
        )
        vm.uiState.test {
            // Every source flow emits its empty/zero default, so the joined state is the zero state.
            // The first emission already carries it (initial default == joined all-zero result).
            val state = awaitItem()

            assertFalse(state.hasData)
            assertEquals(0, state.caloriesConsumed)
            assertEquals(null, state.calorieBudget)
            assertEquals(NutritionUiState.DASH, state.waterLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `water label formats millilitres and litres, dashing a dry day`() = runTest {
        val vm = NutritionViewModel(
            FakeNutrition(daySummary(500.0, 0.0, 0.0, 0.0, waterMl = 1500.0)),
            FakeActivity(caloriesOut = 100.0),
            FakeProfile(fullProfile()),
        )
        vm.uiState.test {
            var state = awaitItem()
            while (!state.hasData) state = awaitItem()
            assertEquals("1.5 L", state.waterLabel)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
