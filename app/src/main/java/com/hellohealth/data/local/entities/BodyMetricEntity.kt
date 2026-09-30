package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `body_metrics` (Supabase conflict key: `id`) — one body-composition row per user
 * per local day, the P5 body-analytics history that feeds the Health screen's Body section trends.
 *
 * Deterministic [id] (`"$userId|body|$localDate"`), mirroring the vitals rollup discipline, so a
 * Health-Connect-daily capture or a manual weight log for the same day upserts the same row instead
 * of duplicating it. All masses in kg, height in **cm** (canonical), body fat as a 0-100 percentage.
 * Derived [bmi] / [fatMassKg] are persisted (not re-derived each read) so a trend point survives even
 * if height is later missing. [source] is `"manual"` or `"health_connect"`. Every field but the keys
 * is nullable — a day may carry only weight, only a scan, etc. Syncable columns LAST (matches the
 * migration column order). `userId` index serves per-user range reads; `isSynced` the push scan.
 */
@Entity(
    tableName = "body_metrics",
    indices = [
        Index(value = ["userId"], name = "idx_body_metrics_userId"),
        Index(value = ["isSynced"], name = "idx_body_metrics_isSynced"),
    ],
)
data class BodyMetricEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val timestampUtcEpochMs: Long,
    val tzOffsetMinutes: Int,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val bodyFatPct: Double? = null,
    val leanMassKg: Double? = null,
    val fatMassKg: Double? = null,
    val bodyWaterKg: Double? = null,
    val boneMassKg: Double? = null,
    val bmr: Double? = null,
    val bmi: Double? = null,
    val waistCm: Double? = null,
    val vo2max: Double? = null,
    val source: String,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
