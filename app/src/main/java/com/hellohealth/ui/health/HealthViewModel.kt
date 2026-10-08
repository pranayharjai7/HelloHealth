package com.hellohealth.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.BodyAnalytics
import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.model.vitals.ReadinessStatus
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.BodyLogUndo
import com.hellohealth.domain.repository.BodyMetricsRepository
import com.hellohealth.domain.repository.GoalsRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.domain.usecase.BodyAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The one date-aware state for the unified Health screen. Section sub-states keep the screen dumb
 * (all display formatting lives here; a missing metric is a dash, never a 0). Reconciles the three
 * previously-separate surfaces — activity/body/sleep (DashboardViewModel/HealthSummary), readiness +
 * recovery chips (VitalsViewModel), and body-composition history (BodyMetricsRepository) — into a
 * single [HealthUiState] keyed on [SelectedDateHolder].
 */
data class HealthUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    // Activity
    val summary: HealthSummary = HealthSummary(),
    val goals: ActivityGoals = ActivityGoals(),
    val sessions: List<ExerciseSession> = emptyList(),
    // Vitals & recovery
    val readiness: ReadinessScore? = null,
    val latestVitals: LatestVitals? = null,
    val vitalsTrend: List<LatestVitals> = emptyList(),
    // Body composition
    val body: BodyAnalytics = BodyAnalytics(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HealthViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val vitalsRepository: VitalsRepository,
    private val bodyMetricsRepository: BodyMetricsRepository,
    private val goalsRepository: GoalsRepository,
    private val profileRepository: ProfileRepository,
    private val bodyAnalyticsUseCase: BodyAnalyticsUseCase,
    private val selectedDateHolder: SelectedDateHolder,
) : ViewModel() {

    // Bumped by refresh() (pull-to-refresh) to force a live Health Connect re-fetch of the summary.
    private val refreshTrigger = MutableStateFlow(0)

    // The Undo token for the most recent manual weight log, consumed by undoLastLog(). Single-slot:
    // only the latest log is undoable, which matches the single-snackbar UX.
    private var lastUndo: BodyLogUndo? = null

    val uiState: StateFlow<HealthUiState> =
        selectedDateHolder.selectedDate.flatMapLatest { date ->
            val iso = date.toString()
            combine(
                activitySummaryFlow(date),
                vitalsRepository.observeReadinessAsOf(date),
                vitalsRepository.observeVitalsForDay(iso),
                vitalsRepository.observeRecentVitals(TREND_DAYS),
                bodyFlow(date),
            ) { summaryAndGoals, readiness, latest, trend, body ->
                val (summary, goals) = summaryAndGoals
                HealthUiState(
                    selectedDate = date,
                    isLoading = false,
                    summary = summary,
                    goals = goals,
                    sessions = summary.exerciseSessions,
                    readiness = readiness,
                    latestVitals = latest,
                    vitalsTrend = trend,
                    body = body,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HealthUiState(),
        )

    /** Activity summary for the day — a one-shot suspend fetch wrapped in a flow, re-run on refresh. */
    private fun activitySummaryFlow(date: LocalDate) = refreshTrigger.flatMapLatest { tick ->
        flow {
            val goals = runCatching { goalsRepository.getCurrentActivityGoals() }.getOrDefault(ActivityGoals())
            val summary = runCatching {
                activityRepository.fetchSummary(goals, date, forceRefresh = tick > 0 && date == LocalDate.now(ZoneId.systemDefault()))
            }.getOrDefault(HealthSummary())
            emit(summary to goals)
        }
    }

    /** Body analytics over the trailing window, ending at the selected day, via the pure use-case. */
    private fun bodyFlow(date: LocalDate) =
        combine(
            bodyMetricsRepository.observeRecentBodyMetrics(TREND_DAYS),
            profileFlow,
        ) { metrics, profile ->
            // Only include rows up to the selected day (browsing a past day shouldn't show later data).
            val upTo = metrics.filter { it.localDate <= date.toString() }
            bodyAnalyticsUseCase(upTo, profile, date)
        }

    private val profileFlow = flow {
        emit(runCatching { profileRepository.getProfile() }.getOrNull())
    }

    /** Pull-to-refresh: force a live Health Connect re-fetch for today. */
    fun refresh() {
        refreshTrigger.value += 1
    }

    /**
     * Log a manual weight (kg) — and optional waist (cm) — for the currently-selected day. Writes
     * through the repository (Room-first, load-then-merge so same-day Health Connect fields survive),
     * stashes the returned Undo token, then nudges the body flow to re-read. No-op on a blank weight.
     */
    fun logWeight(weightKg: Double, waistCm: Double? = null) {
        if (weightKg <= 0) return
        val date = selectedDateHolder.selectedDate.value
        viewModelScope.launch {
            runCatching { bodyMetricsRepository.logWeight(date.toString(), weightKg, waistCm) }
                .onSuccess { lastUndo = it }
                .onFailure { AppLogger.w(FeatureTag.BODY_METRICS, "logWeight failed: ${it.message}") }
            refreshTrigger.value += 1
        }
    }

    /** Reverse the most recent [logWeight] (restores the day's prior state), if one is pending. */
    fun undoLastLog() {
        val token = lastUndo ?: return
        lastUndo = null
        viewModelScope.launch {
            runCatching { bodyMetricsRepository.undoLog(token) }
                .onFailure { AppLogger.w(FeatureTag.BODY_METRICS, "undoLog failed: ${it.message}") }
            refreshTrigger.value += 1
        }
    }

    companion object {
        const val TREND_DAYS = 30
    }
}
