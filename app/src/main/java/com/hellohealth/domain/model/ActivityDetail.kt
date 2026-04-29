package com.hellohealth.domain.model

import java.time.Instant

data class ActivityDetail(
    val sessionId: String,
    val title: String,
    val activityName: String,
    val startTime: Instant,
    val endTime: Instant,
    val durationSeconds: Long,
    val caloriesBurned: Double?,
    val distanceKm: Double?,
    val steps: Long?,
    val activeMinutes: Long,
    val moveMinutes: Long,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val averagePaceSecondsPerKm: Double?,
    val fastestPaceSecondsPerKm: Double?,
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val heartRatePoints: List<ActivityChartPoint> = emptyList(),
    val pacePoints: List<ActivityChartPoint> = emptyList(),
    val elevationPoints: List<ActivityChartPoint> = emptyList(),
    val routePoints: List<ActivityRoutePoint> = emptyList(),
    val routeMessage: String? = null,
    val timeline: List<ActivityTimelineEntry> = emptyList(),
    val dataSourceLabel: String = "Health Connect",
    val syncedFromLabel: String? = null
)

data class ActivityChartPoint(
    val minutesFromStart: Float,
    val value: Float
)

data class ActivityRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val minutesFromStart: Float = 0f
)

data class ActivityTimelineEntry(
    val timestamp: Instant,
    val title: String,
    val subtitle: String? = null,
    val value: String? = null
)
