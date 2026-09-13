package com.hellohealth.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hellohealth.domain.model.Exercise
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room mirror of the read-only global exercise catalog (873 entries from free-exercise-db, seeded
 * from `assets/exercises.json`). Global and identical on every device — NOT user-owned and NOT
 * synced, so it carries NO `userId` and NONE of the [com.hellohealth.data.local.Syncable] columns,
 * and there is no Supabase `exercises` table / syncer.
 *
 * List-valued fields ([primaryMuscles] / [secondaryMuscles] / [instructions]) are stored as
 * JSON-encoded String columns and decoded in [toDomain]. Ported from TrackMe's ExerciseEntity;
 * decode is made defensive here (parse failure → empty list) so a malformed cell can never crash a
 * read — consistent with HelloHealth's no-crash guarantee.
 */
@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["name"], name = "idx_exercises_name"),
        Index(value = ["category"], name = "idx_exercises_category"),
    ],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val primaryMuscles: String,      // JSON array stored as String
    val secondaryMuscles: String,    // JSON array stored as String
    val equipment: String,
    val instructions: String,        // JSON array stored as String
    val gifUrl: String,
    val youtubeQuery: String,
) {
    fun toDomain(): Exercise = Exercise(
        id = id, name = name, category = category,
        primaryMuscles = primaryMuscles.parseJsonStringList(),
        secondaryMuscles = secondaryMuscles.parseJsonStringList(),
        equipment = equipment,
        instructions = instructions.parseJsonStringList(),
        gifUrl = gifUrl,
        youtubeQuery = youtubeQuery,
    )
}

private val entityJson = Json { ignoreUnknownKeys = true }

/** Decode a JSON string array, returning an empty list on any malformed/blank input (never throws). */
fun String.parseJsonStringList(): List<String> =
    runCatching { entityJson.decodeFromString(ListSerializer(String.serializer()), this) }
        .getOrDefault(emptyList())

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id, name = name, category = category,
    primaryMuscles = primaryMuscles.toJsonString(),
    secondaryMuscles = secondaryMuscles.toJsonString(),
    equipment = equipment,
    instructions = instructions.toJsonString(),
    gifUrl = gifUrl,
    youtubeQuery = youtubeQuery,
)

fun List<String>.toJsonString(): String =
    entityJson.encodeToString(ListSerializer(String.serializer()), this)
