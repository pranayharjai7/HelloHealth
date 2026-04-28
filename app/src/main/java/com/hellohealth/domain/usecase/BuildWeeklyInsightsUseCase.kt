package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyStat
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.model.InsightCard
import com.hellohealth.domain.model.WeeklyInsights
import com.hellohealth.domain.model.WeeklyStats
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

class BuildWeeklyInsightsUseCase @Inject constructor() {

    operator fun invoke(
        weeklyStats: WeeklyStats,
        goals: ActivityGoals,
        foodPreferences: FoodPreferences
    ): WeeklyInsights {
        val stats = weeklyStats.dailyStats.sortedBy { it.date }
        if (stats.isEmpty()) return WeeklyInsights()
        val today = LocalDate.now()
        val completedStats = stats.filter { !it.date.isAfter(today) }
        if (completedStats.isEmpty()) return WeeklyInsights()
        val completedDays = completedStats.size

        val averageSteps = completedStats.map { it.steps }.average().roundToInt().toLong()
        val averageCalories = completedStats.map { it.calories }.average().roundToInt()
        val averageActiveMinutes = completedStats.map { it.activeMinutes }.average().roundToInt().toLong()
        val averageSleepMinutes = completedStats.map { it.sleepMinutes }.average().roundToInt().toLong()
        val nonZeroHeartRates = completedStats.map { it.avgHeartRate }.filter { it > 0 }
        val averageHeartRate = if (nonZeroHeartRates.isEmpty()) 0 else nonZeroHeartRates.average().roundToInt()

        val stepGoalDays = completedStats.count { it.steps >= goals.steps }
        val calorieGoalDays = completedStats.count { it.calories >= goals.activeCalories }
        val activeMinutesGoalDays = completedStats.count { it.activeMinutes >= goals.activeMinutes }
        val currentStepStreak = completedStats.asReversed().takeWhile { it.steps >= goals.steps }.count()

        val bestDay = completedStats.maxByOrNull { scoreDay(it, goals) } ?: completedStats.last()
        val bestDayLabel = bestDay.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val bestDayReason = buildBestDayReason(bestDay, goals)

        return WeeklyInsights(
            completedDays = completedDays,
            averageSteps = averageSteps,
            averageCalories = averageCalories,
            averageActiveMinutes = averageActiveMinutes,
            averageSleepMinutes = averageSleepMinutes,
            averageHeartRate = averageHeartRate,
            stepGoalDays = stepGoalDays,
            calorieGoalDays = calorieGoalDays,
            activeMinutesGoalDays = activeMinutesGoalDays,
            currentStepStreak = currentStepStreak,
            bestDayLabel = bestDayLabel,
            bestDayReason = bestDayReason,
            cards = buildCards(completedStats, goals, foodPreferences, completedDays, stepGoalDays, calorieGoalDays, activeMinutesGoalDays, currentStepStreak, averageSleepMinutes, averageHeartRate)
        )
    }

    private fun scoreDay(stat: DailyStat, goals: ActivityGoals): Double {
        val stepScore = stat.steps.toDouble() / goals.steps.coerceAtLeast(1)
        val calorieScore = stat.calories / goals.activeCalories.coerceAtLeast(1)
        val minutesScore = stat.activeMinutes.toDouble() / goals.activeMinutes.coerceAtLeast(1)
        val sleepBonus = (stat.sleepMinutes / 480.0).coerceAtMost(1.0)
        return stepScore + calorieScore + minutesScore + sleepBonus
    }

    private fun buildBestDayReason(stat: DailyStat, goals: ActivityGoals): String {
        val hits = buildList {
            if (stat.steps >= goals.steps) add("steps goal")
            if (stat.calories >= goals.activeCalories) add("calorie goal")
            if (stat.activeMinutes >= goals.activeMinutes) add("active minutes goal")
        }

        return when {
            hits.isNotEmpty() -> "Hit ${hits.joinToString()} with ${stat.sleepMinutes / 60}h ${(stat.sleepMinutes % 60)}m sleep."
            else -> "Strong balance across movement, burn, and recovery."
        }
    }

