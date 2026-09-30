package com.hellohealth.data.repository

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.ai.ChatMessage
import com.hellohealth.data.ai.ChatRequest
import com.hellohealth.data.ai.ChatResult
import com.hellohealth.data.ai.CoachingProvider
import com.hellohealth.domain.coaching.CoachingPrompt
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.model.coaching.CoachingContext
import com.hellohealth.domain.model.coaching.CoachingInsight
import com.hellohealth.domain.repository.ActivityRepository
import com.hellohealth.domain.repository.CoachingRepository
import com.hellohealth.domain.repository.NutritionRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.repository.VitalsRepository
import com.hellohealth.domain.model.ActivityGoals
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Assembles today's cross-dimension [CoachingContext] and drives the coaching fallback chain:
 * LLM ([CoachingProvider]: Gemini -> OpenRouter) when opted in and reachable, else the deterministic
 * on-device [CoachingPrompt.ruleBasedCoach]. Room-first reads via each dimension repo's own no-user-
 * safe Flows/suspends, so this stays offline-first and never throws.
 *
 * The energy-balance net is computed here at read time (caloriesOut - caloriesConsumed), exactly like
 * the dashboard's NutritionViewModel — no denormalized value is stored.
 */
@Singleton
class CoachingRepositoryImpl @Inject constructor(
    private val nutritionRepository: NutritionRepository,
    private val activityRepository: ActivityRepository,
    private val vitalsRepository: VitalsRepository,
    private val emotionsRepository: com.hellohealth.domain.repository.EmotionsRepository,
    private val profileRepository: ProfileRepository,
    private val coachingProvider: CoachingProvider,
) : CoachingRepository {

    private fun today(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    override suspend fun isEnabled(): Boolean =
        runCatching { profileRepository.observeAiCoachingEnabled().first() }.getOrDefault(false)

    override suspend fun buildTodayContext(): CoachingContext = runCatching {
        val todayIso = today()

        // Nutrition (calories in + macros + water).
        val summary = runCatching { nutritionRepository.observeDaySummary(todayIso).first() }.getOrNull()
        val consumed = summary?.caloriesConsumed?.takeIf { it > 0 }?.roundToInt()
        val protein = summary?.proteinG?.takeIf { it > 0 }?.roundToInt()
        val carbs = summary?.carbsG?.takeIf { it > 0 }?.roundToInt()
        val fat = summary?.fatG?.takeIf { it > 0 }?.roundToInt()
        val water = summary?.waterMl?.takeIf { it > 0 }?.roundToInt()

        // Budget from profile (reuse the same pure math as the nutrition card).
        val profile = runCatching { profileRepository.getProfile() }.getOrNull()
        val budget = profile?.let { runCatching { BodyEnergy.calorieBudget(it) }.getOrNull() }

        // Activity (steps + calories out + sleep) — one suspend read of the day's summary.
        val health = runCatching {
            activityRepository.fetchSummary(ActivityGoals(), LocalDate.now(ZoneId.systemDefault()))
        }.getOrNull()
        val caloriesOut = health?.let {
            (it.activeCalories + it.basalMetabolicRate).takeIf { v -> v > 0 }?.roundToInt()
        }
        val steps = health?.steps?.takeIf { it > 0 }?.toInt()
        val sleepHours = health?.sleepDurationMinutes?.takeIf { it > 0 }?.let { it / 60.0 }

        val net = if (caloriesOut != null && consumed != null) caloriesOut - consumed else null

        // Vitals (readiness + RHR + HRV).
        val readiness = runCatching { vitalsRepository.observeReadiness().first() }.getOrNull()
        val latestVitals = runCatching { vitalsRepository.observeLatestVitals().first() }.getOrNull()

        // Mood (dominant today + count).
        val moods = runCatching { emotionsRepository.observeToday().first() }.getOrDefault(emptyList())
        val dominant = moods.dominantOrNull()

        CoachingContext(
            caloriesConsumed = consumed,
            calorieBudget = budget,
            caloriesOut = caloriesOut,
            netKcal = net,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            waterMl = water,
            readinessScore = readiness?.score?.takeIf { it > 0 },
            readinessStatus = readiness?.status?.name,
            restingHeartRate = latestVitals?.restingHeartRate?.roundToInt(),
            hrvRmssd = latestVitals?.hrvRmssd?.roundToInt(),
            sleepHours = sleepHours,
            steps = steps,
            dominantMoodToday = dominant?.name,
            moodCountToday = moods.size.takeIf { it > 0 },
        )
    }.getOrElse { e ->
        AppLogger.w(FeatureTag.COACHING, "buildTodayContext failed: ${e.message}; using empty context")
        CoachingContext.EMPTY
    }

    override suspend fun dailyInsight(): CoachingInsight {
        val ctx = buildTodayContext()
        if (isEnabled()) {
            val request = ChatRequest(
                system = CoachingPrompt.systemPrompt(),
                messages = listOf(ChatMessage(ChatMessage.Role.USER, CoachingPrompt.dailyInsightPrompt(ctx))),
            )
            when (val result = coachingProvider.generate(request)) {
                is ChatResult.Success -> return CoachingInsight(result.text, CoachingInsight.Source.LLM)
                is ChatResult.Failure ->
                    AppLogger.w(FeatureTag.COACHING, "dailyInsight LLM failed (${result.reason}); using rule-based")
            }
        }
        return CoachingInsight(CoachingPrompt.ruleBasedCoach(ctx), CoachingInsight.Source.RULE_BASED)
    }

    override suspend fun ask(question: String): CoachingInsight {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) {
            return CoachingInsight("Ask me anything about your day — your calories, recovery, hydration or mood.", CoachingInsight.Source.RULE_BASED)
        }
        val ctx = buildTodayContext()
        if (isEnabled()) {
            val request = ChatRequest(
                system = CoachingPrompt.systemPrompt(),
                messages = listOf(
                    ChatMessage(
                        ChatMessage.Role.USER,
                        "Here are my numbers for today:\n\n${CoachingPrompt.contextBlock(ctx)}\n\nMy question: $trimmed",
                    )
                ),
            )
            when (val result = coachingProvider.generate(request)) {
                is ChatResult.Success -> return CoachingInsight(result.text, CoachingInsight.Source.LLM)
                is ChatResult.Failure ->
                    AppLogger.w(FeatureTag.COACHING, "ask LLM failed (${result.reason}); using rule-based")
            }
        }
        // Rule-based fallback can't truly "answer" a free question, so return today's best insight.
        return CoachingInsight(CoachingPrompt.ruleBasedCoach(ctx), CoachingInsight.Source.RULE_BASED)
    }

    /** Most-frequent mood today, ties broken toward the most recent (records arrive newest-first). */
    private fun List<com.hellohealth.domain.model.EmotionRecord>.dominantOrNull(): EmotionType? {
        if (isEmpty()) return null
        val counts = LinkedHashMap<EmotionType, Int>()
        for (r in this) counts[r.emotion] = (counts[r.emotion] ?: 0) + 1
        return counts.maxByOrNull { it.value }?.key
    }
}
