package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionsRepository
import javax.inject.Inject

/**
 * Logs a manually-picked mood. The single entry point the UI uses to record an emotion, so the
 * manual `source`/`confidence` policy lives in one place (per docs/integration/05: "route capture
 * through a use case") rather than being duplicated at every call site.
 *
 * A manual log is always full-confidence and tagged `manual`; the P2 camera path will add its own
 * use case (or extend this one) with the detected confidence and `camera` source.
 */
class LogEmotionUseCase @Inject constructor(
    private val emotionsRepository: EmotionsRepository
) {
    suspend operator fun invoke(emotion: EmotionType, note: String? = null) {
        emotionsRepository.logEmotion(
            emotion = emotion,
            confidence = 1.0,
            source = EmotionRecord.SOURCE_MANUAL,
            note = note?.trim()?.ifBlank { null },
            visibility = EmotionRecord.VISIBILITY_PRIVATE
        )
    }
}
