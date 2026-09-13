package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `emotion_records` (Supabase conflict key: `id`). Multiple rows per user — one per
 * logged mood. Unlike the single-row goals/profile tables, this follows the multi-row Snapshot
 * template: an explicit string [id] primary key and list-returning DAO queries.
 *
 * [emotion] is stored as the [com.hellohealth.domain.model.EmotionType] `name` string (converter-
 * free, like the profile enums). [localDate] is the ISO `yyyy-MM-dd` the log falls on in the
 * logging-time zone — it sorts lexicographically and lets "today" / windowed queries run without
 * re-deriving the day from the UTC timestamp on every read.
 *
 * The four [Syncable] sync-meta columns are declared LAST so Room appends them after the feature
 * columns — matching the MIGRATION_5_6 `CREATE TABLE` column order (see Migrations.kt).
 */
@Entity(tableName = "emotion_records")
data class EmotionRecordEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val timestampUtcEpochMs: Long,
    val tzOffsetMinutes: Int,
    val localDate: String,
    val emotion: String,
    val confidence: Double,
    val source: String,
    val note: String?,
    val visibility: String,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false
) : Syncable
