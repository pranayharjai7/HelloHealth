package com.hellohealth.domain.usecase

import android.graphics.Bitmap
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionDetectionRepository
import com.hellohealth.domain.repository.EmotionDetectionResult
import com.hellohealth.domain.repository.EmotionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [DetectAndLogEmotionUseCase] contract: it must route a detection SUCCESS through the shared
 * [EmotionsRepository.logEmotion] path tagged `camera` with the model's confidence, and must NOT
 * log anything for a non-success outcome (NoFace / ModelUnavailable / Error). These pins guard the
 * source-agnostic-pipeline invariant — camera logs share the manual write path, differing only in
 * `source` + `confidence`.
 */
@RunWith(RobolectricTestRunner::class)
class DetectAndLogEmotionUseCaseTest {

    /** Records every logEmotion call so the test can assert source/confidence/emotion pass-through. */
    private class FakeEmotionsRepository : EmotionsRepository {
        data class Logged(
            val emotion: EmotionType,
            val confidence: Double,
            val source: String,
            val note: String?,
            val visibility: String
        )

        val logged = mutableListOf<Logged>()

        override fun observeToday(): Flow<List<EmotionRecord>> = emptyFlow()
        override fun observeLatest(): Flow<EmotionRecord?> = flowOf(null)
        override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = emptyFlow()

        override suspend fun logEmotion(
            emotion: EmotionType,
            confidence: Double,
            source: String,
            note: String?,
            visibility: String
        ) {
            logged += Logged(emotion, confidence, source, note, visibility)
        }

        override suspend fun delete(id: String) = Unit
    }

    /** Returns a fixed detection result and records whether detect() was even called. */
    private class FakeDetectionRepository(private val result: EmotionDetectionResult) : EmotionDetectionRepository {
        var detectCalls = 0
        override suspend fun detect(bitmap: Bitmap): EmotionDetectionResult {
            detectCalls++
            return result
        }
        override suspend fun isAvailable(): Boolean = true
    }

    private val dummyBitmap: Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test
    fun `success logs with camera source and passes confidence through`() = runTest {
        val emotions = FakeEmotionsRepository()
        val detection = FakeDetectionRepository(
            EmotionDetectionResult.Success(EmotionType.HAPPINESS, confidence = 0.87)
        )
        val useCase = DetectAndLogEmotionUseCase(detection, emotions)

        val result = useCase(dummyBitmap)

        assertEquals(1, detection.detectCalls)
        assertEquals(1, emotions.logged.size)
        val logged = emotions.logged.single()
        assertEquals(EmotionType.HAPPINESS, logged.emotion)
        assertEquals(0.87, logged.confidence, 1e-9)
        assertEquals(EmotionRecord.SOURCE_CAMERA, logged.source)
        assertEquals(EmotionRecord.VISIBILITY_PRIVATE, logged.visibility)
        assertNull(logged.note)
        assertTrue(result is EmotionDetectionResult.Success)
    }

    @Test
    fun `no face does not log and returns the result`() = runTest {
        val emotions = FakeEmotionsRepository()
        val useCase = DetectAndLogEmotionUseCase(FakeDetectionRepository(EmotionDetectionResult.NoFace), emotions)

        val result = useCase(dummyBitmap)

        assertTrue(emotions.logged.isEmpty())
        assertTrue(result is EmotionDetectionResult.NoFace)
    }

    @Test
    fun `model unavailable does not log and returns the result`() = runTest {
        val emotions = FakeEmotionsRepository()
        val useCase = DetectAndLogEmotionUseCase(FakeDetectionRepository(EmotionDetectionResult.ModelUnavailable), emotions)

        val result = useCase(dummyBitmap)

        assertTrue(emotions.logged.isEmpty())
        assertTrue(result is EmotionDetectionResult.ModelUnavailable)
    }

    @Test
    fun `error does not log and returns the result`() = runTest {
        val emotions = FakeEmotionsRepository()
        val useCase = DetectAndLogEmotionUseCase(FakeDetectionRepository(EmotionDetectionResult.Error("boom")), emotions)

        val result = useCase(dummyBitmap)

        assertTrue(emotions.logged.isEmpty())
        assertTrue(result is EmotionDetectionResult.Error)
    }

    @Test
    fun `camera source is distinct from manual`() {
        assertFalse(EmotionRecord.SOURCE_CAMERA == EmotionRecord.SOURCE_MANUAL)
    }
}
