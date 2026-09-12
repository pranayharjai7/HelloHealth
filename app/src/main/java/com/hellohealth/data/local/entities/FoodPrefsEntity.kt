package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `food_preferences` (Supabase conflict key: `user_id`). One row per user.
 * [allergies] and [cuisinePreferences] are stored via the `List<String>` type converter.
 */
@Entity(tableName = "food_prefs")
data class FoodPrefsEntity(
    @PrimaryKey val userId: String,
    val dietType: String,
    val allergies: List<String>,
    val cuisinePreferences: List<String>,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false
) : Syncable
