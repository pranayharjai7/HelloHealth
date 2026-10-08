package com.hellohealth.ui.workoutsession

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.PlannedExerciseWithDetails
import com.hellohealth.domain.model.SessionSet
import com.hellohealth.domain.model.WorkoutSession
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.domain.repository.WorkoutSessionRepository
import com.hellohealth.ui.navigation.Screen
import com.hellohealth.workoutsession.RestState
import com.hellohealth.workoutsession.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the active-workout screen. [session] is the live active session (null before it is
 * started / after it finishes). [sets] are its logged sets in order. [plannedExercises] seed the
 * exercise picker + prefill when the workout was started from a planned day (empty for ad-hoc).
 * [restState] is the durable rest timer. [finished] flips once when the session completes so the
 * screen can show the summary / navigate back.
 */
data class ActiveWorkoutUiState(
    val session: WorkoutSession? = null,
    val sets: List<SessionSet> = emptyList(),
    val plannedExercises: List<PlannedExerciseWithDetails> = emptyList(),
    val restState: RestState = RestState.Idle,
    val finished: Boolean = false,
) {
    val isActive: Boolean get() = session?.isActive == true
    val totalVolumeKg: Double get() = sets.sumOf { it.volumeKg ?: 0.0 }
}

/**
 * Backs [ActiveWorkoutScreen]. Optionally resolves a `dayId` (started from a planned day) to load its
 * planned exercises for prefill. Observes the single active session + its sets reactively, folded with
 * the [WorkoutSessionController]'s rest state. All logging actions delegate to
 * [WorkoutSessionRepository]; the rest timer is started/cleared on the controller.
 *
 * Starting: when a `dayId` is present the screen calls [startFromPlannedDay] on first entry; the
 * ad-hoc path calls [startAdHoc]. Both are idempotent against an already-active session (the repo
 * enforces single-active), so a config change / re-entry won't open a duplicate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val sessionRepository: WorkoutSessionRepository,
    private val planRepository: WorkoutPlanRepository,
    private val controller: WorkoutSessionController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val dayId: String? = savedStateHandle.get<String>(Screen.ActiveWorkout.dayIdArg)
        ?.takeIf { it.isNotBlank() && it != NO_DAY }

    private val _finished = MutableStateFlow(false)
    private val _planned = MutableStateFlow<List<PlannedExerciseWithDetails>>(emptyList())

    private val activeSession: Flow<WorkoutSession?> = sessionRepository.observeActiveSession()

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        activeSession,
        activeSession.flatMapLatest { s ->
            if (s == null) flowOf(emptyList()) else sessionRepository.observeSets(s.id)
        },
        _planned,
        controller.restState,
        _finished,
    ) { session, sets, planned, rest, finished ->
        ActiveWorkoutUiState(
            session = session,
            sets = sets,
            plannedExercises = planned,
            restState = rest,
            finished = finished,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(),
    )

    init {
        if (dayId != null) {
            viewModelScope.launch {
                // One snapshot is enough for prefill; the planned list doesn't change mid-workout.
                _planned.value = planRepository.observePlannedExercises(dayId).first()
            }
        }
    }

    /** Start an ad-hoc session (no planned day). No-op if one is already active. */
    fun startAdHoc(activityType: String = "strength_training") {
        viewModelScope.launch {
            if (sessionRepository.activeSessionId() == null) {
                sessionRepository.startSession(activityType = activityType)
            }
        }
    }

    /** Start a session anchored to the planned [dayId], loading its day/plan for the title. */
    fun startFromPlannedDay() {
        val day = dayId ?: return
        viewModelScope.launch {
            if (sessionRepository.activeSessionId() != null) return@launch
            val dayRow = planRepository.getDay(day)
            sessionRepository.startSession(
                activityType = "strength_training",
                planId = dayRow?.planId,
                dayId = day,
                title = dayRow?.name,
            )
        }
    }

    fun logSet(
        exerciseId: String,
        plannedExerciseId: String? = null,
        reps: Int? = null,
        weightKg: Double? = null,
        durationSeconds: Int? = null,
        distanceKm: Double? = null,
        rpe: Double? = null,
        isWarmup: Boolean = false,
        restSeconds: Int? = null,
    ) {
        val sessionId = uiState.value.session?.id ?: return
        viewModelScope.launch {
            sessionRepository.logSet(
                sessionId = sessionId,
                exerciseId = exerciseId,
                plannedExerciseId = plannedExerciseId,
                reps = reps,
                weightKg = weightKg,
                durationSeconds = durationSeconds,
                distanceKm = distanceKm,
                rpe = rpe,
                isWarmup = isWarmup,
            )
            if (restSeconds != null && restSeconds > 0) controller.startRest(restSeconds)
        }
    }

    fun editSet(set: SessionSet) {
        viewModelScope.launch { sessionRepository.editSet(set) }
    }

    fun skipSet(id: String) {
        viewModelScope.launch { sessionRepository.skipSet(id) }
    }

    fun deleteSet(id: String) {
        viewModelScope.launch { sessionRepository.deleteSet(id) }
    }

    fun startRest(seconds: Int) = controller.startRest(seconds)

    fun stopRest() = controller.clearRest()

    /** Finish the active session, clear the rest timer, and flip [finished]. */
    fun finish() {
        val sessionId = uiState.value.session?.id ?: return
        viewModelScope.launch {
            sessionRepository.finishSession(sessionId)
            controller.clearRest()
            _finished.value = true
        }
    }

    /** Abandon the active session (kept, not finished) and clear the rest timer. */
    fun abandon() {
        val sessionId = uiState.value.session?.id ?: return
        viewModelScope.launch {
            sessionRepository.abandonSession(sessionId)
            controller.clearRest()
            _finished.value = true
        }
    }

    /** Suspend prefill lookup for a given exercise — the screen seeds its set form from this. */
    suspend fun lastSetFor(exerciseId: String): SessionSet? =
        sessionRepository.lastCompletedSet(exerciseId)

    private companion object {
        const val NO_DAY = "none"
    }
}
