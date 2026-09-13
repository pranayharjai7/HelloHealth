package com.hellohealth.domain.model

/**
 * Physical-activity level, used as the TDEE multiplier over BMR (standard Mifflin-St Jeor
 * activity factors). See [com.hellohealth.domain.health.BodyEnergy.tdee].
 */
enum class ActivityLevel(val multiplier: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9)
}
