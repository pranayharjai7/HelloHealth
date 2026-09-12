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
                _authState.value = resolveAuthenticatedState(authRepository.getCurrentUser())
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
                resolveAuthenticatedState(authRepository.getCurrentUser())
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
                resolveAuthenticatedState(authRepository.getCurrentUser())
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
                resolveAuthenticatedState(authRepository.getCurrentUser())
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

    /**
     * Fetches the profile once, merges its displayName into [_currentUser], and returns the gate
     * decision: [AuthState.NeedsOnboarding] for a new/incomplete account (`profile == null ||
     * !hasOnboarded`), else [AuthState.Authenticated]. A profile read failure degrades to the
     * onboarding gate rather than crashing — a returning user re-confirms rather than being locked out.
     */
    private suspend fun resolveAuthenticatedState(authUser: User?): AuthState {
        val profile = runCatching { profileRepository.getProfile() }.getOrNull()
        _currentUser.value = mergeProfile(authUser, profile)
        return if (profile == null || !profile.hasOnboarded) {
            AuthState.NeedsOnboarding
        } else {
            AuthState.Authenticated
        }
    }

    private fun mergeProfile(authUser: User?, profile: UserProfile?): User? {
        if (authUser == null) return null
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
    object NeedsOnboarding : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Error(val message: String) : AuthState()
}
