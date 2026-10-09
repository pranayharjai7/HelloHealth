package com.hellohealth.ui.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.BodyMetric
import com.hellohealth.domain.repository.BodyMetricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** [metrics] is the recent body-composition history, ascending by date (empty when signed out). */
data class BodyTrendsUiState(
    val metrics: List<BodyMetric> = emptyList(),
)

/**
 * Backs [BodyTrendsScreen] — the body-composition trends over time (weight / body-fat / lean mass).
 * Mirrors [com.hellohealth.ui.vitals.VitalsTrendsViewModel] exactly: a single reactive read of the
 * recent-history window, shaped into a list the per-metric charts select from. The date window lives
 * in the repository (same as vitals); this screen shows the full recent window, not a selected day.
 */
@HiltViewModel
class BodyTrendsViewModel @Inject constructor(
    repository: BodyMetricsRepository,
) : ViewModel() {

    val uiState: StateFlow<BodyTrendsUiState> =
        repository.observeRecentBodyMetrics(TREND_WINDOW_DAYS)
            .map { BodyTrendsUiState(metrics = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = BodyTrendsUiState(),
            )

    companion object {
        const val TREND_WINDOW_DAYS = 30
    }
}
