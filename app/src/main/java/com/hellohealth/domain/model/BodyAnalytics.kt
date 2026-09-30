package com.hellohealth.domain.model

/**
 * Output of [com.hellohealth.domain.usecase.BodyAnalyticsUseCase] — the latest body-composition
 * values plus trend series for the Health screen's Body section. Every value is nullable so the UI
 * dashes a missing metric (never renders a fake 0). Trend series are `(x, y)` points where x is the
 * day index (0-based, oldest→newest) — the same shape the vitals TrendLine consumes; a series with
 * < 2 points renders a value but a dashed/empty slope.
 */
data class BodyAnalytics(
    val latestWeightKg: Double? = null,
    val latestHeightCm: Double? = null,
    val bmi: Double? = null,
    val bodyFatPct: Double? = null,
    val fatMassKg: Double? = null,
    val leanMassKg: Double? = null,
    val bodyWaterKg: Double? = null,
    val boneMassKg: Double? = null,
    val bmr: Double? = null,
    /** TDEE derived from the profile (BodyEnergy.tdee); null when the profile is too sparse. */
    val tdee: Double? = null,
    /** Weight change over the window (latest − earliest), kg; null when < 2 weight points. */
    val weightChangeKg: Double? = null,
    /** Weight trend points (dayIndex, kg), oldest→newest, for the chart. */
    val weightTrend: List<Pair<Float, Float>> = emptyList(),
    val bodyFatTrend: List<Pair<Float, Float>> = emptyList(),
    val hasAnyData: Boolean = false,
)
