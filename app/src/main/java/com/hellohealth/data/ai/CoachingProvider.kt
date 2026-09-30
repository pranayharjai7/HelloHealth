package com.hellohealth.data.ai

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orders the LLM providers into a fallback chain: try [GeminiDataSource] (default), and on any
 * [ChatResult.Failure] fall through to [OpenRouterDataSource]. Returns the first [ChatResult.Success],
 * or a terminal [ChatResult.Failure] when BOTH providers fail (key unset, quota, network, malformed).
 *
 * This never throws. It deliberately owns ONLY the network providers — the final rule-based local
 * fallback (a templated coach that works fully offline) lives in the repository, because it needs the
 * health-context data to template from, which the provider layer has no business knowing about. So
 * the contract here is: "give me the best available LLM answer, or tell me both failed."
 */
@Singleton
open class CoachingProvider @Inject constructor(
    private val gemini: GeminiDataSource,
    private val openRouter: OpenRouterDataSource,
) {

    open suspend fun generate(request: ChatRequest): ChatResult {
        val primary = gemini.generate(request)
        if (primary is ChatResult.Success) return primary

        AppLogger.w(FeatureTag.COACHING, "Gemini unavailable (${(primary as ChatResult.Failure).reason}); trying OpenRouter")
        val fallback = openRouter.generate(request)
        if (fallback is ChatResult.Success) return fallback

        AppLogger.w(
            FeatureTag.COACHING,
            "Both providers failed (${(fallback as ChatResult.Failure).reason}); caller should use rule-based coach"
        )
        return fallback
    }
}
