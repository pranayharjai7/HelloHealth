package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.BodyMetric
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure tests for [BodyAnalyticsUseCase]: latest-value selection, BMI/fat-mass fallback derivation,
 * TDEE from the profile, the weight trend + delta, and the sparse-safe (0/1/≥2 rows) discipline.
 */
class BodyAnalyticsUseCaseTest {

    private val useCase = BodyAnalyticsUseCase()
    private val today = LocalDate.of(2026, 9, 30)

    private fun metric(
        date: String,
        weightKg: Double? = null,
        heightCm: Double? = null,
        bodyFatPct: Double? = null,
        bmi: Double? = null,
    ) = BodyMetric(
        localDate = date, weightKg = weightKg, heightCm = heightCm, bodyFatPct = bodyFatPct,
        leanMassKg = null, fatMassKg = null, bodyWaterKg = null, boneMassKg = null, bmr = null,
        bmi = bmi, waistCm = null, vo2max = null, source = "health_connect",
    )

    private fun profile() = UserProfile(
        gender = Gender.MALE,
        birthDateEpochDay = LocalDate.of(1996, 9, 30).toEpochDay(),
        heightCm = 180.0, weightKg = 80.0, activityLevel = ActivityLevel.MODERATE,
    )

    @Test
    fun `empty metrics yields no data`() {
        val a = useCase(emptyList(), profile(), today)
        assertFalse(a.hasAnyData)
        assertNull(a.latestWeightKg)
        assertTrue(a.weightTrend.isEmpty())
    }

    @Test
    fun `latest values pick the most recent row that carries each field`() {
        val metrics = listOf(
            metric("2026-09-28", weightKg = 82.0, heightCm = 180.0, bodyFatPct = 22.0),
            metric("2026-09-30", weightKg = 80.0), // newest weight, but no height/bodyFat here
        )
        val a = useCase(metrics, profile(), today)
        assertEquals(80.0, a.latestWeightKg!!, 0.001)     // newest weight
        assertEquals(180.0, a.latestHeightCm!!, 0.001)     // carried from the earlier row
        assertEquals(22.0, a.bodyFatPct!!, 0.001)
    }

    @Test
    fun `bmi and fat mass derive when not persisted`() {
        val a = useCase(listOf(metric("2026-09-30", weightKg = 80.0, heightCm = 180.0, bodyFatPct = 20.0)), profile(), today)
        assertEquals(24.691, a.bmi!!, 0.001)  // derived from weight+height
        assertEquals(16.0, a.fatMassKg!!, 0.001)
        assertEquals(64.0, a.leanMassKg!!, 0.001)
    }

    @Test
    fun `persisted bmi is preferred over a fresh derive`() {
        val a = useCase(listOf(metric("2026-09-30", weightKg = 80.0, heightCm = 180.0, bmi = 25.5)), profile(), today)
        assertEquals(25.5, a.bmi!!, 0.001)
    }

    @Test
    fun `weight trend and delta come from rows that carry weight`() {
        val metrics = listOf(
            metric("2026-09-28", weightKg = 82.0),
            metric("2026-09-29", weightKg = 81.0),
            metric("2026-09-30", weightKg = 80.0),
        )
        val a = useCase(metrics, profile(), today)
        assertEquals(3, a.weightTrend.size)
        assertEquals(-2.0, a.weightChangeKg!!, 0.001) // 80 - 82
    }

    @Test
    fun `a single weight point has no delta`() {
        val a = useCase(listOf(metric("2026-09-30", weightKg = 80.0)), profile(), today)
        assertEquals(1, a.weightTrend.size)
        assertNull(a.weightChangeKg)
    }

    @Test
    fun `tdee comes from the profile and is positive for a full profile`() {
        val a = useCase(listOf(metric("2026-09-30", weightKg = 80.0)), profile(), today)
        assertTrue(a.tdee != null && a.tdee!! > 0.0)
    }
}
