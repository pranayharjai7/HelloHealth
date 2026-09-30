package com.hellohealth.domain.model

import java.time.LocalDate

/**
 * A 7-day, [LocalDate]-aligned cross-dimension series for the Insights screen's correlation views.
 * One [DayPoint] per calendar day (oldest first), with each dimension's daily value or null when
 * that day has no data for it — charts drop nulls rather than plotting a fake zero.
 *
 * Deliberately DESCRIPTIVE, not diagnostic: it exposes aligned daily values so the UI can show how
 * dimensions move together. It asserts no causation and carries no medical judgement — copy in the
 * UI must stay descriptive ("these moved together"), never prescriptive.
 */
data class CrossDimensionInsights(
    val days: List<DayPoint> = emptyList(),
) {
    data class DayPoint(
        val date: LocalDate,
        /** Share of the day's mood logs that were positive-valence (0..1), or null if none logged. */
        val moodPositivity: Double?,
        val moodLogCount: Int,
        /** Per-day recovery readiness 0..100, or null when insufficient history/データ that day. */
        val readiness: Int?,
        val caloriesIn: Double?,
        val caloriesOut: Double?,
        val sleepHours: Double?,
    ) {
        /** Net energy balance for the day (out − in), or null when either side is missing. */
        val netKcal: Double?
            get() = if (caloriesIn != null && caloriesOut != null) caloriesOut - caloriesIn else null
    }

    /** True when at least one day has any cross-dimension data — gates the empty state. */
    val hasAnyData: Boolean
        get() = days.any {
            it.moodPositivity != null || it.readiness != null ||
                it.caloriesIn != null || it.caloriesOut != null || it.sleepHours != null
        }

    /**
     * Pairs of (sleepHours[day], readiness[day+1]) for the correlation scatter — sleep on a night
     * against the NEXT day's readiness, the one relationship worth surfacing. Only days where both
     * exist are included. Purely observational; the UI must not label it causal.
     */
    val sleepVsNextReadiness: List<Pair<Double, Int>>
        get() = days.zipWithNext().mapNotNull { (d0, d1) ->
            val s = d0.sleepHours
            val r = d1.readiness
            if (s != null && r != null) s to r else null
        }
}
