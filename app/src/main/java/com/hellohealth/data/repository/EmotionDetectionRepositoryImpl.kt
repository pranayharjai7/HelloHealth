package com.hellohealth.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.ml.EmotionPyTorchClassifier
import com.hellohealth.data.ml.MTCNN
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionDetectionRepository
import com.hellohealth.domain.repository.EmotionDetectionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Detects a face emotion on-device with the ported MTCNN + PyTorch pipeline. Rewritten
 * HelloHealth-style from the MyEmotions `EmotionRepositoryImpl.detectEmotion`: all work runs on
 * [Dispatchers.IO], and every failure becomes a defined [EmotionDetectionResult] (never a thrown
 * exception across the boundary).
 *
 * The two ML members are created lazily and only ONCE — model construction is expensive and can
 * fail (missing/corrupt assets). [modelsOrNull] centralizes that load-and-cache so both [detect]
 * and [isAvailable] share the same outcome: a failed load flips the pipeline to `ModelUnavailable`
 * for the rest of the process, and the capture UI falls back to manual logging.
 */
@Singleton
class EmotionDetectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : EmotionDetectionRepository {

    private data class Models(val mtcnn: MTCNN, val classifier: EmotionPyTorchClassifier)

    // Load-once cache. null = not yet attempted; a failed attempt sets [loadFailed] so we don't
    // re-attempt a doomed load on every call. Guarded by [lock] because detect() may be called
    // concurrently from different capture attempts.
    @Volatile private var models: Models? = null
    @Volatile private var loadFailed = false
    private val lock = Any()

    private fun modelsOrNull(): Models? {
        models?.let { return it }
        if (loadFailed) return null
        synchronized(lock) {
            models?.let { return it }
            if (loadFailed) return null
            return try {
                val loaded = Models(MTCNN(context), EmotionPyTorchClassifier(context))
                models = loaded
                loaded
            } catch (t: Throwable) {
                // OutOfMemory, missing asset, unsatisfied native link, etc. — all fatal-to-ML but
                // recoverable for the app (manual logging still works).
                AppLogger.e(FeatureTag.EMOTION_ML, "ML model load failed; detection unavailable", t)
                loadFailed = true
                null
            }
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        modelsOrNull() != null
    }

    override suspend fun detect(bitmap: Bitmap): EmotionDetectionResult = withContext(Dispatchers.IO) {
        val m = modelsOrNull() ?: return@withContext EmotionDetectionResult.ModelUnavailable
        try {
            // Downscale so the longest side is ~DETECT_MAX_DIM before running MTCNN — the pyramid
            // is O(pixels) and full-res camera frames are needlessly large. Keep the scale factor
            // so we can map the detected box back to the ORIGINAL bitmap for a sharp classifier crop.
            val longest = max(bitmap.width, bitmap.height)
            val scale = if (longest > DETECT_MAX_DIM) DETECT_MAX_DIM.toFloat() / longest else 1f
            val detectBmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val boxes = m.mtcnn.detectFaces(detectBmp, MIN_FACE_SIZE)
            if (boxes.isEmpty()) {
                if (detectBmp !== bitmap) detectBmp.recycle()
                return@withContext EmotionDetectionResult.NoFace
            }

            // Largest face wins (closest / most prominent subject).
            val best = boxes.maxByOrNull { it.area() }!!

            // Map the box (in downscaled coords) back to the original bitmap, clamped to bounds so
            // an edge face never produces an out-of-range createBitmap crash.
            val invScale = if (scale < 1f) 1f / scale else 1f
            val left = (best.left() * invScale).toInt().coerceIn(0, bitmap.width - 1)
            val top = (best.top() * invScale).toInt().coerceIn(0, bitmap.height - 1)
            val right = (best.right() * invScale).toInt().coerceIn(left + 1, bitmap.width)
            val bottom = (best.bottom() * invScale).toInt().coerceIn(top + 1, bitmap.height)
            val cropW = right - left
            val cropH = bottom - top

            if (detectBmp !== bitmap) detectBmp.recycle()

            if (cropW <= 0 || cropH <= 0) {
                return@withContext EmotionDetectionResult.Error("Invalid face bounding box")
            }

            val faceCrop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)
            val (label, prob) = m.classifier.recognizeWithConfidence(faceCrop)
            faceCrop.recycle()

            // The model emits only the 8 model labels; fromName maps them (and never yields CALM,
            // preserving the manual-only invariant — an unexpected label falls back to NEUTRAL).
            val emotion = EmotionType.fromName(label)
            AppLogger.d(FeatureTag.EMOTION_ML, "Detected $emotion (raw='$label') conf=$prob")
            EmotionDetectionResult.Success(emotion = emotion, confidence = prob.toDouble())
        } catch (t: Throwable) {
            AppLogger.e(FeatureTag.EMOTION_ML, "Emotion detection failed", t)
            EmotionDetectionResult.Error(t.message ?: "Detection failed")
        }
    }

    companion object {
        // Matches the MyEmotions source: downscale so min side ≈ 600 there; we cap the LONGEST side
        // at 600 which is equivalent for the classifier and cheaper on wide frames.
        private const val DETECT_MAX_DIM = 600
        private const val MIN_FACE_SIZE = 32
    }
}
