package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room mirror of the read-only, per-device food catalog — a re-fetchable read-through cache of
 * canonical nutrition facts. Like [ExerciseEntity], this is global (NOT user-owned) and NOT synced,
 * so it carries NO `userId` and NONE of the [com.hellohealth.data.local.Syncable] columns, and
 * there is no Supabase table / syncer for it.
 *
 * Rows come from three sources, encoded in the [id] prefix so a repeat lookup upserts the same row:
 *  - `"seed:<slug>"` — the bundled `assets/common_foods.json` common-foods catalog.
 *  - `"usda:<fdcId>"` — a USDA FoodData Central item resolved via the remote search.
 *  - `"off:<barcode>"` — an Open Food Facts product resolved via barcode lookup.
 *
 * Macros are stored on a canonical [basisUnit] basis — either `"per_100g"` or `"per_serving"` —
 * with [caloriesPer] and the optional per-basis macro grams; the log path scales them to the
 * logged quantity. Nothing here is user data, so a stale/missing cache row is harmless (re-fetch or
 * fall back to quick-add). The `name` index serves the typeahead; `barcode` serves scan lookups.
 */
@Entity(
    tableName = "cached_foods",
    indices = [
        Index(value = ["name"], name = "idx_cached_foods_name"),
        Index(value = ["barcode"], name = "idx_cached_foods_barcode"),
    ],
)
data class CachedFoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String? = null,
    val source: String,
    val basisUnit: String,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
    val caloriesPer: Double,
    val proteinGPer: Double? = null,
    val carbsGPer: Double? = null,
    val fatGPer: Double? = null,
    val fibreGPer: Double? = null,
    val barcode: String? = null,
    val lastRefreshedEpochMs: Long,
)
