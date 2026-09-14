package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.repository.WorkoutPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * At-a-glance summary of the user's active routine for the dashboard hero card. Null [activePlanName]
 * means "no active plan yet" (fresh user, or signed out) and the card renders its empty prompt.
 */
data class WorkoutPlanSummary(
    val activePlanName: String? = null,
    val dayCount: Int = 0,
    val plannedCount: Int = 0,
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
) : ViewModel() {

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
                            // One planned-exercise flow per day; combine sums the counts reactively.
                            combine(days.map { repository.observePlannedExercises(it.id) }) { perDay ->
                                WorkoutPlanSummary(
                                    activePlanName = plan.name,
                                    dayCount = days.size,
                                    plannedCount = perDay.sumOf { it.size },
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
}
