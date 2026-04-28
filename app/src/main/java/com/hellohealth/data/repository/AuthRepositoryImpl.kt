package com.hellohealth.data.repository

import com.hellohealth.data.local.dao.UserDao
import com.hellohealth.data.local.entities.UserEntity
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

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            // In a real local-only app, we might just use the token/id as a user identifier
            val email = "google_user_${idToken.takeLast(5)}@gmail.com" 
            val user = userDao.getUserByEmail(email) ?: UserEntity(email = email, password = "", isGoogleUser = true)
            
            userDao.logoutAll()
            userDao.insertUser(user.copy(isLoggedIn = true))
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
}
