package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmotionsCardUiState(
    val latest: EmotionRecord? = null,
    val dominantToday: EmotionType? = null,
    val todayCount: Int = 0,
    /** The selected day's live logs, newest first — backs the card's "shape of my day" mood-dot strip. */
    val today: List<EmotionRecord> = emptyList()
)

/**
 * Feeds the dashboard [com.hellohealth.ui.dashboard.components.EmotionsCard]. Kept separate from
 * [DashboardViewModel] (which owns the Health Connect flow) so the mood surface stays a small,
 * self-contained concern.
 *
 * Date-aware: the per-day fields ([EmotionsCardUiState.dominantToday]/[todayCount]/[today]) track the
 * dashboard's [SelectedDateHolder] via `observeWindow(day, day)`, so browsing to a past day shows
 * THAT day's moods. [latest] is deliberately NOT re-scoped — it stays the user's most-recent mood so
 * the app's theme tint reflects how they feel *now*, not whatever day they happen to be viewing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmotionsViewModel @Inject constructor(
    private val emotionsRepository: EmotionsRepository,
    private val selectedDateHolder: SelectedDateHolder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmotionsCardUiState())
    val uiState: StateFlow<EmotionsCardUiState> = _uiState.asStateFlow()

    init {
        // Latest mood drives the theme tint — always "now", independent of the selected date.
        viewModelScope.launch {
            emotionsRepository.observeLatest().collectLatest { latest ->
                _uiState.update { it.copy(latest = latest) }
            }
        }
        // The day fields track the selected date (observeWindow of a single day).
        viewModelScope.launch {
            selectedDateHolder.selectedDate.flatMapLatest { date ->
                val epochDay = date.toEpochDay()
                emotionsRepository.observeWindow(epochDay, epochDay)
            }.collectLatest { windowLogs ->
                // observeWindow returns ASC (oldest-first); the card + dominantOf expect newest-first
                // (the tie-break favors the most recent mood), matching the prior observeToday contract.
                val logs = windowLogs.sortedByDescending { it.timestampUtcEpochMs }
                _uiState.update {
                    it.copy(dominantToday = dominantOf(logs), todayCount = logs.size, today = logs)
                }
            }
        }
    }

    /**
     * Log a mood in one tap from the dashboard log sheet's quick-chips. Routes through the same
     * source-agnostic [EmotionsRepository.logEmotion] as manual/camera (defaults: manual source,
     * confidence 1.0), so the theme re-tints and the card/timeline update live.
     *
     * Returns the stable id of the written record (or null if there was no signed-in user) so the
     * caller can bind an "Undo" to exactly THIS record — no shared mutable capture, so concurrent
     * quick-logs never cross-wire their Undo actions.
     */
    suspend fun quickLog(emotion: EmotionType): String? =
        emotionsRepository.logEmotion(emotion)

    /** Soft-delete a mood log by id — backs the "Undo" action on the quick-log confirmation snackbar. */
    fun delete(id: String) {
        viewModelScope.launch { emotionsRepository.delete(id) }
    }

    /**
     * The most-frequent emotion logged today; ties broken toward the most recent mood.
     * [EmotionsRepository.observeToday] delivers records newest-first, so among the tied moods the
     * first one in the list is the most recent — [first], not last.
     */
    private fun dominantOf(today: List<EmotionRecord>): EmotionType? {
        if (today.isEmpty()) return null
        val counts = today.groupingBy { it.emotion }.eachCount()
        val max = counts.values.max()
        return today.first { counts[it.emotion] == max }.emotion
    }
}
