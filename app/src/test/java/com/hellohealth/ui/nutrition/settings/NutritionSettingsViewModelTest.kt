package com.hellohealth.ui.nutrition.settings

import com.hellohealth.domain.health.MicronutrientTargets.Micro
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.model.nutrition.MacroMode
import com.hellohealth.domain.model.nutrition.NutritionGoals
import com.hellohealth.domain.repository.NutritionGoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeGoals(var stored: NutritionGoals = NutritionGoals.DEFAULT) : NutritionGoalsRepository {
        var saved: NutritionGoals? = null
        override fun observeNutritionGoals(): Flow<NutritionGoals> = flowOf(stored)
        override suspend fun getNutritionGoals() = stored
        override suspend fun updateNutritionGoals(goals: NutritionGoals) { saved = goals; stored = goals }
    }

    private class FakeProfile(private val p: UserProfile?) : ProfileRepository {
        override suspend fun getProfile() = p
        override suspend fun upsertProfile(profile: UserProfile) = Unit
        override suspend fun setDynamicTheme(enabled: Boolean) = Unit
        override fun observeDynamicTheme() = flowOf(true)
        override suspend fun setAiCoachingEnabled(enabled: Boolean) = Unit
        override fun observeAiCoachingEnabled() = flowOf(false)
    }

    private fun profile() = UserProfile(
        gender = com.hellohealth.domain.model.Gender.MALE,
        birthDateEpochDay = java.time.LocalDate.of(1996, 1, 1).toEpochDay(),
        heightCm = 180.0, weightKg = 80.0,
        activityLevel = com.hellohealth.domain.model.ActivityLevel.MODERATE,
        goalType = com.hellohealth.domain.model.GoalType.MAINTAIN,
    )

    @Test
    fun `loads derived hints and stored goals`() = runTest(dispatcher) {
        val vm = NutritionSettingsViewModel(FakeGoals(), FakeProfile(profile()))
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertEquals(2759, s.derived?.calorieBudget) // derivation available as a hint
        assertFalse(s.goals.useCustomCalories)        // auto by default
    }

    @Test
    fun `turning on custom calories seeds from the derived value`() = runTest(dispatcher) {
        val vm = NutritionSettingsViewModel(FakeGoals(), FakeProfile(profile()))
        advanceUntilIdle()
        vm.setUseCustomCalories(true)
        assertTrue(vm.uiState.value.goals.useCustomCalories)
        assertEquals("seeded from derived", 2759, vm.uiState.value.goals.calorieBudget)
    }

    @Test
    fun `protein per kg preset computes from profile weight`() = runTest(dispatcher) {
        val vm = NutritionSettingsViewModel(FakeGoals(), FakeProfile(profile()))
        advanceUntilIdle()
        vm.applyProteinPerKg(2.0) // 80kg * 2.0 = 160g
        assertTrue(vm.uiState.value.goals.useCustomMacros)
        assertEquals(160, vm.uiState.value.goals.proteinG)
    }

    @Test
    fun `toggling a micro off removes it from the tracked set`() = runTest(dispatcher) {
        val vm = NutritionSettingsViewModel(FakeGoals(), FakeProfile(profile()))
        advanceUntilIdle()
        vm.toggleMicro(Micro.SODIUM, false)
        assertFalse(Micro.SODIUM in vm.uiState.value.goals.trackedMicros)
        assertTrue(Micro.IRON in vm.uiState.value.goals.trackedMicros) // others untouched
    }

    @Test
    fun `save persists the edited goals exactly`() = runTest(dispatcher) {
        val repo = FakeGoals()
        val vm = NutritionSettingsViewModel(repo, FakeProfile(profile()))
        advanceUntilIdle()
        vm.setUseCustomCalories(true); vm.setCalorieBudget(2100)
        vm.setMacroMode(MacroMode.PERCENT)
        vm.save()
        advanceUntilIdle()
        assertEquals(2100, repo.saved?.calorieBudget)
        assertEquals(MacroMode.PERCENT, repo.saved?.macroMode)
        assertEquals("Nutrition settings saved.", vm.uiState.value.successMessage)
    }
}
