package com.hellohealth.data.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.health.HealthConnectManager
import com.hellohealth.data.local.SnapshotMapper
import com.hellohealth.data.local.dao.SnapshotDao
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.SnapshotDataSource
import com.hellohealth.domain.model.SnapshotSyncStatus
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first activity repository. Daily snapshots are cached in Room (source of truth for reads);
 * the [com.hellohealth.sync.SnapshotSyncer] reconciles them with Supabase in the background.
 *
 * Health Connect remains the live source of truth for today's numbers: [fetchSummary] still
 * prefers fresh Health Connect data and persists it locally (`isSynced=false`). Because the
 * SnapshotSyncer guards today's row against being overwritten by a stale remote pull, today's
 * dashboard numbers never regress after a background sync.
 */
@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val snapshotDao: SnapshotDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler,
    private val profileRepository: ProfileRepository,
    private val vitalsRepository: VitalsRepository
) : ActivityRepository {

    override suspend fun fetchSummary(
        goals: ActivityGoals,
        date: LocalDate,
        forceRefresh: Boolean
    ): HealthSummary {
        val cachedSnapshot = loadSnapshot(date)
        val hasLivePermissions = healthConnectManager.isAvailable && healthConnectManager.hasAllPermissions()
        val shouldUseLiveData = hasLivePermissions && (
            forceRefresh ||
            cachedSnapshot == null ||
            date == LocalDate.now() ||
            cachedSnapshot.syncStatus == SnapshotSyncStatus.PARTIAL
        )

        if (shouldUseLiveData) {
            // The profile-derived BMR is supplied lazily — HealthConnectManager only invokes it when
            // the HC basal-rate record is absent, so no profile read happens on the common path.
            val freshSummary = healthConnectManager.fetchHealthSummary(goals, date) { resolveBmrFallback() }

            // If Health Connect has data, it is the most reliable source of truth.
            if (freshSummary.lastUpdated > 0L) {
                persistSnapshot(
                    date = date,
                    summary = freshSummary,
                    syncStatus = if (date == LocalDate.now()) SnapshotSyncStatus.PARTIAL else SnapshotSyncStatus.COMPLETE
                )
                // Persist a daily vitals rollup for the fetched day. Idempotent via the deterministic
                // rollup id, off the dashboard's critical path (runCatching), and a no-op when no user
                // is signed in — so a rollup problem can never disturb the summary that just succeeded.
                persistVitalsRollup(date, freshSummary)
                return freshSummary
            } else if (cachedSnapshot != null) {
                // Health Connect has no data (e.g. past-30-days read restriction) — use the cache.
                return cachedSnapshot.summary
            }
            return freshSummary
        }

        val finalSummary = cachedSnapshot?.summary ?: defaultSummary(goals)
        return finalSummary.copy(
            stepsGoal = goals.steps.toLong(),
            caloriesGoal = goals.activeCalories.toDouble(),
            activeTimeGoal = goals.activeMinutes.toLong()
        )
    }

    override suspend fun getHistoryForMonth(month: YearMonth): List<DailyHealthSnapshot> {
        val userId = sessionManager.getCurrentUserId() ?: return emptyList()
        return runCatching {
            snapshotDao.getRange(
                userId = userId,
                startDate = month.atDay(1).toString(),
                endDate = month.atEndOfMonth().toString()
            ).map(SnapshotMapper::toDomain)
        }.getOrElse { e ->
            AppLogger.e(FeatureTag.ACTIVITY, "getHistoryForMonth failed", e)
            emptyList()
        }
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

    override suspend fun hasPermissions(): Boolean = healthConnectManager.hasAllPermissions()

    override suspend fun fetchLatestBodyMetrics(): com.hellohealth.domain.model.BodyMetrics {
        // Only attempt a read when Health Connect is present and connected; otherwise the fields
        // stay empty and the onboarding UI falls back to manual entry.
        return if (healthConnectManager.isAvailable && healthConnectManager.hasAllPermissions()) {
            healthConnectManager.fetchLatestBodyMetrics()
        } else {
            com.hellohealth.domain.model.BodyMetrics()
        }
    }

    override fun getRequiredPermissions(): Set<String> = healthConnectManager.permissions

    override fun getAvailability(): Int = healthConnectManager.getAvailability()

    override fun getSettingsIntent(context: Context): Intent =
        healthConnectManager.getHealthConnectSettingsIntent()

    override fun observeTodayCaloriesOut(): Flow<Double> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(0.0)
            return@flow
        }
        // Reactive today-only read off the snapshot rollup. There's no single-day observe query, so
        // observe the [today, today] range and take its one row. caloriesOut = active + BMR, matching
        // the EnergyBalance contract; a missing/partial snapshot degrades to 0.0 rather than throwing.
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        emitAll(
            snapshotDao.observeRange(userId, today, today).map { rows ->
                val summary = rows.firstOrNull()?.let(SnapshotMapper::toDomain)?.summary
                (summary?.activeCalories ?: 0.0) + (summary?.basalMetabolicRate ?: 0.0)
            }
        )
    }.flowOn(Dispatchers.IO)

    override fun observeCaloriesOutForDay(localDate: String): Flow<Double?> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(null)
            return@flow
        }
        // Reactive per-day read off the snapshot rollup for the SELECTED day. Unlike the today-only
        // variant, this emits null when there is no snapshot row for the day, so a date-aware card can
        // DASH a missing day rather than showing a fake 0-kcal burn. caloriesOut = active + BMR.
        emitAll(
            snapshotDao.observeRange(userId, localDate, localDate).map { rows ->
                val summary = rows.firstOrNull()?.let(SnapshotMapper::toDomain)?.summary
                summary?.let { (it.activeCalories) + (it.basalMetabolicRate) }
            }
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun loadSnapshot(date: LocalDate): DailyHealthSnapshot? {
        val userId = sessionManager.getCurrentUserId() ?: return null
        return runCatching {
            snapshotDao.get(userId, date.toString())?.let(SnapshotMapper::toDomain)
        }.getOrElse { e ->
            AppLogger.e(FeatureTag.ACTIVITY, "loadSnapshot failed for $date", e)
            null
        }
    }

    /**
     * Upsert the daily vitals rollup for [date] from a freshly-fetched Health Connect summary.
     * Idempotent (deterministic rollup id in [VitalsRepository]) and defensive — any failure is
     * swallowed so it can never disturb the dashboard summary that just succeeded. SpO2 rides in as
     * the natural 0–100 percentage; sleep is the summary's minutes (0 → null so it doesn't read as a
     * zero-hour night). Deep sleep isn't exposed by [HealthSummary], so it stays null.
     */
    private suspend fun persistVitalsRollup(date: LocalDate, summary: HealthSummary) {
        runCatching {
            vitalsRepository.upsertRollup(
                localDate = date.toString(),
                timestampUtcEpochMs = Timestamps.nowEpochMs(),
                restingHeartRate = summary.restingHeartRate,
                hrvRmssd = summary.hrvRmssd,
                respiratoryRate = summary.respiratoryRate,
                bodyTemperature = summary.bodyTemperature,
                hydrationMl = summary.hydrationMl,
                spo2 = summary.oxygenSaturation,
                sleepDurationMinutes = summary.sleepDurationMinutes.toInt().takeIf { it > 0 },
                deepSleepMinutes = null
            )
        }.onFailure { e ->
            AppLogger.e(FeatureTag.VITALS, "persistVitalsRollup failed for $date", e)
        }
    }

    private suspend fun persistSnapshot(
        date: LocalDate,
        summary: HealthSummary,
        syncStatus: SnapshotSyncStatus
    ) {
        val userId = sessionManager.getCurrentUserId() ?: return
        val now = Timestamps.nowEpochMs()
        runCatching {
            snapshotDao.upsert(
                SnapshotMapper.toEntity(
                    userId = userId,
                    date = date,
                    summary = summary,
                    syncStatus = syncStatus,
                    dataSource = SnapshotDataSource.HEALTH_CONNECT,
                    lastSyncedAtEpochMs = now,
                    updatedAtEpochMs = now,
                    isSynced = false
                )
            )
            AppLogger.d(FeatureTag.ACTIVITY, "snapshot $date persisted locally; requesting sync")
            syncScheduler.requestSync()
        }.onFailure { e ->
            // Previously a silent runCatching that dropped the error. Now logged, and because
            // Room is the source of truth the write already succeeded before sync is requested.
            AppLogger.e(FeatureTag.ACTIVITY, "persistSnapshot failed for $date", e)
        }
    }

    private fun defaultSummary(goals: ActivityGoals): HealthSummary = HealthSummary(
        stepsGoal = goals.steps.toLong(),
        caloriesGoal = goals.activeCalories.toDouble(),
        activeTimeGoal = goals.activeMinutes.toLong(),
        lastUpdated = 0L
    )

    /**
     * The BMR (kcal/day) to hand [HealthConnectManager.fetchHealthSummary] as the fallback when
     * Health Connect has no basal-rate record. Reads the stored profile and derives Mifflin-St Jeor
     * BMR ([BodyEnergy.bmr], neutral-sex when gender is unset); falls back to 1800.0 only when the
     * profile is missing/incomplete or the read fails. Precedence downstream: HC record → profile
     * BMR → 1800.0. `internal` so the profile-read-failure degradation is unit-testable.
     */
    internal suspend fun resolveBmrFallback(): Double {
        val profile = runCatching { profileRepository.getProfile() }.getOrElse { e ->
            AppLogger.w(FeatureTag.ACTIVITY, "resolveBmrFallback: profile read failed", e)
            null
        }
        return profileBmrOrDefault(profile)
    }

    companion object {
        /** Last-resort BMR when neither Health Connect nor the profile can supply one. */
        internal const val DEFAULT_BMR = 1800.0

        /**
         * Pure BMR-source selection for the no-HC-record fallback: the profile's Mifflin-St Jeor BMR
         * ([BodyEnergy.bmr], neutral-sex when gender is unset) when the profile has enough vitals,
         * else [DEFAULT_BMR]. Kept pure/`internal` so the selection is unit-testable without any
         * Health Connect plumbing.
         */
        internal fun profileBmrOrDefault(profile: UserProfile?): Double {
            profile ?: return DEFAULT_BMR
            return BodyEnergy.bmr(
                weightKg = profile.weightKg,
                heightCm = profile.heightCm,
                ageYears = profile.ageYears(),
                gender = profile.gender
            ) ?: DEFAULT_BMR
        }
    }
}
