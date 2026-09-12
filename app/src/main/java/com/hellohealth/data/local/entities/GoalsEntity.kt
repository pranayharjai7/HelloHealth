package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `activity_goals` (Supabase conflict key: `user_id`). One row per user.
 */
@Entity(tableName = "goals")
data class GoalsEntity(
    @PrimaryKey val userId: String,
    val steps: Int,
    val activeCalories: Int,
    val activeMinutes: Int,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false
) : Syncable
