package com.hellohealth.domain.model.nutrition

/**
 * Everything the nutrition screen and dashboard card need for a single local day, aggregated from
 * that day's live (non-tombstoned) entries. Computed in the repository from the Room rows — nothing
 * here is persisted (no denormalized summary table; the totals are always derived at read time).
 *
 * [entriesByMeal] groups the day's food entries into their meal sections in [MealCategory.order];
 * an empty section is simply absent from the map. The macro/calorie totals sum only food rows;
 * [waterMl] sums the day's water rows separately.
 */
data class NutritionDaySummary(
    val localDate: String,
    val caloriesConsumed: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fibreG: Double,
    val waterMl: Double,
    val entriesByMeal: Map<MealCategory, List<FoodEntry>>,
) {
    companion object {
        /** An all-zero summary for a day with no entries (or no signed-in user). */
        fun empty(localDate: String) = NutritionDaySummary(
            localDate = localDate,
            caloriesConsumed = 0.0,
            proteinG = 0.0,
            carbsG = 0.0,
            fatG = 0.0,
            fibreG = 0.0,
            waterMl = 0.0,
            entriesByMeal = emptyMap(),
        )
    }
}
