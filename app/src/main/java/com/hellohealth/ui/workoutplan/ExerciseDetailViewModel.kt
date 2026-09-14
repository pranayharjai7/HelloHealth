package com.hellohealth.ui.workoutplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.Exercise
import com.hellohealth.domain.model.LoggingType
import com.hellohealth.domain.model.PlannedExercise
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editable target form for one planned exercise. Each field is held as raw text so in-progress edits
 * (e.g. "8.") survive recomposition; parsing to the typed target happens on [save]. [loggingType]
 * decides which fields the screen renders (weighted-reps shows reps+weight; cardio shows
 * duration/distance/speed/incline; etc.), but every field is persisted regardless so switching an
 * exercise's derivation later never loses data.
 */
data class ExerciseDetailUiState(
    val loading: Boolean = true,
    val exercise: Exercise? = null,
    val loggingType: LoggingType? = null,
    val sets: String = "",
    val reps: String = "",
    val weightKg: String = "",
    val durationSeconds: String = "",
    val distanceKm: String = "",
    val speedKmh: String = "",
    val incline: String = "",
    val notFound: Boolean = false,
    val savedOk: Boolean = false,
)

/**
 * Backs [ExerciseDetailScreen]. Resolves `plannedId` from [SavedStateHandle], loads the planned row +
 * its catalog exercise once, and seeds the target form. [save] parses the fields into a
 * [PlannedExercise] and delegates to [WorkoutPlanRepository.updateTargets] (which stamps the LWW
 * clock and requests a sync), then flips [ExerciseDetailUiState.savedOk] so the screen navigates back.
 */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val plannedId: String = savedStateHandle.get<String>(Screen.ExerciseDetail.plannedIdArg).orEmpty()

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    /** The domain row we loaded, kept to preserve identity fields (id/dayId/userId/order) on save. */
    private var loaded: PlannedExercise? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val details = repository.getPlannedExercise(plannedId)
            if (details == null) {
                _uiState.update { it.copy(loading = false, notFound = true) }
                return@launch
            }
            loaded = details.planned
            val p = details.planned
            _uiState.value = ExerciseDetailUiState(
                loading = false,
                exercise = details.exercise,
                loggingType = details.loggingType,
                sets = p.targetSets.toString(),
                reps = p.targetReps?.toString().orEmpty(),
                weightKg = p.targetWeightKg?.let(::trimFloat).orEmpty(),
                durationSeconds = p.targetDurationSeconds?.toString().orEmpty(),
                distanceKm = p.targetDistanceKm?.let(::trimFloat).orEmpty(),
                speedKmh = p.targetSpeedKmh?.let(::trimFloat).orEmpty(),
                incline = p.targetIncline?.let(::trimFloat).orEmpty(),
            )
        }
    }

    fun updateSets(v: String) = _uiState.update { it.copy(sets = v) }
    fun updateReps(v: String) = _uiState.update { it.copy(reps = v) }
    fun updateWeight(v: String) = _uiState.update { it.copy(weightKg = v) }
    fun updateDuration(v: String) = _uiState.update { it.copy(durationSeconds = v) }
    fun updateDistance(v: String) = _uiState.update { it.copy(distanceKm = v) }
    fun updateSpeed(v: String) = _uiState.update { it.copy(speedKmh = v) }
    fun updateIncline(v: String) = _uiState.update { it.copy(incline = v) }

    fun save() {
        val base = loaded ?: return
        val s = _uiState.value
        val updated = base.copy(
            // Sets always meaningful; blank/invalid falls back to at least 1 rather than 0.
            targetSets = s.sets.toIntOrNull()?.coerceAtLeast(1) ?: base.targetSets,
            targetReps = s.reps.toIntOrNull(),
            targetWeightKg = s.weightKg.parseFloatOrNull(),
            targetDurationSeconds = s.durationSeconds.toIntOrNull(),
            targetDistanceKm = s.distanceKm.parseFloatOrNull(),
            targetSpeedKmh = s.speedKmh.parseFloatOrNull(),
            targetIncline = s.incline.parseFloatOrNull(),
        )
        viewModelScope.launch {
            repository.updateTargets(updated)
            _uiState.update { it.copy(savedOk = true) }
        }
    }

    private fun String.parseFloatOrNull(): Float? = trim().replace(',', '.').toFloatOrNull()

    private fun trimFloat(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
