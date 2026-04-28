package com.hellohealth.data.repository

import com.hellohealth.data.local.dao.UserDao
import com.hellohealth.data.local.entities.UserEntity
import com.hellohealth.domain.model.User
import com.hellohealth.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : AuthRepository {

    override suspend fun signUp(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val existingUser = userDao.getUserByEmail(email)
            if (existingUser != null) {
                Result.failure(Exception("User already exists"))
            } else {
                userDao.insertUser(UserEntity(email = email, password = password, isLoggedIn = true))
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val user = userDao.getUserByEmail(email)
            if (user != null && user.password == password) {
                userDao.logoutAll()
                userDao.updateUser(user.copy(isLoggedIn = true))
                Result.success(Unit)
            } else {
                Result.failure(Exception("Invalid email or password"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        name: String?,
        avatarUrl: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            // Simplified: use email from idToken or a unique identifier
            // In a production app with Supabase, we would get this from the JWT/Session
            val email = "google_user_${idToken.takeLast(5)}@gmail.com" 
            val existingUser = userDao.getUserByEmail(email)
            
            val user = if (existingUser != null) {
                existingUser.copy(
                    name = name ?: existingUser.name,
                    avatarUrl = avatarUrl ?: existingUser.avatarUrl,
                    isLoggedIn = true
                )
            } else {
                UserEntity(
                    email = email,
                    password = "",
                    name = name,
                    avatarUrl = avatarUrl,
                    isGoogleUser = true,
                    isLoggedIn = true
                )
            }
            
            userDao.logoutAll()
            userDao.insertUser(user)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            userDao.logoutAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isUserLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        return@withContext userDao.getLoggedInUser() != null
    }

    override suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        val entity = userDao.getLoggedInUser() ?: return@withContext null
        return@withContext User(
            email = entity.email,
            name = entity.name,
            avatarUrl = entity.avatarUrl,
            isGoogleUser = entity.isGoogleUser
        )
    }
}
