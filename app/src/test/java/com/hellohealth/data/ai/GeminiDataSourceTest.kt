package com.hellohealth.data.ai

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
 * MockEngine-backed tests for [GeminiDataSource]. Per the no-crash guarantee, a happy path returns
 * [ChatResult.Success] with the parsed text; a 429/quota, a non-2xx, a network error, an empty
 * candidates array and a malformed body all yield [ChatResult.Failure] (never throw) so the
 * [CoachingProvider] can fail over. A blank key short-circuits before any network call.
 *
 * Robolectric so `AppLogger`'s `android.util.Log` warning calls on the failure paths resolve.
 */
@RunWith(RobolectricTestRunner::class)
class GeminiDataSourceTest {

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

    /** Subclass forcing a known non-blank key so the transport path always runs. */
    private class TestGemini(client: HttpClient, private val key: String = "test-key") : GeminiDataSource(client) {
        override val apiKey: String get() = key
    }

    private val request = ChatRequest(
        system = "You are a coach.",
        messages = listOf(ChatMessage(ChatMessage.Role.USER, "How am I doing?")),
    )

    @Test
    fun `maps a candidate part to Success text`() = runTest {
        val body = """
            {
              "candidates": [
                { "content": { "role": "model", "parts": [ { "text": "You're on track. Keep it up!" } ] }, "finishReason": "STOP" }
              ]
            }
        """.trimIndent()
        val ds = TestGemini(client(MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }))
        val result = ds.generate(request)
        assertTrue(result is ChatResult.Success)
        assertEquals("You're on track. Keep it up!", (result as ChatResult.Success).text)
    }

    @Test
    fun `blank key short-circuits to Failure with no network call`() = runTest {
        var called = false
        val ds = TestGemini(client(MockEngine { called = true; respond("", HttpStatusCode.OK) }), key = "")
        val result = ds.generate(request)
        assertTrue(result is ChatResult.Failure)
        assertEquals(false, called)
    }

    @Test
    fun `429 quota yields Failure`() = runTest {
        val ds = TestGemini(client(MockEngine { respondError(HttpStatusCode.TooManyRequests) }))
        assertTrue(ds.generate(request) is ChatResult.Failure)
    }

    @Test
    fun `network error yields Failure`() = runTest {
        val ds = TestGemini(client(MockEngine { throw java.io.IOException("no network") }))
        assertTrue(ds.generate(request) is ChatResult.Failure)
    }

    @Test
    fun `empty candidates array yields Failure`() = runTest {
        val ds = TestGemini(client(MockEngine { respond("""{ "candidates": [] }""", HttpStatusCode.OK, jsonHeaders) }))
        assertTrue(ds.generate(request) is ChatResult.Failure)
    }

    @Test
    fun `malformed body yields Failure`() = runTest {
        val ds = TestGemini(client(MockEngine { respond("not json at all", HttpStatusCode.OK, jsonHeaders) }))
        assertTrue(ds.generate(request) is ChatResult.Failure)
    }
}
