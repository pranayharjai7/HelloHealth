package com.hellohealth.domain.health

import kotlin.math.max

/**
 * Estimates **active** energy expenditure (kcal) for a day, since many Health Connect writers (e.g.
 * Samsung Health) publish only `TotalCaloriesBurnedRecord` and `BasalMetabolicRateRecord` — never
 * `ActiveCaloriesBurnedRecord` — so active burn cannot be read directly and must be reconstructed from
 * movement physiology, not by naively subtracting basal.
 *
 * Tiered, best-available-wins, each tier physiologically grounded + reproducible:
 *
 *  - **Tier A — HR-based (Keytel 2005).** The most accurate during exertion; needs continuous intraday
 *    heart rate. WEARABLE: unused today (phones rarely expose beat-level HR); lights up when a Wear OS
 *    companion feeds HR (see docs/health-intelligence/phase-8-wearable-future.md). Stubbed below.
 *  - **Tier B — MET from distance/steps (ACSM walking/running equations).** The primary ambulatory
 *    estimator today: derive speed from distance ÷ active-time (or cadence from steps), map to a
 *    walking/running VO₂, convert to kcal, and report the NET of the resting (1-MET) component.
 *  - **Tier C — step-count heuristic.** Fallback when distance is absent (~0.04 kcal/step scaled by
 *    body mass), flagged as an estimate.
 *
 * Reconciliation: when a device `TotalCaloriesBurned` is known, the result is clamped to
 * `[0, total − basalForElapsed]` so we never claim more active burn than the device's own measured
 * total allows (basal scaled to the elapsed fraction of the day — a *ceiling*, not the primary value).
 *
 * Pure/deterministic — all inputs passed in; unit-tested.
 */
object ActiveEnergyEstimator {

    /** 1 MET = 3.5 ml O₂/kg/min; resting is 1 MET. */
    private const val RESTING_VO2 = 3.5

    /** Default body mass when the profile/HC weight is unknown (kg). */
    const val DEFAULT_BODY_MASS_KG = 70.0

    /** ~0.04 kcal/step at 70 kg (Fitbit-class rule of thumb), scaled linearly by mass. */
    private const val KCAL_PER_STEP_AT_70KG = 0.04

    data class Result(
        val activeKcal: Double,
        val isEstimate: Boolean,
        /** Which tier produced the value — for provenance + the UI "measured vs estimated" hint. */
        val method: String,
    )

    /**
     * @param steps today's step count.
     * @param distanceMeters today's distance (0 if unknown).
     * @param activeMinutes minutes of movement (used to derive speed from distance).
     * @param bodyMassKg body mass; pass [DEFAULT_BODY_MASS_KG] when unknown.
     * @param hcTotalKcal the device TotalCaloriesBurned for the window, or null.
     * @param bmrKcalPerDay resolved basal metabolic rate (kcal/day).
     * @param elapsedFractionOfDay 0..1 — how much of the local day has elapsed (1.0 for a past day), so
     *   the basal ceiling matches a partial-day total.
     */
    fun estimate(
        steps: Long,
        distanceMeters: Double,
        activeMinutes: Long,
        bodyMassKg: Double,
        hcTotalKcal: Double?,
        bmrKcalPerDay: Double,
        elapsedFractionOfDay: Double,
    ): Result {
        val mass = if (bodyMassKg > 0) bodyMassKg else DEFAULT_BODY_MASS_KG

        // Tier B / C — the movement estimate.
        val (rawActive, method, isEstimate) = when {
            distanceMeters > 0.0 && activeMinutes > 0L -> {
                val kcal = metActiveKcal(distanceMeters, activeMinutes, mass)
                Triple(kcal, "met_distance", false)
            }
            steps > 0L -> {
                val kcal = steps * KCAL_PER_STEP_AT_70KG * (mass / 70.0)
                Triple(kcal, "step_heuristic", true)
            }
            else -> Triple(0.0, "none", false)
        }

        // Reconciliation ceiling: never exceed (device total − basal accrued so far).
        val reconciled = if (hcTotalKcal != null) {
            val basalElapsed = bmrKcalPerDay * elapsedFractionOfDay.coerceIn(0.0, 1.0)
            val ceiling = (hcTotalKcal - basalElapsed).coerceAtLeast(0.0)
            rawActive.coerceIn(0.0, ceiling)
        } else {
            rawActive.coerceAtLeast(0.0)
        }

        return Result(activeKcal = reconciled, isEstimate = isEstimate, method = method)
    }

    /**
     * ACSM-equation net active kcal from average speed. Walking (<~7 km/h):
     * VO₂ = 0.1·v + 3.5 (ml/kg/min, v in m/min, grade 0); running: VO₂ = 0.2·v + 3.5. Net active
     * subtracts the resting 1-MET component; kcal/min = VO₂·mass/1000·5.
     */
    private fun metActiveKcal(distanceMeters: Double, activeMinutes: Long, massKg: Double): Double {
        val vMetersPerMin = distanceMeters / activeMinutes
        val speedKmh = vMetersPerMin * 60.0 / 1000.0
        val grossVo2 = if (speedKmh >= 7.0) 0.2 * vMetersPerMin + RESTING_VO2 else 0.1 * vMetersPerMin + RESTING_VO2
        val netVo2 = max(0.0, grossVo2 - RESTING_VO2)
        val kcalPerMin = netVo2 * massKg / 1000.0 * 5.0
        return kcalPerMin * activeMinutes
    }

    // WEARABLE (Tier A, Phase 8): Keytel 2005 HR→kcal. Unused until a wearable feeds continuous HR.
    // kcal/min(men)   = (-55.0969 + 0.6309·HR + 0.1988·W + 0.2017·A) / 4.184
    // kcal/min(women) = (-20.4022 + 0.4472·HR − 0.1263·W + 0.0740·A) / 4.184
    // Summed over active minutes, resting subtracted. Left as a documented seam, not implemented.
}
