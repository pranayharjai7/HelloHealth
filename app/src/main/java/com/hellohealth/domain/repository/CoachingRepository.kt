package com.hellohealth.domain.repository

import com.hellohealth.domain.model.coaching.CoachingContext
import com.hellohealth.domain.model.coaching.CoachingInsight

/**
 * Reads across all four dimensions (activity, nutrition, vitals, mood) to produce AI coaching. Owns
 * the read-time context assembly and the provider fallback chain (LLM -> rule-based), so the UI just
 * asks for an insight or a chat answer.
 *
 * Consent-gated: when the user has not opted in ([ProfileRepository.observeAiCoachingEnabled] false,
 * the default), no data leaves the device — [dailyInsight] and [ask] still return, but from the
 * on-device rule-based coach only. Never throws; degrades to the rule-based coach on any LLM failure.
 */
interface CoachingRepository {

    /** Assemble today's [CoachingContext] from the four dimension repos (never throws; absent = null). */
    suspend fun buildTodayContext(): CoachingContext

    /**
     * A short daily insight. Uses the LLM (Gemini -> OpenRouter) when the user has opted in AND a
     * provider is reachable; otherwise the deterministic on-device rule-based coach. The returned
     * [CoachingInsight.source] records which path produced it.
     */
    suspend fun dailyInsight(): CoachingInsight

    /**
     * Answer a free-form question grounded in today's context. When coaching is disabled or both LLM
     * providers fail, returns a rule-based insight instead (so the chat never dead-ends). Never throws.
     */
    suspend fun ask(question: String): CoachingInsight

    /** Whether the user has opted in to AI coaching (LLM calls). False => rule-based only. */
    suspend fun isEnabled(): Boolean
}
