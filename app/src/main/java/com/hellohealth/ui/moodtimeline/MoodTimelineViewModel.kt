package com.hellohealth.ui.moodtimeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.usecase.EmotionInsights
import com.hellohealth.domain.usecase.EmotionInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/** One day's moods, newest day first in the list and newest record first within [records]. */
data class DaySection(
    val date: LocalDate,
    val records: List<EmotionRecord>
)

data class MoodTimelineUiState(
    val windowDays: Int = MoodTimelineUiState.WINDOW_DAYS,
    val daySections: List<DaySection> = emptyList(),
    val insights: EmotionInsights = EmotionInsights.EMPTY,
    val isLoading: Boolean = true
) {
    companion object {
        const val WINDOW_DAYS = 30
    }
}

/**
 * Backs the browsable [com.hellohealth.ui.moodtimeline.MoodTimelineScreen]. Mirrors
 * [com.hellohealth.ui.emotioninsights.EmotionInsightsViewModel]: the rolling window is resolved
 * once at construction (a VM lives for one screen visit) then the record feed is observed live so
 * a mood logged or deleted elsewhere updates the timeline and the header score.
 *
 * [EmotionsRepository.observeWindow] delivers records OLDEST-first, which is exactly what the pure
 * [EmotionInsightsUseCase] expects (its stability metric walks adjacent pairs and its dominant
 * tie-break takes the last = most-recent). So the raw list feeds insights unchanged, while the
 * timeline groups it into [DaySection]s reordered newest-first for display.
 */
@HiltViewModel
class MoodTimelineViewModel @Inject constructor(
    private val emotionsRepository: EmotionsRepository,
    private val emotionInsights: EmotionInsightsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoodTimelineUiState())
    val uiState: StateFlow<MoodTimelineUiState> = _uiState.asStateFlow()

    /** The latest raw window emission, kept so state can be re-derived when a pending delete changes. */
    private var latestRecords: List<EmotionRecord> = emptyList()

    /**
     * Ids staged for deletion but not yet committed — hidden from the UI immediately so the row
     * animates out, but only tombstoned in Room once the Undo window closes ([commitDelete]). Kept
     * separate from the repository so an Undo is a pure local revert with no orphaned tombstone.
     */
    private val pendingDeletes = mutableSetOf<String>()

    init {
        val today = LocalDate.now()
        val startEpochDay = today.minusDays((MoodTimelineUiState.WINDOW_DAYS - 1).toLong()).toEpochDay()
        val endEpochDay = today.toEpochDay()
        viewModelScope.launch {
            emotionsRepository.observeWindow(startEpochDay, endEpochDay).collectLatest { records ->
                latestRecords = records
                emitState()
            }
        }
    }

    /**
     * Stage a delete: hide the record from the UI now, but defer the tombstone. The screen shows an
     * Undo snackbar and then calls exactly one of [undoDelete] or [commitDelete].
     */
    fun stageDelete(id: String) {
        pendingDeletes += id
        emitState()
    }

    /** Undo a staged delete — the record reappears; nothing was ever written to Room. */
    fun undoDelete(id: String) {
        if (pendingDeletes.remove(id)) emitState()
    }

    /** Commit a staged delete — actually tombstone the record. No-op if it was already undone. */
    fun commitDelete(id: String) {
        if (id !in pendingDeletes) return
        viewModelScope.launch {
            emotionsRepository.delete(id)
            // The live feed will re-emit without the row; drop it from pending so a late emission
            // (before that re-emit lands) still filters it out.
            pendingDeletes.remove(id)
        }
    }

    /** Re-derive UI state from the latest records minus any pending (staged-but-not-committed) deletes. */
    private fun emitState() {
        val visible = latestRecords.filter { it.id !in pendingDeletes }
        _uiState.update {
            it.copy(
                daySections = groupIntoDays(visible),
                insights = emotionInsights(visible),
                isLoading = false
            )
        }
    }

    /**
     * Group oldest-first [records] into day sections, newest day first and newest record first
     * within each day. A record's local day is derived from its own captured [tzOffsetMinutes]
     * (the offset at the moment it was logged), reproducing the `localDate` the DAO stored — so
     * grouping stays correct across DST changes and travel rather than re-bucketing by today's zone.
     */
    private fun groupIntoDays(records: List<EmotionRecord>): List<DaySection> =
        records
            .groupBy { localDateOf(it) }
            .map { (date, dayRecords) -> DaySection(date, dayRecords.sortedByDescending { it.timestampUtcEpochMs }) }
            .sortedByDescending { it.date }

    private fun localDateOf(record: EmotionRecord): LocalDate =
        Instant.ofEpochMilli(record.timestampUtcEpochMs)
            .atOffset(ZoneOffset.ofTotalSeconds(record.tzOffsetMinutes * 60))
            .toLocalDate()
}
