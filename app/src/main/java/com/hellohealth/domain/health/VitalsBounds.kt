package com.hellohealth.domain.health

/**
 * Shared validation bounds for vitals entry. Hoisted out of `OnboardingViewModel` so the onboarding
 * wizard and the later Profile/Goals editors validate identically — a value the wizard accepts must
 * be one the editor accepts, and vice versa. Permissive enough for real human extremes, tight enough
 * to reject fat-finger entries (a 3 cm height, a 5 kg adult).
 *
 * All bounds are metric; imperial inputs convert before checking (storage is always metric).
 */
object VitalsBounds {
    const val MIN_HEIGHT_CM = 50.0
    const val MAX_HEIGHT_CM = 272.0
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 400.0

    // Age gate (years) and weekly weight-change bounds (kg/week). A rate outside this band would
    // push the calorie budget into unsafe territory, so directional goals block it.
    const val MIN_AGE = 13
    const val MAX_AGE = 120
    const val MIN_RATE_KG_PER_WEEK = 0.1
    const val MAX_RATE_KG_PER_WEEK = 1.0
}
