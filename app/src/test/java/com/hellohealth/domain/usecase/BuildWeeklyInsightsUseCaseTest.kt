package com.hellohealth.domain.usecase

import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyStat
import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.model.WeeklyStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BuildWeeklyInsightsUseCaseTest {

    private val useCase = BuildWeeklyInsightsUseCase()

    @Test
    fun `builds consistency and streak metrics from weekly stats`() {
        val goals = ActivityGoals(steps = 6000, activeCalories = 400, activeMinutes = 45)
        val stats = WeeklyStats(
            dailyStats = listOf(
                dailyStat(0, 6200, 450.0, 50, 430, 72),
                dailyStat(1, 7100, 500.0, 60, 460, 70),
                dailyStat(2, 3000, 260.0, 20, 390, 74),
                dailyStat(3, 6500, 430.0, 50, 440, 71),
                dailyStat(4, 6800, 420.0, 55, 450, 73),
                dailyStat(5, 6400, 410.0, 48, 425, 69),
                dailyStat(6, 6900, 470.0, 52, 455, 68)
            )
        )

        val insights = useCase(
            weeklyStats = stats,
            goals = goals,
            foodPreferences = FoodPreferences(dietType = "vegetarian")
        )

        assertEquals(6, insights.stepGoalDays)
        assertEquals(6, insights.calorieGoalDays)
        assertEquals(6, insights.activeMinutesGoalDays)
        assertEquals(2, insights.currentStepStreak)
        assertEquals(7, insights.completedDays)
        assertTrue(insights.cards.any { it.title == "Nutrition Context" })
    }

    @Test
    fun `returns empty summary for empty weekly stats`() {
        val insights = useCase(
            weeklyStats = WeeklyStats(),
            goals = ActivityGoals(),
            foodPreferences = FoodPreferences()
        )

        assertEquals(0, insights.cards.size)
        assertEquals(0, insights.currentStepStreak)
        assertEquals(0, insights.stepGoalDays)
    }

    @Test
    fun `averages ignore future days in the displayed week`() {
        val today = LocalDate.now()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val stats = WeeklyStats(
            dailyStats = (0..6).map { index ->
                val date = monday.plusDays(index.toLong())
                val isFuture = date.isAfter(today)
                DailyStat(
                    date = date,
                    steps = if (isFuture) 0 else 6000,
                    calories = if (isFuture) 0.0 else 400.0,
                    activeMinutes = if (isFuture) 0 else 45,
                    sleepMinutes = if (isFuture) 0 else 420,
                    avgHeartRate = if (isFuture) 0 else 70
                )
            }
        )

        val insights = useCase(
            weeklyStats = stats,
            goals = ActivityGoals(steps = 6000, activeCalories = 400, activeMinutes = 45),
            foodPreferences = FoodPreferences()
        )

        assertEquals(stats.dailyStats.count { !it.date.isAfter(today) }, insights.completedDays)
        assertEquals(6000, insights.averageSteps)
        assertEquals(400, insights.averageCalories)
        assertEquals(45, insights.averageActiveMinutes)
    }

    private fun dailyStat(
        daysAgo: Long,
        steps: Long,
        calories: Double,
        activeMinutes: Long,
        sleepMinutes: Long,
        avgHeartRate: Int
    ): DailyStat {
        return DailyStat(
            date = LocalDate.now().minusDays(daysAgo),
            steps = steps,
            calories = calories,
            activeMinutes = activeMinutes,
            sleepMinutes = sleepMinutes,
            avgHeartRate = avgHeartRate
        )
    }
}
