package com.hellohealth.ui.logemotion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.usecase.LogEmotionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogEmotionUiState(
    val selectedEmotion: EmotionType? = null,
    val note: String = "",
    val todaysMoods: List<EmotionRecord> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

/**
 * Backs the manual mood-logging screen. Save routes through [LogEmotionUseCase] (single place the
 * manual source/confidence policy lives), then the whole app re-tints because [ThemeViewModel]
 * observes the same repository. "Today's moods" is collected live so a fresh log appears at once.
 */
@HiltViewModel
class LogEmotionViewModel @Inject constructor(
    private val logEmotion: LogEmotionUseCase,
    emotionsRepository: EmotionsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogEmotionUiState())
    val uiState: StateFlow<LogEmotionUiState> = _uiState.asStateFlow()

    init {
        // Live "today" feed — must not touch error/successMessage (those are write-outcome state).
        viewModelScope.launch {
            emotionsRepository.observeToday().collectLatest { today ->
                _uiState.update { it.copy(todaysMoods = today) }
            }
        }
    }

    fun selectEmotion(emotion: EmotionType) {
        _uiState.update { it.copy(selectedEmotion = emotion, error = null, successMessage = null) }
    }

    fun updateNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun save() {
        val emotion = _uiState.value.selectedEmotion
        if (emotion == null) {
            _uiState.update { it.copy(error = "Pick a mood first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, successMessage = null) }
            try {
                logEmotion(emotion = emotion, note = _uiState.value.note)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        note = "",
                        successMessage = "Logged ${emotion.displayLabel()}."
                    )
                }
            } catch (e: Exception) {
                AppLogger.w(FeatureTag.EMOTIONS, "failed to log emotion", e)
                _uiState.update { it.copy(isSaving = false, error = "Couldn't save. Try again.") }
            }
        }
    }
}
