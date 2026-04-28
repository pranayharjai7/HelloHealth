package com.hellohealth.data.health

import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.domain.model.HealthSummary
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    private val healthConnectClient: HealthConnectClient?,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    fun getAvailability(): Int {
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

    val isAvailable: Boolean
        get() = getAvailability() == HealthConnectClient.SDK_AVAILABLE || healthConnectClient != null

    fun getHealthConnectSettingsIntent(): Intent {
        return if (android.os.Build.VERSION.SDK_INT >= 34) {
            Intent("android.intent.action.VIEW_HEALTH_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
            }
        } else {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        }
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        if (healthConnectClient == null) return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        val essentialPermissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class)
        )
        return granted.containsAll(essentialPermissions)
    }

    suspend fun fetchHealthSummary(): HealthSummary {
        if (healthConnectClient == null) return HealthSummary()

        val startOfDay = ZonedDateTime.now().withHour(0).withMinute(0).withSecond(0).toInstant()
        val now = Instant.now()
        val timeRangeFilter = TimeRangeFilter.between(startOfDay, now)

        return try {
            val steps = safeAggregate { aggregateSteps(timeRangeFilter) } ?: 0L
            val activeCalories = safeAggregate { aggregateActiveCalories(timeRangeFilter) } ?: 0.0
            val distance = safeAggregate { aggregateDistance(timeRangeFilter) } ?: 0.0
            val sessions = try { fetchExerciseSessions(timeRangeFilter) } catch (e: Exception) { emptyList() }
            
            val totalCalories = safeAggregate { aggregateTotalCalories(timeRangeFilter) } ?: activeCalories
            var bmr = safeFetch { fetchLatestBasalMetabolicRate() } ?: 0.0
            if (bmr == 0.0) bmr = 1800.0 // Default BMR if record missing
            
            // Refine active calories if direct reading is low but total is high
            val nowCalendar = java.util.Calendar.getInstance()
            val minutesPassedToday = nowCalendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(java.util.Calendar.MINUTE)
            val bmrSoFar = (bmr / 1440.0) * minutesPassedToday
            
            val refinedActiveCalories = if (totalCalories > bmrSoFar) {
                maxOf(activeCalories, totalCalories - bmrSoFar)
            } else {
                activeCalories
            }

            val weight = safeFetch { fetchLatestWeight() }
            val height = safeFetch { fetchLatestHeight() }
            val bodyFat = safeFetch { fetchLatestBodyFat() }
            val heartRate = safeAggregate { aggregateHeartRate(timeRangeFilter) }?.toInt()
            val oxygen = safeFetch { fetchLatestOxygenSaturation() }
            val vo2max = safeFetch { fetchLatestVo2Max() }
            val bloodPressure = safeFetch { fetchLatestBloodPressure() }
            val glucose = safeFetch { fetchLatestBloodGlucose() }
            val sleep = try { fetchLatestSleepSession() } catch (e: Exception) { null }

            val activeTime = sessions.sumOf { it.durationMinutes }.coerceAtLeast(
                if (steps > 0) (steps / 100).coerceAtMost(60) else 0L
            )

            HealthSummary(
                steps = steps,
                activeCalories = refinedActiveCalories,
                activeTimeMinutes = activeTime,
                distanceKm = distance / 1000.0,
                totalCalories = totalCalories,
                basalMetabolicRate = bmr,
                weight = weight,
                height = height,
                bodyFat = bodyFat,
                heartRateAvg = heartRate,
                oxygenSaturation = oxygen,
                vo2max = vo2max,
                bloodPressureSystolic = bloodPressure?.first,
                bloodPressureDiastolic = bloodPressure?.second,
                bloodGlucose = glucose,
                sleepDurationMinutes = sleep?.first ?: 0,
                sleepStartTime = sleep?.second,
                sleepEndTime = sleep?.third,
                exerciseSessions = sessions
            )
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error in fetchHealthSummary", e)
            HealthSummary()
        }
    }

    suspend fun fetchWeeklyStats(): com.hellohealth.domain.model.WeeklyStats {
        if (healthConnectClient == null) return com.hellohealth.domain.model.WeeklyStats()
        
        val stats = mutableListOf<com.hellohealth.domain.model.DailyStat>()
        val now = ZonedDateTime.now()
        
        for (i in 0..6) {
            val date = now.minusDays(i.toLong())
            val startOfDay = date.withHour(0).withMinute(0).withSecond(0).toInstant()
            val endOfDay = date.withHour(23).withMinute(59).withSecond(59).toInstant()
            val timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            
            val steps = safeAggregate { aggregateSteps(timeRangeFilter) } ?: 0L
            val calories = safeAggregate { aggregateActiveCalories(timeRangeFilter) } ?: 0.0
            val sleep = try { 
                val response = healthConnectClient.readRecords(
                    ReadRecordsRequest(recordType = SleepSessionRecord::class, timeRangeFilter = timeRangeFilter)
                )
                response.records.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
            } catch (e: Exception) { 0L }
            val heartRate = safeAggregate { aggregateHeartRate(timeRangeFilter) }?.toInt() ?: 0

            stats.add(
                com.hellohealth.domain.model.DailyStat(
                    date = date.toLocalDate(),
                    steps = steps,
                    calories = calories,
                    sleepMinutes = sleep,
                    avgHeartRate = heartRate
                )
            )
        }
        
        return com.hellohealth.domain.model.WeeklyStats(dailyStats = stats.reversed())
    }

    private suspend fun <T> safeAggregate(block: suspend () -> T): T? {
        return try { block() } catch (e: Exception) { 
            Log.w("HealthConnectManager", "Aggregation failed: ${e.message}")
            null 
        }
    }

    private suspend fun <T> safeFetch(block: suspend () -> T): T? {
        return try { block() } catch (e: Exception) { 
            Log.w("HealthConnectManager", "Fetch failed: ${e.message}")
            null 
        }
    }

    private suspend fun aggregateSteps(timeRangeFilter: TimeRangeFilter): Long {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(StepsRecord.COUNT_TOTAL) ?: 0L
    }

    private suspend fun aggregateActiveCalories(timeRangeFilter: TimeRangeFilter): Double {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0
    }

    private suspend fun aggregateTotalCalories(timeRangeFilter: TimeRangeFilter): Double {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories ?: 0.0
    }

    private suspend fun aggregateDistance(timeRangeFilter: TimeRangeFilter): Double {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(DistanceRecord.DISTANCE_TOTAL), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters ?: 0.0
    }

    private suspend fun aggregateHeartRate(timeRangeFilter: TimeRangeFilter): Long {
        val response = healthConnectClient?.aggregate(
            AggregateRequest(metrics = setOf(HeartRateRecord.BPM_AVG), timeRangeFilter = timeRangeFilter)
        )
        return response?.get(HeartRateRecord.BPM_AVG) ?: 0L
    }

    private suspend fun fetchLatestWeight(): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = WeightRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.weight?.inKilograms
    }

    private suspend fun fetchLatestHeight(): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = HeightRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.height?.inMeters
    }

    private suspend fun fetchLatestBodyFat(): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = BodyFatRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.percentage?.value
    }

    private suspend fun fetchLatestBasalMetabolicRate(): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = BasalMetabolicRateRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.basalMetabolicRate?.inKilocaloriesPerDay
    }

    private suspend fun fetchLatestOxygenSaturation(): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = OxygenSaturationRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.percentage?.value
    }

    private suspend fun fetchLatestVo2Max(): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = Vo2MaxRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.vo2MillilitersPerMinuteKilogram
    }

    private suspend fun fetchLatestBloodPressure(): Pair<Double, Double>? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = BloodPressureRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        val record = response?.records?.firstOrNull() ?: return null
        return Pair(record.systolic.inMillimetersOfMercury, record.diastolic.inMillimetersOfMercury)
    }

    private suspend fun fetchLatestBloodGlucose(): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = BloodGlucoseRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.level?.inMilligramsPerDeciliter
    }

    private suspend fun fetchLatestSleepSession(): Triple<Long, Instant, Instant>? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1)
        )
        val record = response?.records?.firstOrNull() ?: return null
        val duration = java.time.Duration.between(record.startTime, record.endTime).toMinutes()
        return Triple(duration, record.startTime, record.endTime)
    }

    private suspend fun fetchExerciseSessions(timeRangeFilter: TimeRangeFilter): List<ExerciseSession> {
        val client = healthConnectClient ?: return emptyList()
        val response = client.readRecords(
            ReadRecordsRequest(recordType = ExerciseSessionRecord::class, timeRangeFilter = timeRangeFilter)
        )
        return response.records.map { record ->
            val metrics = client.aggregate(
                AggregateRequest(metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, DistanceRecord.DISTANCE_TOTAL), timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime))
            )
            ExerciseSession(
                title = record.title,
                type = record.exerciseType,
                typeLabel = getExerciseTypeLabel(record.exerciseType),
                startTime = record.startTime,
                endTime = record.endTime,
                durationMinutes = java.time.Duration.between(record.startTime, record.endTime).toMinutes(),
                calories = metrics[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                distanceKm = metrics[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
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
