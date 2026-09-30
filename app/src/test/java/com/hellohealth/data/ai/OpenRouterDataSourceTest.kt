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
 * MockEngine-backed tests for [OpenRouterDataSource] (the OpenAI-compatible fallback provider). Same
 * no-crash contract as [GeminiDataSourceTest]: happy path → [ChatResult.Success]; 429/non-2xx/network/
 * empty-choices/malformed → [ChatResult.Failure]; blank key short-circuits with no network call.
 *
 * Robolectric so `AppLogger`'s failure-path `android.util.Log` calls resolve.
 */
@RunWith(RobolectricTestRunner::class)
class OpenRouterDataSourceTest {

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

    private class TestOpenRouter(client: HttpClient, private val key: String = "test-key") : OpenRouterDataSource(client) {
        override val apiKey: String get() = key
    }

    private val request = ChatRequest(
        system = "You are a coach.",
        messages = listOf(ChatMessage(ChatMessage.Role.USER, "How am I doing?")),
    )

    @Test
    fun `maps choices message content to Success text`() = runTest {
        val body = """
            {
              "choices": [
                { "index": 0, "message": { "role": "assistant", "content": "Solid week — hydration is up." }, "finish_reason": "stop" }
              ]
            }
        """.trimIndent()
        val ds = TestOpenRouter(client(MockEngine { respond(body, HttpStatusCode.OK, jsonHeaders) }))
        val result = ds.generate(request)
        assertTrue(result is ChatResult.Success)
        assertEquals("Solid week — hydration is up.", (result as ChatResult.Success).text)
    }

    @Test
    fun `blank key short-circuits to Failure with no network call`() = runTest {
        var called = false
        val ds = TestOpenRouter(client(MockEngine { called = true; respond("", HttpStatusCode.OK) }), key = "")
        assertTrue(ds.generate(request) is ChatResult.Failure)
        assertEquals(false, called)
    }

    @Test
    fun `429 quota yields Failure`() = runTest {
        val ds = TestOpenRouter(client(MockEngine { respondError(HttpStatusCode.TooManyRequests) }))
        assertTrue(ds.generate(request) is ChatResult.Failure)
    }

    @Test
    fun `network error yields Failure`() = runTest {
        val ds = TestOpenRouter(client(MockEngine { throw java.io.IOException("no network") }))
        assertTrue(ds.generate(request) is ChatResult.Failure)
    }

    @Test
    fun `empty choices array yields Failure`() = runTest {
        val ds = TestOpenRouter(client(MockEngine { respond("""{ "choices": [] }""", HttpStatusCode.OK, jsonHeaders) }))
        assertTrue(ds.generate(request) is ChatResult.Failure)
    }

    @Test
    fun `malformed body yields Failure`() = runTest {
        val ds = TestOpenRouter(client(MockEngine { respond("<html>oops</html>", HttpStatusCode.OK, jsonHeaders) }))
        assertTrue(ds.generate(request) is ChatResult.Failure)
    }
}
