package com.hellohealth.data.repository

import com.hellohealth.domain.model.User
import com.hellohealth.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.Google
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.providers.builtin.IDToken
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.exercise.ExerciseSeeder
import com.hellohealth.data.food.NutritionSeeder
import com.hellohealth.data.local.AppDatabase
import com.hellohealth.data.local.dao.UserDao
import com.hellohealth.sync.SyncScheduler
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import io.ktor.http.isSuccess
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val database: AppDatabase,
    private val syncScheduler: SyncScheduler,
    private val exerciseSeeder: ExerciseSeeder,
    private val nutritionSeeder: NutritionSeeder,
    private val userDao: UserDao
) : AuthRepository {

    override suspend fun signUp(email: String, password: String): Result<Unit> {
        return runCatching {
            supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            supabase.auth.awaitInitialization()

            if (supabase.auth.currentSessionOrNull() == null) {
                error("Account created. Please verify your email before logging in.")
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            ensureCurrentUserLoaded()
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        email: String?,
        name: String?,
        avatarUrl: String?
    ): Result<Unit> {
        return runCatching {
            supabase.auth.signInWith(IDToken) {
                this.idToken = idToken
                provider = Google
            }

            ensureCurrentUserLoaded(fallbackEmail = email, fallbackName = name, fallbackAvatarUrl = avatarUrl)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return runCatching {
            try {
                supabase.auth.signOut()
            } catch (_: Exception) {
                supabase.auth.clearSession()
            }
        }
    }

    override suspend fun isUserLoggedIn(): Boolean {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentSessionOrNull() != null
    }

    override suspend fun getCurrentUser(): User? {
        supabase.auth.awaitInitialization()
        val userInfo = supabase.auth.currentUserOrNull()
            ?: supabase.auth.currentSessionOrNull()?.user
            ?: runCatching { supabase.auth.retrieveUserForCurrentSession(updateSession = true) }.getOrNull()

        return userInfo?.toDomainUser()
    }

    override suspend fun updateCurrentUserName(name: String): Result<User?> {
        return runCatching {
            supabase.auth.updateUser {
                data {
                    put("name", name)
                    put("full_name", name)
                }
            }
            getCurrentUser()
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return runCatching {
            supabase.auth.awaitInitialization()
            val session = supabase.auth.currentSessionOrNull()
            if (session == null) {
                error("No authenticated user to delete")
            }

            val response: HttpResponse = supabase.functions.invoke(
                function = "delete-account",
                headers = headers { append(HttpHeaders.ContentType, "application/json") }
            )

            if (!response.status.isSuccess()) {
                val errorBody = runCatching { response.body<String>() }.getOrDefault("Unknown server error")
                AppLogger.e(FeatureTag.AUTH, "Account deletion failed on server: HTTP ${response.status.value} - $errorBody")
                error("Server account deletion failed (HTTP ${response.status.value})")
            }

            AppLogger.i(FeatureTag.AUTH, "Server-side account deletion confirmed. Purging local app data.")

            syncScheduler.cancelAll()
            database.clearAllTables()

            runCatching { exerciseSeeder.seedIfNeeded() }
            runCatching { nutritionSeeder.seedIfNeeded() }

            userDao.logoutAll()
            try {
                supabase.auth.signOut()
            } catch (_: Exception) {
                supabase.auth.clearSession()
            }
        }
    }

    private suspend fun ensureCurrentUserLoaded(
        fallbackEmail: String? = null,
        fallbackName: String? = null,
        fallbackAvatarUrl: String? = null
    ) {
        supabase.auth.awaitInitialization()
        val userInfo = supabase.auth.currentUserOrNull()
            ?: supabase.auth.currentSessionOrNull()?.user
            ?: supabase.auth.retrieveUserForCurrentSession(updateSession = true)

        userInfo.toDomainUser(
            fallbackEmail = fallbackEmail,
            fallbackName = fallbackName,
            fallbackAvatarUrl = fallbackAvatarUrl
        )
    }

    private fun UserInfo.toDomainUser(
        fallbackEmail: String? = null,
        fallbackName: String? = null,
        fallbackAvatarUrl: String? = null
    ): User {
        val googleIdentity = identities?.firstOrNull { it.provider == "google" }
        val provider = appMetadata.string("provider")
        val name = userMetadata.string("full_name")
            ?: userMetadata.string("name")
            ?: googleIdentity?.identityData.string("full_name")
            ?: googleIdentity?.identityData.string("name")
            ?: fallbackName
        val avatarUrl = userMetadata.string("avatar_url")
            ?: userMetadata.string("picture")
            ?: googleIdentity?.identityData.string("avatar_url")
            ?: googleIdentity?.identityData.string("picture")
            ?: fallbackAvatarUrl
        val resolvedEmail = email
            ?: userMetadata.string("email")
            ?: googleIdentity?.identityData.string("email")
            ?: fallbackEmail
            ?: ""
        val createdAtMillis = createdAt?.toJavaInstant()?.toEpochMilli() ?: System.currentTimeMillis()

        return User(
            email = resolvedEmail,
            name = name,
            avatarUrl = avatarUrl,
            isGoogleUser = provider == "google" || identities?.any { it.provider == "google" } == true,
            createdAt = createdAtMillis
        )
    }

    private fun JsonObject?.string(key: String): String? {
        return this?.get(key)?.jsonPrimitive?.contentOrNull
    }
}
