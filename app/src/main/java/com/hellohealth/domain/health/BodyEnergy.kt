package com.hellohealth.domain.health

import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.model.ActivityLevel
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.GoalType
import com.hellohealth.domain.model.UserProfile
import kotlin.math.roundToInt

/**
 * Pure energy math (Mifflin-St Jeor BMR → TDEE → calorie budget) plus goal seeding.
 *
 * This is the single source of truth for the calorie budget so onboarding's Confirm step and the
 * later Profile/Goals editors always derive the same number. All functions are pure and null-safe:
 * when a required input is missing the calculation returns null rather than throwing, and callers
 * fall back to app defaults.
 */
object BodyEnergy {

    /** ~7700 kcal per kg of body mass — converts a weekly weight-change target to a daily delta. */
    private const val KCAL_PER_KG = 7700.0

    // Sex constant in Mifflin-St Jeor. When sex is unset/other/prefer-not-to-say we use the
    // midpoint of the male (+5) and female (-161) constants as a documented neutral estimate.
    private const val SEX_CONSTANT_MALE = 5.0
    private const val SEX_CONSTANT_FEMALE = -161.0
    private const val SEX_CONSTANT_NEUTRAL = (SEX_CONSTANT_MALE + SEX_CONSTANT_FEMALE) / 2.0

    private fun sexConstant(gender: Gender?): Double = when (gender) {
        Gender.MALE -> SEX_CONSTANT_MALE
        Gender.FEMALE -> SEX_CONSTANT_FEMALE
        else -> SEX_CONSTANT_NEUTRAL // OTHER, PREFER_NOT_TO_SAY, or null
    }

    /**
     * Basal Metabolic Rate (kcal/day) via Mifflin-St Jeor.
     * Returns null when height, weight, or age is unavailable.
     */
    fun bmr(weightKg: Double?, heightCm: Double?, ageYears: Int?, gender: Gender?): Double? {
        if (weightKg == null || heightCm == null || ageYears == null) return null
        return 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + sexConstant(gender)
    }

    /**
     * Total Daily Energy Expenditure (kcal/day) = BMR × activity multiplier.
     * Falls back to [ActivityLevel.SEDENTARY] when activity level is unset; null if BMR is null.
     */
    fun tdee(
        weightKg: Double?,
        heightCm: Double?,
        ageYears: Int?,
        gender: Gender?,
        activityLevel: ActivityLevel?
    ): Double? {
        val basal = bmr(weightKg, heightCm, ageYears, gender) ?: return null
        val multiplier = (activityLevel ?: ActivityLevel.SEDENTARY).multiplier
        return basal * multiplier
    }

    /**
     * Daily calorie budget (kcal/day) = TDEE adjusted by the goal.
     * A [GoalType.LOSE] applies a deficit, [GoalType.GAIN] a surplus, sized from
     * [UserProfile.targetRateKgPerWeek] (defaulting to 0.5 kg/week when a directional goal has no
     * explicit rate). [GoalType.MAINTAIN]/null leaves TDEE unchanged. Never returns below a
     * safety floor of 1200 kcal. Null when TDEE can't be computed.
     */
    fun calorieBudget(profile: UserProfile, today: java.time.LocalDate = java.time.LocalDate.now()): Int? {
        val maintenance = tdee(
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            ageYears = profile.ageYears(today),
            gender = profile.gender,
            activityLevel = profile.activityLevel
        ) ?: return null

        val ratePerWeek = when (profile.goalType) {
            GoalType.LOSE -> -(profile.targetRateKgPerWeek ?: 0.5)
            GoalType.GAIN -> (profile.targetRateKgPerWeek ?: 0.5)
            GoalType.MAINTAIN, null -> 0.0
        }
        val dailyDelta = ratePerWeek * KCAL_PER_KG / 7.0
        return (maintenance + dailyDelta).roundToInt().coerceAtLeast(1200)
    }

    /**
     * Suggested activity-ring goals seeded from the profile. Steps/active-minutes scale mildly with
     * activity level; active-calorie target derives from the goal (a deficit goal nudges the burn
     * target up). Falls back to [ActivityGoals] defaults when inputs are missing.
     */
    fun suggestedGoals(profile: UserProfile): ActivityGoals {
        val level = profile.activityLevel ?: ActivityLevel.SEDENTARY
        val steps = when (level) {
            ActivityLevel.SEDENTARY -> 8000
            ActivityLevel.LIGHT -> 9000
            ActivityLevel.MODERATE -> 10000
            ActivityLevel.ACTIVE -> 12000
            ActivityLevel.VERY_ACTIVE -> 14000
        }
        val activeMinutes = when (level) {
            ActivityLevel.SEDENTARY -> 30
            ActivityLevel.LIGHT -> 45
            ActivityLevel.MODERATE -> 60
            ActivityLevel.ACTIVE -> 75
            ActivityLevel.VERY_ACTIVE -> 90
        }
        val activeCalories = when (profile.goalType) {
            GoalType.LOSE -> 600
            GoalType.GAIN -> 400
            GoalType.MAINTAIN, null -> 500
        }
        return ActivityGoals(steps = steps, activeCalories = activeCalories, activeMinutes = activeMinutes)
    }
}
