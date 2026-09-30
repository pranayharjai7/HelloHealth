package com.hellohealth.domain.model.nutrition

/**
 * The meal a food entry belongs to. Persisted as its lowercase [wire] value in
 * `nutrition_entries.meal_category`; water rows carry null. [order] fixes the display sequence of
 * the meal sections on the nutrition screen.
 */
enum class MealCategory(val wire: String, val displayName: String, val order: Int) {
    BREAKFAST("breakfast", "Breakfast", 0),
    LUNCH("lunch", "Lunch", 1),
    DINNER("dinner", "Dinner", 2),
    SNACK("snack", "Snack", 3);

    companion object {
        /** Parse a persisted wire value back to a category, or null if unknown/absent. */
        fun fromWire(value: String?): MealCategory? =
            value?.let { v -> entries.firstOrNull { it.wire == v } }
    }
}
