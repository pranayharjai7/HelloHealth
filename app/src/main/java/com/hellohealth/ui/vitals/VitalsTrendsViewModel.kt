package com.hellohealth.ui.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.repository.VitalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State for the Vitals & Recovery trends screen. [rollups] are the recent daily rollups ascending by
 * date (oldest first) — each trend chart derives its own series from them. Empty means no user / no
 * data yet, and every chart shows its empty state.
 */
data class VitalsTrendsUiState(
    val rollups: List<LatestVitals> = emptyList(),
)

/**
 * VM for [VitalsTrendsScreen]. Reads the persisted daily rollup window (never live Health Connect) via
 * [VitalsRepository.observeRecentVitals]. Purely reactive: the charts recompose as the rollup table
 * changes (e.g. after a sync pull or the daily upsert).
 */
@HiltViewModel
class VitalsTrendsViewModel @Inject constructor(
    repository: VitalsRepository,
) : ViewModel() {

    val uiState: StateFlow<VitalsTrendsUiState> =
        repository.observeRecentVitals(TREND_WINDOW_DAYS)
            .map { VitalsTrendsUiState(rollups = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VitalsTrendsUiState(),
            )

    companion object {
        /** Days of rollup history the trends charts span. */
        const val TREND_WINDOW_DAYS = 30
    }
}
