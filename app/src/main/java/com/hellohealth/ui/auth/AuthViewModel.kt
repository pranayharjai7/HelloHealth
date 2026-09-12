package com.hellohealth.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.domain.health.BodyEnergy
import com.hellohealth.domain.model.Gender
import com.hellohealth.domain.model.UnitPreference
import com.hellohealth.domain.model.User
import com.hellohealth.domain.repository.AuthRepository
import com.hellohealth.domain.repository.GoalsRepository
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
    private val profileRepository: ProfileRepository,
    private val goalsRepository: GoalsRepository
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

    /**
     * Name-only save from the profile [AlertDialog]. MUST load-then-`copy()` because
     * [ProfileRepository.upsertProfile] is a full-row replace — building a fresh
     * `UserProfile(displayName = …)` here would null every vital and reset `hasOnboarded`, wiping
     * the user's onboarding data (the latent bug this fixes). Preserves all other fields untouched.
     */
    fun saveProfile(displayName: String) {
        viewModelScope.launch {
            val trimmedName = displayName.trim()
            if (trimmedName.isEmpty()) {
                _profileEditorState.value = _profileEditorState.value.copy(
                    isSaving = false,
                    error = "Name cannot be empty.",
                    successMessage = null
                )
                return@launch
            }

            _profileEditorState.value = _profileEditorState.value.copy(
                isSaving = true,
                error = null,
                successMessage = null
            )

            runCatching {
                val existing = profileRepository.getProfile() ?: UserProfile()
                profileRepository.upsertProfile(existing.copy(displayName = trimmedName))
                authRepository.updateCurrentUserName(trimmedName)
            }.onSuccess {
                _currentUser.value = _currentUser.value?.copy(name = trimmedName)
                _profileEditorState.value = _profileEditorState.value.copy(
                    isSaving = false,
                    successMessage = "Profile updated."
                )
            }.onFailure { error ->
                _profileEditorState.value = _profileEditorState.value.copy(
                    isSaving = false,
                    error = error.message ?: "Failed to update profile."
                )
            }
        }
    }

    /**
     * Loads the stored profile so the full-screen vitals editor (Step 10b) can seed its fields.
     * Clears any stale save message. A read failure surfaces as [ProfileEditorState.error] with a
     * null profile — the editor then falls back to empty fields rather than crashing.
     */
    fun loadProfileForEditing() {
        _profileEditorState.value = ProfileEditorState(isLoading = true)
        viewModelScope.launch {
            runCatching { profileRepository.getProfile() }
                .onSuccess { profile ->
                    _profileEditorState.value = ProfileEditorState(loaded = true, profile = profile)
                }
                .onFailure { e ->
                    AppLogger.w(FeatureTag.PROFILE, "loadProfileForEditing failed", e)
                    _profileEditorState.value = ProfileEditorState(
                        error = e.message ?: "Couldn't load your profile."
                    )
                }
        }
    }

    /**
     * Full vitals save from the editor screen. Load-then-`copy()` over the stored row so
     * [hasOnboarded] and any field not surfaced by the editor survive (full-row upsert trap), then
     * pushes the display name to the auth account, and re-derives ONLY the active-calorie goal from
     * the new vitals — the user's manual `steps`/`activeMinutes` are preserved (locked decision).
     * On success surfaces a message; on failure surfaces the error and keeps the editor open.
     */
    fun saveVitals(
        displayName: String,
        gender: Gender?,
        birthDateEpochDay: Long?,
        heightCm: Double?,
        weightKg: Double?,
        unitPreference: UnitPreference
    ) {
        if (_profileEditorState.value.isSaving) return
        val editedName = displayName.trim().ifBlank { null }
        _profileEditorState.value = _profileEditorState.value.copy(
            isSaving = true,
            error = null,
            successMessage = null
        )
        viewModelScope.launch {
            runCatching {
                val existing = profileRepository.getProfile() ?: UserProfile()
                // A blank name field must NOT erase the stored name — fall back to what's already
                // persisted. (Only the name-only dialog path can clear a name, and it rejects blank.)
                val resolvedName = editedName ?: existing.displayName
                val updated = existing.copy(
                    displayName = resolvedName,
                    gender = gender,
                    birthDateEpochDay = birthDateEpochDay,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    unitPreference = unitPreference,
                    hasOnboarded = existing.hasOnboarded
                )
                profileRepository.upsertProfile(updated)
                // Name lives in the profile row (source of truth for the greeting), so a failed
                // remote name sync is logged, not fatal.
                resolvedName?.let { name ->
                    authRepository.updateCurrentUserName(name).onFailure { e ->
                        AppLogger.w(FeatureTag.PROFILE, "profile edit: name sync to auth failed", e)
                    }
                }
                // Re-derive ONLY active-calories; keep the user's manual steps/active-minutes.
                val currentGoals = goalsRepository.getCurrentActivityGoals()
                goalsRepository.updateActivityGoals(
                    currentGoals.copy(activeCalories = BodyEnergy.suggestedGoals(updated).activeCalories)
                )
                updated
            }.onSuccess { updated ->
                _currentUser.value = _currentUser.value?.copy(name = updated.displayName)
                _profileEditorState.value = _profileEditorState.value.copy(
                    isSaving = false,
                    successMessage = "Profile updated.",
                    profile = updated
                )
            }.onFailure { e ->
                AppLogger.w(FeatureTag.PROFILE, "saveVitals failed to persist", e)
                _profileEditorState.value = _profileEditorState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to update profile."
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
