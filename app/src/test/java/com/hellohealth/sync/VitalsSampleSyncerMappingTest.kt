package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.VitalsSampleEntity
import com.hellohealth.sync.VitalsSampleSyncer.Companion.toDto
import com.hellohealth.sync.VitalsSampleSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [VitalsSampleSyncer]'s DTO ⇄ entity wire mapping. As with the other syncers, push/pull
 * need a live [io.github.jan.supabase.SupabaseClient] this codebase never fakes, so we test the
 * deterministic surface that carries the risk: the snake_case column mapping (all six nullable vitals
 * + sleep ints), tombstone propagation, and the pulled-rows-are-synced identity. Pure — no DB.
 */
class VitalsSampleSyncerMappingTest {

    private val timestampMs = 1_757_000_000_000L
    private val updatedMs = 1_757_000_500_000L
    private val deletedMs = 1_757_000_900_000L

    @Test
    fun `rollup entity to DTO to entity round-trips the full vitals vocabulary`() {
        val entity = VitalsSampleEntity(
            id = "u1|rollup|2026-09-30", userId = "u1", localDate = "2026-09-30",
            timestampUtcEpochMs = timestampMs, tzOffsetMinutes = 330, kind = "rollup",
            restingHeartRate = 58.0, hrvRmssd = 65.5, respiratoryRate = 14.2,
            bodyTemperature = 36.6, hydrationMl = 750.0, spo2 = 98.0,
            sleepDurationMinutes = 420, deepSleepMinutes = 90,
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("u1|rollup|2026-09-30", dto.id)
        assertEquals("u1", dto.user_id)
        assertEquals("rollup", dto.kind)
        assertEquals(58.0, dto.resting_heart_rate!!, 0.0001)
        assertEquals(98.0, dto.spo2!!, 0.0001) // stays 0-100, not a fraction
        assertEquals(420, dto.sleep_duration_minutes)
        assertNull("live row carries no deleted_at", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals("2026-09-30", back.localDate)
        assertEquals(timestampMs, back.timestampUtcEpochMs)
        assertEquals(65.5, back.hrvRmssd!!, 0.0001)
        assertEquals(14.2, back.respiratoryRate!!, 0.0001)
        assertEquals(36.6, back.bodyTemperature!!, 0.0001)
        assertEquals(750.0, back.hydrationMl!!, 0.0001)
        assertEquals(90, back.deepSleepMinutes)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `all-null vitals round-trip as null`() {
        val entity = VitalsSampleEntity(
            id = "u1|sample|2000", userId = "u1", localDate = "2026-09-30",
            timestampUtcEpochMs = 2000L, tzOffsetMinutes = 0, kind = "sample",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 0,
            deletedAtEpochMs = null, isSynced = false,
        )

        val back = entity.toDto().toEntity(Timestamps.parseServerTimestamp(entity.toDto().updated_at))
        assertNull(back.restingHeartRate)
        assertNull(back.hrvRmssd)
        assertNull(back.respiratoryRate)
        assertNull(back.bodyTemperature)
        assertNull(back.hydrationMl)
        assertNull(back.spo2)
        assertNull(back.sleepDurationMinutes)
        assertNull(back.deepSleepMinutes)
    }

    @Test
    fun `tombstone carries deleted_at across the wire`() {
        val tombstone = VitalsSampleEntity(
            id = "u1|rollup|2026-09-30", userId = "u1", localDate = "2026-09-30",
            timestampUtcEpochMs = timestampMs, tzOffsetMinutes = 0, kind = "rollup",
            updatedAtEpochMs = deletedMs, updatedAtTzOffsetMinutes = 0,
            deletedAtEpochMs = deletedMs, isSynced = false,
        )

        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at so the delete propagates", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals(deletedMs, back.deletedAtEpochMs)
    }
}
