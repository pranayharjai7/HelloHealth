package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.WorkoutSessionEntity
import com.hellohealth.sync.WorkoutSessionSyncer.Companion.toDto
import com.hellohealth.sync.WorkoutSessionSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WorkoutSessionSyncer]'s DTO ⇄ entity wire mapping. push/pull need a live Supabase
 * client this codebase never fakes, so we test the deterministic surface that carries the risk: the
 * snake_case column mapping, the nullable plan/day anchors + end/duration, tombstone propagation, and
 * the pulled-rows-are-synced identity. Pure — no DB.
 */
class WorkoutSessionSyncerMappingTest {

    private val startMs = 1_760_000_000_000L
    private val endMs = 1_760_003_600_000L
    private val updatedMs = 1_760_003_600_000L
    private val deletedMs = 1_760_004_000_000L

    @Test
    fun `entity to DTO to entity round-trips a completed session`() {
        val entity = WorkoutSessionEntity(
            id = "s1", userId = "u1", planId = "p1", dayId = "d1", title = "Push day",
            activityType = "strength_training", startEpochMs = startMs, endEpochMs = endMs,
            durationSeconds = 3600, status = "completed", localDate = "2026-10-08", note = "strong",
            totalVolumeKg = 4200.0, caloriesEstimate = 320.0,
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("s1", dto.id)
        assertEquals("u1", dto.user_id)
        assertEquals("p1", dto.plan_id)
        assertEquals("d1", dto.day_id)
        assertEquals("strength_training", dto.activity_type)
        assertEquals(3600, dto.duration_seconds)
        assertEquals("completed", dto.status)
        assertEquals(4200.0, dto.total_volume_kg!!, 0.0001)
        assertNull("live row carries no deleted_at", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals("2026-10-08", back.localDate)
        assertEquals(startMs, back.startEpochMs)
        assertEquals(endMs, back.endEpochMs)
        assertEquals("Push day", back.title)
        assertEquals(320.0, back.caloriesEstimate!!, 0.0001)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `an ad-hoc active session round-trips with null plan, day, end and duration`() {
        val entity = WorkoutSessionEntity(
            id = "s2", userId = "u1", activityType = "running", startEpochMs = startMs,
            status = "active", localDate = "2026-10-08",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 0,
        )

        val dto = entity.toDto()
        assertNull(dto.plan_id)
        assertNull(dto.day_id)
        assertNull(dto.end_utc)
        assertNull(dto.duration_seconds)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertNull(back.planId)
        assertNull(back.endEpochMs)
        assertEquals("active", back.status)
        assertEquals("running", back.activityType)
    }

    @Test
    fun `tombstone carries deleted_at across the wire`() {
        val tombstone = WorkoutSessionEntity(
            id = "s1", userId = "u1", activityType = "running", startEpochMs = startMs,
            status = "abandoned", localDate = "2026-10-08",
            updatedAtEpochMs = deletedMs, updatedAtTzOffsetMinutes = 0, deletedAtEpochMs = deletedMs,
        )

        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at so the delete propagates", dto.deleted_at)
        assertEquals(deletedMs, dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at)).deletedAtEpochMs)
    }
}
