package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for [WorkoutSessionSyncer] — the DTO⇄entity round-trip and the LWW decision.
 * Mirrors the SnapshotSyncerTest approach of exercising the syncer's testable logic without a live
 * Postgrest client (the push/pull wire calls are covered by on-device QA).
 */
class WorkoutSessionSyncerTest {

    private fun entity(
        id: String = "w1",
        calories: Double? = 320.0,
        distanceKm: Double? = 8.2,
        note: String? = "felt great",
        deletedAtEpochMs: Long? = null,
        updatedAtEpochMs: Long = 2_800_000L
    ) = WorkoutSessionEntity(
        id = id,
        userId = "u1",
        activityType = "RUN",
        title = "Morning run",
        startTimeUtcEpochMs = 1_000L,
        endTimeUtcEpochMs = 2_760_000L,
        durationMinutes = 46L,
        calories = calories,
        distanceKm = distanceKm,
        note = note,
        localDate = "2026-09-13",
        updatedAtEpochMs = updatedAtEpochMs,
        updatedAtTzOffsetMinutes = 330,
        deletedAtEpochMs = deletedAtEpochMs,
        isSynced = false
    )

    @Test
    fun `entity to dto to entity round-trips all fields`() {
        val original = entity()
        val dto = WorkoutSessionSyncer.toDto(original)

        assertEquals("RUN", dto.activity_type)
        assertEquals(46L, dto.duration_minutes)
        assertEquals(320.0, dto.calories!!, 0.0001)
        assertEquals("2026-09-13", dto.local_date)

        val remoteUpdatedAt = Timestamps.parseServerTimestamp(dto.updated_at)
        val back = WorkoutSessionSyncer.toEntity(dto, remoteUpdatedAt)

        assertEquals(original.id, back.id)
        assertEquals(original.activityType, back.activityType)
        assertEquals(original.startTimeUtcEpochMs, back.startTimeUtcEpochMs)
        assertEquals(original.endTimeUtcEpochMs, back.endTimeUtcEpochMs)
        assertEquals(original.durationMinutes, back.durationMinutes)
        assertEquals(original.calories!!, back.calories!!, 0.0001)
        assertEquals(original.distanceKm!!, back.distanceKm!!, 0.0001)
        assertEquals(original.note, back.note)
        assertEquals(original.updatedAtEpochMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are marked synced", back.isSynced)
    }

    @Test
    fun `null optionals round-trip as null`() {
        val dto = WorkoutSessionSyncer.toDto(entity(calories = null, distanceKm = null, note = null))
        assertNull(dto.calories)
        assertNull(dto.distance_km)
        assertNull(dto.note)

        val back = WorkoutSessionSyncer.toEntity(dto, Timestamps.parseServerTimestamp(dto.updated_at))
        assertNull(back.calories)
        assertNull(back.distanceKm)
        assertNull(back.note)
    }

    @Test
    fun `tombstone carries deleted_at through the round-trip`() {
        val dto = WorkoutSessionSyncer.toDto(entity(deletedAtEpochMs = 3_000_000L))
        val back = WorkoutSessionSyncer.toEntity(dto, Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals(3_000_000L, back.deletedAtEpochMs)
    }

    @Test
    fun `pre-DDL server row degrades push-only losslessly - null updated_at never wins`() {
        // A server that predates the updated_at/deleted_at columns omits them -> parses to null.
        val remoteUpdatedAt = Timestamps.parseServerTimestamp(null)
        assertNull(remoteUpdatedAt)
        // Local exists with any timestamp -> LOCAL wins, so pull would skip (remote never clobbers).
        assertEquals(
            LwwResolver.Winner.LOCAL,
            LwwResolver.resolve(localUpdatedAtEpochMs = 100L, remoteUpdatedAtEpochMs = remoteUpdatedAt)
        )
    }

    @Test
    fun `strictly newer remote wins - exact tie stays local`() {
        assertEquals(
            LwwResolver.Winner.REMOTE,
            LwwResolver.resolve(localUpdatedAtEpochMs = 100L, remoteUpdatedAtEpochMs = 200L)
        )
        assertEquals(
            "exact-millis tie -> LOCAL (push runs before pull)",
            LwwResolver.Winner.LOCAL,
            LwwResolver.resolve(localUpdatedAtEpochMs = 200L, remoteUpdatedAtEpochMs = 200L)
        )
    }
}
