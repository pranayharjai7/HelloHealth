package com.hellohealth.ui.nutrition.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.health.MicronutrientTargets.Micro
import com.hellohealth.domain.health.NutritionTargets
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.model.nutrition.MacroMode
import com.hellohealth.domain.model.nutrition.NutritionGoals
import com.hellohealth.domain.repository.NutritionGoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the Nutrition Settings screen. [goals] is the user's editable overrides; [derived] is the
 * profile-based auto targets shown as hints ("Auto: 2383 kcal") and used whenever a custom group is off.
 * The settings are AUTO by default with per-group custom toggles, so toggling custom off instantly
 * restores the derived number.
 */
data class NutritionSettingsUiState(
    val goals: NutritionGoals = NutritionGoals.DEFAULT,
    val derived: NutritionTargets.ResolvedTargets? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
) {
    val canSave: Boolean get() = !isSaving
}

@HiltViewModel
class NutritionSettingsViewModel @Inject constructor(
    private val repository: NutritionGoalsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NutritionSettingsUiState())
    val uiState: StateFlow<NutritionSettingsUiState> = _uiState.asStateFlow()

    private var profile: UserProfile? = null

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val p = runCatching { profileRepository.getProfile() }.getOrNull()
            profile = p
            val goals = runCatching { repository.getNutritionGoals() }.getOrDefault(NutritionGoals.DEFAULT)
            // Derived (all-auto) targets for the hint text, independent of the user's current toggles.
            val derived = NutritionTargets.resolve(p, NutritionGoals.DEFAULT)
            _uiState.value = _uiState.value.copy(goals = goals, derived = derived, isLoading = false)
        }
    }

    private fun mutate(block: (NutritionGoals) -> NutritionGoals) {
        _uiState.value = _uiState.value.copy(
            goals = block(_uiState.value.goals), successMessage = null,
        )
    }

    fun setUseCustomCalories(on: Boolean) = mutate { g ->
        // When turning custom ON, seed the field with the derived value so the user edits from a sane start.
        g.copy(useCustomCalories = on, calorieBudget = if (on) (g.calorieBudget ?: _uiState.value.derived?.calorieBudget) else g.calorieBudget)
    }
    fun setCalorieBudget(v: Int?) = mutate { it.copy(calorieBudget = v) }

    fun setUseCustomMacros(on: Boolean) = mutate { g ->
        if (on) {
            val d = _uiState.value.derived
            g.copy(
                useCustomMacros = true,
                proteinG = g.proteinG ?: d?.proteinG,
                carbsG = g.carbsG ?: d?.carbsG,
                fatG = g.fatG ?: d?.fatG,
                fiberG = g.fiberG ?: d?.fiberG,
            )
        } else g.copy(useCustomMacros = false)
    }
    fun setMacroMode(mode: MacroMode) = mutate { it.copy(macroMode = mode) }
    fun setProtein(v: Int?) = mutate { it.copy(proteinG = v) }
    fun setCarbs(v: Int?) = mutate { it.copy(carbsG = v) }
    fun setFat(v: Int?) = mutate { it.copy(fatG = v) }
    fun setFiber(v: Int?) = mutate { it.copy(fiberG = v) }

    /** Apply a protein target from a g/kg preset against the profile weight (helper for the macro section). */
    fun applyProteinPerKg(gPerKg: Double) {
        val weight = profile?.weightKg ?: return
        mutate { it.copy(useCustomMacros = true, proteinG = (weight * gPerKg).toInt()) }
    }

    fun toggleMicro(micro: Micro, tracked: Boolean) = mutate { g ->
        val set = g.trackedMicros.toMutableSet().apply { if (tracked) add(micro) else remove(micro) }
        g.copy(trackedMicros = set)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.value = state.copy(isSaving = true, error = null, successMessage = null)
        viewModelScope.launch {
            try {
                repository.updateNutritionGoals(state.goals)
                _uiState.value = _uiState.value.copy(isSaving = false, successMessage = "Nutrition settings saved.")
            } catch (e: Exception) {
                AppLogger.w(FeatureTag.NUTRITION, "save nutrition settings failed", e)
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message ?: "Failed to save")
            }
        }
    }
}
