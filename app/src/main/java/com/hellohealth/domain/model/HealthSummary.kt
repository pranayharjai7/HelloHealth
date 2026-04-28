package com.hellohealth.domain.model

data class HealthSummary(
    // Activity
    val steps: Long = 0,
    val stepsGoal: Long = 10000,
    val activeCalories: Double = 0.0,
    val caloriesGoal: Double = 500.0,
    val activeTimeMinutes: Long = 0,
    val activeTimeGoal: Long = 60,
    val distanceKm: Double = 0.0,
    
    // Energy
    val totalCalories: Double = 0.0,
    val basalMetabolicRate: Double = 0.0,
    
    // Body
    val weight: Double? = null,
    val height: Double? = null,
    val bodyFat: Double? = null,
    
    // Heart & Vitals
    val heartRateAvg: Int? = null,
    val oxygenSaturation: Double? = null,
    val bloodPressureSystolic: Double? = null,
    val bloodPressureDiastolic: Double? = null,
    val bloodGlucose: Double? = null,
    
    // Performance
    val vo2max: Double? = null,
    
    // Sleep
    val sleepDurationMinutes: Long = 0,
    val sleepStartTime: java.time.Instant? = null,
    val sleepEndTime: java.time.Instant? = null,
    
    // Sessions
    val exerciseSessions: List<ExerciseSession> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)
