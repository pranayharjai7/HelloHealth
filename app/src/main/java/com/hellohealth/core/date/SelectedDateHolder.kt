package com.hellohealth.core.date

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped holder of the dashboard's currently-selected date, so every date-aware card
 * ViewModel reacts to the same source without manual composable wiring.
 *
 * The dashboard calendar is the single writer ([com.hellohealth.ui.dashboard.DashboardViewModel]
 * calls [set] from `selectDate`/`jumpToToday`); every date-aware VM injects this singleton and
 * switches its reads with `flatMapLatest(selectedDate) { ... }`. Because Hilt hands out the same
 * `@Singleton` instance everywhere, this works identically for VMs created via `hiltViewModel()`
 * inside the dashboard AND for `EmotionsViewModel` (built a layer up in navigation) — no per-VM
 * setter plumbing. In-memory only (like [com.hellohealth.data.local.prefs.CameraPreferences]); the
 * selected date resets to today on process start, which is the desired UX.
 */
@Singleton
class SelectedDateHolder @Inject constructor() {

    private val _selectedDate = MutableStateFlow(LocalDate.now(ZoneId.systemDefault()))

    /** The currently-selected date; defaults to today. Never in the future (see [set]). */
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /**
     * Set the selected date. Future dates are ignored (defense in depth — the calendar UI already
     * disables future cells and [com.hellohealth.ui.dashboard.DashboardViewModel.selectDate] guards
     * too), so a bad call can never put the app into an impossible "viewing the future" state.
     */
    fun set(date: LocalDate) {
        if (date.isAfter(LocalDate.now(ZoneId.systemDefault()))) return
        _selectedDate.value = date
    }
}
