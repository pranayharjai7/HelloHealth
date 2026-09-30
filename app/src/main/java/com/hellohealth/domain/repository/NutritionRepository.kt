package com.hellohealth.domain.repository

import com.hellohealth.domain.model.nutrition.FoodEntry
import com.hellohealth.domain.model.nutrition.MealCategory
import com.hellohealth.domain.model.nutrition.NutritionDaySummary
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the Nutrition dimension — the per-user food + water log (`nutrition_entries`)
 * plus the read-only, per-device food catalog (`cached_foods`). Room-first, offline-first: writes
 * land locally (`isSynced=false`) and the background [com.hellohealth.sync.NutritionEntrySyncer]
 * reconciles the per-user log with Supabase; the catalog is never synced (re-fetchable cache).
 *
 * Mirrors the [VitalsRepository]/EmotionsRepository contract: the impl knows nothing about Postgrest
 * and returns empty/no-op (never throws) when there is no signed-in user, so screens never special
 * case a missing session.
 *
 * The energy-balance `net = caloriesOut − caloriesIn` is NOT computed here — it is joined at read
 * time in the dashboard VM (this repo owns only the calories-in side).
 */
interface NutritionRepository {

    /**
     * The aggregated [NutritionDaySummary] for a local ISO day (food totals + per-meal grouping +
     * water). Emits [NutritionDaySummary.empty] for a day with no entries or no signed-in user.
     */
    fun observeDaySummary(localDate: String): Flow<NutritionDaySummary>

    /**
     * The day's live food entries (tombstones excluded), newest first. Empty when no user / no data.
     * A convenience read for screens that want the flat list rather than the grouped summary.
     */
    fun observeEntries(localDate: String): Flow<List<FoodEntry>>

    /**
     * Log a bare quick-add: a name + calories the user typed, optionally with macros, under a meal.
     * No catalog row involved ([entryMethod] = "quick_add"). No-op (logged) when no user.
     */
    suspend fun addQuickAdd(
        localDate: String,
        mealCategory: MealCategory,
        foodName: String,
        quantity: Double,
        unit: String,
        calories: Double,
        proteinG: Double? = null,
        carbsG: Double? = null,
        fatG: Double? = null,
        fibreG: Double? = null,
    )

    /**
     * Log an entry resolved from a [com.hellohealth.data.local.entities.CachedFoodEntity] catalog
     * item, scaling its per-basis macros to [quantity]/[unit] at log time. No-op when no user or the
     * food id is not in the cache.
     */
    suspend fun addFromFood(
        localDate: String,
        mealCategory: MealCategory,
        foodId: String,
        quantity: Double,
        unit: String,
    )

    /** Log a water intake row (default one 250 ml glass). No-op (logged) when no user. */
    suspend fun addWater(localDate: String, waterMl: Double = 250.0)

    /** Soft-delete an entry (food or water) by id — writes a tombstone that syncs. No-op when no user. */
    suspend fun deleteEntry(id: String)

    /**
     * Typeahead food search. Room cache first; the remote (USDA + Open Food Facts) augmentation is
     * layered in by the impl in a later phase and every remote call has a defined fallback, so this
     * always returns the local matches at minimum and never throws. Blank query → empty list.
     */
    suspend fun searchFoods(query: String): List<com.hellohealth.data.local.entities.CachedFoodEntity>

    /**
     * Resolve a scanned barcode to a cached food, consulting the local cache first and (in a later
     * phase) Open Food Facts on a miss, caching the result. Null when unresolved. Never throws.
     */
    suspend fun resolveBarcode(barcode: String): com.hellohealth.data.local.entities.CachedFoodEntity?

    /**
     * Idempotently seed the bundled common-foods catalog into `cached_foods` if it is empty. Safe to
     * call on every app start (count-gated); never throws (a bad asset just leaves the cache empty
     * and the app falls back to quick-add + remote search). Mirrors the exercise-catalog seed.
     */
    suspend fun seedCatalogIfEmpty()
}
