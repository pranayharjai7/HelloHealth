package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.coaching.CoachingInsight
import com.hellohealth.domain.repository.CoachingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the dashboard's AI Coaching card. On first composition it loads a daily insight (LLM when the
 * user has opted in and a provider is reachable, else the on-device rule-based coach — see
 * [CoachingRepository.dailyInsight], which never throws). The card is deliberately quiet: while
 * loading it shows nothing jarring, and it always ends with *some* insight because the repository's
 * rule-based fallback guarantees non-empty text.
 *
 * A sibling VM to [NutritionViewModel]/[VitalsViewModel] — it does NOT reach into DashboardViewModel.
 * Uses the MutableStateFlow + one-shot-load idiom (like InsightsViewModel) because dailyInsight() is a
 * suspend action, not a reactive Flow.
 */
@HiltViewModel
class CoachingViewModel @Inject constructor(
    private val coachingRepository: CoachingRepository,
) : ViewModel() {

    data class CoachingUiState(
        val insight: String = "",
        val isRuleBased: Boolean = false,
        val isLoading: Boolean = true,
        val hasInsight: Boolean = false,
    )

    private val _uiState = MutableStateFlow(CoachingUiState())
    val uiState: StateFlow<CoachingUiState> = _uiState.asStateFlow()

    init { refresh() }

    /** (Re)load the daily insight. Fire-and-forget; the repo never throws, so no try/catch needed. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val insight: CoachingInsight = coachingRepository.dailyInsight()
            _uiState.value = CoachingUiState(
                insight = insight.text,
                isRuleBased = insight.source == CoachingInsight.Source.RULE_BASED,
                isLoading = false,
                hasInsight = insight.text.isNotBlank(),
            )
        }
    }
}
