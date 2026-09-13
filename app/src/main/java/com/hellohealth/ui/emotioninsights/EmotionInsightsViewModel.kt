package com.hellohealth.ui.emotioninsights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.time.LocalDate
import javax.inject.Inject

data class EmotionInsightsUiState(
    val windowDays: Int = WINDOW_DAYS,
    val insights: EmotionInsights = EmotionInsights.EMPTY,
    val isLoading: Boolean = true
) {
    companion object {
        const val WINDOW_DAYS = 7
    }
}

/**
 * Computes the Balance Score over the last [EmotionInsightsUiState.WINDOW_DAYS] days. The window
 * bounds are resolved once at construction (a VM lives for one screen visit), then the record feed
 * is observed live so a mood logged elsewhere updates the score. All math is in the pure
 * [EmotionInsightsUseCase]; this VM only wires the window + repository to it.
 */
@HiltViewModel
class EmotionInsightsViewModel @Inject constructor(
    emotionsRepository: EmotionsRepository,
    emotionInsights: EmotionInsightsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmotionInsightsUiState())
    val uiState: StateFlow<EmotionInsightsUiState> = _uiState.asStateFlow()

    init {
        val today = LocalDate.now()
        val startEpochDay = today.minusDays((EmotionInsightsUiState.WINDOW_DAYS - 1).toLong()).toEpochDay()
        val endEpochDay = today.toEpochDay()
        viewModelScope.launch {
            emotionsRepository.observeWindow(startEpochDay, endEpochDay).collectLatest { records ->
                _uiState.update {
                    it.copy(insights = emotionInsights(records), isLoading = false)
                }
            }
        }
    }
}
