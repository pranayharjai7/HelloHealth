package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.data.health.HealthConnectManager
import com.hellohealth.domain.model.WorkoutSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val workoutSummary: WorkoutSummary = WorkoutSummary(),
    val hasHealthPermissions: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        checkPermissionsAndLoadData()
    }

    fun checkPermissionsAndLoadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val hasPermissions = healthConnectManager.hasAllPermissions()
            _uiState.value = _uiState.value.copy(hasHealthPermissions = hasPermissions)
            
            if (hasPermissions) {
                loadWorkoutSummary()
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun loadWorkoutSummary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val summary = healthConnectManager.fetchWorkoutSummary()
                _uiState.value = _uiState.value.copy(
                    workoutSummary = summary,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to fetch workout data"
                )
            }
        }
    }

    fun getHealthPermissions(): Set<String> {
        return healthConnectManager.permissions
    }
}
