package com.hellohealth.data.local.entities

import androidx.room.Entity
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `daily_health_snapshots` (Supabase conflict key: `user_id,snapshot_date`).
 * Multiple rows per user — one per calendar day.
 *
 * The full [com.hellohealth.domain.model.HealthSummary] (incl. exercise sessions) is stored
 * as a serialized JSON [summaryJson] blob rather than exploded into ~30 columns: the payload
 * is display/cache data queried only by (user, date), never filtered by inner fields, and the
 * SnapshotSyncer already owns a JSON DTO for the Supabase round-trip. [snapshotDate] is an ISO
 * `yyyy-MM-dd` string (matches the Supabase text column and sorts lexicographically).
 */
@Entity(tableName = "snapshot", primaryKeys = ["userId", "snapshotDate"])
data class SnapshotEntity(
    val userId: String,
    val snapshotDate: String,
    val snapshotTimezone: String,
    val syncStatus: String,
    val dataSource: String,
    val lastSyncedAtEpochMs: Long,
    val summaryJson: String,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false
) : Syncable
