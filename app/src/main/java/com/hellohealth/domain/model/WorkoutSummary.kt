package com.hellohealth.domain.model

data class WorkoutSummary(
    val steps: Long = 0,
    val activeCalories: Double = 0.0,
    val activeTimeMinutes: Long = 0,
    val distanceKm: Double = 0.0,
    val lastUpdated: Long = System.currentTimeMillis()
)
