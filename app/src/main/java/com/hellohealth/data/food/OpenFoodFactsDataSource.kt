package com.hellohealth.data.food

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.CachedFoodEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Read-only client for Open Food Facts. Two uses: [lookupBarcode] resolves a scanned EAN/UPC to a
 * product (the primary path — OFF is barcode-first and needs no API key), and [search] does a
 * name typeahead against the search endpoint.
 *
 * OFF stores nutriments per 100 g under `nutriments` with `energy-kcal_100g` + macro `_100g` keys,
 * so every hit maps to a per-100g [CachedFoodEntity] (`off:<barcode>` id). Hardened per the no-crash
 * guarantee: [lookupBarcode] returns null and [search] returns an empty list on any failure
 * (`status==0` product-not-found, non-2xx, timeout, malformed JSON). Neither throws.
 */
@Singleton
open class OpenFoodFactsDataSource @Inject constructor(
    @Named("food") private val client: HttpClient,
) {

    open suspend fun lookupBarcode(barcode: String): CachedFoodEntity? {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val response = client.get("$BASE_URL/api/v2/product/$trimmed") {
                // Only the fields we map, to keep the payload small.
                parameter("fields", "code,product_name,brands,nutriments")
            }
            if (!response.status.isSuccess()) {
                AppLogger.w(FeatureTag.NUTRITION, "OFF barcode HTTP ${response.status.value}; returning null")
                return null
            }
            val body = response.body<OffProductResponse>()
            if (body.status != 1 || body.product == null) return null
            body.product.toEntity(trimmed, Timestamps.nowEpochMs())
        }.getOrElse {
            AppLogger.w(FeatureTag.NUTRITION, "OFF barcode lookup failed: ${it.message}; returning null")
            null
        }
    }

    open suspend fun search(query: String, pageSize: Int = 20): List<CachedFoodEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return runCatching {
            val response = client.get("$BASE_URL/cgi/search.pl") {
                parameter("search_terms", trimmed)
                parameter("search_simple", "1")
                parameter("action", "process")
                parameter("json", "1")
                parameter("page_size", pageSize.toString())
                parameter("fields", "code,product_name,brands,nutriments")
            }
            if (!response.status.isSuccess()) {
                AppLogger.w(FeatureTag.NUTRITION, "OFF search HTTP ${response.status.value}; returning empty")
                return emptyList()
            }
            val now = Timestamps.nowEpochMs()
            response.body<OffSearchResponse>().products.mapNotNull { product ->
                product.code?.let { product.toEntity(it, now) }
            }
        }.getOrElse {
            AppLogger.w(FeatureTag.NUTRITION, "OFF search failed: ${it.message}; returning empty")
            emptyList()
        }
    }

    private fun OffProduct.toEntity(barcode: String, nowEpochMs: Long): CachedFoodEntity? {
        val name = productName?.takeIf { it.isNotBlank() } ?: return null
        val calories = nutriments?.energyKcal100g ?: return null
        return CachedFoodEntity(
            id = "off:$barcode",
            name = name,
            brand = brands?.takeIf { it.isNotBlank() },
            source = "off",
            basisUnit = "per_100g",
            servingLabel = null,
            servingGrams = null,
            caloriesPer = calories,
            proteinGPer = nutriments.protein100g,
            carbsGPer = nutriments.carbs100g,
            fatGPer = nutriments.fat100g,
            fibreGPer = nutriments.fibre100g,
            barcode = barcode,
            lastRefreshedEpochMs = nowEpochMs,
        )
    }

    @Serializable
    private data class OffProductResponse(
        val status: Int = 0,
        val product: OffProduct? = null,
    )

    @Serializable
    private data class OffSearchResponse(
        val products: List<OffProduct> = emptyList(),
    )

    @Serializable
    private data class OffProduct(
        val code: String? = null,
        @SerialName("product_name") val productName: String? = null,
        val brands: String? = null,
        val nutriments: OffNutriments? = null,
    )

    @Serializable
    private data class OffNutriments(
        @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
        @SerialName("proteins_100g") val protein100g: Double? = null,
        @SerialName("carbohydrates_100g") val carbs100g: Double? = null,
        @SerialName("fat_100g") val fat100g: Double? = null,
        @SerialName("fiber_100g") val fibre100g: Double? = null,
    )

    companion object {
        private const val BASE_URL = "https://world.openfoodfacts.org"
    }
}
