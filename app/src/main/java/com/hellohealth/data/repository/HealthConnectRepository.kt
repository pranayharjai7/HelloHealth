package com.hellohealth.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.domain.model.WorkoutSummary
import com.hellohealth.domain.repository.ActivityRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectRepository @Inject constructor(
    private val healthConnectClient: HealthConnectClient?,
    @ApplicationContext private val context: Context
) : ActivityRepository {

    override fun getAvailability(): Int {
        val status = HealthConnectClient.getSdkStatus(context)
        if (healthConnectClient != null) return HealthConnectClient.SDK_AVAILABLE
        if (android.os.Build.VERSION.SDK_INT >= 34) return HealthConnectClient.SDK_AVAILABLE
        
        if (status == HealthConnectClient.SDK_UNAVAILABLE) {
            try {
                context.packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
                return HealthConnectClient.SDK_AVAILABLE
            } catch (e: Exception) {}
        }
        return status
    }

    override fun isAvailable(): Boolean = getAvailability() == HealthConnectClient.SDK_AVAILABLE

    override fun getSettingsIntent(context: Context): Intent {
        return if (android.os.Build.VERSION.SDK_INT >= 34) {
            Intent("android.intent.action.VIEW_HEALTH_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            }
        } else {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        }
    }

    override fun getRequiredPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    override suspend fun hasPermissions(): Boolean {
        if (healthConnectClient == null) return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        val essential = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class)
        )
        return granted.containsAll(essential)
    }

    override suspend fun fetchSummary(): WorkoutSummary {
        if (healthConnectClient == null) return WorkoutSummary()

        val startOfDay = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).toInstant()
        val now = Instant.now()
        val timeRangeFilter = TimeRangeFilter.between(startOfDay, now)

        return try {
            val steps = try { aggregateSteps(timeRangeFilter) } catch (e: Exception) { 0L }
            val calories = try { aggregateCalories(timeRangeFilter) } catch (e: Exception) { 0.0 }
            val distance = try { aggregateDistance(timeRangeFilter) } catch (e: Exception) { 0.0 }
            val floors = try { aggregateFloors(timeRangeFilter) } catch (e: Exception) { 0.0 }
            val sessions = try { fetchExerciseSessions(timeRangeFilter) } catch (e: Exception) { emptyList() }
            
            val activeTime = sessions.sumOf { it.durationMinutes }.coerceAtLeast(
                if (steps > 0) (steps / 100).coerceAtMost(60) else 0L
            )

            WorkoutSummary(
                steps = steps,
                activeCalories = calories,
                activeTimeMinutes = activeTime,
                distanceKm = distance / 1000.0,
                floorsClimbed = floors,
                exerciseSessions = sessions
            )
        } catch (e: Exception) {
            WorkoutSummary()
        }
    }

    override fun sync(): Flow<Result<WorkoutSummary>> = flow {
        try {
            emit(Result.success(fetchSummary()))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    private suspend fun aggregateSteps(timeRangeFilter: TimeRangeFilter): Long {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(StepsRecord.COUNT_TOTAL) ?: 0L
    }

    private suspend fun aggregateCalories(timeRangeFilter: TimeRangeFilter): Double {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0
    }

    private suspend fun aggregateDistance(timeRangeFilter: TimeRangeFilter): Double {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(DistanceRecord.DISTANCE_TOTAL), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters ?: 0.0
    }

    private suspend fun aggregateFloors(timeRangeFilter: TimeRangeFilter): Double {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL) ?: 0.0
    }

    private suspend fun fetchExerciseSessions(timeRangeFilter: TimeRangeFilter): List<ExerciseSession> {
        val client = healthConnectClient ?: return emptyList()
        val response = client.readRecords(
            ReadRecordsRequest(recordType = ExerciseSessionRecord::class, timeRangeFilter = timeRangeFilter)
        )
        
        return response.records.map { record ->
            val sessionTimeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
            val sessionMetrics = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = sessionTimeFilter
                )
            )

            ExerciseSession(
                title = record.title,
                type = record.exerciseType,
                typeLabel = getExerciseTypeLabel(record.exerciseType),
                startTime = record.startTime,
                endTime = record.endTime,
                durationMinutes = java.time.Duration.between(record.startTime, record.endTime).toMinutes(),
                calories = sessionMetrics[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                distanceKm = sessionMetrics[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
            )
        }
    }

    private fun getExerciseTypeLabel(type: Int): String {
        return when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength Training"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
            else -> "Workout"
        }
    }
}
