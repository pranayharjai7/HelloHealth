package com.hellohealth.domain.repository

import com.hellohealth.domain.model.FoodPreferences
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getFoodPreferences(): Flow<FoodPreferences>
    suspend fun updateFoodPreferences(preferences: FoodPreferences)
}
