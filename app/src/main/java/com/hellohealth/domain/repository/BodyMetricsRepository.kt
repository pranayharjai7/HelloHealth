package com.hellohealth.domain.repository

import com.hellohealth.domain.model.BodyMetric
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the per-day body-composition history (`body_metrics`). Room-first, offline-first:
 * writes land locally (`isSynced=false`) and [com.hellohealth.sync.BodyMetricSyncer] reconciles with
 * Supabase. Mirrors [VitalsRepository]: empty/no-op (never throws) when there is no signed-in user.
 */
interface BodyMetricsRepository {

    /** Live body-metric rows over the last [days] days, ascending by date. Empty when no user/data. */
    fun observeRecentBodyMetrics(days: Int): Flow<List<BodyMetric>>

    /** The most recent body-metric row, or null when no user / no data. */
    fun observeLatest(): Flow<BodyMetric?>

    /** Log a manual weight (kg), optionally with waist (cm), for a local day. No-op when no user. */
    suspend fun logWeight(localDate: String, weightKg: Double, waistCm: Double? = null)

    /**
     * Upsert a body-metric row captured from Health Connect for [localDate] (idempotent via the
     * deterministic id). No-op when no user or when every field is null. Called off the dashboard's
     * critical path by [ActivityRepository]'s daily summary fetch.
     */
    suspend fun upsertFromHealthConnect(
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
    )
}
