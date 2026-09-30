package com.hellohealth.data.food

import com.hellohealth.BuildConfig
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.CachedFoodEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Read-only client for USDA FoodData Central's food search. Maps each hit to a per-100g
 * [CachedFoodEntity] (`usda:<fdcId>` id) by pulling the four macro nutrients + energy out of the
 * `foodNutrients` array by their FDC nutrient numbers.
 *
 * Hardened per the no-crash guarantee: [search] wraps the whole call in `runCatching` and returns
 * an EMPTY list on any failure (non-2xx incl. 429 rate-limit, timeout, malformed JSON). It never
 * throws — the repository layers these results on top of the always-available local cache.
 */
@Singleton
open class UsdaFoodDataSource @Inject constructor(
    @Named("food") private val client: HttpClient,
) {

    open suspend fun search(query: String, pageSize: Int = 20): List<CachedFoodEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching {
            val response = client.get("$BASE_URL/fdc/v1/foods/search") {
                parameter("api_key", BuildConfig.USDA_FDC_API_KEY)
                parameter("query", trimmed)
                parameter("pageSize", pageSize.toString())
                parameter("dataType", "Foundation,SR Legacy")
            }
            if (!response.status.isSuccess()) {
                AppLogger.w(FeatureTag.NUTRITION, "USDA search HTTP ${response.status.value}; returning empty")
                return emptyList()
            }
            val now = Timestamps.nowEpochMs()
            response.body<UsdaSearchResponse>().foods.mapNotNull { it.toEntity(now) }
        }.getOrElse {
            AppLogger.w(FeatureTag.NUTRITION, "USDA search failed: ${it.message}; returning empty")
            emptyList()
        }
    }

    private fun UsdaFood.toEntity(nowEpochMs: Long): CachedFoodEntity? {
        val name = description?.takeIf { it.isNotBlank() } ?: return null
        val calories = nutrientValue(NUM_ENERGY_KCAL) ?: return null
        return CachedFoodEntity(
            id = "usda:$fdcId",
            name = name,
            brand = brandOwner?.takeIf { it.isNotBlank() },
            source = "usda",
            basisUnit = "per_100g", // FDC Foundation/SR values are per 100 g
            servingLabel = null,
            servingGrams = null,
            caloriesPer = calories,
            proteinGPer = nutrientValue(NUM_PROTEIN),
            carbsGPer = nutrientValue(NUM_CARBS),
            fatGPer = nutrientValue(NUM_FAT),
            fibreGPer = nutrientValue(NUM_FIBRE),
            barcode = gtinUpc?.takeIf { it.isNotBlank() },
            lastRefreshedEpochMs = nowEpochMs,
        )
    }

    private fun UsdaFood.nutrientValue(number: String): Double? =
        foodNutrients.firstOrNull { it.nutrientNumber == number }?.value

    @Serializable
    private data class UsdaSearchResponse(val foods: List<UsdaFood> = emptyList())

    @Serializable
    private data class UsdaFood(
        val fdcId: Long,
        val description: String? = null,
        val brandOwner: String? = null,
        val gtinUpc: String? = null,
        val foodNutrients: List<UsdaNutrient> = emptyList(),
    )

    @Serializable
    private data class UsdaNutrient(
        val nutrientNumber: String? = null,
        val value: Double? = null,
    )

    companion object {
        private const val BASE_URL = "https://api.nal.usda.gov"
        // FDC nutrient numbers (stable across dataTypes).
        private const val NUM_ENERGY_KCAL = "1008"
        private const val NUM_PROTEIN = "1003"
        private const val NUM_FAT = "1004"
        private const val NUM_CARBS = "1005"
        private const val NUM_FIBRE = "1079"
    }
}
