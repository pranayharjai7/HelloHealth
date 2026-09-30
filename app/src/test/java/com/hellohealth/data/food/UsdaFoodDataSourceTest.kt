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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * MockEngine-backed tests for [UsdaFoodDataSource]. The no-crash guarantee is the point here: a 429
 * rate-limit, a network/timeout failure, and a malformed body must all degrade to an EMPTY list —
 * never an exception — because the repository layers these results on top of the local cache.
 *
 * Robolectric so `AppLogger`'s `android.util.Log` warning calls on the failure paths resolve.
 */
@RunWith(RobolectricTestRunner::class)
class UsdaFoodDataSourceTest {

    /** A client with the same JSON content-negotiation the production `@Named("food")` client uses. */
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
    fun `search maps energy plus the four macros to a per-100g entry`() = runTest {
        val body = """
            {
              "foods": [
                {
                  "fdcId": 12345,
                  "description": "Oats, raw",
                  "brandOwner": "ACME",
                  "gtinUpc": "0001112223334",
                  "foodNutrients": [
                    { "nutrientNumber": "1008", "value": 389.0 },
                    { "nutrientNumber": "1003", "value": 16.9 },
                    { "nutrientNumber": "1005", "value": 66.3 },
                    { "nutrientNumber": "1004", "value": 6.9 },
                    { "nutrientNumber": "1079", "value": 10.6 }
                  ]
                }
              ]
            }
        """.trimIndent()
        val ds = UsdaFoodDataSource(client(MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }))

        val results = ds.search("oats")

        assertEquals(1, results.size)
        val food = results.single()
        assertEquals("usda:12345", food.id)
        assertEquals("Oats, raw", food.name)
        assertEquals("ACME", food.brand)
        assertEquals("usda", food.source)
        assertEquals("per_100g", food.basisUnit)
        assertEquals(389.0, food.caloriesPer, 0.0001)
        assertEquals(16.9, food.proteinGPer!!, 0.0001)
        assertEquals(66.3, food.carbsGPer!!, 0.0001)
        assertEquals(6.9, food.fatGPer!!, 0.0001)
        assertEquals(10.6, food.fibreGPer!!, 0.0001)
        assertEquals("0001112223334", food.barcode)
    }

    @Test
    fun `a food with no energy nutrient is skipped rather than mapped to zero calories`() = runTest {
        val body = """
            {
              "foods": [
                { "fdcId": 1, "description": "No energy", "foodNutrients": [ { "nutrientNumber": "1003", "value": 5.0 } ] },
                { "fdcId": 2, "description": "Has energy", "foodNutrients": [ { "nutrientNumber": "1008", "value": 100.0 } ] }
              ]
            }
        """.trimIndent()
        val ds = UsdaFoodDataSource(client(MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }))

        val results = ds.search("thing")

        assertEquals(1, results.size)
        assertEquals("usda:2", results.single().id)
    }

    @Test
    fun `blank query short-circuits and never touches the network`() = runTest {
        var called = false
        val ds = UsdaFoodDataSource(client(MockEngine { called = true; respond("{}", HttpStatusCode.OK, jsonHeaders) }))

        assertTrue(ds.search("   ").isEmpty())
        assertTrue("blank query must not hit the engine", !called)
    }

    @Test
    fun `a 429 rate-limit degrades to an empty list`() = runTest {
        val ds = UsdaFoodDataSource(client(MockEngine { respondError(HttpStatusCode.TooManyRequests) }))
        assertTrue(ds.search("oats").isEmpty())
    }

    @Test
    fun `a network failure degrades to an empty list`() = runTest {
        val ds = UsdaFoodDataSource(client(MockEngine { throw java.io.IOException("connection reset") }))
        assertTrue(ds.search("oats").isEmpty())
    }

    @Test
    fun `a malformed body degrades to an empty list`() = runTest {
        val ds = UsdaFoodDataSource(client(MockEngine { respond("not json at all", HttpStatusCode.OK, jsonHeaders) }))
        assertTrue(ds.search("oats").isEmpty())
    }

    @Test
    fun `an empty foods array yields no results`() = runTest {
        val ds = UsdaFoodDataSource(client(MockEngine { respond("""{ "foods": [] }""", HttpStatusCode.OK, jsonHeaders) }))
        assertEquals(emptyList<Any>(), ds.search("nothing"))
    }
}
