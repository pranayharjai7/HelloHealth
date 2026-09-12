package com.hellohealth.domain.model

/**
 * Latest body measurements read from Health Connect for onboarding pre-fill. Both fields are
 * nullable — Health Connect may hold neither, one, or both, and a null simply means "no record to
 * pre-fill" (the user then types it manually). Stored/exposed in metric (the app's canonical unit):
 * [heightCm] in centimeters, [weightKg] in kilograms.
 */
data class BodyMetrics(
    val heightCm: Double? = null,
    val weightKg: Double? = null
)
