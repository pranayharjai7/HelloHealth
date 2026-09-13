package com.hellohealth.domain.model

import java.time.LocalDate
import java.time.Period

/**
 * The user's profile and health vitals. Everything past [displayName] is captured during
 * onboarding (P0.5) and editable later from Profile/Goals/FoodPrefs. All vitals are nullable so
 * a pre-onboarding row (or a partially-completed one) is valid; storage is always metric.
 *
 * [hasOnboarded] gates the first-run wizard: a null profile OR `hasOnboarded == false` means the
 * user still needs onboarding.
 */
data class UserProfile(
    val displayName: String? = null,
    val gender: Gender? = null,
    val birthDateEpochDay: Long? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activityLevel: ActivityLevel? = null,
    val goalType: GoalType? = null,
    val targetWeightKg: Double? = null,
    val targetRateKgPerWeek: Double? = null,
    val unitPreference: UnitPreference = UnitPreference.METRIC,
    val hasOnboarded: Boolean = false
) {
    /**
     * Whole years between [birthDateEpochDay] and [today], or null if birth date is unset.
     * Returns null (rather than a negative age) for a future birth date.
     */
    fun ageYears(today: LocalDate = LocalDate.now()): Int? {
        val birthDate = birthDateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: return null
        if (birthDate.isAfter(today)) return null
        return Period.between(birthDate, today).years
    }
}
