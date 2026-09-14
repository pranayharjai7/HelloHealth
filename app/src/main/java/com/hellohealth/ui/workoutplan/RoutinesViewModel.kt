package com.hellohealth.ui.workoutplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.WorkoutPlan
import com.hellohealth.domain.repository.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the routines list. [plans] is the live list (newest first); [activePlanId] marks which
 * one the dashboard hero card reflects. The create-plan dialog is pure UI state ([showCreateDialog]),
 * so a config change re-renders it without re-triggering a write.
 */
data class RoutinesUiState(
    val plans: List<WorkoutPlan> = emptyList(),
    val activePlanId: String? = null,
    val showCreateDialog: Boolean = false,
)

/**
 * Backs [RoutinesScreen]. Reads the plan list + active plan reactively from [WorkoutPlanRepository]
 * (both emit empty/null when signed out — no special-casing here). Writes (create / set-active /
 * delete) delegate to the repository, which stamps the LWW clock and requests a sync; this VM never
 * touches timestamps or sync directly.
 */
@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository,
) : ViewModel() {

    private val _showCreateDialog = MutableStateFlow(false)

    val uiState: StateFlow<RoutinesUiState> = combine(
        repository.observePlans(),
        repository.observeActivePlan(),
        _showCreateDialog,
    ) { plans, activePlan, showDialog ->
        RoutinesUiState(
            plans = plans,
            activePlanId = activePlan?.id,
            showCreateDialog = showDialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutinesUiState(),
    )

    fun showCreateDialog() { _showCreateDialog.value = true }

    fun dismissCreateDialog() { _showCreateDialog.value = false }

    /** Create a routine and, being the intended entry action, make it the active plan. */
    fun createPlan(name: String, planType: PlanType) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.createPlan(name = trimmed, planType = planType, makeActive = true)
            _showCreateDialog.value = false
        }
    }

    fun setActive(planId: String) {
        viewModelScope.launch { repository.setActivePlan(planId) }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch { repository.deletePlan(planId) }
    }
}
