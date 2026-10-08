package com.hellohealth.domain.repository

import com.hellohealth.domain.model.BodyMetric
import kotlinx.coroutines.flow.Flow

/**
 * Opaque Undo token for [BodyMetricsRepository.logWeight]. Captures the day's prior row (or its
 * absence) so [BodyMetricsRepository.undoLog] can restore exactly that state. Carries only domain
 * data — [prior] is null when the day had no row before the log (undo then tombstones it).
 */
data class BodyLogUndo(val localDate: String, val prior: BodyMetric?)

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

    /**
     * Log a manual weight (kg), optionally with waist (cm), for a local day. No-op when no user.
     * Returns an opaque [BodyLogUndo] token capturing the day's state BEFORE the write, so the caller
     * can offer a true Undo via [undoLog]; null when the write was dropped (no signed-in user).
     */
    suspend fun logWeight(localDate: String, weightKg: Double, waistCm: Double? = null): BodyLogUndo?

    /**
     * Reverse a [logWeight] using its [BodyLogUndo] token: restore the exact prior row, or tombstone
     * the day's row when there was none before. No-op when no user. Safe if the row changed in the
     * meantime — the restore is a normal LWW write (newer updatedAt), and sync is re-requested.
     */
    suspend fun undoLog(token: BodyLogUndo)

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
