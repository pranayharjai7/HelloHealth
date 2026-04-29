package com.hellohealth.domain.model

data class ExerciseSession(
    val id: String = "",
    val title: String?,
    val type: Int, // Use ExerciseSessionRecord types
    val typeLabel: String,
    val startTime: java.time.Instant,
    val endTime: java.time.Instant,
    val durationMinutes: Long,
    val calories: Double?,
    val distanceKm: Double?,
    val sourcePackageName: String? = null,
    val sourceAppName: String? = null
)
