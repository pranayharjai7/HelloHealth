package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `points_ledger` (Supabase conflict key: `id`) — an APPEND-ONLY ledger of awarded
 * points. Deterministic id `"$userId|pts|$localDate|$source"` makes each (day, source) award
 * IDEMPOTENT: recompute-on-read upserts the same row rather than double-awarding. [source] is the
 * award reason (e.g. a pillar name, `balanced_day`, or an achievement code). [points] is the amount.
 *
 * Syncable columns LAST. `userId` index serves per-user sync filtering; `(userId, localDate)` the
 * per-day total read; `isSynced` the unsynced scan.
 */
@Entity(
    tableName = "points_ledger",
    indices = [
        Index(value = ["userId"], name = "idx_points_ledger_userId"),
        Index(value = ["userId", "localDate"], name = "idx_points_ledger_userId_localDate"),
        Index(value = ["isSynced"], name = "idx_points_ledger_isSynced"),
    ],
)
data class PointsLedgerEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val source: String,
    val points: Int,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
