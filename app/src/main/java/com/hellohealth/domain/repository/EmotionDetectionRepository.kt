package com.hellohealth.domain.repository

import android.graphics.Bitmap
import com.hellohealth.domain.model.EmotionType

/**
 * On-device face-emotion detection (P2). Wraps the MTCNN + PyTorch pipeline behind a suspend
 * boundary that NEVER throws — every failure mode is a defined [EmotionDetectionResult] variant
 * (roadmap guardrail: "nothing crashes — everything has a defined fallback"). Detection runs off
 * the main thread inside the impl.
 *
 * This repository ONLY classifies; it does not persist. The [com.hellohealth.domain.usecase.DetectAndLogEmotionUseCase]
 * routes a [EmotionDetectionResult.Success] into the existing [EmotionsRepository.logEmotion] with
 * `source = camera`, so camera logs share the manual pipeline (Room-first write, sync, theme tint).
 */
interface EmotionDetectionRepository {

    /**
     * Detect the dominant face's emotion in [bitmap]. The bitmap is treated as read-only (never
     * recycled by the impl). Returns a defined result for every outcome — see [EmotionDetectionResult].
     */
    suspend fun detect(bitmap: Bitmap): EmotionDetectionResult

    /**
     * Whether the ML models can be loaded on this device/build. Attempts (and caches) a model load;
     * the capture screen uses this to fall back to manual logging when the pipeline is unavailable.
     */
    suspend fun isAvailable(): Boolean
}

/**
 * The exhaustive set of detection outcomes. The UI renders one message/action per variant; none of
 * them is an exception.
 */
sealed interface EmotionDetectionResult {
    /** A face was found and classified. [confidence] is the model's softmax probability (0..1). */
    data class Success(val emotion: EmotionType, val confidence: Double) : EmotionDetectionResult

    /** The pipeline ran but found no face — ask the user to reframe. */
    data object NoFace : EmotionDetectionResult

    /** The ML models could not be loaded (missing/corrupt assets, unsupported device). Fall back to manual. */
    data object ModelUnavailable : EmotionDetectionResult

    /** Any other failure during detection (decode/inference error). */
    data class Error(val message: String) : EmotionDetectionResult
}
