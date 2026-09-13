package com.hellohealth.ui.activitysettings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivitySettingsUiState(
    val healthConnectAvailability: Int = 0,
    val hasPermissions: Boolean = false,
    val lastSyncTime: Long? = null,
    val isSyncing: Boolean = false,
    val isDynamicTheme: Boolean = true
)

@HiltViewModel
class ActivitySettingsViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val goalsRepository: GoalsRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivitySettingsUiState())
    val uiState: StateFlow<ActivitySettingsUiState> = _uiState.asStateFlow()

    init {
        checkStatus()
        observeDynamicTheme()
    }

    /**
     * Reactively mirrors the stored mood-tint flag into UI state, so the switch reflects the persisted
     * value (and any change synced from another device) rather than a local optimistic guess. This is
     * the same source the theme layer reads, so the switch and the app tint can't disagree.
     */
    private fun observeDynamicTheme() {
        viewModelScope.launch {
            profileRepository.observeDynamicTheme().collectLatest { enabled ->
                _uiState.update { it.copy(isDynamicTheme = enabled) }
            }
        }
    }

    /**
     * Persists the mood-tint toggle immediately (no Save button) — the repository's single-owner
     * load-then-copy preserves every other profile field, and [observeDynamicTheme] echoes the new
     * value back into state, so the app re-tints live.
     */
    fun setDynamicTheme(enabled: Boolean) {
        viewModelScope.launch {
            profileRepository.setDynamicTheme(enabled)
        }
    }


    fun checkStatus() {
        viewModelScope.launch {
            // Atomic update: getAvailability()/hasPermissions() suspend, and observeDynamicTheme may
            // write isDynamicTheme while we're suspended. update{} re-reads the latest state at apply
            // time, so a concurrent theme-flag write is never clobbered by a stale-snapshot copy.
            val availability = activityRepository.getAvailability()
            val hasPermissions = activityRepository.hasPermissions()
            _uiState.update {
                it.copy(healthConnectAvailability = availability, hasPermissions = hasPermissions)
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val goals = goalsRepository.getCurrentActivityGoals()
            activityRepository.fetchSummary(goals)
            _uiState.update {
                it.copy(isSyncing = false, lastSyncTime = System.currentTimeMillis())
            }
        }
    }

    fun openHealthConnectSettings(context: Context) {
        val intent = activityRepository.getSettingsIntent(context)
        context.startActivity(intent)
    }
}
