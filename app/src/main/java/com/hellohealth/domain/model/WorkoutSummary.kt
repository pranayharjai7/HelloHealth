package com.hellohealth.domain.model

data class WorkoutSummary(
    val steps: Long = 0,
    val stepsGoal: Long = 10000,
    val activeCalories: Double = 0.0,
    val caloriesGoal: Double = 500.0,
    val activeTimeMinutes: Long = 0,
    val activeTimeGoal: Long = 60,
    val distanceKm: Double = 0.0,
    val floorsClimbed: Double = 0.0,
    val heartRateAverage: Int? = null,
    val exerciseSessions: List<ExerciseSession> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)
