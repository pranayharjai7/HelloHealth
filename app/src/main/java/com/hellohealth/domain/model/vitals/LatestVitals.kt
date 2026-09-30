package com.hellohealth.domain.model.vitals

/**
 * The most recent day's raw vitals, for the dashboard card's at-a-glance chips. Unlike
 * [HealthMetricsData] (which carries only the recovery inputs the readiness calculator needs), this
 * exposes the full vitals vocabulary the card displays. Every field is nullable — a device missing a
 * sensor (no HRV, no thermometer) leaves that value null and the card renders a dash, never a crash.
 *
 * All values are in NATURAL HUMAN UNITS: RHR in bpm, HRV RMSSD in ms, respiratory rate in
 * breaths/min, temperature in °C, hydration in ml, SpO2 as 0-100 (NOT a 0-1 fraction).
 */
data class LatestVitals(
    val localDate: String,
    val restingHeartRate: Double?,
    val hrvRmssd: Double?,
    val respiratoryRate: Double?,
    val bodyTemperature: Double?,
    val hydrationMl: Double?,
    val spo2: Double?
)
