package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formula tests for [EmotionInsightsUseCase]. Balance Score = PositiveRatio*50 + Stability*30 +
 * Diversity*20, each sub-metric a 0..1 fraction; the tests pin each component and the edge cases
 * (empty, single record) so the score can't silently drift.
 */
class EmotionInsightsUseCaseTest {

    private val useCase = EmotionInsightsUseCase()

    private var seq = 0L
    private fun record(emotion: EmotionType) = EmotionRecord(
        id = "r${seq}",
        userId = "u1",
        timestampUtcEpochMs = seq++,
        tzOffsetMinutes = 0,
        emotion = emotion
    )

    @Test
    fun `empty window returns EMPTY with no dominant and score zero`() {
        val result = useCase(emptyList())
        assertEquals(EmotionInsights.EMPTY, result)
        assertEquals(0, result.score)
        assertNull(result.dominant)
        assertEquals(0, result.total)
    }

    @Test
    fun `single positive record is fully stable and fully positive`() {
        val result = useCase(listOf(record(EmotionType.HAPPINESS)))
        assertEquals(1.0, result.positiveRatio, 1e-9)
        assertEquals(1.0, result.stability, 1e-9) // no pairs -> perfectly stable
        assertEquals(EmotionType.HAPPINESS, result.dominant)
        // diversity = 1/9 available; score = 50 + 30 + 20*(1/9) ~= 82.2 -> 82
        assertEquals(82, result.score)
    }

    @Test
    fun `all negative same emotion yields zero positive ratio but full stability`() {
        val result = useCase(listOf(record(EmotionType.SADNESS), record(EmotionType.SADNESS), record(EmotionType.SADNESS)))
        assertEquals(0.0, result.positiveRatio, 1e-9)
        assertEquals(1.0, result.stability, 1e-9) // no changes across the run
        // diversity = 1/9; score = 0 + 30 + 20/9 ~= 32.2 -> 32
        assertEquals(32, result.score)
        assertEquals(EmotionType.SADNESS, result.dominant)
    }

    @Test
    fun `positive ratio counts positive-valence moods`() {
        // HAPPINESS + SURPRISE + CALM are POSITIVE; ANGER is NEGATIVE -> 3/4 = 0.75
        val result = useCase(
            listOf(
                record(EmotionType.HAPPINESS),
                record(EmotionType.SURPRISE),
                record(EmotionType.CALM),
                record(EmotionType.ANGER)
            )
        )
        assertEquals(0.75, result.positiveRatio, 1e-9)
    }

    @Test
    fun `stability drops as consecutive moods change`() {
        // 3 pairs, all different -> 3 changes -> stability = 1 - 3/3 = 0
        val alternating = useCase(
            listOf(
                record(EmotionType.HAPPINESS),
                record(EmotionType.SADNESS),
                record(EmotionType.HAPPINESS),
                record(EmotionType.SADNESS)
            )
        )
        assertEquals(0.0, alternating.stability, 1e-9)

        // 3 pairs, 1 change -> stability = 1 - 1/3
        val mostlySteady = useCase(
            listOf(
                record(EmotionType.CALM),
                record(EmotionType.CALM),
                record(EmotionType.CALM),
                record(EmotionType.HAPPINESS)
            )
        )
        assertEquals(1.0 - 1.0 / 3.0, mostlySteady.stability, 1e-9)
    }

    @Test
    fun `diversity is distinct moods over available moods`() {
        val result = useCase(
            listOf(
                record(EmotionType.HAPPINESS),
                record(EmotionType.SADNESS),
                record(EmotionType.CALM)
            )
        )
        // 3 distinct out of 9 available
        assertEquals(3.0 / EmotionType.entries.size, result.diversity, 1e-9)
    }

    @Test
    fun `dominant breaks ties toward the most recent mood`() {
        // HAPPINESS and CALM tie at 1 each; CALM is logged last -> dominant CALM.
        val result = useCase(listOf(record(EmotionType.HAPPINESS), record(EmotionType.CALM)))
        assertEquals(EmotionType.CALM, result.dominant)
        assertEquals(1, result.counts[EmotionType.HAPPINESS])
        assertEquals(1, result.counts[EmotionType.CALM])
    }

    @Test
    fun `score stays within 0 to 100`() {
        val result = useCase(EmotionType.entries.map { record(it) })
        assertTrue(result.score in 0..100)
    }
}
