package com.hellohealth.data.exercise

import android.content.Context
import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.data.local.entities.ExerciseEntity
import com.hellohealth.data.local.entities.toJsonString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val EXERCISE_IMAGE_BASE_URL =
    "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

/**
 * Raw shape of one record in `assets/exercises.json` (free-exercise-db). `ignoreUnknownKeys` lets us
 * name only the fields we use — the asset also carries `force`/`level`/`mechanic`, which we skip.
 * Every list defaults to empty and [equipment] is nullable so a sparse record can't fail decode.
 */
@Serializable
private data class RawExercise(
    val id: String,
    val name: String,
    val category: String,
    val equipment: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

/**
 * Loads the bundled read-only exercise catalog (873 entries) from app assets into [ExerciseEntity]
 * rows ready for Room seeding. Ported from TrackMe's loader with one hardening change: [loadExercises]
 * returns a defined result on failure ([Result]) rather than throwing, so a corrupt/absent asset
 * degrades to "catalog unavailable" instead of crashing app start (HelloHealth no-crash guarantee).
 *
 * Only the ~1 MB JSON is bundled; images load remotely via Coil from the GitHub raw URL built here
 * ([EXERCISE_IMAGE_BASE_URL] + first image path), so the catalog adds zero image weight to the APK.
 */
@Singleton
open class ExerciseAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parse the asset. On any failure (missing asset, malformed JSON) returns [Result.failure] after
     * logging — the caller leaves the table empty so the next launch retries. Never throws.
     *
     * `open` so unit tests can substitute a success/failure result without touching the filesystem.
     */
    open fun loadExercises(): Result<List<ExerciseEntity>> = runCatching {
        val raw = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
        json.decodeFromString<List<RawExercise>>(raw).map { it.toEntity() }
    }.onFailure { t ->
        AppLogger.e(FeatureTag.EXERCISE_CATALOG, "failed to load exercises.json; catalog stays empty", t)
    }

    private fun RawExercise.toEntity(): ExerciseEntity {
        val gifUrl = images.firstOrNull()?.let { "$EXERCISE_IMAGE_BASE_URL$it" } ?: ""
        return ExerciseEntity(
            id = id,
            name = name,
            category = category,
            primaryMuscles = primaryMuscles.toJsonString(),
            secondaryMuscles = secondaryMuscles.toJsonString(),
            equipment = equipment ?: "none",
            instructions = instructions.toJsonString(),
            gifUrl = gifUrl,
            youtubeQuery = "$name tutorial",
        )
    }
}
