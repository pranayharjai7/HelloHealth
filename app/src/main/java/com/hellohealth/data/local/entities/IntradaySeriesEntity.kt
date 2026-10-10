package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room table `intraday_series` — DEVICE-LOCAL intraday time-of-day buckets (Phase 1 of the
 * health-intelligence program). One row per (user, metric, local day, bucket start).
 *
 * Deliberately a NEW table rather than the dormant `vitals_samples kind="sample"` path: vitals rows are
 * vitals-shaped (RHR/HRV/respiratory/temp/hydration/spo2/sleep), whereas intraday series are TYPED
 * metric buckets (a [metric] discriminator + a single [value]). A separate table keeps the readiness
 * rollup read (`vitals_samples kind='rollup'`) completely untouched and lets intraday evolve on its own.
 *
 * **Not synced to Supabase** — intraday is high-volume (~288 heart-rate buckets/day) and stays on-device;
 * only daily rollups + computed scores sync. The [Syncable] sync-meta columns are still carried for a
 * uniform entity shape and so a future opt-in sync needs no migration, but no syncer reads this table.
 *
 * [id] is deterministic (`"$userId|$metric|$localDate|$bucketStartUtcEpochMs"`) so re-reading Health
 * Connect for the same day upserts the same bucket instead of duplicating it — this is what makes
 * partial-day → full-day reconciliation converge without double-counting. [value] is in the metric's
 * natural unit (steps count, active kcal, or bpm). Syncable columns LAST (matches the vitals_samples
 * column order from MIGRATION_9_10).
 */
@Entity(
    tableName = "intraday_series",
    indices = [
        Index(value = ["userId", "localDate", "metric"], name = "idx_intraday_series_user_date_metric"),
        Index(value = ["isSynced"], name = "idx_intraday_series_isSynced"),
    ],
)
data class IntradaySeriesEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val metric: String,
    val bucketStartUtcEpochMs: Long,
    val bucketMinutes: Int,
    val tzOffsetMinutes: Int,
    val value: Double,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable {
    companion object {
        const val METRIC_STEPS = "steps"
        const val METRIC_ACTIVE_CAL = "active_cal"
        const val METRIC_HR = "hr"

        /** Deterministic row id so a re-read of the same bucket upserts in place (reconciliation). */
        fun idFor(userId: String, metric: String, localDate: String, bucketStartUtcEpochMs: Long): String =
            "$userId|$metric|$localDate|$bucketStartUtcEpochMs"
    }
}
