package com.hellohealth.domain.model

/**
 * Weight goal direction. Drives the sign of the calorie delta applied to TDEE
 * (deficit for [LOSE], surplus for [GAIN], none for [MAINTAIN]).
 */
enum class GoalType {
    LOSE,
    MAINTAIN,
    GAIN
}
