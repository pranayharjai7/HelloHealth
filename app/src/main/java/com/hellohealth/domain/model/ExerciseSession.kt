package com.hellohealth.domain.model

data class ExerciseSession(
    val title: String?,
    val type: Int, // Use ExerciseSessionRecord types
    val typeLabel: String,
    val startTime: java.time.Instant,
    val endTime: java.time.Instant,
    val durationMinutes: Long,
    val calories: Double?,
    val distanceKm: Double?
)
