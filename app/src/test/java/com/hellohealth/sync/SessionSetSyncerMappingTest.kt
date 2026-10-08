package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.SessionSetEntity
import com.hellohealth.sync.SessionSetSyncer.Companion.toDto
import com.hellohealth.sync.SessionSetSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SessionSetSyncer]'s DTO ⇄ entity wire mapping. Covers the snake_case column mapping
 * of the full measurement vocabulary, the boolean set-state flags, the nullable planned-exercise link,
 * tombstone propagation, and the pulled-rows-are-synced identity. Pure — no DB.
 */
class SessionSetSyncerMappingTest {

    private val loggedMs = 1_760_000_100_000L
    private val updatedMs = 1_760_000_200_000L
    private val deletedMs = 1_760_000_900_000L

    @Test
    fun `entity to DTO to entity round-trips a completed working set`() {
        val entity = SessionSetEntity(
            id = "set1", sessionId = "s1", userId = "u1", plannedExerciseId = "pe1", exerciseId = "ex1",
            orderIndex = 0, setNumber = 1, reps = 8, weightKg = 100.0, durationSeconds = null,
            distanceKm = null, rpe = 7.5, isWarmup = false, isCompleted = true, isSkipped = false,
            loggedAtEpochMs = loggedMs,
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("set1", dto.id)
        assertEquals("s1", dto.session_id)
        assertEquals("pe1", dto.planned_exercise_id)
        assertEquals("ex1", dto.exercise_id)
        assertEquals(8, dto.reps)
        assertEquals(100.0, dto.weight_kg!!, 0.0001)
        assertEquals(7.5, dto.rpe!!, 0.0001)
        assertTrue(dto.is_completed)
        assertFalse(dto.is_skipped)
        assertNull("live row carries no deleted_at", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals("ex1", back.exerciseId)
        assertEquals(1, back.setNumber)
        assertEquals(100.0, back.weightKg!!, 0.0001)
        assertEquals(loggedMs, back.loggedAtEpochMs)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `a skipped cardio set round-trips with null strength fields and the right flags`() {
        val entity = SessionSetEntity(
            id = "set2", sessionId = "s1", userId = "u1", exerciseId = "ex2",
            orderIndex = 1, setNumber = 1, distanceKm = 5.0, durationSeconds = 1500,
            isWarmup = true, isCompleted = false, isSkipped = true, loggedAtEpochMs = loggedMs,
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 0,
        )

        val dto = entity.toDto()
        assertNull(dto.planned_exercise_id)
        assertNull(dto.reps)
        assertNull(dto.weight_kg)
        assertEquals(5.0, dto.distance_km!!, 0.0001)
        assertTrue(dto.is_warmup)
        assertTrue(dto.is_skipped)
        assertFalse(dto.is_completed)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertNull(back.reps)
        assertEquals(1500, back.durationSeconds)
        assertTrue(back.isSkipped)
    }

    @Test
    fun `tombstone carries deleted_at across the wire`() {
        val tombstone = SessionSetEntity(
            id = "set1", sessionId = "s1", userId = "u1", exerciseId = "ex1",
            orderIndex = 0, setNumber = 1, loggedAtEpochMs = loggedMs,
            updatedAtEpochMs = deletedMs, updatedAtTzOffsetMinutes = 0, deletedAtEpochMs = deletedMs,
        )

        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at so the delete propagates", dto.deleted_at)
        assertEquals(deletedMs, dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at)).deletedAtEpochMs)
    }
}
