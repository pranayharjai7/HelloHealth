package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.VitalsSampleDao
import com.hellohealth.data.local.entities.VitalsSampleEntity
import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.domain.vitals.ReadinessScoreCalculator
import com.hellohealth.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-first vitals repository. Writes hit Room with `isSynced=false` then poke [SyncScheduler];
 * the [com.hellohealth.sync.VitalsSampleSyncer] owns the Supabase round-trip. Reads are Room Flows.
 *
 * With no signed-in user, reads emit empty/null and writes are dropped with a warning — matching the
 * EmotionsRepositoryImpl contract so screens never special-case a missing session.
 *
 * Readiness is computed here from the persisted rollup window: today's rollup is the `todayMetric`
 * and the earlier rollups form the history. [ReadinessScoreCalculator] is pure and reads nothing —
 * this is the sole place readiness reads history. All vitals stay in natural human units.
 */
@Singleton
class VitalsRepositoryImpl @Inject constructor(
    private val vitalsSampleDao: VitalsSampleDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler,
    private val readinessCalculator: ReadinessScoreCalculator
) : VitalsRepository {

    override fun observeReadiness(): Flow<ReadinessScore?> =
        observeReadinessAsOf(LocalDate.now(ZoneId.systemDefault()))

    override fun observeReadinessAsOf(date: LocalDate): Flow<ReadinessScore?> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(null)
            return@flow
        }
        val startDate = date.minusDays(READINESS_WINDOW_DAYS.toLong()).toString()
        val endDate = date.toString()
        val dayStr = date.toString()

        emitAll(
            vitalsSampleDao.observeRollupsForUser(userId, startDate, endDate).map { rows ->
                // The selected day's rollup is the `todayMetric`; every earlier rollup forms the
                // history the calculator averages (bounded internally to its 28-day baseline window).
                val dayRow = rows.lastOrNull { it.localDate == dayStr }
                val history = rows.filter { it.localDate != dayStr }.map { it.toMetrics() }
                readinessCalculator.calculate(history, dayRow?.toMetrics())
            }
        )
    }.flowOn(Dispatchers.IO)

    override fun observeVitalsForDay(localDate: String): Flow<LatestVitals?> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(null)
            return@flow
        }
        // Reuse the range query for a single day; take the matching rollup (null if that day has none).
        emitAll(
            vitalsSampleDao.observeRollupsForUser(userId, localDate, localDate).map { rows ->
                rows.firstOrNull { it.localDate == localDate }?.toLatestVitals()
            }
        )
    }.flowOn(Dispatchers.IO)

    override fun observeRecentRollups(days: Int): Flow<List<HealthMetricsData>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startDate = today.minusDays(days.toLong()).toString()
        val endDate = today.toString()
        emitAll(
            vitalsSampleDao.observeRollupsForUser(userId, startDate, endDate)
                .map { rows -> rows.map { it.toMetrics() } }
        )
    }.flowOn(Dispatchers.IO)

    override fun observeLatestVitals(): Flow<LatestVitals?> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(null)
            return@flow
        }
        emitAll(
            vitalsSampleDao.observeLatestRollup(userId).map { row -> row?.toLatestVitals() }
        )
    }.flowOn(Dispatchers.IO)

    override fun observeRecentVitals(days: Int): Flow<List<LatestVitals>> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(emptyList())
            return@flow
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startDate = today.minusDays(days.toLong()).toString()
        val endDate = today.toString()
        emitAll(
            vitalsSampleDao.observeRollupsForUser(userId, startDate, endDate)
                .map { rows -> rows.map { it.toLatestVitals() } }
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun upsertRollup(
        localDate: String,
        timestampUtcEpochMs: Long,
        restingHeartRate: Double?,
        hrvRmssd: Double?,
        respiratoryRate: Double?,
        bodyTemperature: Double?,
        hydrationMl: Double?,
        spo2: Double?,
        sleepDurationMinutes: Int?,
        deepSleepMinutes: Int?
    ) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.VITALS, "upsertRollup with no signed-in user; dropping write")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        val zone = ZoneId.systemDefault()
        // Deterministic id: one rollup row per user per local day. Re-reading Health Connect for the
        // same day upserts this same row instead of duplicating the day. No UUID / no MessageDigest.
        val id = "$userId|rollup|$localDate"
        vitalsSampleDao.upsert(
            VitalsSampleEntity(
                id = id,
                userId = userId,
                localDate = localDate,
                timestampUtcEpochMs = timestampUtcEpochMs,
                tzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                kind = KIND_ROLLUP,
                restingHeartRate = restingHeartRate,
                hrvRmssd = hrvRmssd,
                respiratoryRate = respiratoryRate,
                bodyTemperature = bodyTemperature,
                hydrationMl = hydrationMl,
                spo2 = spo2,
                sleepDurationMinutes = sleepDurationMinutes,
                deepSleepMinutes = deepSleepMinutes,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                deletedAtEpochMs = null,
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.VITALS, "rollup upserted for $localDate; requesting sync")
        syncScheduler.requestSync()
    }

    override suspend fun upsertSample(
        localDate: String,
        timestampUtcEpochMs: Long,
        restingHeartRate: Double?,
        hrvRmssd: Double?,
        respiratoryRate: Double?,
        bodyTemperature: Double?,
        hydrationMl: Double?,
        spo2: Double?
    ) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.VITALS, "upsertSample with no signed-in user; dropping write")
            return
        }
        val nowMs = Timestamps.nowEpochMs()
        val zone = ZoneId.systemDefault()
        val id = "$userId|sample|$timestampUtcEpochMs"
        vitalsSampleDao.upsert(
            VitalsSampleEntity(
                id = id,
                userId = userId,
                localDate = localDate,
                timestampUtcEpochMs = timestampUtcEpochMs,
                tzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                kind = KIND_SAMPLE,
                restingHeartRate = restingHeartRate,
                hrvRmssd = hrvRmssd,
                respiratoryRate = respiratoryRate,
                bodyTemperature = bodyTemperature,
                hydrationMl = hydrationMl,
                spo2 = spo2,
                sleepDurationMinutes = null,
                deepSleepMinutes = null,
                updatedAtEpochMs = nowMs,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                deletedAtEpochMs = null,
                isSynced = false
            )
        )
        AppLogger.d(FeatureTag.VITALS, "sample upserted at $timestampUtcEpochMs; requesting sync")
        syncScheduler.requestSync()
    }

    private fun VitalsSampleEntity.toMetrics() = HealthMetricsData(
        dateMillis = timestampUtcEpochMs,
        hrvRmssd = hrvRmssd,
        restingHeartRate = restingHeartRate,
        sleepDurationMinutes = sleepDurationMinutes,
        deepSleepMinutes = deepSleepMinutes
    )

    private fun VitalsSampleEntity.toLatestVitals() = LatestVitals(
        localDate = localDate,
        restingHeartRate = restingHeartRate,
        hrvRmssd = hrvRmssd,
        respiratoryRate = respiratoryRate,
        bodyTemperature = bodyTemperature,
        hydrationMl = hydrationMl,
        spo2 = spo2
    )

    companion object {
        private const val KIND_ROLLUP = "rollup"
        private const val KIND_SAMPLE = "sample"

        /**
         * How many days of rollup history to read for readiness. Slightly wider than the
         * calculator's 28-day baseline window so a full window is always available even when the
         * user opens the app a day or two after the last sync.
         */
        private const val READINESS_WINDOW_DAYS = 35
    }
}
