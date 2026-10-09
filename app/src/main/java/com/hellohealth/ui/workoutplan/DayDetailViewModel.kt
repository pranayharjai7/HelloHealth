package com.hellohealth.ui.workoutplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.PlannedExerciseWithDetails
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
 * UI state for one day's planned exercises. [dayName] labels the screen; [exercises] is the live,
 * ordered list joined with catalog details (an entry with a null [PlannedExerciseWithDetails.exercise]
 * still renders with an "unknown exercise" fallback rather than vanishing).
 */
data class DayDetailUiState(
    val dayName: String = "",
    val exercises: List<PlannedExerciseWithDetails> = emptyList(),
)

/**
 * Backs [DayDetailScreen]. Resolves `dayId` from [SavedStateHandle], loads the day row once for its
 * name (via a one-shot [MutableStateFlow] refreshed in [init]), and observes the day's planned
 * exercises reactively. Reorder is expressed as move-up/move-down over the current order and pushed
 * as a full ordered-id list; delete removes a single planned row (soft tombstone in the repository).
 */
@HiltViewModel
class DayDetailViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val dayId: String = savedStateHandle.get<String>(Screen.DayDetail.dayIdArg).orEmpty()

    private val _dayName = MutableStateFlow("")

    val uiState: StateFlow<DayDetailUiState> = combine(
        _dayName,
        repository.observePlannedExercises(dayId),
    ) { name, exercises ->
        DayDetailUiState(dayName = name, exercises = exercises)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DayDetailUiState(),
    )

    init {
        refreshDayName()
    }

    /** Reload the day's display name (also picks up a rename made elsewhere on re-entry). */
    private fun refreshDayName() {
        viewModelScope.launch {
            _dayName.value = repository.getDay(dayId)?.name.orEmpty()
        }
    }

    /**
     * Persist a new full ordering of the day's planned exercises (index = position). Driven by the
     * drag-to-reorder list; the repository skips rows already at the right index so a no-op drag
     * doesn't churn sync state.
     */
    fun reorder(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        viewModelScope.launch { repository.reorderExercises(dayId, orderedIds) }
    }

    fun deleteExercise(plannedId: String) {
        viewModelScope.launch { repository.deleteExercise(plannedId) }
    }
}