    private fun buildCards(
        stats: List<DailyStat>,
        goals: ActivityGoals,
        foodPreferences: FoodPreferences,
        completedDays: Int,
        stepGoalDays: Int,
        calorieGoalDays: Int,
        activeMinutesGoalDays: Int,
        currentStepStreak: Int,
        averageSleepMinutes: Long,
        averageHeartRate: Int
    ): List<InsightCard> {
        val cards = mutableListOf<InsightCard>()

        cards += InsightCard(
            title = "Goal Consistency",
            body = "Across the $completedDays completed day(s) this week, you hit your step goal on $stepGoalDays day(s), calorie goal on $calorieGoalDays day(s), and active minutes goal on $activeMinutesGoalDays day(s)."
        )

        val stepTrend = compareRecentPerformance(stats, selector = { it.steps.toDouble() })
        cards += InsightCard(
            title = "Momentum",
            body = when {
                stepTrend > 0.1 -> "Your recent step count is trending up. Keep the current routine and protect the streak."
                stepTrend < -0.1 -> "Your recent step count dipped versus the start of the week. A shorter walk block could stabilize the trend."
                else -> "Your step volume stayed steady this week, which is a solid sign of consistency."
            }
        )

        cards += InsightCard(
            title = "Recovery",
            body = when {
                averageSleepMinutes < 420 -> "Average sleep stayed under 7 hours. Recovery is likely the biggest lever for better energy and workout quality."
                averageHeartRate > 0 && averageHeartRate >= 85 -> "Average heart rate ran a little high this week. Prioritize hydration, lighter recovery work, and sleep."
                else -> "Recovery looks steady overall. Keep protecting sleep on the days with higher activity."
            }
        )

        cards += InsightCard(
            title = "Nutrition Context",
            body = buildNutritionGuidance(foodPreferences, goals)
        )

        if (currentStepStreak > 0) {
            cards += InsightCard(
                title = "Current Streak",
                body = "You are on a $currentStepStreak-day step-goal streak. Tomorrow's target is ${goals.steps} steps."
            )
        }

        return cards
    }

    private fun compareRecentPerformance(
        stats: List<DailyStat>,
        selector: (DailyStat) -> Double
    ): Double {
        if (stats.size < 4) return 0.0
        val firstWindow = stats.take(3).map(selector).average()
        val lastWindow = stats.takeLast(3).map(selector).average()
        if (firstWindow == 0.0) return 0.0
        return (lastWindow - firstWindow) / firstWindow
    }

    private fun buildNutritionGuidance(
        foodPreferences: FoodPreferences,
        goals: ActivityGoals
    ): String {
        val dietGuidance = when (foodPreferences.dietType) {
            "vegetarian" -> "Build recovery meals around dairy, eggs, tofu, lentils, or Greek yogurt."
            "vegan" -> "Prioritize tofu, tempeh, beans, lentils, and fortified foods after workouts."
            "pescatarian" -> "Lean on fish, yogurt, eggs, and legumes to support recovery."
            else -> "Aim for a balanced protein plus carb meal after bigger activity days."
        }

        val allergyGuidance = when {
            foodPreferences.allergies.isEmpty() -> ""
            else -> " Watch out for ${foodPreferences.allergies.joinToString()} when planning quick recovery meals."
        }

        val cuisineGuidance = when {
            foodPreferences.cuisinePreferences.isEmpty() -> ""
            else -> " Good fit options from your preferred cuisines: ${foodPreferences.cuisinePreferences.joinToString()}."
        }

        return buildString {
            append(dietGuidance)
            append(" Your current daily burn goal is ${goals.activeCalories} Cal and active time goal is ${goals.activeMinutes} min, so recovery meals should stay consistent.")
            append(allergyGuidance)
            append(cuisineGuidance)
        }
    }
}
