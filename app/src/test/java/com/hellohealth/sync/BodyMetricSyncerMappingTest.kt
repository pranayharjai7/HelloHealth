package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.BodyMetricEntity
import com.hellohealth.sync.BodyMetricSyncer.Companion.toDto
import com.hellohealth.sync.BodyMetricSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BodyMetricSyncer]'s DTO ⇄ entity wire mapping. push/pull need a live Supabase
 * client this codebase never fakes, so we test the deterministic surface that carries the risk: the
 * snake_case column mapping (all body-composition metrics), source, tombstone propagation, and the
 * pulled-rows-are-synced identity. Pure — no DB.
 */
class BodyMetricSyncerMappingTest {

    private val timestampMs = 1_757_000_000_000L
    private val updatedMs = 1_757_000_500_000L
    private val deletedMs = 1_757_000_900_000L

    @Test
    fun `entity to DTO to entity round-trips the full body-composition vocabulary`() {
        val entity = BodyMetricEntity(
            id = "u1|body|2026-09-30", userId = "u1", localDate = "2026-09-30",
            timestampUtcEpochMs = timestampMs, tzOffsetMinutes = 330,
            weightKg = 80.0, heightCm = 180.0, bodyFatPct = 20.0, leanMassKg = 64.0, fatMassKg = 16.0,
            bodyWaterKg = 42.0, boneMassKg = 3.2, bmr = 1780.0, bmi = 24.69, waistCm = 85.0, vo2max = 48.0,
            source = "health_connect",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("u1|body|2026-09-30", dto.id)
        assertEquals("u1", dto.user_id)
        assertEquals(80.0, dto.weight_kg!!, 0.0001)
        assertEquals(180.0, dto.height_cm!!, 0.0001)
        assertEquals(20.0, dto.body_fat_pct!!, 0.0001)
        assertEquals(3.2, dto.bone_mass_kg!!, 0.0001)
        assertEquals(24.69, dto.bmi!!, 0.0001)
        assertEquals("health_connect", dto.source)
        assertNull("live row carries no deleted_at", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals("2026-09-30", back.localDate)
        assertEquals(timestampMs, back.timestampUtcEpochMs)
        assertEquals(64.0, back.leanMassKg!!, 0.0001)
        assertEquals(16.0, back.fatMassKg!!, 0.0001)
        assertEquals(42.0, back.bodyWaterKg!!, 0.0001)
        assertEquals(1780.0, back.bmr!!, 0.0001)
        assertEquals("health_connect", back.source)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `all-null metrics round-trip as null`() {
        val entity = BodyMetricEntity(
            id = "u1|body|2026-09-29", userId = "u1", localDate = "2026-09-29",
            timestampUtcEpochMs = 2000L, tzOffsetMinutes = 0, source = "manual",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 0,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertNull(back.weightKg)
        assertNull(back.heightCm)
        assertNull(back.bodyFatPct)
        assertNull(back.leanMassKg)
        assertNull(back.bmi)
        assertNull(back.vo2max)
        assertEquals("manual", back.source)
    }

    @Test
    fun `tombstone carries deleted_at across the wire`() {
        val tombstone = BodyMetricEntity(
            id = "u1|body|2026-09-30", userId = "u1", localDate = "2026-09-30",
            timestampUtcEpochMs = timestampMs, tzOffsetMinutes = 0, source = "manual",
            updatedAtEpochMs = deletedMs, updatedAtTzOffsetMinutes = 0,
            deletedAtEpochMs = deletedMs, isSynced = false,
        )

        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at so the delete propagates", dto.deleted_at)
        assertEquals(deletedMs, dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at)).deletedAtEpochMs)
    }
}
