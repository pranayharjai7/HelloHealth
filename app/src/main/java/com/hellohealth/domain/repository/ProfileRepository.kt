package com.hellohealth.domain.repository

import com.hellohealth.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getProfile(): UserProfile?
    suspend fun upsertProfile(profile: UserProfile)
}
