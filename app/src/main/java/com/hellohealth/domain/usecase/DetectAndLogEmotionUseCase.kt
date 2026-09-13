package com.hellohealth.domain.usecase

import android.graphics.Bitmap
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.repository.EmotionDetectionRepository
import com.hellohealth.domain.repository.EmotionDetectionResult
import com.hellohealth.domain.repository.EmotionsRepository
import javax.inject.Inject

/**
 * Detects a face emotion from [bitmap] on-device and, on success, records it through the SAME
 * [com.hellohealth.domain.repository.EmotionsRepository.logEmotion] path as a manual log — tagged
 * `camera` with the model's softmax confidence (per docs/integration/05: "route capture through a
 * use case"; sibling of [LogEmotionUseCase]).
 *
 * The camera path can only produce the 8 model labels (never `CALM`, which stays manual-only): the
 * impl maps every label through `EmotionType.fromName`. The returned [EmotionDetectionResult] is
 * handed back to the UI so it can render the outcome (emoji/label/confidence, or a fallback for
 * `NoFace`/`ModelUnavailable`/`Error`) — logging only happens on [EmotionDetectionResult.Success].
 */
class DetectAndLogEmotionUseCase @Inject constructor(
    private val emotionDetectionRepository: EmotionDetectionRepository,
    private val emotionsRepository: EmotionsRepository
) {
    suspend operator fun invoke(bitmap: Bitmap): EmotionDetectionResult {
        val result = emotionDetectionRepository.detect(bitmap)
        if (result is EmotionDetectionResult.Success) {
            emotionsRepository.logEmotion(
                emotion = result.emotion,
                confidence = result.confidence,
                source = EmotionRecord.SOURCE_CAMERA,
                visibility = EmotionRecord.VISIBILITY_PRIVATE
            )
        }
        return result
    }
}
