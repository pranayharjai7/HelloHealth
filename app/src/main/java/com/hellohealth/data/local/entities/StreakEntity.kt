package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `streaks` (Supabase conflict key: `id`) — one row per user per pillar tracking the
 * current and longest run. Deterministic id `"$userId|streak|$pillar"` so recompute upserts the same
 * row (never duplicates). [pillar] is a [com.hellohealth.domain.wellness.Pillar] name, or the literal
 * `balanced` for the composite Balanced-Day streak. [currentCount]/[longestCount] are recomputed on
 * read; [lastHitLocalDate] is the most recent day the pillar was hit (for display / debugging).
 *
 * Syncable columns LAST (project convention). `userId` index serves per-user sync filtering;
 * `isSynced` the unsynced push scan.
 */
@Entity(
    tableName = "streaks",
    indices = [
        Index(value = ["userId"], name = "idx_streaks_userId"),
        Index(value = ["isSynced"], name = "idx_streaks_isSynced"),
    ],
)
data class StreakEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val pillar: String,
    val currentCount: Int,
    val longestCount: Int,
    val lastHitLocalDate: String?,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
