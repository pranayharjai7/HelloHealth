package com.hellohealth.domain.health

import kotlin.math.roundToInt

/**
 * Daily reference targets for fiber and the curated micronutrient set. Pure, null-safe — the sibling
 * of [MacroTargets], and the single source of truth for every "/ target" the nutrition UI shows.
 *
 * Values are the FDA Daily Values (DV) for adults & children 4+ — the regulatory consolidation of the
 * NIH Dietary Reference Intakes (DRIs), so they are defensible and label-aligned. Fiber additionally
 * supports a calorie-scaled target (the DRI basis of 14 g per 1000 kcal) when a calorie budget is known,
 * which is more personal than the flat DV; it falls back to the 28 g DV otherwise.
 *
 * Units are fixed per nutrient (see [Micro.unit]); the UI formats "<consumed> / <target> <unit>".
 * These are reference intakes, not medical advice — the UI copy must stay descriptive.
 */
object MicronutrientTargets {

    /** FDA Daily Value for dietary fiber, grams. */
    const val FIBER_DV_G = 28

    /** DRI basis: 14 g fiber per 1000 kcal. */
    private const val FIBER_G_PER_1000_KCAL = 14.0

    /**
     * Daily fiber target in grams. Prefers the calorie-scaled DRI (14 g / 1000 kcal) when [calorieBudget]
     * is known and positive; otherwise the flat 28 g DV. Never null — fiber is universal enough to always
     * show a target.
     */
    fun fiberTargetG(calorieBudget: Int?): Int =
        if (calorieBudget != null && calorieBudget > 0)
            (calorieBudget / 1000.0 * FIBER_G_PER_1000_KCAL).roundToInt()
        else FIBER_DV_G

    /**
     * The curated micronutrients tracked beyond the macros + fiber, chosen for impact × how reliably
     * food databases carry them. Each carries its FDA Daily Value and display unit. Sugar uses the
     * added-sugars DV (50 g) as a pragmatic ceiling for total sugars (the value food sources report).
     */
    enum class Micro(val label: String, val dv: Double, val unit: String, val isLimit: Boolean) {
        SODIUM("Sodium", 2300.0, "mg", isLimit = true),
        SATURATED_FAT("Saturated fat", 20.0, "g", isLimit = true),
        SUGAR("Sugar", 50.0, "g", isLimit = true),
        CALCIUM("Calcium", 1300.0, "mg", isLimit = false),
        IRON("Iron", 18.0, "mg", isLimit = false),
        POTASSIUM("Potassium", 4700.0, "mg", isLimit = false),
        VITAMIN_D("Vitamin D", 20.0, "mcg", isLimit = false),
        VITAMIN_C("Vitamin C", 90.0, "mg", isLimit = false),
    }

    /**
     * Daily target for a micronutrient. The DV is the same for everyone 4+ in the FDA table; iron is the
     * one real sex/age split in the DRIs (18 mg for menstruating women vs 8 mg otherwise), so it accepts
     * an optional override. [isLimit] nutrients (sodium, saturated fat, sugar) are ceilings to stay
     * under, not goals to reach — the UI should frame them that way.
     */
    fun targetFor(micro: Micro, ironIsHigherNeed: Boolean = true): Double = when (micro) {
        Micro.IRON -> if (ironIsHigherNeed) 18.0 else 8.0
        else -> micro.dv
    }
}
