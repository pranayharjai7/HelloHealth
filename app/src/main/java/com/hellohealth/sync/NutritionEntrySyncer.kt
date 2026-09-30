package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.NutritionEntryDao
import com.hellohealth.data.local.entities.NutritionEntryEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `nutrition_entries` table (Supabase `nutrition_entries`, conflict key `id`) — the
 * per-user food + water log. Multi-row per user; follows the [VitalsSampleSyncer] template exactly.
 *
 * **Bidirectional**: [push] upserts local unsynced rows *including tombstones* (so a soft-delete
 * propagates), acking each at its exact version; [pull] fetches remote rows and applies each
 * [LwwResolver] winner, adopting remote tombstones as local deletes.
 *
 * Macros ride the wire in natural units (kcal, grams, ml) — no scaling on either side. The DTO
 * carries `updated_at`/`deleted_at` as ISO strings; the server's set_updated_at trigger stamps
 * `updated_at` on UPDATE, and [Timestamps.parseServerTimestamp] feeds the [LwwResolver].
 *
 * The read-only `cached_foods` catalog is NOT synced (global, per-device — like `exercises`), so it
 * has no syncer.
 */
class NutritionEntrySyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val nutritionEntryDao: NutritionEntryDao,
) : Syncer {

    @Serializable
    data class NutritionEntryDto(
        val id: String,
        val user_id: String,
        val local_date: String,
        val timestamp_utc: String,
        val tz_offset: Int,
        val kind: String,
        val meal_category: String? = null,
        val food_id: String? = null,
        val food_name: String,
        val quantity: Double,
        val unit: String,
        val calories: Double,
        val protein_g: Double? = null,
        val carbs_g: Double? = null,
        val fat_g: Double? = null,
        val fibre_g: Double? = null,
        val water_ml: Double? = null,
        val entry_method: String,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.NUTRITION

    override suspend fun push(userId: String): Int {
        val unsynced = nutritionEntryDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["nutrition_entries"].upsert(value = row.toDto(), onConflict = "id")
            nutritionEntryDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.NUTRITION, "pushed $pushed nutrition entry row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["nutrition_entries"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<NutritionEntryDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = nutritionEntryDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            nutritionEntryDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.NUTRITION, "pulled $applied remote nutrition entry row(s)")
        return applied
    }

    companion object {
        /** Entity → DTO. `internal` so unit tests can verify the wire mapping without a live client. */
        internal fun NutritionEntryEntity.toDto() = NutritionEntryDto(
            id = id,
            user_id = userId,
            local_date = localDate,
            timestamp_utc = Timestamps.epochMsToServerTimestamp(timestampUtcEpochMs),
            tz_offset = tzOffsetMinutes,
            kind = kind,
            meal_category = mealCategory,
            food_id = foodId,
            food_name = foodName,
            quantity = quantity,
            unit = unit,
            calories = calories,
            protein_g = proteinG,
            carbs_g = carbsG,
            fat_g = fatG,
            fibre_g = fibreG,
            water_ml = waterMl,
            entry_method = entryMethod,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        /** DTO → already-synced entity, carrying the resolved remote LWW clock. */
        internal fun NutritionEntryDto.toEntity(remoteUpdatedAt: Long?) = NutritionEntryEntity(
            id = id,
            userId = user_id,
            localDate = local_date,
            timestampUtcEpochMs = Timestamps.parseServerTimestamp(timestamp_utc) ?: Timestamps.nowEpochMs(),
            tzOffsetMinutes = tz_offset,
            kind = kind,
            mealCategory = meal_category,
            foodId = food_id,
            foodName = food_name,
            quantity = quantity,
            unit = unit,
            calories = calories,
            proteinG = protein_g,
            carbsG = carbs_g,
            fatG = fat_g,
            fibreG = fibre_g,
            waterMl = water_ml,
            entryMethod = entry_method,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
