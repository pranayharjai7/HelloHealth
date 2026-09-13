package com.hellohealth.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.repository.GoalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Daily Goals screen — the activity-ring targets ([goals]) only. The goal-direction
 * fields (goal type / target weight / weekly rate) moved to the Preferences screen (they own the
 * profile's goalType/targets and the calorie-ring re-derivation); this screen persists ONLY the
 * three ring sliders.
 */
data class GoalsUiState(
    val goals: ActivityGoals = ActivityGoals(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
) {
    /** The ring sliders are bounded by their own ranges, so Save is only gated on an in-flight save. */
    val canSave: Boolean
        get() = !isSaving
}

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    init {
        loadGoals()
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
     * Persists the three activity-ring targets as edited. Active-calories is stored exactly as the
     * slider shows it — the goal-type-driven re-derivation lives on the Preferences screen now, so
     * this screen never overwrites the calorie ring from a goal type.
     */
    fun saveGoals() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.value = state.copy(isSaving = true, error = null, successMessage = null)
        viewModelScope.launch {
            try {
                repository.updateActivityGoals(state.goals)
                _uiState.value = _uiState.value.copy(
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
