package com.hellohealth.data.food

import android.content.Context
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.local.entities.CachedFoodEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw shape of one record in `assets/common_foods.json` — the bundled common-foods catalog.
 * `ignoreUnknownKeys` lets the asset carry extra descriptive fields we don't map. Macros are on a
 * `per_100g` or `per_serving` [basisUnit]; optional macros default to null so a sparse record (e.g.
 * calories only) still decodes.
 */
@Serializable
private data class RawCommonFood(
    val slug: String,
    val name: String,
    val brand: String? = null,
    val basisUnit: String,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
    val caloriesPer: Double,
    val proteinGPer: Double? = null,
    val carbsGPer: Double? = null,
    val fatGPer: Double? = null,
    val fibreGPer: Double? = null,
    val barcode: String? = null,
)

/**
 * Loads the bundled read-only common-foods catalog from app assets into [CachedFoodEntity] rows
 * ready for Room seeding. Mirrors [com.hellohealth.data.exercise.ExerciseAssetLoader]'s hardening:
 * [load] returns a defined [Result] on failure (missing/corrupt asset) rather than throwing, so a
 * catalog problem degrades to "catalog unavailable" (quick-add + remote search still work) instead
 * of crashing app start.
 *
 * Each row is given a deterministic `"seed:<slug>"` id and a `lastRefreshedEpochMs` stamped by the
 * caller-supplied clock so the loader stays free of `Instant.now()` (keeps it unit-testable). The
 * bundled facts are treated as `source = "seed"`.
 */
@Singleton
open class FoodCatalogAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parse the asset into cache rows stamped with [nowEpochMs]. On any failure returns
     * [Result.failure] after logging — the caller leaves the table empty so the next launch retries.
     * Never throws. `open` so unit tests can substitute a result without touching the filesystem.
     */
    open fun load(nowEpochMs: Long): Result<List<CachedFoodEntity>> = runCatching {
        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        json.decodeFromString<List<RawCommonFood>>(raw).map { it.toEntity(nowEpochMs) }
    }.onFailure { t ->
        AppLogger.e(FeatureTag.NUTRITION, "failed to load $ASSET_NAME; catalog stays empty", t)
    }

    private fun RawCommonFood.toEntity(nowEpochMs: Long) = CachedFoodEntity(
        id = "seed:$slug",
        name = name,
        brand = brand,
        source = "seed",
        basisUnit = basisUnit,
        servingLabel = servingLabel,
        servingGrams = servingGrams,
        caloriesPer = caloriesPer,
        proteinGPer = proteinGPer,
        carbsGPer = carbsGPer,
        fatGPer = fatGPer,
        fibreGPer = fibreGPer,
        barcode = barcode,
        lastRefreshedEpochMs = nowEpochMs,
    )

    companion object {
        private const val ASSET_NAME = "common_foods.json"
    }
}
