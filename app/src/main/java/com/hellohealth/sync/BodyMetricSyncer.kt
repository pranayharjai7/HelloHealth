package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.BodyMetricDao
import com.hellohealth.data.local.entities.BodyMetricEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `body_metrics` table (Supabase `body_metrics`, conflict key `id`) — the per-day body
 * composition history. Multi-row per user; follows the [VitalsSampleSyncer]/[WorkoutPlanSyncer]
 * template exactly. Bidirectional: [push] upserts unsynced rows incl. tombstones; [pull] applies each
 * [LwwResolver] winner. Masses ride the wire in kg, height in cm, body fat 0-100 — no scaling.
 */
class BodyMetricSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val bodyMetricDao: BodyMetricDao,
) : Syncer {

    @Serializable
    data class BodyMetricDto(
        val id: String,
        val user_id: String,
        val local_date: String,
        val timestamp_utc: String,
        val tz_offset: Int,
        val weight_kg: Double? = null,
        val height_cm: Double? = null,
        val body_fat_pct: Double? = null,
        val lean_mass_kg: Double? = null,
        val fat_mass_kg: Double? = null,
        val body_water_kg: Double? = null,
        val bone_mass_kg: Double? = null,
        val bmr: Double? = null,
        val bmi: Double? = null,
        val waist_cm: Double? = null,
        val vo2max: Double? = null,
        val source: String,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.BODY_METRICS

    override suspend fun push(userId: String): Int {
        val unsynced = bodyMetricDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["body_metrics"].upsert(value = row.toDto(), onConflict = "id")
            bodyMetricDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.BODY_METRICS, "pushed $pushed body-metric row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["body_metrics"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<BodyMetricDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = bodyMetricDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            bodyMetricDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.BODY_METRICS, "pulled $applied remote body-metric row(s)")
        return applied
    }

    companion object {
        /** Entity → DTO. `internal` so unit tests can verify the wire mapping without a live client. */
        internal fun BodyMetricEntity.toDto() = BodyMetricDto(
            id = id,
            user_id = userId,
            local_date = localDate,
            timestamp_utc = Timestamps.epochMsToServerTimestamp(timestampUtcEpochMs),
            tz_offset = tzOffsetMinutes,
            weight_kg = weightKg,
            height_cm = heightCm,
            body_fat_pct = bodyFatPct,
            lean_mass_kg = leanMassKg,
            fat_mass_kg = fatMassKg,
            body_water_kg = bodyWaterKg,
            bone_mass_kg = boneMassKg,
            bmr = bmr,
            bmi = bmi,
            waist_cm = waistCm,
            vo2max = vo2max,
            source = source,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        /** DTO → already-synced entity, carrying the resolved remote LWW clock. */
        internal fun BodyMetricDto.toEntity(remoteUpdatedAt: Long?) = BodyMetricEntity(
            id = id,
            userId = user_id,
            localDate = local_date,
            timestampUtcEpochMs = Timestamps.parseServerTimestamp(timestamp_utc) ?: Timestamps.nowEpochMs(),
            tzOffsetMinutes = tz_offset,
            weightKg = weight_kg,
            heightCm = height_cm,
            bodyFatPct = body_fat_pct,
            leanMassKg = lean_mass_kg,
            fatMassKg = fat_mass_kg,
            bodyWaterKg = body_water_kg,
            boneMassKg = bone_mass_kg,
            bmr = bmr,
            bmi = bmi,
            waistCm = waist_cm,
            vo2max = vo2max,
            source = source,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
