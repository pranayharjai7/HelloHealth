package com.hellohealth.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room type converters for the small number of non-primitive columns in the sync schema.
 *
 * We deliberately keep converters limited to `List<String>` (food preference lists). The
 * daily-snapshot payload is stored as an already-serialized JSON `String` column produced
 * by the repository/syncer layer (which owns the DTO), so it needs no converter here — the
 * entity just holds the raw JSON.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        json.encodeToString(ListSerializer(String.serializer()), value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        }.getOrDefault(emptyList())

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
