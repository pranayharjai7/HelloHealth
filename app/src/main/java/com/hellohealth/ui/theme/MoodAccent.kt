package com.hellohealth.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.hellohealth.domain.model.EmotionType

/**
 * Presentation-only mapping from an [EmotionType] to the accent color that tints the app's theme.
 *
 * Colors live here, NOT on [EmotionType] — the domain enum stays a data concern (valence only) and
 * the palette stays swappable per the roadmap guardrail. The accent is applied by lerping the base
 * `colorScheme.primary` toward it (see [HelloHealthTheme]); it is never written raw into a Material3
 * role, so surface/text contrast is untouched.
 *
 * Palette is the doc's 8-emotion set + Calm (docs/integration/08-FEATURE-PLAN-gamification-theme.md
 * §A). [Neutral] and the null/no-mood case both resolve to the app's base green so the app looks
 * exactly as it does today until a non-neutral mood is logged.
 */
data class MoodAccent(val accent: Color, val gradient: List<Color>)

/** The base green accent — used when dynamic theming is off, no mood is logged, or mood is Neutral. */
val NeutralMoodAccent = MoodAccent(
    accent = Color(0xFF4CAF50),
    gradient = listOf(Color(0xFF4CAF50), Color(0xFF81C784))
)

/**
 * Doc palette accent for [emotion], or [NeutralMoodAccent] when [emotion] is null or Neutral.
 * The gradient is the accent paired with a lighter tint of itself, for any component that wants the
 * raw mood gradient (via [LocalMoodAccent]); the primary-driven tint path only reads [accent].
 */
fun moodAccentFor(emotion: EmotionType?): MoodAccent = when (emotion) {
    null, EmotionType.NEUTRAL -> NeutralMoodAccent
    EmotionType.HAPPINESS -> accentOf(0xFFFFD54F)
    EmotionType.CALM -> accentOf(0xFF64B5F6)
    EmotionType.SADNESS -> accentOf(0xFF9575CD)
    EmotionType.ANGER -> accentOf(0xFFE57373)
    EmotionType.FEAR -> accentOf(0xFF81C784)
    EmotionType.SURPRISE -> accentOf(0xFFBA68C8)
    EmotionType.DISGUST -> accentOf(0xFFAED581)
    EmotionType.CONTEMPT -> accentOf(0xFFF06292)
}

/** Build a [MoodAccent] from a single ARGB long, pairing it with a lighter companion for gradients. */
private fun accentOf(argb: Long): MoodAccent {
    val base = Color(argb)
    return MoodAccent(accent = base, gradient = listOf(base, base.copy(alpha = 0.65f)))
}

/**
 * The mood accent in effect for the current composition. Defaults to [NeutralMoodAccent] so any
 * consumer reading it outside a mood-aware theme still gets the base green. [HelloHealthTheme]
 * provides the resolved value.
 */
val LocalMoodAccent = staticCompositionLocalOf { NeutralMoodAccent }
