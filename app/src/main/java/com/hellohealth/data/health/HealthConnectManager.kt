package com.hellohealth.data.health

import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.hellohealth.domain.model.ActivityChartPoint
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.ActivityRoutePoint
import com.hellohealth.domain.model.ActivityTimelineEntry
import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.domain.model.HealthSummary
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
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
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class)
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

    suspend fun fetchHealthSummary(
        goals: ActivityGoals,
        date: LocalDate = LocalDate.now()
    ): HealthSummary {
        if (healthConnectClient == null) {
            return HealthSummary(
                stepsGoal = goals.steps.toLong(),
                caloriesGoal = goals.activeCalories.toDouble(),
                activeTimeGoal = goals.activeMinutes.toLong(),
                lastUpdated = 0L
            )
        }

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val startOfDay = date.atStartOfDay(zoneId).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)

        return try {
            val steps = safeAggregate { aggregateSteps(timeRangeFilter) } ?: 0L
            val activeCalories = safeAggregate { aggregateActiveCalories(timeRangeFilter) } ?: 0.0
            val distance = safeAggregate { aggregateDistance(timeRangeFilter) } ?: 0.0
            val sessions = try { fetchExerciseSessions(timeRangeFilter) } catch (e: Exception) { emptyList() }
            
            val totalCalories = safeAggregate { aggregateTotalCalories(timeRangeFilter) } ?: activeCalories
            var bmr = safeFetch { fetchLatestBasalMetabolicRate(endOfDay) } ?: 0.0
            if (bmr == 0.0) bmr = 1800.0 // Default BMR if record missing
            
            val minutesCovered = if (date == today) {
                val nowCalendar = java.util.Calendar.getInstance()
                nowCalendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(java.util.Calendar.MINUTE)
            } else {
                24 * 60
            }
            val bmrSoFar = (bmr / 1440.0) * minutesCovered
            
            val refinedActiveCalories = if (totalCalories > bmrSoFar) {
                maxOf(activeCalories, totalCalories - bmrSoFar)
            } else {
                activeCalories
            }

            val weight = safeFetch { fetchLatestWeight(endOfDay) }
            val height = safeFetch { fetchLatestHeight(endOfDay) }
            val bodyFat = safeFetch { fetchLatestBodyFat(endOfDay) }
            val heartRate = safeAggregate { aggregateHeartRate(timeRangeFilter) }?.toInt()
            val oxygen = safeFetch { fetchLatestOxygenSaturation(endOfDay) }
            val vo2max = safeFetch { fetchLatestVo2Max(endOfDay) }
            val bloodPressure = safeFetch { fetchLatestBloodPressure(endOfDay) }
            val glucose = safeFetch { fetchLatestBloodGlucose(endOfDay) }
            val sleep = try { fetchSleepSummary(startOfDay, endOfDay) } catch (e: Exception) { null }

            val activeTime = sessions.sumOf { it.durationMinutes }.coerceAtLeast(
                if (steps > 0) (steps / 100).coerceAtMost(60) else 0L
            )

            HealthSummary(
                steps = steps,
                stepsGoal = goals.steps.toLong(),
                activeCalories = refinedActiveCalories,
                caloriesGoal = goals.activeCalories.toDouble(),
                activeTimeMinutes = activeTime,
                activeTimeGoal = goals.activeMinutes.toLong(),
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
                exerciseSessions = sessions,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error in fetchHealthSummary", e)
            HealthSummary(
                stepsGoal = goals.steps.toLong(),
                caloriesGoal = goals.activeCalories.toDouble(),
                activeTimeGoal = goals.activeMinutes.toLong(),
                lastUpdated = 0L
            )
        }
    }

    suspend fun fetchWeeklyStats(): com.hellohealth.domain.model.WeeklyStats {
        if (healthConnectClient == null) return com.hellohealth.domain.model.WeeklyStats()
        
        val stats = mutableListOf<com.hellohealth.domain.model.DailyStat>()
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        
        for (i in 0..6) {
            val date = startOfWeek.plusDays(i.toLong())
            val startOfDay = date.atStartOfDay(zoneId).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            
            val steps = safeAggregate { aggregateSteps(timeRangeFilter) } ?: 0L
            val calories = safeAggregate { aggregateActiveCalories(timeRangeFilter) } ?: 0.0
            val sleep = try {
                fetchSleepSummary(startOfDay, endOfDay)?.first ?: 0L
            } catch (e: Exception) { 0L }
            val heartRate = safeAggregate { aggregateHeartRate(timeRangeFilter) }?.toInt() ?: 0
            val sessions = try { fetchExerciseSessions(timeRangeFilter) } catch (e: Exception) { emptyList() }
            val activeMinutes = sessions.sumOf { it.durationMinutes }.coerceAtLeast(
                if (steps > 0) (steps / 100).coerceAtMost(60) else 0L
            )

            stats.add(
                com.hellohealth.domain.model.DailyStat(
                    date = date,
                    steps = steps,
                    calories = calories,
                    activeMinutes = activeMinutes,
                    sleepMinutes = sleep,
                    avgHeartRate = heartRate
                )
            )
        }
        
        return com.hellohealth.domain.model.WeeklyStats(dailyStats = stats)
    }

    suspend fun fetchExerciseSessionDetail(
        sessionId: String,
        startTimeHint: Instant? = null,
        endTimeHint: Instant? = null
    ): ActivityDetail? {
        val client = healthConnectClient ?: return null
        if (sessionId.isBlank()) return null

        return try {
            val session = findExerciseSessionRecord(
                client = client,
                sessionId = sessionId,
                startTimeHint = startTimeHint,
                endTimeHint = endTimeHint
            ) ?: return null

            val sessionRange = TimeRangeFilter.between(session.startTime, session.endTime)
            val aggregates = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        StepsRecord.COUNT_TOTAL,
                        ElevationGainedRecord.ELEVATION_GAINED_TOTAL
                    ),
                    timeRangeFilter = sessionRange
                )
            )

            val heartRateSamples = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = sessionRange
                )
            ).records
                .flatMap { it.samples }
                .filter { it.time in session.startTime..session.endTime }
                .sortedBy { it.time }

            val speedSamples = client.readRecords(
                ReadRecordsRequest(
                    recordType = SpeedRecord::class,
                    timeRangeFilter = sessionRange
                )
            ).records
                .flatMap { it.samples }
                .filter { it.time in session.startTime..session.endTime }
                .sortedBy { it.time }

            val routeResult = session.exerciseRouteResult
            val routePoints = when (routeResult) {
                is ExerciseRouteResult.Data -> downSampleRoutePoints(
                    routeResult.exerciseRoute.route
                        .sortedBy { it.time }
                        .map {
                            ActivityRoutePoint(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                altitudeMeters = it.altitude?.inMeters,
                                minutesFromStart = minutesFromSessionStart(session.startTime, it.time)
                            )
                        }
                )
                else -> emptyList()
            }

            val routeMessage = when (routeResult) {
                is ExerciseRouteResult.ConsentRequired ->
                    "Route access requires Health Connect route permission for this workout."
                is ExerciseRouteResult.NoData ->
                    "Route data unavailable for this workout."
                is ExerciseRouteResult.Data ->
                    if (routePoints.isEmpty()) "Route data unavailable for this workout." else null
                else -> "Route data unavailable for this workout."
            }

            val heartRatePoints = downSampleChartPoints(
                heartRateSamples.map {
                    ActivityChartPoint(
                        minutesFromStart = minutesFromSessionStart(session.startTime, it.time),
                        value = it.beatsPerMinute.toFloat()
                    )
                }
            )

            val pacePointsFromSpeed = speedSamples.mapNotNull { sample ->
                speedToPaceSecondsPerKm(sample.speed.inMetersPerSecond)?.let { paceSeconds ->
                    ActivityChartPoint(
                        minutesFromStart = minutesFromSessionStart(session.startTime, sample.time),
                        value = paceSeconds.toFloat()
                    )
                }
            }

            val pacePoints = downSampleChartPoints(
                if (pacePointsFromSpeed.isNotEmpty()) {
                    pacePointsFromSpeed
                } else {
                    derivePacePointsFromRoute(routePoints, session.durationSeconds())
                }
            )

            val elevationPoints = downSampleChartPoints(
                routePoints.mapNotNull { point ->
                    point.altitudeMeters?.let { altitude ->
                        ActivityChartPoint(
                            minutesFromStart = point.minutesFromStart,
                            value = altitude.toFloat()
                        )
                    }
                }
            )

            val distanceKm = aggregates[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
            val caloriesBurned = aggregates[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            val steps = aggregates[StepsRecord.COUNT_TOTAL]
            val duration = Duration.between(session.startTime, session.endTime)
            val activeMinutes = duration.toMinutes()
            val moveMinutes = (activeMinutes - pausedMinutes(session.segments)).coerceAtLeast(0L)
            val averageHeartRate = heartRateSamples
                .takeIf { it.isNotEmpty() }
                ?.map { it.beatsPerMinute.toDouble() }
                ?.average()
                ?.roundToInt()
            val maxHeartRate = heartRateSamples.maxOfOrNull { it.beatsPerMinute.toInt() }
            val averagePaceSecondsPerKm = distanceKm
                ?.takeIf { it > 0.0 }
                ?.let { duration.seconds / it.toDouble() }
            val fastestPaceSecondsPerKm = speedSamples
                .maxOfOrNull { it.speed.inMetersPerSecond }
                ?.let(::speedToPaceSecondsPerKm)
            val derivedElevationGain = calculateElevationGain(routePoints)
            val derivedElevationLoss = calculateElevationLoss(routePoints)
            val elevationGainMeters = aggregates[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters
                ?: derivedElevationGain

            ActivityDetail(
                sessionId = session.metadata.id,
                title = session.title?.takeIf { it.isNotBlank() }
                    ?: buildFallbackSessionTitle(getExerciseTypeLabel(session.exerciseType), session.startTime),
                activityName = getExerciseTypeLabel(session.exerciseType),
                startTime = session.startTime,
                endTime = session.endTime,
                durationSeconds = duration.seconds,
                caloriesBurned = caloriesBurned,
                distanceKm = distanceKm,
                steps = steps,
                activeMinutes = activeMinutes,
                moveMinutes = moveMinutes,
                averageHeartRate = averageHeartRate,
                maxHeartRate = maxHeartRate,
                averagePaceSecondsPerKm = averagePaceSecondsPerKm,
                fastestPaceSecondsPerKm = fastestPaceSecondsPerKm,
                elevationGainMeters = elevationGainMeters,
                elevationLossMeters = derivedElevationLoss,
                heartRatePoints = heartRatePoints,
                pacePoints = pacePoints,
                elevationPoints = elevationPoints,
                routePoints = routePoints,
                routeMessage = routeMessage,
                timeline = buildActivityTimeline(session),
                dataSourceLabel = "Health Connect",
                syncedFromLabel = resolveSourceAppName(session.metadata.dataOrigin.packageName)
                    ?: session.metadata.dataOrigin.packageName.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Failed to load exercise detail for $sessionId", e)
            null
        }
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

    private suspend fun fetchLatestWeight(before: Instant): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = WeightRecord::class, timeRangeFilter = TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.weight?.inKilograms
    }

    private suspend fun fetchLatestHeight(before: Instant): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = HeightRecord::class, timeRangeFilter = TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.height?.inMeters
    }

    private suspend fun fetchLatestBodyFat(before: Instant): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = BodyFatRecord::class, timeRangeFilter = TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.percentage?.value
    }

    private suspend fun fetchLatestBasalMetabolicRate(before: Instant): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = BasalMetabolicRateRecord::class, timeRangeFilter = TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.basalMetabolicRate?.inKilocaloriesPerDay
    }

    private suspend fun fetchLatestOxygenSaturation(before: Instant): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = OxygenSaturationRecord::class, timeRangeFilter = TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.percentage?.value
    }

    private suspend fun fetchLatestVo2Max(before: Instant): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = Vo2MaxRecord::class, timeRangeFilter = TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.vo2MillilitersPerMinuteKilogram
    }

    private suspend fun fetchLatestBloodPressure(before: Instant): Pair<Double, Double>? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = BloodPressureRecord::class, timeRangeFilter = TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
        )
        val record = response?.records?.firstOrNull() ?: return null
        return Pair(record.systolic.inMillimetersOfMercury, record.diastolic.inMillimetersOfMercury)
    }

    private suspend fun fetchLatestBloodGlucose(before: Instant): Double? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(recordType = BloodGlucoseRecord::class, timeRangeFilter = TimeRangeFilter.before(before), ascendingOrder = false, pageSize = 1)
        )
        return response?.records?.firstOrNull()?.level?.inMilligramsPerDeciliter
    }

    private suspend fun fetchSleepSummary(startOfDay: Instant, endOfRange: Instant): Triple<Long, Instant?, Instant?>? {
        val response = healthConnectClient?.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.before(endOfRange),
                ascendingOrder = false,
                pageSize = 20
            )
        )
        val overlappingSessions = response?.records
            ?.filter { it.endTime > startOfDay && it.startTime < endOfRange }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val totalDuration = overlappingSessions.sumOf { record ->
            val overlapStart = if (record.startTime.isAfter(startOfDay)) record.startTime else startOfDay
            val overlapEnd = if (record.endTime.isBefore(endOfRange)) record.endTime else endOfRange
            java.time.Duration.between(overlapStart, overlapEnd).toMinutes().coerceAtLeast(0)
        }

        return Triple(
            totalDuration,
            overlappingSessions.minByOrNull { it.startTime }?.startTime,
            overlappingSessions.maxByOrNull { it.endTime }?.endTime
        )
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
                id = record.metadata.id,
                title = record.title,
                type = record.exerciseType,
                typeLabel = getExerciseTypeLabel(record.exerciseType),
                startTime = record.startTime,
                endTime = record.endTime,
                durationMinutes = java.time.Duration.between(record.startTime, record.endTime).toMinutes(),
                calories = metrics[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                distanceKm = metrics[DistanceRecord.DISTANCE_TOTAL]?.inKilometers,
                sourcePackageName = record.metadata.dataOrigin.packageName.takeIf { it.isNotBlank() },
                sourceAppName = resolveSourceAppName(record.metadata.dataOrigin.packageName)
            )
        }.sortedByDescending { it.startTime }
    }

    private suspend fun findExerciseSessionRecord(
        client: HealthConnectClient,
        sessionId: String,
        startTimeHint: Instant?,
        endTimeHint: Instant?
    ): ExerciseSessionRecord? {
        val zoneId = ZoneId.systemDefault()
        val fallbackEnd = endTimeHint ?: Instant.now()
        val fallbackStart = startTimeHint ?: fallbackEnd.minus(Duration.ofDays(2))
        val request = ReadRecordsRequest(
            recordType = ExerciseSessionRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                fallbackStart.minus(Duration.ofHours(12)),
                fallbackEnd.plus(Duration.ofHours(12))
            ),
            ascendingOrder = true,
            pageSize = 200
        )

        return client.readRecords(request).records.firstOrNull { it.metadata.id == sessionId }
            ?: client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        ZonedDateTime.now(zoneId).minusDays(30).toInstant(),
                        Instant.now()
                    ),
                    ascendingOrder = false,
                    pageSize = 500
                )
            ).records.firstOrNull { it.metadata.id == sessionId }
    }

    private fun buildActivityTimeline(session: ExerciseSessionRecord): List<ActivityTimelineEntry> {
        val entries = mutableListOf<ActivityTimelineEntry>()
        entries += ActivityTimelineEntry(
            timestamp = session.startTime,
            title = "Workout started",
            subtitle = getExerciseTypeLabel(session.exerciseType),
            value = formatClockTime(session.startTime)
        )

        session.segments.forEach { segment ->
            entries += ActivityTimelineEntry(
                timestamp = segment.startTime,
                title = getExerciseSegmentLabel(segment.segmentType),
                subtitle = formatDurationCompact(Duration.between(segment.startTime, segment.endTime)),
                value = segment.repetitions.takeIf { it > 0 }?.let { "$it reps" }
            )
        }

        session.laps.forEachIndexed { index, lap ->
            entries += ActivityTimelineEntry(
                timestamp = lap.endTime,
                title = "Lap ${index + 1}",
                subtitle = formatDurationCompact(Duration.between(lap.startTime, lap.endTime)),
                value = lap.length?.inMeters?.takeIf { it > 0.0 }?.let { meters ->
                    if (meters >= 1000) String.format("%.2f km", meters / 1000.0)
                    else "${meters.roundToInt()} m"
                }
            )
        }

        entries += ActivityTimelineEntry(
            timestamp = session.endTime,
            title = "Workout finished",
            subtitle = formatDurationCompact(Duration.between(session.startTime, session.endTime)),
            value = formatClockTime(session.endTime)
        )

        return entries.sortedBy { it.timestamp }
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

    private fun buildFallbackSessionTitle(activityName: String, startTime: Instant): String {
        val hour = startTime.atZone(ZoneId.systemDefault()).hour
        val partOfDay = when {
            hour < 12 -> "Morning"
            hour < 17 -> "Afternoon"
            else -> "Evening"
        }
        return "$partOfDay $activityName"
    }

    private fun pausedMinutes(segments: List<ExerciseSegment>): Long {
        return segments
            .filter {
                it.segmentType == ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE ||
                    it.segmentType == ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST
            }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
    }

    private fun speedToPaceSecondsPerKm(metersPerSecond: Double): Double? {
        if (metersPerSecond <= 0.0) return null
        return 1000.0 / metersPerSecond
    }

    private fun calculateElevationGain(routePoints: List<ActivityRoutePoint>): Double? {
        val altitudes = routePoints.mapNotNull { it.altitudeMeters }
        if (altitudes.size < 2) return null
        return routePoints.zipWithNext().sumOf { (current, next) ->
            val rise = (next.altitudeMeters ?: return@sumOf 0.0) - (current.altitudeMeters ?: return@sumOf 0.0)
            rise.takeIf { it > 0.0 } ?: 0.0
        }.takeIf { it > 0.0 }
    }

    private fun calculateElevationLoss(routePoints: List<ActivityRoutePoint>): Double? {
        val altitudes = routePoints.mapNotNull { it.altitudeMeters }
        if (altitudes.size < 2) return null
        return routePoints.zipWithNext().sumOf { (current, next) ->
            val drop = (current.altitudeMeters ?: return@sumOf 0.0) - (next.altitudeMeters ?: return@sumOf 0.0)
            drop.takeIf { it > 0.0 } ?: 0.0
        }.takeIf { it > 0.0 }
    }

    private fun derivePacePointsFromRoute(
        routePoints: List<ActivityRoutePoint>,
        durationSeconds: Long
    ): List<ActivityChartPoint> {
        if (routePoints.size < 2 || durationSeconds <= 0) return emptyList()
        return routePoints.zipWithNext().mapNotNull { (current, next) ->
            val seconds = (next.minutesFromStart - current.minutesFromStart) * 60f
            if (seconds <= 0f) return@mapNotNull null
            val meters = haversineMeters(
                startLat = current.latitude,
                startLon = current.longitude,
                endLat = next.latitude,
                endLon = next.longitude
            )
            if (meters <= 2.0) return@mapNotNull null
            speedToPaceSecondsPerKm(meters / seconds)?.let { paceSeconds ->
                ActivityChartPoint(
                    minutesFromStart = next.minutesFromStart,
                    value = paceSeconds.toFloat()
                )
            }
        }
    }

    private fun haversineMeters(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val latDistance = Math.toRadians(endLat - startLat)
        val lonDistance = Math.toRadians(endLon - startLon)
        val a = kotlin.math.sin(latDistance / 2) * kotlin.math.sin(latDistance / 2) +
            kotlin.math.cos(Math.toRadians(startLat)) * kotlin.math.cos(Math.toRadians(endLat)) *
            kotlin.math.sin(lonDistance / 2) * kotlin.math.sin(lonDistance / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadiusMeters * c
    }

    private fun downSampleChartPoints(
        points: List<ActivityChartPoint>,
        maxPoints: Int = 120
    ): List<ActivityChartPoint> {
        if (points.size <= maxPoints) return points
        val step = points.size.toFloat() / maxPoints.toFloat()
        return buildList {
            var index = 0f
            repeat(maxPoints) {
                add(points[index.toInt().coerceAtMost(points.lastIndex)])
                index += step
            }
        }
    }

    private fun downSampleRoutePoints(
        points: List<ActivityRoutePoint>,
        maxPoints: Int = 300
    ): List<ActivityRoutePoint> {
        if (points.size <= maxPoints) return points
        val step = points.size.toFloat() / maxPoints.toFloat()
        return buildList {
            var index = 0f
            repeat(maxPoints) {
                add(points[index.toInt().coerceAtMost(points.lastIndex)])
                index += step
            }
        }
    }

    private fun resolveSourceAppName(packageName: String?): String? {
        val safePackageName = packageName?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(safePackageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()
    }

    private fun minutesFromSessionStart(startTime: Instant, sampleTime: Instant): Float {
        return Duration.between(startTime, sampleTime).seconds.coerceAtLeast(0).toFloat() / 60f
    }

    private fun formatDurationCompact(duration: Duration): String {
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun formatClockTime(time: Instant): String {
        return time.atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)
    }

    private fun getExerciseSegmentLabel(type: Int): String {
        return when (type) {
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE -> "Paused"
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST -> "Recovery"
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING -> "Running interval"
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING -> "Walking interval"
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING -> "Cycling interval"
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING -> "Stretch block"
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT block"
            else -> "Workout segment"
        }
    }

    private fun ExerciseSessionRecord.durationSeconds(): Long =
        Duration.between(startTime, endTime).seconds
}
