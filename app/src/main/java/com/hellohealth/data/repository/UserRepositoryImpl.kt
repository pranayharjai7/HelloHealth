package com.hellohealth.data.repository

import com.hellohealth.domain.model.FoodPreferences
import com.hellohealth.domain.repository.UserRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FoodPreferencesDto(
    val user_id: String,
    val diet_type: String,
    val allergies: List<String>,
    val cuisines: List<String>
)

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient
) : UserRepository {

    override fun getFoodPreferences(): Flow<FoodPreferences> = flow {
        val userId = resolveUserId().orEmpty()
        if (userId.isEmpty()) {
            emit(FoodPreferences())
            return@flow
        }

        try {
            val response = supabase.postgrest["food_preferences"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeSingleOrNull<FoodPreferencesDto>()

            if (response != null) {
                emit(FoodPreferences(
                    dietType = response.diet_type,
                    allergies = response.allergies,
                    cuisinePreferences = response.cuisines
                ))
            } else {
                emit(FoodPreferences())
            }
        } catch (e: Exception) {
            emit(FoodPreferences())
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun updateFoodPreferences(preferences: FoodPreferences) {
        val userId = resolveUserId() ?: return
        
        val dto = FoodPreferencesDto(
            user_id = userId,
            diet_type = preferences.dietType,
            allergies = preferences.allergies,
            cuisines = preferences.cuisinePreferences
        )

        supabase.postgrest["food_preferences"].upsert(
            value = dto,
            onConflict = "user_id"
        )
    }

    private suspend fun resolveUserId(): String? {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentUserOrNull()?.id
            ?: supabase.auth.currentSessionOrNull()?.user?.id
            ?: runCatching { supabase.auth.retrieveUserForCurrentSession(updateSession = true).id }.getOrNull()
    }
}
