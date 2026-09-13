package com.hellohealth.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.health.VitalsBounds
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Preferences screen, which owns TWO concerns on one screen:
 *
 *  - **Weight preferences** — the goal-direction fields ([goalType]/[targetWeightKg]/
 *    [targetRateKgPerWeek]) that live on the stored [UserProfile]. Seeded once from the profile and
 *    persisted back on save (this screen is now their sole owner — the Daily Goals screen no longer
 *    touches them). [unitPreference] governs the target-weight field's units.
 *  - **Food preferences** — [preferences] (diet/allergies/cuisines), backed by [UserRepository].
 *
 * [targetWeightError]/[targetRateError] mirror onboarding's validation.
 */
data class PreferencesUiState(
    val preferences: FoodPreferences = FoodPreferences(),
    val goalType: GoalType? = null,
    val targetWeightKg: Double? = null,
    val targetRateKgPerWeek: Double? = null,
    val unitPreference: UnitPreference = UnitPreference.METRIC,
    val profileLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
) {
    /** Out-of-range target weight (only meaningful for a directional goal); null == valid/absent. */
    val targetWeightError: String?
        get() {
            val t = targetWeightKg ?: return null
            return if (t in VitalsBounds.MIN_WEIGHT_KG..VitalsBounds.MAX_WEIGHT_KG) null
            else "Enter a realistic target weight"
        }

    /** Out-of-range weekly rate; null == valid/absent. */
    val targetRateError: String?
        get() {
            val r = targetRateKgPerWeek ?: return null
            return if (r in VitalsBounds.MIN_RATE_KG_PER_WEEK..VitalsBounds.MAX_RATE_KG_PER_WEEK) null
            else "Rate must be ${VitalsBounds.MIN_RATE_KG_PER_WEEK}–${VitalsBounds.MAX_RATE_KG_PER_WEEK} kg/week"
        }

    // Save is blocked until the profile snapshot has loaded — otherwise the weight-goal fields still
    // hold their null defaults and savePreferences() would project those nulls over a real stored
    // goalType/targets (full-row upsert data loss). [profileLoaded] flips true only after a SUCCESSFUL
    // profile read. (Food-prefs alone would be safe to save, but the two saves are coupled on one
    // button, so we gate the whole thing on the stricter condition.)
    val canSave: Boolean
        get() = profileLoaded && !isSaving && targetWeightError == null && targetRateError == null
}

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val repository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val goalsRepository: GoalsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreferencesUiState())
    val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

    /**
     * The goal type as last loaded/saved. Save re-derives the active-calorie ring ONLY when the
     * current [PreferencesUiState.goalType] differs from this — so switching Lose↔Gain updates the
     * ring, but saving with an unchanged goal type leaves the user's manual slider value alone
     * (mirrors the locked Daily-Goals decision).
     */
    private var persistedGoalType: GoalType? = null

    init {
        loadPreferences()
        loadProfileGoalFields()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getFoodPreferences().collectLatest { prefs ->
                // Hot Room observable: refresh the food prefs but DON'T touch error/success (a
                // background sync re-emit must not wipe a save-owned banner the user is reading).
                _uiState.value = _uiState.value.copy(
                    preferences = prefs,
                    isLoading = false
                )
            }
        }
    }

    /**
     * One-shot snapshot of the goal-direction fields from the profile (not a Flow). [profileLoaded]
     * flips true ONLY on a successful read — until then Save is blocked (see
     * [PreferencesUiState.canSave]), so a Save tapped before this resolves (or after a failed read)
     * can't project the null defaults over a real stored goalType/targets.
     */
    private fun loadProfileGoalFields() {
        viewModelScope.launch {
            runCatching { profileRepository.getProfile() }
                .onSuccess { profile ->
                    persistedGoalType = profile?.goalType
                    _uiState.value = _uiState.value.copy(
                        goalType = profile?.goalType,
                        targetWeightKg = profile?.targetWeightKg,
                        targetRateKgPerWeek = profile?.targetRateKgPerWeek,
                        unitPreference = profile?.unitPreference ?: UnitPreference.METRIC,
                        profileLoaded = true
                    )
                }
                .onFailure { e ->
                    AppLogger.w(FeatureTag.PROFILE, "loadProfileGoalFields failed", e)
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Couldn't load your weight preferences.",
                        profileLoaded = false
                    )
                }
        }
    }

    // --- Weight preferences (goal direction) ---

    /**
     * Sets the goal direction. Switching to MAINTAIN clears any target weight/rate (they only make
     * sense for a directional goal) — mirrors onboarding so a leftover out-of-range value can't
     * linger and block Save or corrupt the budget.
     */
    fun updateGoalType(goalType: GoalType) {
        _uiState.value = if (goalType == GoalType.MAINTAIN) {
            _uiState.value.copy(goalType = goalType, targetWeightKg = null, targetRateKgPerWeek = null)
        } else {
            _uiState.value.copy(goalType = goalType)
        }
    }

    /** Stores target weight in metric (kg); null clears it. The UI converts imperial lb first. */
    fun updateTargetWeightKg(weightKg: Double?) {
        _uiState.value = _uiState.value.copy(targetWeightKg = weightKg)
    }

    /** Stores the weekly weight-change target (kg/week, always metric); null clears it. */
    fun updateTargetRateKgPerWeek(rate: Double?) {
        _uiState.value = _uiState.value.copy(targetRateKgPerWeek = rate)
    }

    // --- Food preferences ---

    fun updateDietType(dietType: String) {
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(dietType = dietType)
        )
    }

    fun toggleAllergy(allergy: String) {
        val currentAllergies = _uiState.value.preferences.allergies.toMutableList()
        if (currentAllergies.contains(allergy)) {
            currentAllergies.remove(allergy)
        } else {
            currentAllergies.add(allergy)
        }
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(allergies = currentAllergies)
        )
    }

    fun toggleCuisine(cuisine: String) {
        val currentCuisines = _uiState.value.preferences.cuisinePreferences.toMutableList()
        if (currentCuisines.contains(cuisine)) {
            currentCuisines.remove(cuisine)
        } else {
            currentCuisines.add(cuisine)
        }
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(cuisinePreferences = currentCuisines)
        )
    }

    /**
     * Persists BOTH cards in one action: the food preferences AND the goal-direction fields on the
     * profile. The profile write load-then-`copy()`s (full-row upsert trap) so `hasOnboarded` and
     * vitals survive. Active calories is re-derived from the goal type ONLY if the type changed this
     * session; otherwise the user's manual slider value is kept (locked decision, mirrors Goals).
     */
    fun savePreferences() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.value = state.copy(isSaving = true, error = null, successMessage = null)
        viewModelScope.launch {
            try {
                repository.updateFoodPreferences(state.preferences)

                val existing = profileRepository.getProfile() ?: UserProfile()
                val updatedProfile = existing.copy(
                    goalType = state.goalType,
                    targetWeightKg = state.targetWeightKg,
                    targetRateKgPerWeek = state.targetRateKgPerWeek,
                    hasOnboarded = existing.hasOnboarded
                )
                profileRepository.upsertProfile(updatedProfile)

                // Re-derive active-calories ONLY when the goal type changed; else keep the slider.
                val goalTypeChanged = state.goalType != persistedGoalType
                if (goalTypeChanged) {
                    val currentGoals = goalsRepository.getCurrentActivityGoals()
                    goalsRepository.updateActivityGoals(
                        currentGoals.copy(activeCalories = BodyEnergy.suggestedGoals(updatedProfile).activeCalories)
                    )
                }
                persistedGoalType = state.goalType

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "Preferences saved."
                )
            } catch (e: Exception) {
                AppLogger.w(FeatureTag.PROFILE, "savePreferences failed to persist", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save preferences"
                )
            }
        }
    }
}
