package com.hellohealth.core.logging

/**
 * Stable per-feature log tags. Using an enum (rather than free-form strings) keeps
 * Logcat filtering consistent across the codebase and makes it obvious which subsystem
 * a log line came from.
 */
enum class FeatureTag(val tag: String) {
    SYNC("HH.Sync"),
    GOALS("HH.Goals"),
    PROFILE("HH.Profile"),
    ACTIVITY("HH.Activity"),
    FOODPREFS("HH.FoodPrefs"),
    DB("HH.Db"),
    AUTH("HH.Auth")
}
