package com.hellohealth.data.repository

import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ProfileDto(
    val id: String,
    val display_name: String? = null
)

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionManager: SupabaseSessionManager
) : ProfileRepository {

    override suspend fun getProfile(): UserProfile? {
        val userId = sessionManager.getCurrentUserId() ?: return null
        val response = supabase.postgrest["profiles"]
            .select {
                filter {
                    eq("id", userId)
                }
            }
            .decodeSingleOrNull<ProfileDto>()

        return response?.let {
            UserProfile(displayName = it.display_name)
        }
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        val userId = sessionManager.getCurrentUserId() ?: return
        val dto = ProfileDto(
            id = userId,
            display_name = profile.displayName?.trim().orEmpty().ifBlank { null }
        )

        supabase.postgrest["profiles"].upsert(
            value = dto,
            onConflict = "id"
        )
    }
}
