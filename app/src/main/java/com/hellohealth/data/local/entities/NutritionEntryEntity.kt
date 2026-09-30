package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.data.local.Syncable

/**
 * Room mirror of `nutrition_entries` (Supabase conflict key: `id`) — one row per logged food or
 * water intake. Nutrition phase (the "calories-in" counterpart to the activity snapshot's
 * "calories-out").
 *
 * A single table serves two [kind]s, mirroring [VitalsSampleEntity]'s rollup/sample split:
 *  - `"food"`  — a logged food item under a [mealCategory] (breakfast/lunch/dinner/snack), carrying
 *    resolved macros ([calories] + optional [proteinG]/[carbsG]/[fatG]/[fibreG]).
 *  - `"water"` — a water intake row: [waterMl] set, [mealCategory] null, [foodName] = "Water".
 *
 * Every row has a random-UUID [id] (multi-row per user per day, like `emotion_records` — no
 * deterministic dedupe key). Macros ride the wire in natural units (kcal, grams). `Double?` → REAL
 * nullable. Syncable columns LAST (matches MIGRATION_10_11). The `userId` index serves the per-user
 * day read; `isSynced` serves the unsynced push scan.
 */
@Entity(
    tableName = "nutrition_entries",
    indices = [
        Index(value = ["userId"], name = "idx_nutrition_entries_userId"),
        Index(value = ["isSynced"], name = "idx_nutrition_entries_isSynced"),
    ],
)
data class NutritionEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val localDate: String,
    val timestampUtcEpochMs: Long,
    val tzOffsetMinutes: Int,
    val kind: String,
    val mealCategory: String? = null,
    val foodId: String? = null,
    val foodName: String,
    val quantity: Double,
    val unit: String,
    val calories: Double,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fibreG: Double? = null,
    val waterMl: Double? = null,
    val entryMethod: String,
    override val updatedAtEpochMs: Long,
    override val updatedAtTzOffsetMinutes: Int,
    override val deletedAtEpochMs: Long? = null,
    override val isSynced: Boolean = false,
) : Syncable
