package com.hellohealth.ui.wellness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.model.WellnessSnapshot
import com.hellohealth.domain.repository.WellnessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * UI state for the wellness surfaces. [isLoading] starts true and flips false on the first repository
 * emission, so the UI can show a loader (not the misleading empty/"no data" state) during the initial
 * read — which does slow suspend work (weekly stats → Health Connect) before its first value. A null
 * [snapshot] AFTER loading is a genuinely empty day.
 */
data class WellnessUiState(
    val snapshot: WellnessSnapshot? = null,
    val isLoading: Boolean = true,
)

/**
 * Backs both the dashboard [com.hellohealth.ui.dashboard.components.WellnessCard] and the
 * [WellnessScreen]. Date-aware: it reacts to [SelectedDateHolder] exactly like the other dashboard
 * VMs, so browsing a past day re-scopes the score (read-only — the repository only persists today).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WellnessViewModel @Inject constructor(
    repository: WellnessRepository,
    selectedDateHolder: SelectedDateHolder,
) : ViewModel() {

    val uiState: StateFlow<WellnessUiState> = selectedDateHolder.selectedDate
        .flatMapLatest { date -> repository.observeSnapshot(date) }
        .map { WellnessUiState(snapshot = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WellnessUiState(snapshot = null, isLoading = true),
        )
}

