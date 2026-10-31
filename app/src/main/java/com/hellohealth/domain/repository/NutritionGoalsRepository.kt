package com.hellohealth.domain.repository

import com.hellohealth.domain.model.nutrition.NutritionGoals
import kotlinx.coroutines.flow.Flow

/** Store for the user's nutrition target overrides (one row per user). Mirrors [GoalsRepository]. */
interface NutritionGoalsRepository {
    /** Live overrides; emits [NutritionGoals.DEFAULT] when there is no user or no stored row. */
    fun observeNutritionGoals(): Flow<NutritionGoals>

    /** One-shot read; [NutritionGoals.DEFAULT] when there is no user or no stored row. */
    suspend fun getNutritionGoals(): NutritionGoals

    /** Upsert the overrides locally (isSynced=false) and request a background sync. No-op when signed out. */
    suspend fun updateNutritionGoals(goals: NutritionGoals)
}
