package com.hellohealth.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.repository.GoalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalsUiState(
    val goals: ActivityGoals = ActivityGoals(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

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

    fun saveGoals() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repository.updateActivityGoals(_uiState.value.goals)
                _uiState.value = _uiState.value.copy(isSaving = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save goals"
                )
            }
        }
    }
}
