package com.hellohealth.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EmotionsCardUiState(
    val latest: EmotionRecord? = null,
    val dominantToday: EmotionType? = null,
    val todayCount: Int = 0,
    /** Today's live logs, newest first — backs the card's "shape of my day" mood-dot strip. */
    val today: List<EmotionRecord> = emptyList()
)

/**
 * Feeds the dashboard [com.hellohealth.ui.dashboard.components.EmotionsCard]. Kept separate from
 * [DashboardViewModel] (which owns the Health Connect flow) so the mood surface stays a small,
 * self-contained concern. Latest + today's dominant are derived live from the same repository the
 * theme reads, so the card and the app tint always agree.
 */
@HiltViewModel
class EmotionsViewModel @Inject constructor(
    private val emotionsRepository: EmotionsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmotionsCardUiState())
    val uiState: StateFlow<EmotionsCardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            emotionsRepository.observeLatest().collectLatest { latest ->
                _uiState.update { it.copy(latest = latest) }
            }
        }
        viewModelScope.launch {
            emotionsRepository.observeToday().collectLatest { today ->
                _uiState.update {
                    it.copy(dominantToday = dominantOf(today), todayCount = today.size, today = today)
                }
            }
        }
    }

    /**
     * Log a mood in one tap from the dashboard log sheet's quick-chips. Routes through the same
     * source-agnostic [EmotionsRepository.logEmotion] as manual/camera (defaults: manual source,
     * confidence 1.0), so the theme re-tints and the card/timeline update live.
     */
    fun quickLog(emotion: EmotionType) {
        viewModelScope.launch { emotionsRepository.logEmotion(emotion) }
    }

    /** Soft-delete a mood log — backs the "Undo" action on the quick-log confirmation snackbar. */
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
