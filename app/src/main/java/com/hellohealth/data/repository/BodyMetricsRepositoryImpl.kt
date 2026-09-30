package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.BodyMetricDao
import com.hellohealth.data.local.entities.BodyMetricEntity
import com.hellohealth.domain.model.BodyMetric
import com.hellohealth.domain.repository.BodyMetricsRepository
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
 * Room-first body-metrics repository. Writes hit Room (`isSynced=false`) then poke [SyncScheduler];
 * [com.hellohealth.sync.BodyMetricSyncer] owns the Supabase round-trip. Reads are Room Flows. With no
 * signed-in user, reads emit empty/null and writes are dropped with a warning (mirrors
 * [VitalsRepositoryImpl]). Deterministic id `"$userId|body|$localDate"` — one row per user per day, so
 * a Health-Connect-daily capture and a manual weight log for the same day upsert (never duplicate).
 */
@Singleton
class BodyMetricsRepositoryImpl @Inject constructor(
    private val bodyMetricDao: BodyMetricDao,
    private val sessionManager: SupabaseSessionManager,
    private val syncScheduler: SyncScheduler,
) : BodyMetricsRepository {

    override fun observeRecentBodyMetrics(days: Int): Flow<List<BodyMetric>> = flow {
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
            bodyMetricDao.observeRecentForUser(userId, startDate, endDate)
                .map { rows -> rows.map { it.toDomain() } }
        )
    }.flowOn(Dispatchers.IO)

    override fun observeLatest(): Flow<BodyMetric?> = flow {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            emit(null)
            return@flow
        }
        emitAll(bodyMetricDao.observeLatest(userId).map { it?.toDomain() })
    }.flowOn(Dispatchers.IO)

    override suspend fun logWeight(localDate: String, weightKg: Double, waistCm: Double?) {
        val userId = sessionManager.getCurrentUserId()
        if (userId == null) {
            AppLogger.w(FeatureTag.BODY_METRICS, "logWeight with no signed-in user; dropping write")
            return
        }
        // Load-then-merge: keep any other metrics already recorded for the day, update weight/waist.
        val existing = bodyMetricDao.getForDate(userId, localDate)
        upsert(
            userId = userId,
            localDate = localDate,
            weightKg = weightKg,
            heightCm = existing?.heightCm,
            bodyFatPct = existing?.bodyFatPct,
            leanMassKg = existing?.leanMassKg,
            fatMassKg = existing?.fatMassKg,
            bodyWaterKg = existing?.bodyWaterKg,
            boneMassKg = existing?.boneMassKg,
            bmr = existing?.bmr,
            bmi = existing?.bmi,
            waistCm = waistCm ?: existing?.waistCm,
            vo2max = existing?.vo2max,
            source = "manual",
        )
    }

    override suspend fun upsertFromHealthConnect(
        localDate: String,
        weightKg: Double?,
        heightCm: Double?,
        bodyFatPct: Double?,
        leanMassKg: Double?,
        fatMassKg: Double?,
        bodyWaterKg: Double?,
        boneMassKg: Double?,
        bmr: Double?,
        bmi: Double?,
        vo2max: Double?,
    ) {
        val userId = sessionManager.getCurrentUserId() ?: return
        // No body data for the day → no phantom row.
        val hasAny = listOf(weightKg, heightCm, bodyFatPct, leanMassKg, fatMassKg, bodyWaterKg, boneMassKg, bmr, bmi, vo2max).any { it != null }
        if (!hasAny) return
        val existing = bodyMetricDao.getForDate(userId, localDate)
        upsert(
            userId = userId,
            localDate = localDate,
            weightKg = weightKg,
            heightCm = heightCm,
            bodyFatPct = bodyFatPct,
            leanMassKg = leanMassKg,
            fatMassKg = fatMassKg,
            bodyWaterKg = bodyWaterKg,
            boneMassKg = boneMassKg,
            bmr = bmr,
            bmi = bmi,
            // Preserve a manually-entered waist across a Health Connect refresh.
            waistCm = existing?.waistCm,
            vo2max = vo2max,
            source = "health_connect",
        )
    }

    private suspend fun upsert(
        userId: String,
        localDate: String,
        weightKg: Double?,
        heightCm: Double?,
        bodyFatPct: Double?,
        leanMassKg: Double?,
        fatMassKg: Double?,
        bodyWaterKg: Double?,
        boneMassKg: Double?,
        bmr: Double?,
        bmi: Double?,
        waistCm: Double?,
        vo2max: Double?,
        source: String,
    ) {
        val now = Timestamps.nowEpochMs()
        val zone = ZoneId.systemDefault()
        bodyMetricDao.upsert(
            BodyMetricEntity(
                id = "$userId|body|$localDate",
                userId = userId,
                localDate = localDate,
                timestampUtcEpochMs = now,
                tzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                weightKg = weightKg,
                heightCm = heightCm,
                bodyFatPct = bodyFatPct,
                leanMassKg = leanMassKg,
                fatMassKg = fatMassKg,
                bodyWaterKg = bodyWaterKg,
                boneMassKg = boneMassKg,
                bmr = bmr,
                bmi = bmi,
                waistCm = waistCm,
                vo2max = vo2max,
                source = source,
                updatedAtEpochMs = now,
                updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(zone),
                deletedAtEpochMs = null,
                isSynced = false,
            )
        )
        syncScheduler.requestSync()
    }

    private fun BodyMetricEntity.toDomain() = BodyMetric(
        localDate = localDate,
        weightKg = weightKg,
        heightCm = heightCm,
        bodyFatPct = bodyFatPct,
        leanMassKg = leanMassKg,
        fatMassKg = fatMassKg,
        bodyWaterKg = bodyWaterKg,
        boneMassKg = boneMassKg,
        bmr = bmr,
        bmi = bmi,
        waistCm = waistCm,
        vo2max = vo2max,
        source = source,
    )
}
