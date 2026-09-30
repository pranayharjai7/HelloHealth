package com.hellohealth.data.food

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * MockEngine-backed tests for [OpenFoodFactsDataSource]. Covers the barcode primary path
 * ([OpenFoodFactsDataSource.lookupBarcode]) and the name typeahead ([OpenFoodFactsDataSource.search]).
 * Per the no-crash guarantee, a product-not-found (`status==0`), a non-2xx, a timeout/network error
 * and a malformed body must all yield `null` / an empty list rather than throwing.
 *
 * Robolectric so `AppLogger`'s `android.util.Log` warning calls on the failure paths resolve.
 */
@RunWith(RobolectricTestRunner::class)
class OpenFoodFactsDataSourceTest {

    private fun client(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 2_000
            connectTimeoutMillis = 2_000
            socketTimeoutMillis = 2_000
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `lookupBarcode maps a found product to a per-100g entry keyed by barcode`() = runTest {
        val body = """
            {
              "status": 1,
              "product": {
                "code": "5449000000996",
                "product_name": "Coca-Cola",
                "brands": "Coca-Cola",
                "nutriments": {
                  "energy-kcal_100g": 42.0,
                  "proteins_100g": 0.0,
                  "carbohydrates_100g": 10.6,
                  "fat_100g": 0.0,
                  "fiber_100g": 0.0
                }
              }
            }
        """.trimIndent()
        val ds = OpenFoodFactsDataSource(client(MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }))

        val food = ds.lookupBarcode("5449000000996")

        assertEquals("off:5449000000996", food?.id)
        assertEquals("Coca-Cola", food?.name)
        assertEquals("Coca-Cola", food?.brand)
        assertEquals("off", food?.source)
        assertEquals("per_100g", food?.basisUnit)
        assertEquals(42.0, food!!.caloriesPer, 0.0001)
        assertEquals(10.6, food.carbsGPer!!, 0.0001)
        assertEquals("5449000000996", food.barcode)
    }

    @Test
    fun `lookupBarcode returns null when the product is not found`() = runTest {
        val ds = OpenFoodFactsDataSource(
            client(MockEngine { respond("""{ "status": 0 }""", HttpStatusCode.OK, jsonHeaders) })
        )
        assertNull(ds.lookupBarcode("0000000000000"))
    }

    @Test
    fun `lookupBarcode returns null when the product has no energy value`() = runTest {
        val body = """
            { "status": 1, "product": { "code": "1", "product_name": "Mystery", "nutriments": {} } }
        """.trimIndent()
        val ds = OpenFoodFactsDataSource(client(MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }))
        assertNull(ds.lookupBarcode("1"))
    }

    @Test
    fun `lookupBarcode is blank-safe and does not touch the network`() = runTest {
        var called = false
        val ds = OpenFoodFactsDataSource(
            client(MockEngine { called = true; respond("{}", HttpStatusCode.OK, jsonHeaders) })
        )
        assertNull(ds.lookupBarcode("   "))
        assertTrue("blank barcode must not hit the engine", !called)
    }

    @Test
    fun `lookupBarcode degrades to null on non-2xx, network error and malformed body`() = runTest {
        val notFound = OpenFoodFactsDataSource(client(MockEngine { respondError(HttpStatusCode.NotFound) }))
        assertNull(notFound.lookupBarcode("123"))

        val network = OpenFoodFactsDataSource(client(MockEngine { throw java.io.IOException("boom") }))
        assertNull(network.lookupBarcode("123"))

        val malformed = OpenFoodFactsDataSource(client(MockEngine { respond("<<garbage>>", HttpStatusCode.OK, jsonHeaders) }))
        assertNull(malformed.lookupBarcode("123"))
    }

    @Test
    fun `search maps products and skips those missing a code or energy`() = runTest {
        val body = """
            {
              "products": [
                { "code": "111", "product_name": "Yogurt", "nutriments": { "energy-kcal_100g": 59.0, "proteins_100g": 10.0 } },
                { "product_name": "No code", "nutriments": { "energy-kcal_100g": 100.0 } },
                { "code": "222", "product_name": "No energy", "nutriments": {} }
              ]
            }
        """.trimIndent()
        val ds = OpenFoodFactsDataSource(client(MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }))

        val results = ds.search("yogurt")

        assertEquals(1, results.size)
        val food = results.single()
        assertEquals("off:111", food.id)
        assertEquals("Yogurt", food.name)
        assertEquals(59.0, food.caloriesPer, 0.0001)
        assertEquals(10.0, food.proteinGPer!!, 0.0001)
    }

    @Test
    fun `search is blank-safe and degrades to empty on failure`() = runTest {
        val blank = OpenFoodFactsDataSource(client(MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) }))
        assertTrue(blank.search("  ").isEmpty())

        val rateLimited = OpenFoodFactsDataSource(client(MockEngine { respondError(HttpStatusCode.TooManyRequests) }))
        assertTrue(rateLimited.search("cola").isEmpty())

        val malformed = OpenFoodFactsDataSource(client(MockEngine { respond("not json", HttpStatusCode.OK, jsonHeaders) }))
        assertTrue(malformed.search("cola").isEmpty())
    }
}
