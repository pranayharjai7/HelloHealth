package com.hellohealth.domain.model

/**
 * Biological sex for BMR/TDEE math, kept inclusive. Only [FEMALE]/[MALE] feed the
 * sex-specific Mifflin-St Jeor constant; [OTHER]/[PREFER_NOT_TO_SAY] (and null) fall back to a
 * documented neutral estimate — see [com.hellohealth.domain.health.BodyEnergy].
 */
enum class Gender {
    FEMALE,
    MALE,
    OTHER,
    PREFER_NOT_TO_SAY
}
