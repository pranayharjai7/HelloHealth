package com.hellohealth.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.WorkoutSummary
import com.hellohealth.domain.model.User
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val workoutSummary: WorkoutSummary = WorkoutSummary(),
    val hasHealthPermissions: Boolean = false,
    val healthConnectAvailability: Int = 1, // Default to UNAVAILABLE
    val user: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastSyncTime: Long? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
        checkPermissionsAndLoadData()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(user = user)
        }
    }

    fun checkPermissionsAndLoadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val availability = activityRepository.getAvailability()
            Log.d("DashboardViewModel", "Availability check: $availability (Available is ${HealthConnectClient.SDK_AVAILABLE})")
            _uiState.value = _uiState.value.copy(healthConnectAvailability = availability)
            
            if (availability == HealthConnectClient.SDK_AVAILABLE) {
                // Check if we have at least the core permissions
                val hasPermissions = activityRepository.hasPermissions()
                Log.d("DashboardViewModel", "Has permissions: $hasPermissions")
                _uiState.value = _uiState.value.copy(hasHealthPermissions = hasPermissions)
                
                // Try to load whatever data we have access to
                loadWorkoutSummary()
            } else {
                Log.d("DashboardViewModel", "Availability NOT 0, skipping data load")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun loadWorkoutSummary() {
        viewModelScope.launch {
            try {
                val summary = activityRepository.fetchSummary()
                _uiState.value = _uiState.value.copy(
                    workoutSummary = summary,
                    isLoading = false,
                    lastSyncTime = System.currentTimeMillis(),
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load activity data"
                )
            }
        }
    }

    fun getHealthPermissions(): Set<String> {
        return activityRepository.getRequiredPermissions()
    }

    fun openHealthConnectSettings(context: Context) {
        try {
            val intent = activityRepository.getSettingsIntent(context)
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
