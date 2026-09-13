package com.hellohealth.ui.emotioncapture

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionDetectionResult
import com.hellohealth.domain.usecase.DetectAndLogEmotionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The capture screen's outcome, one-to-one with [EmotionDetectionResult] plus [Idle]/[Analyzing].
 * The UI renders one message/action per variant; a [Success] means the record is ALREADY logged
 * (the use case writes it), so the app has re-tinted and the Emotions card has updated by the time
 * this state lands.
 */
sealed interface EmotionCaptureUiState {
    data object Idle : EmotionCaptureUiState
    data object Analyzing : EmotionCaptureUiState
    data class Success(val emotion: EmotionType, val confidence: Double) : EmotionCaptureUiState
    data object NoFace : EmotionCaptureUiState
    data object ModelUnavailable : EmotionCaptureUiState
    data class Error(val message: String) : EmotionCaptureUiState
}

/**
 * Backs the on-device face-scan screen. [analyze] hands a captured/picked bitmap to
 * [DetectAndLogEmotionUseCase] on [viewModelScope]; detection itself runs off the main thread inside
 * the repository. On [EmotionCaptureUiState.Success] the mood is already logged with `source=camera`,
 * so no extra write happens here — the shared theme/insights pipeline reacts automatically.
 */
@HiltViewModel
class EmotionCaptureViewModel @Inject constructor(
    private val detectAndLog: DetectAndLogEmotionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EmotionCaptureUiState>(EmotionCaptureUiState.Idle)
    val uiState: StateFlow<EmotionCaptureUiState> = _uiState.asStateFlow()

    fun analyze(bitmap: Bitmap) {
        // Guard against double-taps while an analysis is already running.
        if (_uiState.value is EmotionCaptureUiState.Analyzing) return
        _uiState.value = EmotionCaptureUiState.Analyzing
        viewModelScope.launch {
            val next = try {
                when (val result = detectAndLog(bitmap)) {
                    is EmotionDetectionResult.Success ->
                        EmotionCaptureUiState.Success(result.emotion, result.confidence)
                    EmotionDetectionResult.NoFace -> EmotionCaptureUiState.NoFace
                    EmotionDetectionResult.ModelUnavailable -> EmotionCaptureUiState.ModelUnavailable
                    is EmotionDetectionResult.Error -> EmotionCaptureUiState.Error(result.message)
                }
            } catch (t: Throwable) {
                // The use case shouldn't throw (detect() returns a defined result), but never let a
                // capture crash the screen — fall back to a clean error.
                AppLogger.e(FeatureTag.EMOTION_ML, "Capture analysis failed unexpectedly", t)
                EmotionCaptureUiState.Error("Something went wrong. Try again.")
            }
            _uiState.value = next
        }
    }

    /** Return to the live preview to scan again. */
    fun reset() {
        _uiState.value = EmotionCaptureUiState.Idle
    }
}
