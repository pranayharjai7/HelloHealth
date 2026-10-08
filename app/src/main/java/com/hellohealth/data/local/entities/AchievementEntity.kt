package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `achievements` (Supabase conflict key: `id`) — one row per user per earned
 * achievement. Deterministic id `"$userId|ach|$code"` so the one-time unlock is a stable upsert.
 * [code] is a stable [com.hellohealth.domain.wellness.Achievement] code. [unlockedAtEpochMs] is set
 * once when the achievement is first earned and NEVER overwritten on recompute — that non-null guard
 * is how the repository keeps the unlock one-time.
 *
 * Syncable columns LAST. `userId` index serves per-user sync filtering; `isSynced` the unsynced scan.
 */
@Entity(
    tableName = "achievements",
    indices = [
        Index(value = ["userId"], name = "idx_achievements_userId"),
        Index(value = ["isSynced"], name = "idx_achievements_isSynced"),
    ],
)
data class AchievementEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val code: String,
    val unlockedAtEpochMs: Long,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
