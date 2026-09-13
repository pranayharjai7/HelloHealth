package com.hellohealth.domain.repository

import com.hellohealth.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getProfile(): UserProfile?
    suspend fun upsertProfile(profile: UserProfile)

    /** Toggle the mood-tint theme, preserving all other profile fields (load-then-copy). */
    suspend fun setDynamicTheme(enabled: Boolean)
}
