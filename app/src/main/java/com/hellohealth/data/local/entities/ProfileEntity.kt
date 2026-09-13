package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `profiles` (Supabase conflict key: `id`). One row per user; the primary
 * key [userId] maps to the Supabase `profiles.id` column.
 *
 * Enum vitals are stored as their `name` string (nullable) and converted in the repository mapper,
 * keeping this entity converter-free. Onboarding vitals are declared AFTER the [Syncable] overrides
 * so Room appends them to the end of the table — matching MIGRATION_4_5's `ALTER TABLE ADD COLUMN`
 * (SQLite appends), so the migration and Room's generated v5 schema agree on column order.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String?,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
    val gender: String? = null,
    val birthDateEpochDay: Long? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activityLevel: String? = null,
    val goalType: String? = null,
    val targetWeightKg: Double? = null,
    val targetRateKgPerWeek: Double? = null,
    val unitPreference: String = "METRIC",
    val hasOnboarded: Boolean = false,
    val isDynamicTheme: Boolean = true
) : Syncable
