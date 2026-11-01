package com.hellohealth.data.ai

import androidx.test.core.app.ApplicationProvider
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoachingProxyDataSourceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `generate returns Success when proxy responds 200 with text`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"text":"Great job completing your workout today!","provider":"gemini"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }
        val client = createSupabaseClient("https://test.supabase.co", "anon") {
            httpEngine = engine
            install(Functions)
        }
        val ds = CoachingProxyDataSource(client)

        val result = ds.generate(
            ChatRequest(
                system = "You are a health coach.",
                messages = listOf(ChatMessage(ChatMessage.Role.USER, "How did I do today?"))
            )
        )

        assertTrue(result is ChatResult.Success)
        assertEquals("Great job completing your workout today!", (result as ChatResult.Success).text)
    }

    @Test
    fun `generate returns Failure on 429 rate limit without throwing`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"error":"rate limit or quota exceeded: rate_limit_exceeded"}""",
                status = HttpStatusCode.TooManyRequests,
                headers = jsonHeaders
            )
        }
        val client = createSupabaseClient("https://test.supabase.co", "anon") {
            httpEngine = engine
            install(Functions)
        }
        val ds = CoachingProxyDataSource(client)

        val result = ds.generate(
            ChatRequest(
                system = null,
                messages = listOf(ChatMessage(ChatMessage.Role.USER, "Check vitals"))
            )
        )

        assertTrue("429 rate limit must map to ChatResult.Failure", result is ChatResult.Failure)
        assertTrue((result as ChatResult.Failure).reason.isNotBlank())
    }

    @Test
    fun `generate returns Failure on 413 payload too large without throwing`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"error":"request body too large"}""",
                status = HttpStatusCode.PayloadTooLarge,
                headers = jsonHeaders
            )
        }
        val client = createSupabaseClient("https://test.supabase.co", "anon") {
            httpEngine = engine
            install(Functions)
        }
        val ds = CoachingProxyDataSource(client)

        val result = ds.generate(
            ChatRequest(
                system = null,
                messages = listOf(ChatMessage(ChatMessage.Role.USER, "Oversized request"))
            )
        )

        assertTrue("413 payload too large must map to ChatResult.Failure", result is ChatResult.Failure)
        assertTrue((result as ChatResult.Failure).reason.isNotBlank())
    }

    @Test
    fun `generate returns Failure on 400 bad request without throwing`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"error":"too many messages (max 10)"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders
            )
        }
        val client = createSupabaseClient("https://test.supabase.co", "anon") {
            httpEngine = engine
            install(Functions)
        }
        val ds = CoachingProxyDataSource(client)

        val result = ds.generate(
            ChatRequest(
                system = null,
                messages = listOf(ChatMessage(ChatMessage.Role.USER, "Bad request"))
            )
        )

        assertTrue("400 bad request must map to ChatResult.Failure", result is ChatResult.Failure)
        assertTrue((result as ChatResult.Failure).reason.isNotBlank())
    }

    @Test
    fun `generate returns Failure on 502 provider failure without throwing`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"error":"all providers failed"}""",
                status = HttpStatusCode.BadGateway,
                headers = jsonHeaders
            )
        }
        val client = createSupabaseClient("https://test.supabase.co", "anon") {
            httpEngine = engine
            install(Functions)
        }
        val ds = CoachingProxyDataSource(client)

        val result = ds.generate(
            ChatRequest(
                system = null,
                messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hello coach"))
            )
        )

        assertTrue("502 provider failure must map to ChatResult.Failure", result is ChatResult.Failure)
        assertTrue((result as ChatResult.Failure).reason.isNotBlank())
    }
}
