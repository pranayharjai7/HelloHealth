package com.hellohealth.domain.repository

import com.hellohealth.domain.model.ActivityGoals
import kotlinx.coroutines.flow.Flow

interface GoalsRepository {
    fun getActivityGoals(): Flow<ActivityGoals>
    suspend fun getCurrentActivityGoals(): ActivityGoals
    suspend fun updateActivityGoals(goals: ActivityGoals)
}
