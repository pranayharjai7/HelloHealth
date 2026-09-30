package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.vitals.ReadinessScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pure tests for [BuildCrossInsightsUseCase]: the 7-day alignment, mood-positivity ratio, energy
 * in/out mapping, the sleep→next-day-readiness pairing, and the null-not-zero discipline. Uses the
 * real [ReadinessScoreCalculator] (also pure) so the readiness trend matches production exactly.
 */
class BuildCrossInsightsUseCaseTest {

    private val useCase = BuildCrossInsightsUseCase(ReadinessScoreCalculator())
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 30)

    private fun mood(day: LocalDate, emotion: EmotionType): EmotionRecord {
        val ms = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return EmotionRecord(id = "$day-$emotion", userId = "u1", timestampUtcEpochMs = ms, tzOffsetMinutes = 0, emotion = emotion)
    }

    private fun rollup(day: LocalDate, hrv: Double?, rhr: Double?, sleepMin: Int?): HealthMetricsData =
        HealthMetricsData(
            dateMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            hrvRmssd = hrv, restingHeartRate = rhr, sleepDurationMinutes = sleepMin, deepSleepMinutes = null,
        )

    private fun nutrition(day: LocalDate, kcal: Double) = NutritionDaySummary(
        localDate = day.toString(), caloriesConsumed = kcal, proteinG = 0.0, carbsG = 0.0, fatG = 0.0,
        fibreG = 0.0, waterMl = 0.0, entriesByMeal = emptyMap(),
    )

    @Test
    fun `builds a 7-day window oldest-first ending at today`() {
        val result = useCase(today, zone, emptyList(), emptyList(), emptyMap(), emptyMap())
        assertEquals(7, result.days.size)
        assertEquals(today.minusDays(6), result.days.first().date)
        assertEquals(today, result.days.last().date)
        assertFalse(result.hasAnyData)
    }

    @Test
    fun `mood positivity is the share of positive-valence logs per day`() {
        // Today: HAPPINESS (pos) + CALM (pos) + ANGER (neg) -> 2/3.
        val emotions = listOf(
            mood(today, EmotionType.HAPPINESS),
            mood(today, EmotionType.CALM),
            mood(today, EmotionType.ANGER),
        )
        val result = useCase(today, zone, emotions, emptyList(), emptyMap(), emptyMap())
        val last = result.days.last()
        assertEquals(2.0 / 3.0, last.moodPositivity!!, 0.0001)
        assertEquals(3, last.moodLogCount)
        // A day with no logs is null, not zero.
        assertNull(result.days.first().moodPositivity)
        assertTrue(result.hasAnyData)
    }

    @Test
    fun `energy in and out map per day and net is out minus in`() {
        val nut = mapOf(today.toString() to nutrition(today, 1800.0))
        val out = mapOf(today.toString() to 2200.0)
        val last = useCase(today, zone, emptyList(), emptyList(), nut, out).days.last()
        assertEquals(1800.0, last.caloriesIn!!, 0.01)
        assertEquals(2200.0, last.caloriesOut!!, 0.01)
        assertEquals(400.0, last.netKcal!!, 0.01) // deficit
    }

    @Test
    fun `zero calories read as null, not a plotted zero`() {
        val nut = mapOf(today.toString() to nutrition(today, 0.0))
        val last = useCase(today, zone, emptyList(), emptyList(), nut, mapOf(today.toString() to 0.0)).days.last()
        assertNull(last.caloriesIn)
        assertNull(last.caloriesOut)
        assertNull(last.netKcal)
    }

    @Test
    fun `readiness is null until the calculator has enough history`() {
        // Only 3 days of rollups -> calculator returns INSUFFICIENT_DATA -> null everywhere.
        val rollups = (0..2).map { rollup(today.minusDays(it.toLong()), hrv = 60.0, rhr = 55.0, sleepMin = 420) }
        val result = useCase(today, zone, emptyList(), rollups, emptyMap(), emptyMap())
        assertTrue(result.days.all { it.readiness == null })
    }

    @Test
    fun `readiness is computed for a day with sufficient prior history`() {
        // 10 days of history before `today` + today itself -> today has >=7 prior days -> a real score.
        val rollups = (0..10).map { rollup(today.minusDays(it.toLong()), hrv = 60.0, rhr = 55.0, sleepMin = 420) }
        val result = useCase(today, zone, emptyList(), rollups, emptyMap(), emptyMap())
        assertTrue("today should have a readiness score", result.days.last().readiness != null)
        assertTrue(result.days.last().readiness!! in 1..100)
    }

    @Test
    fun `sleepVsNextReadiness pairs each night's sleep with the next day's readiness`() {
        // Enough history so the last couple of days have readiness; sleep present throughout.
        val rollups = (0..12).map { rollup(today.minusDays(it.toLong()), hrv = 60.0, rhr = 55.0, sleepMin = 450) }
        val result = useCase(today, zone, emptyList(), rollups, emptyMap(), emptyMap())
        val pairs = result.sleepVsNextReadiness
        // Every pair's sleep is 7.5h (450 min) and readiness is a valid 1..100 score.
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.all { it.first == 7.5 && it.second in 1..100 })
    }

    @Test
    fun `sleep hours derive from the day's rollup minutes`() {
        val rollups = (0..10).map { rollup(today.minusDays(it.toLong()), hrv = 60.0, rhr = 55.0, sleepMin = 480) }
        assertEquals(8.0, useCase(today, zone, emptyList(), rollups, emptyMap(), emptyMap()).days.last().sleepHours!!, 0.01)
    }
}
