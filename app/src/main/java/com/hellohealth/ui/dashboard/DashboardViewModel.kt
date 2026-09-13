package com.hellohealth.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.User
import com.hellohealth.domain.model.WorkoutSession
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class DashboardUiState(
    val healthSummary: HealthSummary = HealthSummary(),
    val hasHealthPermissions: Boolean = false,
    val healthConnectAvailability: Int = 1, // Default to UNAVAILABLE
    val user: User? = null,
    val isLoading: Boolean = false,
    val isCalendarLoading: Boolean = false,
    val error: String? = null,
    val lastSyncTime: Long? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val monthSnapshots: Map<LocalDate, DailyHealthSnapshot> = emptyMap()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
    private val goalsRepository: GoalsRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * Manually-logged workouts (Phase A). Kept as an independent stream — NOT folded into the
     * Health Connect [HealthSummary] — so the separate non-clickable section on the Activity surface
     * reads it directly and the read-only HC path is untouched. Room is the source of truth; the Flow
     * is already tombstone-filtered and newest-first.
     */
    val workouts: StateFlow<List<WorkoutSession>> =
        workoutRepository.observeWorkouts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Soft-deletes a manual workout (tombstone + resync). HC-origin sessions never route here. */
    fun deleteWorkout(id: String) {
        viewModelScope.launch {
            workoutRepository.delete(id)
        }
    }

    init {
        loadUserInfo()
        checkPermissionsAndLoadData()
        observeGoals()
    }

    private fun observeGoals() {
        viewModelScope.launch {
            goalsRepository.getActivityGoals().collectLatest { goals ->
                _uiState.update { state ->
                    state.copy(
                        healthSummary = state.healthSummary.copy(
                            stepsGoal = goals.steps.toLong(),
                            caloriesGoal = goals.activeCalories.toDouble(),
                            activeTimeGoal = goals.activeMinutes.toLong()
                        )
                    )
                }
            }
        }
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            val user = authRepository.getCurrentUser()
            _uiState.value = _uiState.value.copy(user = user)
        }
    }

    fun checkPermissionsAndLoadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val hasPermissions = refreshPermissionStateInternal()

            loadMonthSnapshots()
            loadHealthSummary(forceRefresh = hasPermissions && _uiState.value.selectedDate == LocalDate.now())
        }
    }

    /**
     * Re-checks Health Connect availability + granted permissions and, if the connection status
     * changed (e.g. the user just granted permissions in the Health Connect UI and returned to the
     * app), reloads the selected date so the rings appear immediately without an app restart.
     *
     * Safe to call on every ON_RESUME and after the permission dialog returns.
     */
    fun refreshPermissionState() {
        viewModelScope.launch {
            val wasConnected = _uiState.value.hasHealthPermissions
            val nowConnected = refreshPermissionStateInternal()
            // Only trigger a reload when the connection status actually changed, to avoid
            // hammering Health Connect on every resume.
            if (nowConnected != wasConnected) {
                loadMonthSnapshots(silent = true)
                loadHealthSummary(
                    forceRefresh = nowConnected && _uiState.value.selectedDate == LocalDate.now()
                )
            }
        }
    }

    /** Updates availability + permission flags in state and returns the current connected status. */
    private suspend fun refreshPermissionStateInternal(): Boolean {
        val availability = activityRepository.getAvailability()
        Log.d("DashboardViewModel", "Availability check: $availability (Available is ${HealthConnectClient.SDK_AVAILABLE})")

        var hasPermissions = false
        if (availability == HealthConnectClient.SDK_AVAILABLE) {
            hasPermissions = activityRepository.hasPermissions()
            Log.d("DashboardViewModel", "Has permissions: $hasPermissions")
        }

        _uiState.update {
            it.copy(
                healthConnectAvailability = availability,
                hasHealthPermissions = hasPermissions,
                isLoading = false
            )
        }
        return hasPermissions
    }

    fun loadHealthSummary(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val selectedDate = _uiState.value.selectedDate
                val goals = loadGoals()
                val summary = activityRepository.fetchSummary(
                    goals = goals,
                    date = selectedDate,
                    forceRefresh = forceRefresh
                )
                _uiState.update {
                    it.copy(
                        healthSummary = summary,
                        isLoading = false,
                        lastSyncTime = summary.lastUpdated.takeIf { timestamp -> timestamp > 0L },
                        error = null
                    )
                }
                loadMonthSnapshots(silent = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load health data"
                    )
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        _uiState.update { it.copy(selectedDate = date) }
        loadHealthSummary(forceRefresh = _uiState.value.hasHealthPermissions && date == LocalDate.now())
    }

    fun changeMonth(monthOffset: Long) {
        _uiState.update { it.copy(visibleMonth = it.visibleMonth.plusMonths(monthOffset)) }
        loadMonthSnapshots()
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        val monthChanged = _uiState.value.visibleMonth != YearMonth.from(today)
        _uiState.update {
            it.copy(
                selectedDate = today,
                visibleMonth = YearMonth.from(today)
            )
        }
        if (monthChanged) {
            loadMonthSnapshots()
        }
        loadHealthSummary(forceRefresh = _uiState.value.hasHealthPermissions)
    }

    private fun loadMonthSnapshots(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isCalendarLoading = true) }
            }
            val month = _uiState.value.visibleMonth
            val snapshots = activityRepository.getHistoryForMonth(month).associateBy { it.date }
            _uiState.update {
                it.copy(
                    monthSnapshots = snapshots,
                    isCalendarLoading = false
                )
            }
        }
    }

    private suspend fun loadGoals(): ActivityGoals {
        return runCatching { goalsRepository.getCurrentActivityGoals() }
            .getOrDefault(ActivityGoals())
    }

    fun refreshSelectedDate() {
        loadHealthSummary(forceRefresh = true)
    }

    fun hasSnapshotForSelectedDate(): Boolean {
        return _uiState.value.monthSnapshots.containsKey(_uiState.value.selectedDate)
    }

    fun canOpenDetails(): Boolean {
        return _uiState.value.hasHealthPermissions || _uiState.value.lastSyncTime != null
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
