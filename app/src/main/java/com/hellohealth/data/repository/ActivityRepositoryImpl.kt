package com.hellohealth.data.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.data.health.HealthConnectManager
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.ExerciseSession
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.SnapshotDataSource
import com.hellohealth.domain.model.SnapshotSyncStatus
import com.hellohealth.domain.repository.ActivityRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ExerciseSessionSnapshotDto(
    val id: String = "",
    val title: String? = null,
    val type: Int,
    val type_label: String,
    val start_time: String,
    val end_time: String,
    val duration_minutes: Long,
    val calories: Double? = null,
    val distance_km: Double? = null,
    val source_package_name: String? = null,
    val source_app_name: String? = null
)

@Serializable
private data class DailyHealthSnapshotDto(
    val user_id: String,
    val snapshot_date: String,
    val snapshot_timezone: String,
    val steps: Long,
    val steps_goal: Int,
    val active_calories: Double,
    val calories_goal: Int,
    val active_minutes: Int,
    val active_minutes_goal: Int,
    val distance_km: Double,
    val total_calories: Double,
    val basal_metabolic_rate: Double,
    val weight_kg: Double? = null,
    val height_m: Double? = null,
    val body_fat_percent: Double? = null,
    val avg_heart_rate: Int? = null,
    val oxygen_saturation: Double? = null,
    val blood_pressure_systolic: Double? = null,
    val blood_pressure_diastolic: Double? = null,
    val blood_glucose_mg_dl: Double? = null,
    val vo2max: Double? = null,
    val sleep_duration_minutes: Int,
    val sleep_start_time: String? = null,
    val sleep_end_time: String? = null,
    val exercise_sessions: List<ExerciseSessionSnapshotDto>,
    val data_source: String,
    val sync_status: String,
    val last_synced_at: String,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val supabase: SupabaseClient,
    private val sessionManager: SupabaseSessionManager
) : ActivityRepository {
    override suspend fun fetchSummary(
        goals: ActivityGoals,
        date: LocalDate,
        forceRefresh: Boolean
    ): HealthSummary {
        val cachedSnapshot = loadSnapshot(date)
        val hasLivePermissions = healthConnectManager.isAvailable && healthConnectManager.hasAllPermissions()
        val shouldUseLiveData = hasLivePermissions && (forceRefresh || cachedSnapshot == null || date == LocalDate.now())

        if (shouldUseLiveData) {
            val freshSummary = healthConnectManager.fetchHealthSummary(goals, date)
            if (freshSummary.lastUpdated > 0L) {
                persistSnapshot(
                    date = date,
                    summary = freshSummary,
                    syncStatus = if (date == LocalDate.now()) SnapshotSyncStatus.PARTIAL else SnapshotSyncStatus.COMPLETE
                )
            }
            return freshSummary
        }

        return cachedSnapshot?.summary ?: defaultSummary(goals)
    }

    override suspend fun getHistoryForMonth(month: YearMonth): List<DailyHealthSnapshot> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()

        return runCatching {
            supabase.postgrest["daily_health_snapshots"]
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("snapshot_date", month.atDay(1).toString())
                        lte("snapshot_date", month.atEndOfMonth().toString())
                    }
                    order("snapshot_date", Order.ASCENDING)
                }
                .decodeList<DailyHealthSnapshotDto>()
                .map { it.toDomain() }
        }.getOrElse { emptyList() }
    }

    override suspend fun getExerciseSessionDetail(
        sessionId: String,
        startTimeHint: Instant?,
        endTimeHint: Instant?
    ): ActivityDetail? {
        if (sessionId.isBlank()) return null
        return healthConnectManager.fetchExerciseSessionDetail(
            sessionId = sessionId,
            startTimeHint = startTimeHint,
            endTimeHint = endTimeHint
        )
    }

    override suspend fun fetchWeeklyStats(): com.hellohealth.domain.model.WeeklyStats {
        return if (healthConnectManager.isAvailable && healthConnectManager.hasAllPermissions()) {
            healthConnectManager.fetchWeeklyStats()
        } else {
            com.hellohealth.domain.model.WeeklyStats()
        }
    }

    override suspend fun hasPermissions(): Boolean {
        return healthConnectManager.hasAllPermissions()
    }

    override fun getRequiredPermissions(): Set<String> {
        return healthConnectManager.permissions
    }

    override fun getAvailability(): Int {
        return healthConnectManager.getAvailability()
    }

    override fun getSettingsIntent(context: Context): Intent {
        return healthConnectManager.getHealthConnectSettingsIntent()
    }

    private suspend fun loadSnapshot(date: LocalDate): DailyHealthSnapshot? {
        val userId = sessionManager.getCurrentUserId() ?: return null

        return runCatching {
            supabase.postgrest["daily_health_snapshots"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("snapshot_date", date.toString())
                    }
                    limit(1)
                }
                .decodeSingleOrNull<DailyHealthSnapshotDto>()
                ?.toDomain()
        }.getOrNull()
    }

    private suspend fun persistSnapshot(
        date: LocalDate,
        summary: HealthSummary,
        syncStatus: SnapshotSyncStatus
    ) {
        val userId = sessionManager.getCurrentUserId() ?: return

        runCatching {
            supabase.postgrest["daily_health_snapshots"].upsert(
                value = dailyHealthSnapshotDtoFrom(
                    userId = userId,
                    date = date,
                    summary = summary,
                    syncStatus = syncStatus
                ),
                onConflict = "user_id,snapshot_date"
            )
        }
    }

    private fun defaultSummary(goals: ActivityGoals): HealthSummary {
        return HealthSummary(
            stepsGoal = goals.steps.toLong(),
            caloriesGoal = goals.activeCalories.toDouble(),
            activeTimeGoal = goals.activeMinutes.toLong(),
            lastUpdated = 0L
        )
    }
}

