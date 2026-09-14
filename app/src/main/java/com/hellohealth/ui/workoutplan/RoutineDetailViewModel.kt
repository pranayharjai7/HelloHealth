package com.hellohealth.ui.workoutplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.PlanType
import com.hellohealth.domain.model.WorkoutDay
import com.hellohealth.domain.model.WorkoutPlan
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A slot the user can add a day into, with its human label. [key] is the opaque, stable slot key
 * stored on [WorkoutDay.slotKey]; [label] is derived for display (e.g. `MONDAY` → "Monday",
 * `D01` → "Day 1", `C01` → "Custom 1").
 */
data class SlotOption(val key: String, val label: String)

/**
 * UI state for a single routine's days. [planName]/[planType] come from the plan row; [days] is its
 * live day list in slot order. [availableSlots] are the plan-type slot keys not yet occupied — the
 * add-day picker's options; when empty, every slot is filled. The add-day dialog is pure UI state.
 */
data class RoutineDetailUiState(
    val planName: String = "",
    val planType: PlanType = PlanType.WEEKLY,
    val days: List<WorkoutDay> = emptyList(),
    val availableSlots: List<SlotOption> = emptyList(),
    val showAddDayDialog: Boolean = false,
    val planMissing: Boolean = false,
)

/**
 * Backs [RoutineDetailScreen]. Resolves `planId` from [SavedStateHandle] (ActivityDetail template),
 * observes the plan + its days, and computes the still-free slots for the add-day picker. Writes
 * (add / rename / delete day) delegate to [WorkoutPlanRepository]; deleting a day cascades to its
 * planned exercises inside the repository transaction.
 */
@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val planId: String = savedStateHandle.get<String>(Screen.RoutineDetail.planIdArg).orEmpty()

    private val _showAddDayDialog = MutableStateFlow(false)

    val uiState: StateFlow<RoutineDetailUiState> = combine(
        repository.observePlans(),
        repository.observeDays(planId),
        _showAddDayDialog,
    ) { plans, days, showDialog ->
        val plan: WorkoutPlan? = plans.firstOrNull { it.id == planId }
        val planType = plan?.planType ?: PlanType.WEEKLY
        val occupied = days.map { it.slotKey }.toSet()
        val available = PlanType.slotKeysFor(planType)
            .filter { it !in occupied }
            .map { SlotOption(key = it, label = slotLabel(planType, it)) }
        RoutineDetailUiState(
            planName = plan?.name.orEmpty(),
            planType = planType,
            days = days,
            availableSlots = available,
            showAddDayDialog = showDialog,
            // Only "missing" once the plan list has loaded and this id isn't in it (deleted).
            planMissing = plan == null && plans.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutineDetailUiState(),
    )

    fun showAddDayDialog() { _showAddDayDialog.value = true }

    fun dismissAddDayDialog() { _showAddDayDialog.value = false }

    /** Add a day at [slotKey]. [name] defaults to the slot label when the user leaves it blank. */
    fun addDay(slotKey: String, name: String) {
        val label = name.trim().ifEmpty { slotLabel(uiState.value.planType, slotKey) }
        viewModelScope.launch {
            repository.addDay(planId = planId, slotKey = slotKey, name = label)
            _showAddDayDialog.value = false
        }
    }

    fun renameDay(dayId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameDay(dayId, trimmed) }
    }

    fun deleteDay(dayId: String) {
        viewModelScope.launch { repository.deleteDay(dayId) }
    }

    companion object {
        /** Map an opaque slot key to a human label based on its plan type. Null-tolerant. */
        fun slotLabel(planType: PlanType, slotKey: String): String = when (planType) {
            PlanType.WEEKLY -> slotKey.lowercase().replaceFirstChar { it.uppercase() }
            PlanType.MONTHLY -> "Day " + (slotKey.removePrefix("D").toIntOrNull() ?: slotKey)
            PlanType.CUSTOM -> "Custom " + (slotKey.removePrefix("C").toIntOrNull() ?: slotKey)
        }
    }
}
