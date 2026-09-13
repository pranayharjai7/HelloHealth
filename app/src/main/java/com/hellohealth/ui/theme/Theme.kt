package com.hellohealth.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    secondary = Color(0xFF64B5F6),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4CAF50),
    secondary = Color(0xFF2196F3),
    tertiary = Color(0xFFFF9800),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
)

/** Fraction the base primary is dragged toward the mood accent. Subtle by design — surfaces/text
 *  never move, only the primary that the gradients read live, so readability is guaranteed. */
private const val MOOD_TINT_FRACTION = 0.35f

/** Cross-fade duration when the mood (and therefore the accent) changes. */
private const val MOOD_TINT_ANIM_MS = 900

@Composable
fun HelloHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = NeutralMoodAccent.accent,
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Animate the accent so a newly-logged mood eases in rather than snapping. When dynamic theming
    // is off / no mood is logged, callers pass the base green and this resolves to a no-op tint.
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(durationMillis = MOOD_TINT_ANIM_MS),
        label = "moodAccent"
    )

    // Only `primary` is lerp'd toward the accent — every gradient/wordmark reads primary live, so
    // the whole app re-tints with no call-site edits. Surfaces, backgrounds, and text roles are
    // left exactly as the base scheme defines them, preserving contrast in light and dark.
    val colorScheme = baseScheme.copy(
        primary = lerp(baseScheme.primary, animatedAccent, MOOD_TINT_FRACTION)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        // Expose the raw (un-lerp'd) mood accent + gradient for any component that wants it directly.
        CompositionLocalProvider(LocalMoodAccent provides moodAccentForColor(accent)) {
            content()
        }
    }
}

/** Map an accent color back to its [MoodAccent] for [LocalMoodAccent]. Falls back to the neutral
 *  gradient for the base green so a no-mood app still has a sensible raw gradient. */
private fun moodAccentForColor(accent: Color): MoodAccent =
    if (accent == NeutralMoodAccent.accent) NeutralMoodAccent
    else MoodAccent(accent = accent, gradient = listOf(accent, accent.copy(alpha = 0.65f)))
