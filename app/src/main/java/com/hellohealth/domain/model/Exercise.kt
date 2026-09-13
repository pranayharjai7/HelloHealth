package com.hellohealth.domain.model

/**
 * A read-only catalog exercise, seeded from the bundled `assets/exercises.json` (free-exercise-db,
 * 873 entries). Global and identical on every device — NOT user-owned and NOT synced, so it carries
 * no `userId` and none of the Syncable bookkeeping columns.
 *
 * [primaryMuscles] / [secondaryMuscles] / [instructions] are stored on the entity as JSON-encoded
 * String lists and decoded back to `List<String>` here. [gifUrl] is a remote free-exercise-db image
 * URL built at seed time (loaded on demand via Coil — no image bytes ship in the APK).
 * [youtubeQuery] is a prebuilt search string for a "how-to" link.
 */
data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val instructions: List<String>,
    val gifUrl: String,
    val youtubeQuery: String,
)
