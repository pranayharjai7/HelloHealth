package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.CrossDimensionInsights
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.Valence
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.ReadinessStatus
import com.hellohealth.domain.vitals.ReadinessScoreCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Pure builder for the 7-day cross-dimension series ([CrossDimensionInsights]). Aligns the four
 * dimensions on a canonical [LocalDate] key and computes each day's mood positivity, recovery
 * readiness, and energy in/out. Mirrors [BuildWeeklyInsightsUseCase]'s discipline: no I/O, no
 * `LocalDate.now()` inside (the caller passes `today` and the zone), so it is fully deterministic
 * and unit-testable.
 *
 * Per-day readiness is computed with the SAME [ReadinessScoreCalculator] the dashboard uses, feeding
 * each day as "today" against the prior rollups as history — so the trend matches the card. Days
 * before the calculator has enough history read as null (not zero).
 */
class BuildCrossInsightsUseCase @Inject constructor(
    private val readinessCalculator: ReadinessScoreCalculator,
) {

    /**
     * @param today the most recent day of the window (inclusive); the window is `[today-6, today]`.
     * @param zone the zone used to bucket emotion timestamps into local days.
     * @param emotions the window's mood logs (from `observeWindow`).
     * @param rollups recovery vitals over a window that SHOULD extend >= 7 days before `today` so the
     *   readiness calculator has baseline history; extras are ignored for display but used as history.
     * @param nutritionByDay per-day nutrition summaries keyed by ISO date (from combined per-day flows).
     * @param caloriesOutByDay per-day calories-out (active + BMR) keyed by ISO date, or empty.
     */
    operator fun invoke(
        today: LocalDate,
        zone: ZoneId,
        emotions: List<EmotionRecord>,
        rollups: List<HealthMetricsData>,
        nutritionByDay: Map<String, NutritionDaySummary>,
        caloriesOutByDay: Map<String, Double>,
    ): CrossDimensionInsights {
        val windowDays = (0..6).map { today.minusDays((6 - it).toLong()) } // oldest -> newest

        // Bucket emotions into local days.
        val moodByDay: Map<LocalDate, List<EmotionRecord>> = emotions.groupBy { rec ->
            Instant.ofEpochMilli(rec.timestampUtcEpochMs).atZone(zone).toLocalDate()
        }

        // Index rollups by local day for readiness history + per-day lookup.
        val rollupByDay: Map<LocalDate, HealthMetricsData> = rollups.associateBy { r ->
            Instant.ofEpochMilli(r.dateMillis).atZone(zone).toLocalDate()
        }

        val points = windowDays.map { day ->
            // Mood positivity: share of the day's logs that are positive-valence.
            val logs = moodByDay[day].orEmpty()
            val moodPositivity = if (logs.isEmpty()) null
            else logs.count { it.emotion.valence == Valence.POSITIVE }.toDouble() / logs.size

            // Readiness: reuse the calculator with this day as "today" and strictly-prior days as history.
            val todayMetric = rollupByDay[day]
            val readiness = if (todayMetric == null) null else {
                val history = rollups.filter {
                    Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate().isBefore(day)
                }
                val score = readinessCalculator.calculate(history, todayMetric)
                if (score.status == ReadinessStatus.INSUFFICIENT_DATA) null else score.score
            }

            val iso = day.toString()
            val nutrition = nutritionByDay[iso]
            val caloriesIn = nutrition?.caloriesConsumed?.takeIf { it > 0 }
            val caloriesOut = caloriesOutByDay[iso]?.takeIf { it > 0 }
            val sleepHours = todayMetric?.sleepDurationMinutes?.takeIf { it > 0 }?.let { it / 60.0 }

            CrossDimensionInsights.DayPoint(
                date = day,
                moodPositivity = moodPositivity,
                moodLogCount = logs.size,
                readiness = readiness,
                caloriesIn = caloriesIn,
                caloriesOut = caloriesOut,
                sleepHours = sleepHours,
            )
        }

        return CrossDimensionInsights(days = points)
    }
}
