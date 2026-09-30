package com.hellohealth.data.ai

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the `ai-coach` Supabase Edge Function — the server-side LLM proxy that holds the Gemini and
 * OpenRouter keys. Replaces the old on-device GeminiDataSource/OpenRouterDataSource/CoachingProvider:
 * the provider secrets no longer live in the APK. The Supabase SDK attaches the signed-in user's JWT
 * automatically (the function's verify_jwt gate), so only an authenticated user can invoke it.
 *
 * Same non-throwing contract as before: any failure (no session, non-2xx incl. the function's 502
 * "all providers failed", network, malformed) maps to [ChatResult.Failure] so the repository falls
 * back to its on-device rule-based coach — coaching still works offline / when the proxy is down.
 */
@Singleton
open class CoachingProxyDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    open suspend fun generate(request: ChatRequest): ChatResult {
        return runCatching {
            val payload = buildJsonObject {
                request.system?.let { put("system", it) }
                put("messages", buildJsonArray {
                    request.messages.forEach { msg ->
                        add(
                            buildJsonObject {
                                put("role", if (msg.role == ChatMessage.Role.MODEL) "model" else "user")
                                put("text", msg.text)
                            }
                        )
                    }
                })
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxOutputTokens)
            }

            val response: HttpResponse = supabase.functions.invoke(
                function = "ai-coach",
                body = payload,
                headers = headers { append(HttpHeaders.ContentType, "application/json") },
            )
            if (!response.status.isSuccess()) {
                AppLogger.w(FeatureTag.COACHING, "ai-coach HTTP ${response.status.value}; using rule-based")
                return ChatResult.Failure("ai-coach HTTP ${response.status.value}")
            }
            val text = json.decodeFromString(CoachResponse.serializer(), response.body<String>()).text
            if (text.isNullOrBlank()) {
                ChatResult.Failure("ai-coach empty response")
            } else {
                ChatResult.Success(text.trim())
            }
        }.getOrElse {
            AppLogger.w(FeatureTag.COACHING, "ai-coach call failed: ${it.message}; using rule-based")
            ChatResult.Failure("ai-coach error: ${it.message}")
        }
    }

    @Serializable
    private data class CoachResponse(
        val text: String? = null,
        val provider: String? = null,
        @SerialName("error") val error: String? = null,
    )
}
