package com.hellohealth.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodPreferences(
    val dietType: String = "non-vegetarian", // vegetarian, vegan, non-vegetarian, pescatarian
    val allergies: List<String> = emptyList(), // dairy, gluten, nuts, soy
    val cuisinePreferences: List<String> = emptyList() // Indian, Western, Asian, Mixed
)
