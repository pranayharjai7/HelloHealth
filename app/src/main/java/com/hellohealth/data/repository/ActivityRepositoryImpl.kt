package com.hellohealth.data.repository

import android.content.Context
import android.content.Intent
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.health.HealthConnectManager
import com.hellohealth.data.local.SnapshotMapper
import com.hellohealth.data.local.dao.SnapshotDao
import com.hellohealth.domain.model.ActivityDetail
import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.DailyHealthSnapshot
import com.hellohealth.domain.model.HealthSummary
import com.hellohealth.domain.model.SnapshotDataSource
import com.hellohealth.domain.model.SnapshotSyncStatus
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.sync.SyncScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
    private val syncScheduler: SyncScheduler
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
            val freshSummary = healthConnectManager.fetchHealthSummary(goals, date)

            // If Health Connect has data, it is the most reliable source of truth.
            if (freshSummary.lastUpdated > 0L) {
                persistSnapshot(
                    date = date,
                    summary = freshSummary,
                    syncStatus = if (date == LocalDate.now()) SnapshotSyncStatus.PARTIAL else SnapshotSyncStatus.COMPLETE
                )
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

    private suspend fun loadSnapshot(date: LocalDate): DailyHealthSnapshot? {
        val userId = sessionManager.getCurrentUserId() ?: return null
        return runCatching {
            snapshotDao.get(userId, date.toString())?.let(SnapshotMapper::toDomain)
        }.getOrElse { e ->
            AppLogger.e(FeatureTag.ACTIVITY, "loadSnapshot failed for $date", e)
            null
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
}
