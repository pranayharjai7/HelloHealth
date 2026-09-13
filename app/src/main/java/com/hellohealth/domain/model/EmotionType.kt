package com.hellohealth.domain.model

/**
 * The mood a user can log or that the (future, P2) on-device model can detect.
 *
 * The first eight labels are exactly the classes produced by the MyEmotions PyTorch model
 * (`enet_b0_8_va_mtl.ptl`) — see docs/integration/05-FEATURE-PLAN-emotions.md. [Calm] is an extra
 * manual-only mood: the model never emits it, so it is only ever reachable through the manual
 * picker. Keep that invariant when wiring the P2 camera path.
 *
 * [valence] drives both the Balance-Score insights (positive ratio) and is available for any
 * future coaching nudge. It is NOT used to pick the theme accent — that mapping lives in the UI's
 * MoodAccent registry so colors stay a presentation concern.
 */
enum class EmotionType(val valence: Valence) {
    ANGER(Valence.NEGATIVE),
    CONTEMPT(Valence.NEGATIVE),
    DISGUST(Valence.NEGATIVE),
    FEAR(Valence.NEGATIVE),
    HAPPINESS(Valence.POSITIVE),
    NEUTRAL(Valence.NEUTRAL),
    SADNESS(Valence.NEGATIVE),
    SURPRISE(Valence.POSITIVE),
    CALM(Valence.POSITIVE);

    /** Human-facing label, e.g. "Happiness". */
    fun displayLabel(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    /** A single emoji glyph for compact card/picker display. */
    fun emoji(): String = when (this) {
        ANGER -> "😠"
        CONTEMPT -> "😒"
        DISGUST -> "🤢"
        FEAR -> "😨"
        HAPPINESS -> "😄"
        NEUTRAL -> "😐"
        SADNESS -> "😢"
        SURPRISE -> "😲"
        CALM -> "😌"
    }

    companion object {
        /**
         * Parse a stored/wire `name` string back to an [EmotionType]; unknown or null values fall
         * back to [NEUTRAL] so a bad row never crashes a read (defensive per the roadmap guardrail).
         */
        fun fromName(value: String?): EmotionType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NEUTRAL
    }
}

/** Coarse mood polarity used by insights and (future) coaching. */
enum class Valence { POSITIVE, NEUTRAL, NEGATIVE }
