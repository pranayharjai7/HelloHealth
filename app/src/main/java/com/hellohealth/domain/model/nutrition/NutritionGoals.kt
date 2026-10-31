package com.hellohealth.domain.model.nutrition

import com.hellohealth.domain.health.MicronutrientTargets.Micro

/** Whether the user's stored macro split is expressed as grams or as a percent of the calorie budget. */
enum class MacroMode { GRAMS, PERCENT }

/**
 * User-chosen overrides for the nutrition targets. The targets themselves are otherwise DERIVED from
 * the profile ([com.hellohealth.domain.health.BodyEnergy] / [com.hellohealth.domain.health.MacroTargets]
 * / [com.hellohealth.domain.health.MicronutrientTargets]); these fields let the user pin custom values.
 *
 * Model: AUTO by default, with a per-group custom toggle. When [useCustomCalories] is false the derived
 * budget is used (and [calorieBudget] is ignored); likewise [useCustomMacros] gates the macro fields.
 * A null stored macro value means "fall back to derivation for that macro even in custom mode" — but the
 * settings UI fills them when custom is on. [trackedMicros] selects which of the 8 micronutrients the UI
 * shows (default: all).
 *
 * The canonical persisted macro representation is GRAMS; [macroMode] only records how the user prefers to
 * EDIT them so the settings screen can round-trip percents. One row per user — mirrors ActivityGoals.
 */
data class NutritionGoals(
    val useCustomCalories: Boolean = false,
    val calorieBudget: Int? = null,
    val useCustomMacros: Boolean = false,
    val macroMode: MacroMode = MacroMode.GRAMS,
    val proteinG: Int? = null,
    val carbsG: Int? = null,
    val fatG: Int? = null,
    val fiberG: Int? = null,
    val trackedMicros: Set<Micro> = Micro.entries.toSet(),
) {
    companion object {
        /** Auto everything, all micros tracked — the state for a user who has never opened settings. */
        val DEFAULT = NutritionGoals()
    }
}
