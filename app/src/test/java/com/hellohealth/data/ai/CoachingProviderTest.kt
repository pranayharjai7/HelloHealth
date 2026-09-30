package com.hellohealth.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the [CoachingProvider] fallback ORDER: Gemini first, OpenRouter only on Gemini failure, and a
 * terminal [ChatResult.Failure] when both fail (the signal for the repository to use its rule-based
 * coach). Uses fake datasources that override `generate` so the ordering logic is tested in isolation
 * from the transport (which its own datasource tests cover).
 *
 * Robolectric so the provider's `AppLogger` failover warnings resolve `android.util.Log`.
 */
@RunWith(RobolectricTestRunner::class)
class CoachingProviderTest {

    private class FakeGemini(private val result: ChatResult, val onCall: () -> Unit = {}) :
        GeminiDataSource(CoachingProviderTest.stubClientStatic()) {
        override suspend fun generate(request: ChatRequest): ChatResult { onCall(); return result }
    }

    private class FakeOpenRouter(private val result: ChatResult, val onCall: () -> Unit = {}) :
        OpenRouterDataSource(CoachingProviderTest.stubClientStatic()) {
        override suspend fun generate(request: ChatRequest): ChatResult { onCall(); return result }
    }

    private val request = ChatRequest(system = null, messages = listOf(ChatMessage(ChatMessage.Role.USER, "hi")))

    @Test
    fun `returns Gemini success without touching OpenRouter`() = runTest {
        var openRouterCalled = false
        val provider = CoachingProvider(
            FakeGemini(ChatResult.Success("from gemini")),
            FakeOpenRouter(ChatResult.Success("from openrouter")) { openRouterCalled = true },
        )
        val result = provider.generate(request)
        assertEquals("from gemini", (result as ChatResult.Success).text)
        assertEquals(false, openRouterCalled)
    }

    @Test
    fun `falls over to OpenRouter when Gemini fails`() = runTest {
        val provider = CoachingProvider(
            FakeGemini(ChatResult.Failure("gemini down")),
            FakeOpenRouter(ChatResult.Success("from openrouter")),
        )
        val result = provider.generate(request)
        assertEquals("from openrouter", (result as ChatResult.Success).text)
    }

    @Test
    fun `returns Failure when both providers fail`() = runTest {
        val provider = CoachingProvider(
            FakeGemini(ChatResult.Failure("gemini down")),
            FakeOpenRouter(ChatResult.Failure("openrouter down")),
        )
        assertTrue(provider.generate(request) is ChatResult.Failure)
    }

    companion object {
        /** A throwaway client so the fake datasources can call their super constructor. */
        fun stubClientStatic() = HttpClient(MockEngine { respond("") })
    }
}
