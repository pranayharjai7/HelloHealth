package com.hellohealth.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.health.VitalsBounds
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Daily Goals screen. The activity-ring targets ([goals]) come from
 * [GoalsRepository]; the goal-direction fields ([goalType]/[targetWeightKg]/[targetRateKgPerWeek])
 * and [unitPreference] are seeded once from the stored [UserProfile] (P0.5 Step 10c) and persisted
 * back onto it on save. [targetWeightError]/[targetRateError] mirror onboarding's validation.
 */
data class GoalsUiState(
    val goals: ActivityGoals = ActivityGoals(),
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

    // Save is blocked until the profile snapshot has loaded — otherwise the goal-direction fields
    // still hold their null defaults and saveGoals() would project those nulls over a real stored
    // goalType/targets (full-row upsert data loss). [profileLoaded] flips true only after a
    // SUCCESSFUL profile read.
    val canSave: Boolean
        get() = profileLoaded && !isSaving && targetWeightError == null && targetRateError == null
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalsRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    /**
     * The goal type as last loaded/saved. Save re-derives the active-calorie ring ONLY when the
     * current [GoalsUiState.goalType] differs from this — so switching Lose↔Gain updates the ring,
     * but a pure Active-Calories slider tweak is persisted as-is (locked decision).
     */
    private var persistedGoalType: GoalType? = null

    init {
        loadGoals()
        loadProfileGoalFields()
    }

    private fun loadGoals() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getActivityGoals().collectLatest { goals ->
                // Refresh the activity-ring targets from the store, but DON'T touch error/success:
                // this is a hot Room observable that re-emits on background sync writes, and clearing
                // a save-owned error here would silently erase a failure banner the user is looking at.
                _uiState.value = _uiState.value.copy(
                    goals = goals,
                    isLoading = false
                )
            }
        }
    }

    /**
     * One-shot snapshot of the goal-direction fields from the profile (not a Flow). [profileLoaded]
     * flips true ONLY on a successful read — until then Save is blocked (see [GoalsUiState.canSave]),
     * so a Save tapped before this resolves (or after a failed read) can't project the null defaults
     * over a real stored goalType/targets. A failure surfaces an error and leaves Save disabled.
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
                        error = e.message ?: "Couldn't load your goal settings.",
                        profileLoaded = false
                    )
                }
        }
    }

    fun updateStepsGoal(steps: Int) {
        _uiState.value = _uiState.value.copy(
            goals = _uiState.value.goals.copy(steps = steps)
        )
    }

    fun updateCaloriesGoal(calories: Int) {
        _uiState.value = _uiState.value.copy(
            goals = _uiState.value.goals.copy(activeCalories = calories)
        )
    }

    fun updateMinutesGoal(minutes: Int) {
        _uiState.value = _uiState.value.copy(
            goals = _uiState.value.goals.copy(activeMinutes = minutes)
        )
    }

    /**
     * Sets the goal direction. Switching to MAINTAIN clears any target weight/rate (they only make
     * sense for a directional goal) — mirrors the onboarding rule so a leftover out-of-range value
     * can't linger and block Save or corrupt the budget.
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

    /**
     * Persists the activity-ring targets AND the goal-direction fields. The profile write
     * load-then-`copy()`s (full-row upsert trap) so `hasOnboarded` and vitals survive. Active
     * calories is re-derived from the goal type ONLY if the type changed this session; otherwise the
     * user's manual slider value is persisted untouched (locked decision).
     */
    fun saveGoals() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.value = state.copy(isSaving = true, error = null, successMessage = null)
        viewModelScope.launch {
            try {
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
                val goalsToSave = if (goalTypeChanged) {
                    state.goals.copy(activeCalories = BodyEnergy.suggestedGoals(updatedProfile).activeCalories)
                } else {
                    state.goals
                }
                repository.updateActivityGoals(goalsToSave)
                persistedGoalType = state.goalType

                _uiState.value = _uiState.value.copy(
                    goals = goalsToSave,
                    isSaving = false,
                    successMessage = "Daily goals saved."
                )
            } catch (e: Exception) {
                AppLogger.w(FeatureTag.PROFILE, "saveGoals failed to persist", e)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save goals"
                )
            }
        }
    }
}
