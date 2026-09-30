package com.hellohealth.domain.coaching

import com.hellohealth.domain.model.coaching.CoachingContext
import kotlin.math.abs

/**
 * Pure prompt/coaching text builders — the genuinely testable heart of AI Coaching, kept free of any
 * Android/network/Room dependency (mirrors how [com.hellohealth.domain.health.BodyEnergy] isolates
 * the nutrition math). [systemPrompt] and [contextBlock] assemble what the LLM sees; [ruleBasedCoach]
 * is the offline fallback that templates an insight from the same [CoachingContext].
 *
 * The rule-based coach is intentionally simple and deterministic: pick the single most salient signal
 * (energy balance, then recovery, then hydration, then mood, then steps) and render one encouraging,
 * NON-medical sentence. It never fabricates data — a fully-empty context yields a gentle "start
 * logging" nudge.
 */
object CoachingPrompt {

    /**
     * The system instruction. Constrains the model to short, supportive, NON-medical coaching over
     * ONLY the provided numbers — no diagnosis, no fabricated data, no numbers not in the context.
     */
    fun systemPrompt(): String = """
        You are a friendly, concise health coach inside the HelloHealth app. You are given the user's
        own logged numbers for today across activity, nutrition, recovery (vitals) and mood. Offer a
        short, encouraging insight (2-3 sentences max) and at most one gentle, general lifestyle
        suggestion. Rules:
        - Base everything ONLY on the numbers provided. Never invent data or cite numbers not given.
        - You are NOT a doctor. Give no medical diagnosis or treatment advice. For anything concerning,
          suggest they consult a healthcare professional.
        - Be warm and specific, not generic. If little data is present, gently encourage logging.
    """.trimIndent()

    /** A compact, labeled rendering of the context for the LLM. Absent fields are omitted entirely. */
    fun contextBlock(ctx: CoachingContext): String {
        val lines = buildList {
            ctx.caloriesConsumed?.let { add("Calories consumed: $it kcal") }
            ctx.calorieBudget?.let { add("Calorie budget: $it kcal") }
            ctx.caloriesOut?.let { add("Calories burned: $it kcal") }
            ctx.netKcal?.let {
                add("Energy balance: ${abs(it)} kcal ${if (it >= 0) "deficit" else "surplus"}")
            }
            ctx.proteinG?.let { add("Protein: ${it}g") }
            ctx.carbsG?.let { add("Carbs: ${it}g") }
            ctx.fatG?.let { add("Fat: ${it}g") }
            ctx.waterMl?.let { add("Water: $it ml") }
            ctx.readinessScore?.let { add("Recovery readiness: $it/100${ctx.readinessStatus?.let { s -> " ($s)" } ?: ""}") }
            ctx.restingHeartRate?.let { add("Resting heart rate: $it bpm") }
            ctx.hrvRmssd?.let { add("HRV: $it ms") }
            ctx.sleepHours?.let { add("Sleep: ${"%.1f".format(it)} h") }
            ctx.steps?.let { add("Steps: $it") }
            ctx.dominantMoodToday?.let { add("Mood today: ${it.lowercase()}${ctx.moodCountToday?.let { n -> " ($n logged)" } ?: ""}") }
        }
        return if (lines.isEmpty()) "No data logged yet today." else lines.joinToString("\n")
    }

    /** The full user-turn prompt for the daily insight: context + a short instruction. */
    fun dailyInsightPrompt(ctx: CoachingContext): String =
        "Here are my numbers for today:\n\n${contextBlock(ctx)}\n\n" +
            "Give me a short insight on how my day is going and one gentle suggestion."

    /**
     * Deterministic offline coach. Picks the most salient signal and renders one encouraging,
     * non-medical sentence. Order of salience: empty → energy balance → recovery → hydration → mood
     * → steps → a generic positive.
     */
    fun ruleBasedCoach(ctx: CoachingContext): String {
        if (!ctx.hasAnyData) {
            return "Log a meal, some water, or a quick mood check-in and I'll start spotting patterns across your day."
        }
        // Energy balance (flagship).
        if (ctx.netKcal != null && ctx.calorieBudget != null) {
            val net = ctx.netKcal
            return when {
                net >= 300 -> "You're in a ${abs(net)} kcal deficit today — nice work staying under budget. Keep protein up to hold onto muscle."
                net <= -300 -> "You're ${abs(net)} kcal over your budget so far. A short walk or a lighter dinner would help even things out."
                else -> "Your energy balance is close to your budget today — a steady, sustainable place to be."
            }
        }
        // Recovery.
        if (ctx.readinessScore != null) {
            return when {
                ctx.readinessScore >= 80 -> "Recovery is strong today (${ctx.readinessScore}/100) — a good day to push a bit harder if you're training."
                ctx.readinessScore < 50 -> "Recovery is on the lower side (${ctx.readinessScore}/100). Consider an easier day and prioritising sleep tonight."
                else -> "Recovery is moderate (${ctx.readinessScore}/100) — listen to your body and keep intensity sensible."
            }
        }
        // Hydration.
        if (ctx.waterMl != null) {
            return if (ctx.waterMl < 1000) {
                "You've logged ${ctx.waterMl} ml of water — try to top up through the day to stay well hydrated."
            } else {
                "Hydration is looking good at ${ctx.waterMl} ml today. Keep it up."
            }
        }
        // Mood.
        if (ctx.dominantMoodToday != null) {
            return "You've been feeling mostly ${ctx.dominantMoodToday.lowercase()} today. Logging how you feel alongside your habits helps you spot what lifts your mood."
        }
        // Steps.
        if (ctx.steps != null) {
            return if (ctx.steps >= 8000) {
                "Great movement today — ${ctx.steps} steps already. Consistency like this adds up."
            } else {
                "You're at ${ctx.steps} steps. A short walk would be an easy way to build on that."
            }
        }
        return "You're logging your health data consistently — that's the habit that drives real change. Keep it going."
    }
}
