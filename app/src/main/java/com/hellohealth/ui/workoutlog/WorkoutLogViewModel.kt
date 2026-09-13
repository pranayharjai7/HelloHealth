package com.hellohealth.ui.workoutlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.domain.model.WorkoutActivityType
import com.hellohealth.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutLogUiState(
    val activityType: WorkoutActivityType = WorkoutActivityType.RUN,
    val title: String = "",
    val durationText: String = "",
    val caloriesText: String = "",
    val distanceText: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedOk: Boolean = false
) {
    /** Save is enabled once a positive duration is entered — the one required field. */
    val canSave: Boolean get() = durationText.trim().toLongOrNull()?.let { it > 0 } == true && !isSaving
}

/**
 * Backs the manual workout-logging screen. Save writes through [WorkoutRepository] on
 * [viewModelScope]: the Save tap completes (and sets [WorkoutLogUiState.savedOk]) before the screen
 * navigates back, so a process-lifetime scope is unnecessary in Phase A (unlike the delete-on-leave
 * path in MoodTimeline).
 *
 * Duration is the only required input; calories/distance/note/title are optional. Numeric fields are
 * parsed defensively (blank/garbage → null) so a bad keystroke never crashes the save.
 */
@HiltViewModel
class WorkoutLogViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutLogUiState())
    val uiState: StateFlow<WorkoutLogUiState> = _uiState.asStateFlow()

    fun selectActivityType(type: WorkoutActivityType) {
        _uiState.update { it.copy(activityType = type, error = null) }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateDuration(value: String) = _uiState.update { it.copy(durationText = value.filter { c -> c.isDigit() }, error = null) }
    fun updateCalories(value: String) = _uiState.update { it.copy(caloriesText = value) }
    fun updateDistance(value: String) = _uiState.update { it.copy(distanceText = value) }
    fun updateNote(value: String) = _uiState.update { it.copy(note = value) }

    fun save() {
        val state = _uiState.value
        val durationMinutes = state.durationText.trim().toLongOrNull()
        if (durationMinutes == null || durationMinutes <= 0) {
            _uiState.update { it.copy(error = "Enter a duration in minutes.") }
            return
        }

        // Anchor the session to now, extending back by its duration so the start/end range is sane
        // even though Phase A doesn't ask the user to pick exact wall-clock times.
        val endMs = Timestamps.nowEpochMs()
        val startMs = endMs - durationMinutes * 60_000L
        val calories = state.caloriesText.replace(',', '.').trim().toDoubleOrNull()
        val distanceKm = state.distanceText.replace(',', '.').trim().toDoubleOrNull()
        val title = state.title.trim().ifBlank { null }
        val note = state.note.trim().ifBlank { null }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val id = workoutRepository.saveWorkout(
                    activityType = state.activityType,
                    title = title,
                    startTimeUtcEpochMs = startMs,
                    endTimeUtcEpochMs = endMs,
                    durationMinutes = durationMinutes,
                    calories = calories,
                    distanceKm = distanceKm,
                    note = note
                )
                if (id == null) {
                    _uiState.update { it.copy(isSaving = false, error = "You're not signed in.") }
                } else {
                    _uiState.update { it.copy(isSaving = false, savedOk = true) }
                }
            } catch (e: Exception) {
                AppLogger.w(FeatureTag.WORKOUT, "failed to save workout", e)
                _uiState.update { it.copy(isSaving = false, error = "Couldn't save. Try again.") }
            }
        }
    }
}
