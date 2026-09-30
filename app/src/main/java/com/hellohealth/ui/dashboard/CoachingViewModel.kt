package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.model.coaching.CoachingInsight
import com.hellohealth.domain.repository.CoachingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Backs the dashboard's AI Coaching surface. Coaching is a "today" action: on today it loads a daily
 * insight (LLM when opted in + reachable, else the on-device rule-based coach — see
 * [CoachingRepository.dailyInsight], which never throws). On a PAST day (the dashboard calendar moved
 * back) it shows a neutral "viewing {date}" state and does NOT call the LLM — retro-coaching an
 * arbitrary past day is neither useful nor worth the cost.
 *
 * A sibling VM to [NutritionViewModel]/[VitalsViewModel] — it does NOT reach into DashboardViewModel.
 * It observes [SelectedDateHolder] so it reacts to date changes, but only re-generates for today.
 */
@HiltViewModel
class CoachingViewModel @Inject constructor(
    private val coachingRepository: CoachingRepository,
    private val selectedDateHolder: SelectedDateHolder,
) : ViewModel() {

    data class CoachingUiState(
        val insight: String = "",
        val isRuleBased: Boolean = false,
        val isLoading: Boolean = true,
        val hasInsight: Boolean = false,
        /** True when the selected date is not today — the surface shows a neutral, non-LLM message. */
        val isPastDay: Boolean = false,
        val viewingDate: LocalDate? = null,
    )

    private val _uiState = MutableStateFlow(CoachingUiState())
    val uiState: StateFlow<CoachingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectedDateHolder.selectedDate.collectLatest { date ->
                if (date == LocalDate.now(ZoneId.systemDefault())) {
                    loadToday()
                } else {
                    // Neutral past-day state; no dailyInsight() call, so no network / no cost.
                    _uiState.value = CoachingUiState(
                        isLoading = false,
                        hasInsight = false,
                        isPastDay = true,
                        viewingDate = date,
                    )
                }
            }
        }
    }

    /** (Re)load today's insight. Fire-and-forget; the repo never throws, so no try/catch needed. */
    fun refresh() {
        if (selectedDateHolder.selectedDate.value != LocalDate.now(ZoneId.systemDefault())) return
        viewModelScope.launch { loadToday() }
    }

    private suspend fun loadToday() {
        _uiState.value = _uiState.value.copy(isLoading = true, isPastDay = false, viewingDate = null)
        val insight: CoachingInsight = coachingRepository.dailyInsight()
        _uiState.value = CoachingUiState(
            insight = insight.text,
            isRuleBased = insight.source == CoachingInsight.Source.RULE_BASED,
            isLoading = false,
            hasInsight = insight.text.isNotBlank(),
            isPastDay = false,
            viewingDate = null,
        )
    }
}
