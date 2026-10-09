package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.repository.WorkoutPlanRepository
import com.hellohealth.domain.repository.WorkoutSessionRepository
import com.hellohealth.domain.usecase.ResolveSmartStartDayUseCase
import com.hellohealth.ui.workoutplan.RoutineDetailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * At-a-glance summary of the user's active routine for the dashboard hero card. Null [activePlanName]
 * means "no active plan yet" (fresh user, or signed out) and the card renders its empty prompt.
 *
 * [suggestedDayId]/[suggestedDayLabel] carry the "Smart Start" target (resolved by
 * [ResolveSmartStartDayUseCase]) — the planned day the card offers to start today ("Start Tuesday" /
 * "Start Day 12" / "Start Custom 3"). Both null when nothing is scheduled for today (WEEKLY/MONTHLY)
 * or the plan has no days — the card then falls back to its plain "view routines" affordance.
 */
data class WorkoutPlanSummary(
    val activePlanName: String? = null,
    val dayCount: Int = 0,
    val plannedCount: Int = 0,
    val suggestedDayId: String? = null,
    val suggestedDayLabel: String? = null,
)

/**
 * Own VM for the dashboard's workout hero card — deliberately NOT folded into [DashboardViewModel] so
 * the planning feature's read surface stays in one obvious place. Reactively derives the summary from
 * the active plan: its day count, and the total planned exercises across those days.
 *
 * Chain: active plan → its days → each day's planned-exercise flow. [flatMapLatest] re-subscribes when
 * the active plan flips; the inner [combine] recomputes when any day's exercise list changes. With no
 * active plan (or signed out) the repository flows emit null/empty, so the summary collapses to the
 * empty state without special-casing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutPlanViewModel @Inject constructor(
    private val repository: WorkoutPlanRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val resolveSmartStartDay: ResolveSmartStartDayUseCase,
) : ViewModel() {

    /**
     * True when a workout session is currently active — gates the dashboard card's "Resume workout"
     * affordance so a session left running (e.g. across a process restart) is reachable again.
     * Signed-out → null → false. Left separate from [summary] to keep that chain untouched.
     */
    val hasActiveSession: StateFlow<Boolean> =
        sessionRepository.observeActiveSession()
            .map { it != null }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    val summary: StateFlow<WorkoutPlanSummary> =
        repository.observeActivePlan()
            .flatMapLatest { plan ->
                if (plan == null) {
                    flowOf(WorkoutPlanSummary())
                } else {
                    repository.observeDays(plan.id).flatMapLatest { days ->
                        if (days.isEmpty()) {
                            flowOf(
                                WorkoutPlanSummary(
                                    activePlanName = plan.name,
                                    dayCount = 0,
                                    plannedCount = 0,
                                )
                            )
                        } else {
                            // Count the planned exercises across the days reactively, and separately
                            // watch recent sessions (for CUSTOM next-in-sequence); combine both to
                            // produce the summary with its resolved Smart-Start target.
                            val today = LocalDate.now()
                            val plannedCountFlow = combine(
                                days.map { repository.observePlannedExercises(it.id) }
                            ) { perDay -> perDay.sumOf { it.size } }
                            val recentSessionsFlow = sessionRepository.observeRecentSessions(
                                startDate = today.minusDays(RECENT_WINDOW_DAYS).toString(),
                                endDate = today.toString(),
                            )
                            combine(plannedCountFlow, recentSessionsFlow) { plannedCount, recentSessions ->
                                val suggested = resolveSmartStartDay(
                                    planType = plan.planType,
                                    days = days,
                                    recentSessionDayIds = recentSessions.mapNotNull { it.dayId },
                                    today = today,
                                )
                                WorkoutPlanSummary(
                                    activePlanName = plan.name,
                                    dayCount = days.size,
                                    plannedCount = plannedCount,
                                    suggestedDayId = suggested?.id,
                                    suggestedDayLabel = suggested?.let {
                                        RoutineDetailViewModel.slotLabel(plan.planType, it.slotKey)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WorkoutPlanSummary(),
            )

    companion object {
        /** How far back to read sessions for CUSTOM next-in-sequence resolution. */
        private const val RECENT_WINDOW_DAYS = 60L
    }
}
