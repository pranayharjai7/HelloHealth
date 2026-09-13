package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `workout_sessions` (Supabase conflict key: `id`). Multiple rows per user — one per
 * manually-logged workout. Follows the multi-row Emotions template: an explicit string [id] primary
 * key (a random UUID, minted in WorkoutRepositoryImpl) and list-returning DAO queries.
 *
 * [activityType] is stored as the [com.hellohealth.domain.model.WorkoutActivityType] `name` string
 * (converter-free, like the emotion/profile enums). [localDate] is the ISO `yyyy-MM-dd` the workout
 * falls on in the logging-time zone — it sorts lexicographically and lets day/windowed queries run
 * without re-deriving the day from the UTC timestamp on every read.
 *
 * Phase A is intentionally lean: NO exercise/set columns (Phase B), NO detailJson blob, NO
 * HC-linkage columns (Phase D) — later phases add their own tables/columns additively so no
 * workout row is ever reshaped.
 *
 * The four [Syncable] sync-meta columns are declared LAST so Room appends them after the feature
 * columns — matching the MIGRATION_7_8 `CREATE TABLE` column order (see Migrations.kt).
 */
@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val activityType: String,
    val title: String?,
    val startTimeUtcEpochMs: Long,
    val endTimeUtcEpochMs: Long,
    val durationMinutes: Long,
    val calories: Double?,
    val distanceKm: Double?,
    val note: String?,
    val localDate: String,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false
) : Syncable
