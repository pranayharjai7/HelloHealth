package com.hellohealth.data.ai

/**
 * Provider-neutral chat request/response model shared by [GeminiDataSource] and
 * [OpenRouterDataSource]. Each datasource adapts this to its own wire shape (Gemini's
 * contents/parts, OpenRouter's OpenAI-style messages) so the [CoachingProvider] and repository can
 * stay provider-agnostic — swapping or reordering providers touches only the datasources.
 */

/** A single turn in the conversation. [system] is carried separately in [ChatRequest]. */
data class ChatMessage(
    val role: Role,
    val text: String,
) {
    enum class Role { USER, MODEL }
}

/**
 * A generation request: an optional [system] instruction, the ordered conversation [messages], and
 * generation knobs. [maxOutputTokens] is generous on purpose: `gemini-flash-latest` is a *thinking*
 * model that spends output-token budget on internal reasoning before emitting any text, so a low cap
 * makes it return `finishReason=MAX_TOKENS` with EMPTY content (verified on-device). 2048 leaves room
 * for the reasoning plus the short coaching reply; the answer itself stays brief via the prompt.
 */
data class ChatRequest(
    val system: String?,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 2048,
)

/**
 * The outcome of one datasource call. A datasource NEVER throws — a network error, non-2xx, quota
 * (429), or an empty/malformed body all map to [Failure] with a short [reason] for logging, so the
 * [CoachingProvider] can fall through to the next provider (or the rule-based coach) deterministically.
 */
sealed interface ChatResult {
    data class Success(val text: String) : ChatResult
    data class Failure(val reason: String) : ChatResult
}
