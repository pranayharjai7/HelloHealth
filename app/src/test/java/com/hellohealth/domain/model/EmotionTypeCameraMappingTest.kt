package com.hellohealth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the model-vs-manual invariant for the P2 camera path: the `enet_b0_8_va_mtl.ptl` model
 * emits exactly the 8 labels in `emotionsLabel.txt` (Anger…Surprise), and [EmotionType.fromName]
 * — the only bridge the camera path uses — maps every one of them to a model EmotionType and NEVER
 * yields [EmotionType.CALM] (manual-only). An unrecognized/garbage label falls back to NEUTRAL, so
 * a bad inference can never crash a read.
 */
class EmotionTypeCameraMappingTest {

    /** Exactly the labels in app/src/main/assets/emotionsLabel.txt, in model index order. */
    private val modelLabels = listOf(
        "Anger", "Contempt", "Disgust", "Fear", "Happiness", "Neutral", "Sadness", "Surprise"
    )

    @Test
    fun `every model label maps to a non-CALM EmotionType`() {
        modelLabels.forEach { label ->
            val mapped = EmotionType.fromName(label)
            assertNotEquals("Model label '$label' must not map to CALM", EmotionType.CALM, mapped)
        }
    }

    @Test
    fun `model labels map to the matching emotion by name`() {
        assertEquals(EmotionType.ANGER, EmotionType.fromName("Anger"))
        assertEquals(EmotionType.CONTEMPT, EmotionType.fromName("Contempt"))
        assertEquals(EmotionType.DISGUST, EmotionType.fromName("Disgust"))
        assertEquals(EmotionType.FEAR, EmotionType.fromName("Fear"))
        assertEquals(EmotionType.HAPPINESS, EmotionType.fromName("Happiness"))
        assertEquals(EmotionType.NEUTRAL, EmotionType.fromName("Neutral"))
        assertEquals(EmotionType.SADNESS, EmotionType.fromName("Sadness"))
        assertEquals(EmotionType.SURPRISE, EmotionType.fromName("Surprise"))
    }

    @Test
    fun `the 8 model labels are exactly the enum minus CALM`() {
        val fromLabels = modelLabels.map { EmotionType.fromName(it) }.toSet()
        val expected = EmotionType.entries.toSet() - EmotionType.CALM
        assertEquals(expected, fromLabels)
    }

    @Test
    fun `CALM is not producible from any model label`() {
        assertTrue(modelLabels.none { EmotionType.fromName(it) == EmotionType.CALM })
    }

    @Test
    fun `unknown and null labels fall back to NEUTRAL`() {
        assertEquals(EmotionType.NEUTRAL, EmotionType.fromName("nonsense"))
        assertEquals(EmotionType.NEUTRAL, EmotionType.fromName(null))
        assertEquals(EmotionType.NEUTRAL, EmotionType.fromName(""))
    }

    @Test
    fun `Calm is only reachable by its own name and is never a model label`() {
        // fromName("Calm") DOES resolve to CALM — CALM is a real enum entry — but "Calm" is not one
        // of the model's 8 labels, so the camera path never hands that string to fromName.
        assertEquals(EmotionType.CALM, EmotionType.fromName("Calm"))
        assertTrue(modelLabels.none { it.equals("Calm", ignoreCase = true) })
    }
}
