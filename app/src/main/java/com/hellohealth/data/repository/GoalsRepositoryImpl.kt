package com.hellohealth.data.repository

import com.hellohealth.domain.model.ActivityGoals
import com.hellohealth.domain.repository.GoalsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ActivityGoalsDto(
    val user_id: String,
    val steps_goal: Int,
    val calories_goal: Int,
    val active_minutes_goal: Int
)

@Singleton
class GoalsRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionManager: SupabaseSessionManager
) : GoalsRepository {

    private val _goals = MutableStateFlow<ActivityGoals?>(null)

    override fun getActivityGoals(): Flow<ActivityGoals> = flow {
        if (_goals.value == null) {
            _goals.value = fetchActivityGoals()
        }
        _goals.collect { goals ->
            if (goals != null) emit(goals)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getCurrentActivityGoals(): ActivityGoals {
        return _goals.value ?: fetchActivityGoals().also { _goals.value = it }
    }

    override suspend fun updateActivityGoals(goals: ActivityGoals) {
        val userId = sessionManager.getCurrentUserId() ?: return
        
        _goals.value = goals
        
        val dto = ActivityGoalsDto(
            user_id = userId,
            steps_goal = goals.steps,
            calories_goal = goals.activeCalories,
            active_minutes_goal = goals.activeMinutes
        )

        supabase.postgrest["activity_goals"].upsert(
            value = dto,
            onConflict = "user_id"
        )
    }

    private suspend fun fetchActivityGoals(): ActivityGoals {
        val userId = sessionManager.getCurrentUserId().orEmpty()
        if (userId.isEmpty()) {
            return ActivityGoals()
        }

        return try {
            val response = supabase.postgrest["activity_goals"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<ActivityGoalsDto>()

            if (response != null) {
                ActivityGoals(
                    steps = response.steps_goal,
                    activeCalories = response.calories_goal,
                    activeMinutes = response.active_minutes_goal
                )
            } else {
                ActivityGoals()
            }
        } catch (_: Exception) {
            ActivityGoals()
        }
    }
}
