package com.hellohealth.domain.model.vitals

/**
 * Pure, storage-agnostic snapshot of one day's recovery-relevant vitals.
 *
 * Ported from TrackMe's analytics model. Numeric types are [Double]? (not Float/Int) to match
 * HelloHealth's Health Connect reads (all `Double?`) and Room REAL affinity, so values round-trip
 * through [com.hellohealth.data.local.entities.VitalsSampleEntity] without lossy conversions.
 *
 * All vitals are stored in their natural human units: HRV RMSSD in milliseconds, resting heart
 * rate in bpm, sleep in minutes.
 */
data class HealthMetricsData(
    val dateMillis: Long,
    val hrvRmssd: Double?,
    val restingHeartRate: Double?,
    val sleepDurationMinutes: Int?,
    val deepSleepMinutes: Int?
)
