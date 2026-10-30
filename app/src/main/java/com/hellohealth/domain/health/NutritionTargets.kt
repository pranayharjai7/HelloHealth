package com.hellohealth.domain.health

import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.model.nutrition.NutritionGoals
import kotlin.math.roundToInt

/**
 * The single place where DERIVED nutrition targets (from the profile) meet the user's stored OVERRIDES
 * ([NutritionGoals]). Pure and null-safe — the sibling of [BodyEnergy]/[MacroTargets]/[MicronutrientTargets],
 * reused by both the dashboard [com.hellohealth.ui.dashboard.NutritionViewModel] and the settings screen's
 * live preview so the number the user sees while editing is exactly the number the app will use.
 *
 * Precedence per group: when the group's `useCustom*` flag is set in [NutritionGoals], the stored value
 * wins; otherwise the derivation is used. A custom macro value that is null still falls back to the
 * derived macro, so a partially-filled custom row degrades gracefully rather than showing a blank target.
 */
object NutritionTargets {

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0

    /** Resolved per-day targets. Any field may be null when neither an override nor a derivation exists. */
    data class ResolvedTargets(
        val calorieBudget: Int?,
        val proteinG: Int?,
        val carbsG: Int?,
        val fatG: Int?,
        val fiberG: Int?,
        val trackedMicros: Set<MicronutrientTargets.Micro>,
    )

    fun resolve(profile: UserProfile?, goals: NutritionGoals): ResolvedTargets {
        val derivedBudget = profile?.let { BodyEnergy.calorieBudget(it) }
        val budget = if (goals.useCustomCalories) (goals.calorieBudget ?: derivedBudget) else derivedBudget

        val derivedMacros = MacroTargets.split(budget, profile?.goalType)
        val protein: Int?; val carbs: Int?; val fat: Int?
        if (goals.useCustomMacros) {
            protein = goals.proteinG ?: derivedMacros?.proteinG
            carbs = goals.carbsG ?: derivedMacros?.carbsG
            fat = goals.fatG ?: derivedMacros?.fatG
        } else {
            protein = derivedMacros?.proteinG
            carbs = derivedMacros?.carbsG
            fat = derivedMacros?.fatG
        }

        val derivedFiber = MicronutrientTargets.fiberTargetG(budget)
        val fiber = if (goals.useCustomMacros) (goals.fiberG ?: derivedFiber) else derivedFiber

        return ResolvedTargets(
            calorieBudget = budget,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fiberG = fiber,
            trackedMicros = goals.trackedMicros,
        )
    }

    /** Convert a macro gram target to its percent-of-budget (for the settings GRAMS⇄PERCENT toggle). */
    fun gramsToPercent(grams: Int, kcalPerG: Double, budget: Int): Int =
        if (budget <= 0) 0 else ((grams * kcalPerG) / budget * 100.0).roundToInt()

    /** Convert a percent-of-budget macro target back to grams. */
    fun percentToGrams(percent: Int, kcalPerG: Double, budget: Int): Int =
        ((budget * percent / 100.0) / kcalPerG).roundToInt()

    const val KCAL_P = KCAL_PER_G_PROTEIN
    const val KCAL_C = KCAL_PER_G_CARBS
    const val KCAL_F = KCAL_PER_G_FAT
}
