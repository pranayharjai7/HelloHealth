package com.hellohealth.ui.common

/**
 * Display-time name formatting. Capitalizes the first letter of each whitespace-separated token so a
 * name stored as the user typed it ("pranay harjai") renders personably ("Pranay Harjai").
 *
 * Deliberately NON-destructive — it only touches the FIRST character of each token via
 * [replaceFirstChar] (the idiom already used across the app for single-word labels) and preserves the
 * rest of each token's casing, so intentional internal capitals survive ("McDonald" stays "McDonald",
 * "o'brien" → "O'brien"). Runs of whitespace collapse to a single space and surrounding whitespace is
 * trimmed. A non-letter leading char (digit, apostrophe, non-Latin script) is left as-is.
 *
 * This is for DISPLAY only; it never changes what is stored. Apply it at each read site, not on save.
 */
fun String.toDisplayName(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }

/**
 * Null/blank-safe variant for the common `user?.name` read sites: returns [fallback] unchanged when the
 * name is null or blank (so the existing "Healthy User" / "--" placeholders are untouched), otherwise
 * the title-cased name.
 */
fun String?.toDisplayNameOr(fallback: String): String =
    this?.takeIf { it.isNotBlank() }?.toDisplayName() ?: fallback
