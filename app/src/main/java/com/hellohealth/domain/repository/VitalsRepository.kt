package com.hellohealth.domain.repository

import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the P3 vitals rollup. Room-first, offline-first: writes land locally
 * (`isSynced=false`) and a background [com.hellohealth.sync.VitalsSampleSyncer] reconciles with
 * Supabase; reads are Room Flows. Mirrors the EmotionsRepository contract — the impl knows nothing
 * about Postgrest, and returns empty/null (never throws) when there is no signed-in user.
 *
 * Readiness is derived here (and ONLY here) from the persisted daily rollup — the calculator never
 * touches live Health Connect. Live HC is read only by the daily-rollup upsert and the backfill.
 */
interface VitalsRepository {

    /**
     * The current readiness score derived from the persisted daily rollup window, or null when there
     * is no signed-in user. Emits [com.hellohealth.domain.model.vitals.ReadinessStatus.INSUFFICIENT_DATA]
     * / `isEstablishingBaseline` while the user has < 7 days of rollup history.
     */
    fun observeReadiness(): Flow<ReadinessScore?>

    /**
     * Live daily rollups over the last [days] days as pure [HealthMetricsData], ascending by date —
     * feeds the trends charts. Empty when no user / no data.
     */
    fun observeRecentRollups(days: Int): Flow<List<HealthMetricsData>>

    /**
     * The most recent day's raw vitals for the dashboard card's chips, or null when there is no
     * signed-in user or no rollup yet. Nullable fields render as dashes in the UI.
     */
    fun observeLatestVitals(): Flow<LatestVitals?>

    /**
     * Upsert the daily rollup for a local day. The row id is deterministic
     * (`"$userId|rollup|$localDate"`) so re-reading Health Connect for the same day upserts the same
     * row instead of duplicating it. No-op (logged) when no user is signed in.
     */
    suspend fun upsertRollup(
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
    )

    /**
     * Upsert an individual intraday reading (reserved; not written by the initial rollup flow).
     * Deterministic id `"$userId|sample|$timestampUtcEpochMs"`. No-op (logged) when no user.
     */
    suspend fun upsertSample(
        localDate: String,
        timestampUtcEpochMs: Long,
        restingHeartRate: Double?,
        hrvRmssd: Double?,
        respiratoryRate: Double?,
        bodyTemperature: Double?,
        hydrationMl: Double?,
        spo2: Double?
    )
}
