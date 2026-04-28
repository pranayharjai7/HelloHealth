package com.hellohealth.domain.repository

import com.hellohealth.domain.model.User

interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signInWithGoogle(
        idToken: String, 
        name: String? = null, 
        avatarUrl: String? = null
    ): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun isUserLoggedIn(): Boolean
    suspend fun getCurrentUser(): User?
}
