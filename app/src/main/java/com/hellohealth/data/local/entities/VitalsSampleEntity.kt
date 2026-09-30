package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `vitals_samples` (Supabase conflict key: `id`) — one row per captured vitals
 * reading. P3 Vitals & Recovery.
 *
 * A single table serves two [kind]s:
 *  - `"rollup"` — exactly one row per user per local day, the aggregated daily vitals that feed the
 *    readiness calculation. Its [id] is deterministic (`"$userId|rollup|$localDate"`) so re-reading
 *    Health Connect upserts the same row instead of duplicating a day.
 *  - `"sample"` — an individual intraday reading (reserved; not written by the initial rollup flow).
 *
 * All vitals are stored in NATURAL HUMAN UNITS (bpm, ms, breaths/min, °C, ml, SpO2 as 0-100) —
 * matching [com.hellohealth.data.health.HealthConnectManager]. `Double?` → REAL nullable, `Int?` →
 * INTEGER nullable. Syncable columns LAST (matches MIGRATION_9_10). The `userId` index serves the
 * per-user rollup window read; `isSynced` serves the unsynced push scan.
 */
@Entity(
    tableName = "vitals_samples",
    indices = [
        Index(value = ["userId"], name = "idx_vitals_samples_userId"),
        Index(value = ["isSynced"], name = "idx_vitals_samples_isSynced"),
    ],
)
data class VitalsSampleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val timestampUtcEpochMs: Long,
    val tzOffsetMinutes: Int,
    val kind: String,
    val restingHeartRate: Double? = null,
    val hrvRmssd: Double? = null,
    val respiratoryRate: Double? = null,
    val bodyTemperature: Double? = null,
    val hydrationMl: Double? = null,
    val spo2: Double? = null,
    val sleepDurationMinutes: Int? = null,
    val deepSleepMinutes: Int? = null,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
