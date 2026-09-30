package com.hellohealth.data.ai

import com.hellohealth.BuildConfig
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The FALLBACK AI Coaching provider — OpenRouter's OpenAI-compatible chat completions, used when
 * [GeminiDataSource] fails or is quota-limited. Same non-throwing discipline: `runCatching` → a typed
 * [ChatResult.Failure] on any error, non-2xx checked via [isSuccess], a blank key short-circuits so
 * the [CoachingProvider] falls through to the rule-based coach.
 *
 * The provider-neutral [ChatRequest] is adapted to the OpenAI `messages` shape: the optional system
 * instruction becomes a leading `role="system"` message. Response text is `choices[0].message.content`.
 */
@Singleton
open class OpenRouterDataSource @Inject constructor(
    @Named("ai") private val client: HttpClient,
) {

    /** Overridable in tests so the transport paths are exercised with a known, non-blank key. */
    protected open val apiKey: String get() = BuildConfig.OPENROUTER_API_KEY

    open suspend fun generate(request: ChatRequest): ChatResult {
        val key = apiKey
        if (key.isBlank()) {
            return ChatResult.Failure("OpenRouter key not configured")
        }
        return runCatching {
            val messages = buildList {
                request.system?.let { add(OpenRouterMessage(role = "system", content = it)) }
                request.messages.forEach { msg ->
                    add(
                        OpenRouterMessage(
                            role = if (msg.role == ChatMessage.Role.MODEL) "assistant" else "user",
                            content = msg.text,
                        )
                    )
                }
            }
            val response = client.post("$BASE_URL/api/v1/chat/completions") {
                header("Authorization", "Bearer $key")
                // Optional attribution headers OpenRouter recommends; harmless if ignored.
                header("HTTP-Referer", "https://hellohealth.app")
                header("X-Title", "HelloHealth")
                contentType(ContentType.Application.Json)
                setBody(
                    OpenRouterRequest(
                        model = MODEL,
                        messages = messages,
                        temperature = request.temperature,
                        maxTokens = request.maxOutputTokens,
                    )
                )
            }
            if (!response.status.isSuccess()) {
                AppLogger.w(FeatureTag.COACHING, "OpenRouter HTTP ${response.status.value}; falling back to rule-based")
                return ChatResult.Failure("OpenRouter HTTP ${response.status.value}")
            }
            val text = response.body<OpenRouterResponse>()
                .choices.firstOrNull()
                ?.message?.content
                ?.trim()
            if (text.isNullOrEmpty()) {
                ChatResult.Failure("OpenRouter empty response")
            } else {
                ChatResult.Success(text)
            }
        }.getOrElse {
            AppLogger.w(FeatureTag.COACHING, "OpenRouter call failed: ${it.message}; falling back to rule-based")
            ChatResult.Failure("OpenRouter error: ${it.message}")
        }
    }

    @Serializable
    private data class OpenRouterRequest(
        val model: String,
        val messages: List<OpenRouterMessage>,
        val temperature: Double,
        @kotlinx.serialization.SerialName("max_tokens") val maxTokens: Int,
    )

    @Serializable
    private data class OpenRouterMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class OpenRouterResponse(
        val choices: List<OpenRouterChoice> = emptyList(),
    )

    @Serializable
    private data class OpenRouterChoice(
        val message: OpenRouterMessage? = null,
    )

    companion object {
        private const val BASE_URL = "https://openrouter.ai"
        /** A fast, low-cost fallback model. Bump here if a cheaper/newer id is preferred. */
        private const val MODEL = "google/gemini-2.0-flash-001"
    }
}
