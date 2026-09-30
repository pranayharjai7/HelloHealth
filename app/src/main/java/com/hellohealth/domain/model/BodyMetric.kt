package com.hellohealth.domain.model

/**
 * A single day's body-composition reading, the domain projection of a `body_metrics` row. All masses
 * in kg, height in cm, body fat as a 0-100 percentage. Every measured field is nullable — a day may
 * carry only a weight, only a scan, etc. [source] is "manual" or "health_connect".
 */
data class BodyMetric(
    val localDate: String,
    val weightKg: Double?,
    val heightCm: Double?,
    val bodyFatPct: Double?,
    val leanMassKg: Double?,
    val fatMassKg: Double?,
    val bodyWaterKg: Double?,
    val boneMassKg: Double?,
    val bmr: Double?,
    val bmi: Double?,
    val waistCm: Double?,
    val vo2max: Double?,
    val source: String,
)
