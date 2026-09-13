package com.hellohealth.domain.repository

import com.hellohealth.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    suspend fun getProfile(): UserProfile?
    suspend fun upsertProfile(profile: UserProfile)

    /** Toggle the mood-tint theme, preserving all other profile fields (load-then-copy). */
    suspend fun setDynamicTheme(enabled: Boolean)

    /**
     * Reactive read of the mood-tint toggle for the signed-in user. Emits true (default-on) when no
     * profile row exists yet or no user is signed in, so the theme layer never has to special-case
     * a missing profile.
     */
    fun observeDynamicTheme(): Flow<Boolean>
}
