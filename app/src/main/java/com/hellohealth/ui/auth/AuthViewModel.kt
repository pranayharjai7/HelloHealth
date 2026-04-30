package com.hellohealth.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.model.User
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.ProfileRepository
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.ui.profile.ProfileEditorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState = _authState.asStateFlow()
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()
    private val _profileEditorState = MutableStateFlow(ProfileEditorState())
    val profileEditorState = _profileEditorState.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        viewModelScope.launch {
            if (authRepository.isUserLoggedIn()) {
                _currentUser.value = mergeProfile(authRepository.getCurrentUser())
                _authState.value = AuthState.Authenticated
            } else {
                _currentUser.value = null
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signUp(email, password)
            _authState.value = if (result.isSuccess) {
                _currentUser.value = mergeProfile(authRepository.getCurrentUser())
                AuthState.Authenticated
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signIn(email, password)
            _authState.value = if (result.isSuccess) {
                _currentUser.value = mergeProfile(authRepository.getCurrentUser())
                AuthState.Authenticated
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun signInWithGoogle(idToken: String, email: String? = null, name: String? = null, avatarUrl: String? = null) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signInWithGoogle(idToken, email, name, avatarUrl)
            _authState.value = if (result.isSuccess) {
                _currentUser.value = mergeProfile(authRepository.getCurrentUser())
                AuthState.Authenticated
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }

    fun signOut() {
        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated
        viewModelScope.launch {
            authRepository.signOut()
        }
    }

    fun setLoading() {
        _authState.value = AuthState.Loading
    }

    fun setError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    fun saveProfile(displayName: String) {
        viewModelScope.launch {
            val trimmedName = displayName.trim()
            if (trimmedName.isEmpty()) {
                _profileEditorState.value = ProfileEditorState(error = "Name cannot be empty.")
                return@launch
            }

            _profileEditorState.value = ProfileEditorState(isSaving = true)

            runCatching {
                profileRepository.upsertProfile(UserProfile(displayName = trimmedName))
                authRepository.updateCurrentUserName(trimmedName)
            }.onSuccess {
                _currentUser.value = _currentUser.value?.copy(name = trimmedName)
                _profileEditorState.value = ProfileEditorState(successMessage = "Profile updated.")
            }.onFailure { error ->
                _profileEditorState.value = ProfileEditorState(
                    error = error.message ?: "Failed to update profile."
                )
            }
        }
    }

    fun clearProfileEditorMessage() {
        _profileEditorState.value = ProfileEditorState()
    }

    private suspend fun mergeProfile(authUser: User?): User? {
        if (authUser == null) return null
        val profile = runCatching { profileRepository.getProfile() }.getOrNull()
        return if (profile?.displayName.isNullOrBlank()) {
            authUser
        } else {
            authUser.copy(name = profile?.displayName)
        }
    }
}

sealed class AuthState {
    object Checking : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Error(val message: String) : AuthState()
}
