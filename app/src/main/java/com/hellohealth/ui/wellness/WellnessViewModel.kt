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
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

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

    val snapshot: StateFlow<WellnessSnapshot?> = selectedDateHolder.selectedDate
        .flatMapLatest { date -> repository.observeSnapshot(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )
}
