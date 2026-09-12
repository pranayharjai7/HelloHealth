package com.hellohealth.data.local

import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.domain.model.HealthSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * On-disk JSON representation of [HealthSummary] for the `snapshot.summaryJson` column.
 *
 * We serialize a dedicated DTO rather than annotating the domain model because [HealthSummary]
 * holds `java.time.Instant` fields that kotlinx.serialization can't encode without custom
 * serializers. Instants are stored as ISO-8601 strings here; this format is owned by the data
 * layer and independent of both the domain model and the Supabase DTO.
 */
@Serializable
data class SnapshotSummaryJson(
    val steps: Long = 0,
    val stepsGoal: Long = 10000,
    val activeCalories: Double = 0.0,
    val caloriesGoal: Double = 500.0,
    val activeTimeMinutes: Long = 0,
    val activeTimeGoal: Long = 60,
    val distanceKm: Double = 0.0,
    val totalCalories: Double = 0.0,
    val basalMetabolicRate: Double = 0.0,
    val weight: Double? = null,
    val height: Double? = null,
    val bodyFat: Double? = null,
    val heartRateAvg: Int? = null,
    val oxygenSaturation: Double? = null,
    val bloodPressureSystolic: Double? = null,
    val bloodPressureDiastolic: Double? = null,
    val bloodGlucose: Double? = null,
    val vo2max: Double? = null,
    val sleepDurationMinutes: Long = 0,
    val sleepStartTime: String? = null,
    val sleepEndTime: String? = null,
    val exerciseSessions: List<ExerciseSessionJson> = emptyList(),
    val lastUpdated: Long = 0L
)

@Serializable
data class ExerciseSessionJson(
    val id: String = "",
    val title: String? = null,
    val type: Int,
    val typeLabel: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Long,
    val calories: Double? = null,
    val distanceKm: Double? = null,
    val sourcePackageName: String? = null,
    val sourceAppName: String? = null
)

/** Serialize/deserialize [HealthSummary] to/from the `summaryJson` column. */
object SnapshotJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(summary: HealthSummary): String =
        json.encodeToString(SnapshotSummaryJson.serializer(), summary.toJsonModel())

    fun decode(raw: String): HealthSummary =
        json.decodeFromString(SnapshotSummaryJson.serializer(), raw).toDomain()
}

fun HealthSummary.toJsonModel() = SnapshotSummaryJson(
    steps = steps,
    stepsGoal = stepsGoal,
    activeCalories = activeCalories,
    caloriesGoal = caloriesGoal,
    activeTimeMinutes = activeTimeMinutes,
    activeTimeGoal = activeTimeGoal,
    distanceKm = distanceKm,
    totalCalories = totalCalories,
    basalMetabolicRate = basalMetabolicRate,
    weight = weight,
    height = height,
    bodyFat = bodyFat,
    heartRateAvg = heartRateAvg,
    oxygenSaturation = oxygenSaturation,
    bloodPressureSystolic = bloodPressureSystolic,
    bloodPressureDiastolic = bloodPressureDiastolic,
    bloodGlucose = bloodGlucose,
    vo2max = vo2max,
    sleepDurationMinutes = sleepDurationMinutes,
    sleepStartTime = sleepStartTime?.toString(),
    sleepEndTime = sleepEndTime?.toString(),
    exerciseSessions = exerciseSessions.map {
        ExerciseSessionJson(
            id = it.id,
            title = it.title,
            type = it.type,
            typeLabel = it.typeLabel,
            startTime = it.startTime.toString(),
            endTime = it.endTime.toString(),
            durationMinutes = it.durationMinutes,
            calories = it.calories,
            distanceKm = it.distanceKm,
            sourcePackageName = it.sourcePackageName,
            sourceAppName = it.sourceAppName
        )
    },
    lastUpdated = lastUpdated
)

fun SnapshotSummaryJson.toDomain() = HealthSummary(
    steps = steps,
    stepsGoal = stepsGoal,
    activeCalories = activeCalories,
    caloriesGoal = caloriesGoal,
    activeTimeMinutes = activeTimeMinutes,
    activeTimeGoal = activeTimeGoal,
    distanceKm = distanceKm,
    totalCalories = totalCalories,
    basalMetabolicRate = basalMetabolicRate,
    weight = weight,
    height = height,
    bodyFat = bodyFat,
    heartRateAvg = heartRateAvg,
    oxygenSaturation = oxygenSaturation,
    bloodPressureSystolic = bloodPressureSystolic,
    bloodPressureDiastolic = bloodPressureDiastolic,
    bloodGlucose = bloodGlucose,
    vo2max = vo2max,
    sleepDurationMinutes = sleepDurationMinutes,
    sleepStartTime = sleepStartTime?.let(Instant::parse),
    sleepEndTime = sleepEndTime?.let(Instant::parse),
    exerciseSessions = exerciseSessions.map {
        ExerciseSession(
            id = it.id,
            title = it.title,
            type = it.type,
            typeLabel = it.typeLabel,
            startTime = Instant.parse(it.startTime),
            endTime = Instant.parse(it.endTime),
            durationMinutes = it.durationMinutes,
            calories = it.calories,
            distanceKm = it.distanceKm,
            sourcePackageName = it.sourcePackageName,
            sourceAppName = it.sourceAppName
        )
    },
    lastUpdated = lastUpdated
)
