package com.hellohealth.domain.model.nutrition

/**
 * A single logged food item as the UI consumes it — the domain projection of a `kind='food'`
 * [com.hellohealth.data.local.entities.NutritionEntryEntity] row. Water rows are NOT modelled here;
 * they collapse into [NutritionDaySummary.waterMl].
 *
 * All values are already scaled to the logged [quantity]/[unit] (the per-basis catalog math happens
 * at log time), so this carries absolute totals for the entry: [calories] in kcal, macros in grams.
 * Macros are nullable — a bare quick-add may carry only calories, and the UI renders a dash.
 */
data class FoodEntry(
    val id: String,
    val mealCategory: MealCategory?,
    val foodName: String,
    val quantity: Double,
    val unit: String,
    val calories: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val fibreG: Double?,
    val timestampUtcEpochMs: Long,
)
