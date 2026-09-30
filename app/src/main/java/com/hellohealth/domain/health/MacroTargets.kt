package com.hellohealth.domain.health

import com.hellohealth.domain.model.GoalType
import kotlin.math.roundToInt

/**
 * Pure macronutrient math — the only genuinely new nutrition math (BMR/TDEE/budget already live in
 * [BodyEnergy] and are reused verbatim). Given a daily calorie budget and the user's goal, splits it
 * into protein/carbs/fat gram targets.
 *
 * Mirrors [BodyEnergy]'s discipline: an object of pure, null-safe functions. When the budget is
 * missing (profile too sparse for [BodyEnergy.calorieBudget]) the split is null and callers fall
 * back to hiding the macro targets rather than showing zeros.
 *
 * The percentage split shifts by goal — a LOSE goal favors protein (muscle-sparing in a deficit), a
 * GAIN goal favors carbs (training fuel), MAINTAIN is a balanced baseline. Grams derive from the
 * Atwater factors: protein and carbs 4 kcal/g, fat 9 kcal/g.
 */
object MacroTargets {

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0

    /** Protein / carbs / fat gram targets for a day. */
    data class MacroSplit(
        val proteinG: Int,
        val carbsG: Int,
        val fatG: Int,
    )

    // Percentage-of-calories splits by goal. Each triple sums to 1.0 (protein, carbs, fat).
    private data class Ratio(val protein: Double, val carbs: Double, val fat: Double)

    private fun ratioFor(goal: GoalType?): Ratio = when (goal) {
        // Higher protein preserves lean mass in a deficit; lower carbs.
        GoalType.LOSE -> Ratio(protein = 0.40, carbs = 0.30, fat = 0.30)
        // More carbs to fuel training and support a surplus.
        GoalType.GAIN -> Ratio(protein = 0.30, carbs = 0.45, fat = 0.25)
        // Balanced baseline.
        GoalType.MAINTAIN, null -> Ratio(protein = 0.30, carbs = 0.40, fat = 0.30)
    }

    /**
     * Split a [calorieBudget] (kcal/day) into macro gram targets for the [goal]. Returns null when
     * the budget is null (profile too sparse to compute a budget) so the UI can hide the targets.
     * A non-positive budget also yields null — there is nothing meaningful to split.
     */
    fun split(calorieBudget: Int?, goal: GoalType?): MacroSplit? {
        if (calorieBudget == null || calorieBudget <= 0) return null
        val ratio = ratioFor(goal)
        val budget = calorieBudget.toDouble()
        return MacroSplit(
            proteinG = (budget * ratio.protein / KCAL_PER_G_PROTEIN).roundToInt(),
            carbsG = (budget * ratio.carbs / KCAL_PER_G_CARBS).roundToInt(),
            fatG = (budget * ratio.fat / KCAL_PER_G_FAT).roundToInt(),
        )
    }
}
