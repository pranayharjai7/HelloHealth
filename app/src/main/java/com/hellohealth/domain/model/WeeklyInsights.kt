package com.hellohealth.domain.model

data class InsightCard(
    val title: String,
    val body: String
)

data class WeeklyInsights(
    val completedDays: Int = 0,
    val averageSteps: Long = 0,
    val averageCalories: Int = 0,
    val averageActiveMinutes: Long = 0,
    val averageSleepMinutes: Long = 0,
    val averageHeartRate: Int = 0,
    val stepGoalDays: Int = 0,
    val calorieGoalDays: Int = 0,
    val activeMinutesGoalDays: Int = 0,
    val currentStepStreak: Int = 0,
    val bestDayLabel: String = "--",
    val bestDayReason: String = "--",
    val cards: List<InsightCard> = emptyList()
)