private fun DailyHealthSnapshotDto.toDomain(): DailyHealthSnapshot {
    val lastSyncedAt = parseInstant(last_synced_at)
    return DailyHealthSnapshot(
        date = LocalDate.parse(snapshot_date),
        snapshotTimezone = snapshot_timezone,
        lastSyncedAt = lastSyncedAt,
        syncStatus = SnapshotSyncStatus.valueOf(sync_status.uppercase()),
        dataSource = SnapshotDataSource.valueOf(data_source.uppercase()),
        summary = HealthSummary(
            steps = steps,
            stepsGoal = steps_goal.toLong(),
            activeCalories = active_calories,
            caloriesGoal = calories_goal.toDouble(),
            activeTimeMinutes = active_minutes.toLong(),
            activeTimeGoal = active_minutes_goal.toLong(),
            distanceKm = distance_km,
            totalCalories = total_calories,
            basalMetabolicRate = basal_metabolic_rate,
            weight = weight_kg,
            height = height_m,
            bodyFat = body_fat_percent,
            heartRateAvg = avg_heart_rate,
            oxygenSaturation = oxygen_saturation,
            bloodPressureSystolic = blood_pressure_systolic,
            bloodPressureDiastolic = blood_pressure_diastolic,
            bloodGlucose = blood_glucose_mg_dl,
            vo2max = vo2max,
            sleepDurationMinutes = sleep_duration_minutes.toLong(),
            sleepStartTime = sleep_start_time?.let(::parseInstant),
            sleepEndTime = sleep_end_time?.let(::parseInstant),
            exerciseSessions = exercise_sessions.map {
                ExerciseSession(
                    id = it.id,
                    title = it.title,
                    type = it.type,
                    typeLabel = it.type_label,
                    startTime = parseInstant(it.start_time),
                    endTime = parseInstant(it.end_time),
                    durationMinutes = it.duration_minutes,
                    calories = it.calories,
                    distanceKm = it.distance_km,
                    sourcePackageName = it.source_package_name,
                    sourceAppName = it.source_app_name
                )
            },
            lastUpdated = lastSyncedAt.toEpochMilli()
        )
    )
}

private fun dailyHealthSnapshotDtoFrom(
    userId: String,
    date: LocalDate,
    summary: HealthSummary,
    syncStatus: SnapshotSyncStatus
): DailyHealthSnapshotDto {
    val now = Instant.now().toString()
    return DailyHealthSnapshotDto(
        user_id = userId,
        snapshot_date = date.toString(),
        snapshot_timezone = ZoneId.systemDefault().id,
        steps = summary.steps,
        steps_goal = summary.stepsGoal.toInt(),
        active_calories = summary.activeCalories,
        calories_goal = summary.caloriesGoal.toInt(),
        active_minutes = summary.activeTimeMinutes.toInt(),
        active_minutes_goal = summary.activeTimeGoal.toInt(),
        distance_km = summary.distanceKm,
        total_calories = summary.totalCalories,
        basal_metabolic_rate = summary.basalMetabolicRate,
        weight_kg = summary.weight,
        height_m = summary.height,
        body_fat_percent = summary.bodyFat,
        avg_heart_rate = summary.heartRateAvg,
        oxygen_saturation = summary.oxygenSaturation,
        blood_pressure_systolic = summary.bloodPressureSystolic,
        blood_pressure_diastolic = summary.bloodPressureDiastolic,
        blood_glucose_mg_dl = summary.bloodGlucose,
        vo2max = summary.vo2max,
        sleep_duration_minutes = summary.sleepDurationMinutes.toInt(),
        sleep_start_time = summary.sleepStartTime?.toString(),
        sleep_end_time = summary.sleepEndTime?.toString(),
        exercise_sessions = summary.exerciseSessions.map {
            ExerciseSessionSnapshotDto(
                id = it.id,
                title = it.title,
                type = it.type,
                type_label = it.typeLabel,
                start_time = it.startTime.toString(),
                end_time = it.endTime.toString(),
                duration_minutes = it.durationMinutes,
                calories = it.calories,
                distance_km = it.distanceKm,
                source_package_name = it.sourcePackageName,
                source_app_name = it.sourceAppName
            )
        },
        data_source = SnapshotDataSource.HEALTH_CONNECT.name.lowercase(),
        sync_status = syncStatus.name.lowercase(),
        last_synced_at = now
    )
}

private fun parseInstant(value: String): Instant = OffsetDateTime.parse(value).toInstant()
