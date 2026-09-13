package com.hellohealth.ui.moodtimeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.di.ApplicationScope
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.usecase.EmotionInsights
import com.hellohealth.domain.usecase.EmotionInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
    private val emotionInsights: EmotionInsightsUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _uiState = MutableStateFlow(MoodTimelineUiState())
    val uiState: StateFlow<MoodTimelineUiState> = _uiState.asStateFlow()

    /** The latest raw window emission, kept so state can be re-derived when a pending delete changes. */
    private var latestRecords: List<EmotionRecord> = emptyList()

    /**
     * Ids staged for deletion but not yet committed — hidden from the UI immediately (the removed
     * row disappears and the rows below it slide up to close the gap), but only tombstoned in Room
     * once the Undo window closes ([commitDelete]). Kept separate from the repository so an Undo is
     * a pure local revert with no orphaned tombstone.
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

    /**
     * Commit a staged delete — actually tombstone the record. No-op if it was already committed or
     * undone. The guard is [MutableSet.remove]'s own boolean: removing the id synchronously (before
     * any suspension) means a second caller for the same id — e.g. the snackbar timeout AND the
     * screen-exit flush racing in the same frame — finds it already gone and returns, so [delete] runs
     * exactly once.
     *
     * The write launches on [applicationScope], NOT [viewModelScope]: on navigate-away the
     * NavBackStackEntry destroys this ViewModel and cancels [viewModelScope] in the same teardown, and
     * [emotionsRepository.delete] suspends at its first Room read before the tombstone write — so a
     * viewModelScope-bound write would be cancelled mid-flight and the delete silently lost. The
     * process-lifetime scope guarantees the tombstone lands.
     */
    fun commitDelete(id: String) {
        if (!pendingDeletes.remove(id)) return
        applicationScope.launch {
            emotionsRepository.delete(id)
        }
        // Deliberately DON'T re-derive state here: the row is already hidden (staging removed it), and
        // the tombstone write is async, so an emitState() now — with the id gone from pendingDeletes
        // but the row not yet tombstoned in Room — would briefly un-hide it (a delete-then-reappear
        // flicker). Room's observeWindow re-emits without the row once the write commits, finalizing
        // the removal cleanly.
    }

    /**
     * Flush every still-pending delete — called from [onCleared] on genuine navigate-away so a delete
     * staged then left before its Undo snackbar closed still persists. Snapshots the ids first because
     * [commitDelete] mutates [pendingDeletes]; each id routes through the guarded, application-scoped
     * [commitDelete] path so it commits exactly once and survives this ViewModel's teardown.
     */
    private fun commitAllPending() {
        pendingDeletes.toList().forEach { commitDelete(it) }
    }

    /**
     * Fires on genuine destruction (backstack pop / navigate-away) but NOT on a configuration change —
     * a retained ViewModel survives rotation, so a still-undoable staged delete is left alone then and
     * only flushed when the user actually leaves the screen.
     */
    override fun onCleared() {
        commitAllPending()
        super.onCleared()
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
