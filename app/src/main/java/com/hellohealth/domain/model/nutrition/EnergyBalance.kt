package com.hellohealth.domain.model.nutrition

/**
 * The flagship energy-balance number the dashboard is built around, computed at READ TIME (never
 * persisted): `net = caloriesOut − caloriesIn`. A positive [net] is a deficit (burned more than
 * eaten), a negative [net] a surplus.
 *
 * [caloriesOut] is the activity snapshot's active + BMR total; [caloriesIn] is the nutrition day's
 * consumed total; [budget] is the profile-derived daily calorie budget from
 * [com.hellohealth.domain.health.BodyEnergy.calorieBudget] (nullable when the profile is too sparse
 * — the card then hides the budget-relative UI).
 */
data class EnergyBalance(
    val caloriesIn: Double,
    val caloriesOut: Double,
    val budget: Int?,
) {
    /** Deficit (positive) / surplus (negative), always derived — never stored. */
    val net: Double get() = caloriesOut - caloriesIn

    /** Calories remaining against the budget, or null when no budget is available. */
    val remainingToBudget: Double? get() = budget?.let { it - caloriesIn }
}
