package com.hellohealth.data.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.hellohealth.domain.model.WorkoutSummary
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    private val healthConnectClient: HealthConnectClient?
) {
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        return healthConnectClient?.permissionController?.getGrantedPermissions()?.containsAll(permissions) ?: false
    }

    suspend fun fetchWorkoutSummary(): WorkoutSummary {
        if (healthConnectClient == null) return WorkoutSummary()

        val startTime = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).toInstant()
        val endTime = Instant.now()
        val timeRangeFilter = TimeRangeFilter.between(startTime, endTime)

        return try {
            val steps = aggregateSteps(timeRangeFilter)
            val calories = aggregateCalories(timeRangeFilter)
            val distance = aggregateDistance(timeRangeFilter)
            val activeTime = fetchActiveTime(timeRangeFilter)

            WorkoutSummary(
                steps = steps,
                activeCalories = calories,
                activeTimeMinutes = activeTime,
                distanceKm = distance / 1000.0
            )
        } catch (e: Exception) {
            WorkoutSummary()
        }
    }

    private suspend fun aggregateSteps(timeRangeFilter: TimeRangeFilter): Long {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = timeRangeFilter
            )
        )
        return response?.get(StepsRecord.COUNT_TOTAL) ?: 0L
    }

    private suspend fun aggregateCalories(timeRangeFilter: TimeRangeFilter): Double {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = timeRangeFilter
            )
        )
        return response?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0
    }

    private suspend fun aggregateDistance(timeRangeFilter: TimeRangeFilter): Double {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = timeRangeFilter
            )
        )
        return response?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters ?: 0.0
    }

    private suspend fun fetchActiveTime(timeRangeFilter: TimeRangeFilter): Long {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = timeRangeFilter
            )
        )
        return response?.records?.sumOf { 
            java.time.Duration.between(it.startTime, it.endTime).toMinutes()
        } ?: 0L
    }
}
