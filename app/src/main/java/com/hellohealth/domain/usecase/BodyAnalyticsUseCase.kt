package com.hellohealth.domain.usecase

import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.model.BodyAnalytics
import com.hellohealth.domain.model.BodyMetric
import com.hellohealth.domain.model.UserProfile
import java.time.LocalDate
import javax.inject.Inject

/**
 * Pure builder for the Health screen's Body section ([BodyAnalytics]). No I/O; `today` is passed in
 * for testability (mirrors [BuildCrossInsightsUseCase]). Takes the window's [BodyMetric] rows
 * (ascending by date) + the [UserProfile], and produces the latest values + trend series.
 *
 * Sparse-safe (the discipline from VitalsTrendsScreen's TrendChartCard): 0 rows → all null / empty;
 * 1 row → latest values but no slope; ≥2 → trend lines + weight delta. BMI prefers the persisted
 * per-row value, falling back to a fresh derive from the latest weight+height. TDEE comes from the
 * profile via [BodyEnergy.tdee] (independent of the body_metrics history).
 */
class BodyAnalyticsUseCase @Inject constructor() {

    operator fun invoke(
        metrics: List<BodyMetric>,
        profile: UserProfile?,
        today: LocalDate = LocalDate.now(),
    ): BodyAnalytics {
        if (metrics.isEmpty()) return BodyAnalytics(hasAnyData = false)

        val ordered = metrics.sortedBy { it.localDate }
        // "Latest available" per field — the most recent row that actually carries it.
        fun latest(select: (BodyMetric) -> Double?): Double? =
            ordered.lastOrNull { select(it) != null }?.let(select)

        val weight = latest { it.weightKg }
        val height = latest { it.heightCm }
        val bodyFat = latest { it.bodyFatPct }
        val bmi = latest { it.bmi } ?: BodyEnergy.bmi(weight, height)
        val fatMass = latest { it.fatMassKg } ?: BodyEnergy.fatMassKg(weight, bodyFat)
        val leanMass = latest { it.leanMassKg } ?: BodyEnergy.leanMassKg(weight, bodyFat)
        val bmr = latest { it.bmr }
            ?: BodyEnergy.bmr(weight, height, profile?.ageYears(today), profile?.gender)
        val tdee = profile?.let {
            BodyEnergy.tdee(weight ?: it.weightKg, height ?: it.heightCm, it.ageYears(today), it.gender, it.activityLevel)
        }

        // Trend series: (dayIndex, value), oldest→newest, dropping rows missing the metric.
        fun trend(select: (BodyMetric) -> Double?): List<Pair<Float, Float>> =
            ordered.mapIndexedNotNull { i, m -> select(m)?.let { i.toFloat() to it.toFloat() } }

        val weightTrend = trend { it.weightKg }
        val weightChange = if (weightTrend.size >= 2) {
            (weightTrend.last().second - weightTrend.first().second).toDouble()
        } else null

        return BodyAnalytics(
            latestWeightKg = weight,
            latestHeightCm = height,
            bmi = bmi,
            bodyFatPct = bodyFat,
            fatMassKg = fatMass,
            leanMassKg = leanMass,
            bodyWaterKg = latest { it.bodyWaterKg },
            boneMassKg = latest { it.boneMassKg },
            bmr = bmr,
            tdee = tdee,
            weightChangeKg = weightChange,
            weightTrend = weightTrend,
            bodyFatTrend = trend { it.bodyFatPct },
            hasAnyData = true,
        )
    }
}
