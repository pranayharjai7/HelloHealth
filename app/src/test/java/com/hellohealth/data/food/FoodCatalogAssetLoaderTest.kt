package com.hellohealth.data.food

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Parses the REAL bundled `assets/common_foods.json` through Robolectric's asset manager — this is
 * the guard that the shipped catalog decodes cleanly into [com.hellohealth.data.local.entities.CachedFoodEntity]
 * rows (a malformed entry, a missing required field, or an unknown basis would surface here rather
 * than as a silent empty catalog on-device).
 */
@RunWith(RobolectricTestRunner::class)
class FoodCatalogAssetLoaderTest {

    private val loader = FoodCatalogAssetLoader(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `bundled common_foods asset parses into cache rows`() {
        val result = loader.load(nowEpochMs = 42L)
        assertTrue("bundled asset must parse", result.isSuccess)

        val foods = result.getOrThrow()
        assertFalse("catalog must be non-empty", foods.isEmpty())

        // Every row carries the seed id prefix, the caller's clock, and a valid canonical basis.
        assertTrue(foods.all { it.id.startsWith("seed:") })
        assertTrue(foods.all { it.lastRefreshedEpochMs == 42L })
        assertTrue(foods.all { it.source == "seed" })
        assertTrue(foods.all { it.basisUnit == "per_100g" || it.basisUnit == "per_serving" })
        assertTrue(foods.all { it.name.isNotBlank() })

        // Spot-check a known per-100g staple and a barcoded per-serving item.
        val oats = foods.first { it.id == "seed:oats-dry" }
        assertNotNull(oats.proteinGPer)
        val cola = foods.first { it.id == "seed:coca-cola-can" }
        assertNotNull("barcoded item must keep its barcode", cola.barcode)
    }
}
