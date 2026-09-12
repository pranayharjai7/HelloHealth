package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.SnapshotJson
import com.hellohealth.data.local.dao.SnapshotDao
import com.hellohealth.data.local.entities.SnapshotEntity
import com.hellohealth.domain.model.HealthSummary
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import java.time.LocalDate
import javax.inject.Inject

/**
 * Syncs the `snapshot` table (Supabase `daily_health_snapshots`, conflict key
 * `user_id,snapshot_date`). Day-keyed: many rows per user, one per calendar day.
 *
 * This syncer owns the Supabase wire format ([DailyHealthSnapshotDto] / [ExerciseSessionDto]),
 * which was moved here out of `ActivityRepositoryImpl` — the repository is now Room-only and
 * knows nothing about Postgrest. On disk Room stores the [HealthSummary] as a single JSON blob
 * (see [SnapshotJson]); here we explode it into the server's ~30 columns on push and reassemble
 * it on pull.
 *
 * **Today-guard (critical):** Health Connect is the source of truth for *today*'s numbers. The
 * repository writes today's row with [com.hellohealth.domain.model.SnapshotSyncStatus.PARTIAL].
 * A pull must never overwrite a PARTIAL today row with a stale remote snapshot, or today's
 * dashboard visibly regresses. We therefore skip pulling any row whose date is today AND whose
 * local copy is PARTIAL, independent of LWW timestamps.
 */
class SnapshotSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val snapshotDao: SnapshotDao
) : Syncer {

    @Serializable
    data class ExerciseSessionDto(
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
    data class DailyHealthSnapshotDto(
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
        val exercise_sessions: List<ExerciseSessionDto>,
        val data_source: String,
        val sync_status: String,
        val last_synced_at: String,
        val created_at: String? = null,
        val updated_at: String? = null,
        val deleted_at: String? = null
    )

    override val featureTag = FeatureTag.ACTIVITY

    override suspend fun push(userId: String): Int {
        val unsynced = snapshotDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["daily_health_snapshots"]
                .upsert(value = row.toDto(), onConflict = "user_id,snapshot_date")
            snapshotDao.markSynced(row.userId, row.snapshotDate, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.ACTIVITY, "pushed $pushed snapshot row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        // Pull the recent window (this month + last month). Older history is rarely edited and
        // stays whatever Room already cached; a full-table pull would grow unbounded.
        val now = LocalDate.now()
        val start = now.minusMonths(1).withDayOfMonth(1)
        val remote = supabase.postgrest["daily_health_snapshots"]
            .select {
                filter {
                    eq("user_id", userId)
                    gte("snapshot_date", start.toString())
                }
                order("snapshot_date", Order.ASCENDING)
            }
            .decodeList<DailyHealthSnapshotDto>()
        if (remote.isEmpty()) return 0

        val todayStr = now.toString()
        var applied = 0
        for (dto in remote) {
            val local = snapshotDao.get(userId, dto.snapshot_date)

            // Today-guard: never clobber a locally-refreshed (PARTIAL) today snapshot. Health
            // Connect is the truth for today; the local write will be pushed on the next cycle.
            if (shouldSkipTodayPull(dto.snapshot_date, todayStr, local?.syncStatus)) {
                AppLogger.d(FeatureTag.ACTIVITY, "skipping pull of today ($todayStr): local PARTIAL wins")
                continue
            }

            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }

            snapshotDao.upsert(dto.toEntity(userId, remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.ACTIVITY, "pulled $applied remote snapshot row(s)")
        return applied
    }

    private fun SnapshotEntity.toDto(): DailyHealthSnapshotDto {
        val summary = SnapshotJson.decode(summaryJson)
        return DailyHealthSnapshotDto(
            user_id = userId,
            snapshot_date = snapshotDate,
            snapshot_timezone = snapshotTimezone,
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
                ExerciseSessionDto(
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
            data_source = dataSource,
            sync_status = syncStatus,
            last_synced_at = Timestamps.epochMsToServerTimestamp(lastSyncedAtEpochMs),
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) }
        )
    }

    private fun DailyHealthSnapshotDto.toEntity(userId: String, remoteUpdatedAt: Long?): SnapshotEntity {
        val summary = HealthSummary(
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
            sleepStartTime = sleep_start_time?.let { Timestamps.parseServerTimestamp(it)?.let(java.time.Instant::ofEpochMilli) },
            sleepEndTime = sleep_end_time?.let { Timestamps.parseServerTimestamp(it)?.let(java.time.Instant::ofEpochMilli) },
            exerciseSessions = exercise_sessions.map {
                com.hellohealth.domain.model.ExerciseSession(
                    id = it.id,
                    title = it.title,
                    type = it.type,
                    typeLabel = it.type_label,
                    startTime = java.time.Instant.ofEpochMilli(Timestamps.parseServerTimestamp(it.start_time) ?: 0L),
                    endTime = java.time.Instant.ofEpochMilli(Timestamps.parseServerTimestamp(it.end_time) ?: 0L),
                    durationMinutes = it.duration_minutes,
                    calories = it.calories,
                    distanceKm = it.distance_km,
                    sourcePackageName = it.source_package_name,
                    sourceAppName = it.source_app_name
                )
            },
            lastUpdated = Timestamps.parseServerTimestamp(last_synced_at) ?: 0L
        )
        return SnapshotEntity(
            userId = userId,
            snapshotDate = snapshot_date,
            snapshotTimezone = snapshot_timezone,
            syncStatus = sync_status.lowercase(),
            dataSource = data_source.lowercase(),
            lastSyncedAtEpochMs = Timestamps.parseServerTimestamp(last_synced_at) ?: Timestamps.nowEpochMs(),
            summaryJson = SnapshotJson.encode(summary),
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true
        )
    }

    companion object {
        /**
         * Today-guard decision (pure, testable). Returns true when a remote pull must be skipped
         * because the local row is today's Health-Connect-refreshed PARTIAL snapshot — pulling
         * would regress today's dashboard numbers. Any other day, or a COMPLETE/absent local row,
         * pulls normally and lets LWW decide.
         */
        fun shouldSkipTodayPull(remoteDate: String, todayStr: String, localSyncStatus: String?): Boolean =
            remoteDate == todayStr && localSyncStatus.equals("partial", ignoreCase = true)
    }
}
