package com.hellohealth.data.ai

import com.hellohealth.BuildConfig
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * The DEFAULT AI Coaching provider — Google Gemini `generateContent`. Mirrors the nutrition
 * datasources' discipline: `@Singleton open class` + `@Named("ai")` client, `runCatching` → a typed
 * [ChatResult.Failure] on any error (never throws), non-2xx checked via [isSuccess]. A blank key
 * (unset in local.properties) short-circuits to [ChatResult.Failure] so the [CoachingProvider] falls
 * through to OpenRouter, then the rule-based coach.
 *
 * `open` so tests can override; the request/response DTOs match the v1beta REST contract
 * (contents/parts, systemInstruction, generationConfig; response candidates[0].content.parts[].text).
 * The model id is a constant so a bump is a one-line change.
 */
@Singleton
open class GeminiDataSource @Inject constructor(
    @Named("ai") private val client: HttpClient,
) {

    /** Overridable in tests so the transport paths are exercised with a known, non-blank key. */
    protected open val apiKey: String get() = BuildConfig.GEMINI_API_KEY

    open suspend fun generate(request: ChatRequest): ChatResult {
        val key = apiKey
        if (key.isBlank()) {
            return ChatResult.Failure("Gemini key not configured")
        }
        return runCatching {
            val body = GeminiRequest(
                contents = request.messages.map { msg ->
                    GeminiContent(
                        role = if (msg.role == ChatMessage.Role.MODEL) "model" else "user",
                        parts = listOf(GeminiPart(text = msg.text)),
                    )
                },
                systemInstruction = request.system?.let { GeminiContent(role = null, parts = listOf(GeminiPart(it))) },
                generationConfig = GeminiGenerationConfig(
                    temperature = request.temperature,
                    maxOutputTokens = request.maxOutputTokens,
                ),
            )
            val response = client.post("$BASE_URL/v1beta/models/$MODEL:generateContent") {
                parameter("key", key)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                AppLogger.w(FeatureTag.COACHING, "Gemini HTTP ${response.status.value}; failing over")
                return ChatResult.Failure("Gemini HTTP ${response.status.value}")
            }
            val text = response.body<GeminiResponse>()
                .candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.text
                ?.trim()
            if (text.isNullOrEmpty()) {
                ChatResult.Failure("Gemini empty response")
            } else {
                ChatResult.Success(text)
            }
        }.getOrElse {
            AppLogger.w(FeatureTag.COACHING, "Gemini call failed: ${it.message}; failing over")
            ChatResult.Failure("Gemini error: ${it.message}")
        }
    }

    @Serializable
    private data class GeminiRequest(
        val contents: List<GeminiContent>,
        val systemInstruction: GeminiContent? = null,
        val generationConfig: GeminiGenerationConfig? = null,
    )

    @Serializable
    private data class GeminiContent(
        val role: String? = null,
        val parts: List<GeminiPart>,
    )

    @Serializable
    private data class GeminiPart(val text: String)

    @Serializable
    private data class GeminiGenerationConfig(
        val temperature: Double,
        val maxOutputTokens: Int,
    )

    @Serializable
    private data class GeminiResponse(
        val candidates: List<GeminiCandidate> = emptyList(),
    )

    @Serializable
    private data class GeminiCandidate(
        val content: GeminiContent? = null,
        @SerialName("finishReason") val finishReason: String? = null,
    )

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com"
        /**
         * Fast, low-cost text model. Uses the `-latest` alias so a Gemini version bump doesn't 404
         * the app (verified on-device: pinned ids like `gemini-2.0-flash` 404 for this key's API
         * version, while `gemini-flash-latest` always resolves to the current flash tier).
         */
        private const val MODEL = "gemini-flash-latest"
    }
}
