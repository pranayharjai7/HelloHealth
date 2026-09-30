package com.hellohealth.sync

import com.hellohealth.core.logging.AppLogger
import com.hellohealth.core.logging.FeatureTag
import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.dao.VitalsSampleDao
import com.hellohealth.data.local.entities.VitalsSampleEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Syncs the `vitals_samples` table (Supabase `vitals_samples`, conflict key `id`) — the P3 daily
 * vitals rollup (and reserved intraday samples). Multi-row per user; follows the [WorkoutPlanSyncer]
 * template exactly.
 *
 * **Bidirectional**: [push] upserts local unsynced rows *including tombstones* (so a soft-delete
 * propagates), acking each at its exact version; [pull] fetches remote rows and applies each
 * [LwwResolver] winner, adopting remote tombstones as local deletes.
 *
 * All vitals ride the wire in natural human units (bpm, ms, breaths/min, °C, ml, SpO2 0-100) — no
 * scaling on either side. The DTO carries `updated_at`/`deleted_at` as ISO strings; the server's
 * set_updated_at trigger stamps `updated_at` on UPDATE, and [Timestamps.parseServerTimestamp]
 * feeds the [LwwResolver].
 */
class VitalsSampleSyncer @Inject constructor(
    private val supabase: SupabaseClient,
    private val vitalsSampleDao: VitalsSampleDao,
) : Syncer {

    @Serializable
    data class VitalsSampleDto(
        val id: String,
        val user_id: String,
        val local_date: String,
        val timestamp_utc: String,
        val tz_offset: Int,
        val kind: String,
        val resting_heart_rate: Double? = null,
        val hrv_rmssd: Double? = null,
        val respiratory_rate: Double? = null,
        val body_temperature: Double? = null,
        val hydration_ml: Double? = null,
        val spo2: Double? = null,
        val sleep_duration_minutes: Int? = null,
        val deep_sleep_minutes: Int? = null,
        val updated_at: String? = null,
        val deleted_at: String? = null,
    )

    override val featureTag = FeatureTag.VITALS

    override suspend fun push(userId: String): Int {
        val unsynced = vitalsSampleDao.getUnsynced().filter { it.userId == userId }
        if (unsynced.isEmpty()) return 0

        var pushed = 0
        for (row in unsynced) {
            supabase.postgrest["vitals_samples"].upsert(value = row.toDto(), onConflict = "id")
            vitalsSampleDao.markSynced(row.id, row.updatedAtEpochMs)
            pushed++
        }
        AppLogger.d(FeatureTag.VITALS, "pushed $pushed vitals sample row(s)")
        return pushed
    }

    override suspend fun pull(userId: String): Int {
        val remote = supabase.postgrest["vitals_samples"]
            .select { filter { eq("user_id", userId) } }
            .decodeList<VitalsSampleDto>()
        if (remote.isEmpty()) return 0

        var applied = 0
        for (dto in remote) {
            val local = vitalsSampleDao.getById(dto.id)
            val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
            if (LwwResolver.resolve(local?.updatedAtEpochMs, remoteUpdatedAt) == LwwResolver.Winner.LOCAL) {
                continue
            }
            vitalsSampleDao.upsert(dto.toEntity(remoteUpdatedAt))
            applied++
        }
        AppLogger.d(FeatureTag.VITALS, "pulled $applied remote vitals sample row(s)")
        return applied
    }

    companion object {
        /** Entity → DTO. `internal` so unit tests can verify the wire mapping without a live client. */
        internal fun VitalsSampleEntity.toDto() = VitalsSampleDto(
            id = id,
            user_id = userId,
            local_date = localDate,
            timestamp_utc = Timestamps.epochMsToServerTimestamp(timestampUtcEpochMs),
            tz_offset = tzOffsetMinutes,
            kind = kind,
            resting_heart_rate = restingHeartRate,
            hrv_rmssd = hrvRmssd,
            respiratory_rate = respiratoryRate,
            body_temperature = bodyTemperature,
            hydration_ml = hydrationMl,
            spo2 = spo2,
            sleep_duration_minutes = sleepDurationMinutes,
            deep_sleep_minutes = deepSleepMinutes,
            updated_at = Timestamps.epochMsToServerTimestamp(updatedAtEpochMs),
            deleted_at = deletedAtEpochMs?.let { Timestamps.epochMsToServerTimestamp(it) },
        )

        /** DTO → already-synced entity, carrying the resolved remote LWW clock. */
        internal fun VitalsSampleDto.toEntity(remoteUpdatedAt: Long?) = VitalsSampleEntity(
            id = id,
            userId = user_id,
            localDate = local_date,
            timestampUtcEpochMs = Timestamps.parseServerTimestamp(timestamp_utc) ?: Timestamps.nowEpochMs(),
            tzOffsetMinutes = tz_offset,
            kind = kind,
            restingHeartRate = resting_heart_rate,
            hrvRmssd = hrv_rmssd,
            respiratoryRate = respiratory_rate,
            bodyTemperature = body_temperature,
            hydrationMl = hydration_ml,
            spo2 = spo2,
            sleepDurationMinutes = sleep_duration_minutes,
            deepSleepMinutes = deep_sleep_minutes,
            updatedAtEpochMs = remoteUpdatedAt ?: Timestamps.nowEpochMs(),
            updatedAtTzOffsetMinutes = Timestamps.currentTzOffsetMinutes(),
            deletedAtEpochMs = Timestamps.parseServerTimestamp(deleted_at),
            isSynced = true,
        )
    }
}
