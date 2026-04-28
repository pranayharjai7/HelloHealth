package com.hellohealth.ui.activitysettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.repository.ActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivitySettingsUiState(
    val healthConnectAvailability: Int = 0,
    val hasPermissions: Boolean = false,
    val lastSyncTime: Long? = null,
    val isSyncing: Boolean = false
)

@HiltViewModel
class ActivitySettingsViewModel @Inject constructor(
    private val activityRepository: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivitySettingsUiState())
    val uiState: StateFlow<ActivitySettingsUiState> = _uiState.asStateFlow()

    init {
        checkStatus()
    }

    fun checkStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                healthConnectAvailability = activityRepository.getAvailability(),
                hasPermissions = activityRepository.hasPermissions()
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            activityRepository.fetchSummary()
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }

    fun openHealthConnectSettings(context: Context) {
        val intent = activityRepository.getSettingsIntent(context)
        context.startActivity(intent)
    }
}
