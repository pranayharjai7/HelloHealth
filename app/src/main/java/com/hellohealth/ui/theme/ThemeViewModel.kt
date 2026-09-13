package com.hellohealth.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the app-wide mood tint. Emits the accent [Color] the [HelloHealthTheme] should lerp the
 * base primary toward: the palette color for the user's latest logged emotion when dynamic theming
 * is on, or the base green when it is off (or no mood is logged yet).
 *
 * The theme reacts live to both inputs — logging a mood re-tints the whole app, and toggling the
 * preference reverts it — because both are Room-backed Flows.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    emotionsRepository: EmotionsRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    val accent: StateFlow<Color> = combine(
        emotionsRepository.observeLatest(),
        profileRepository.observeDynamicTheme()
    ) { latest, dynamicOn ->
        if (dynamicOn) moodAccentFor(latest?.emotion).accent else NeutralMoodAccent.accent
    }.stateIn(
        scope = viewModelScope,
        // Keep the tint alive briefly across config changes so it doesn't flash back to green.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NeutralMoodAccent.accent
    )
}
