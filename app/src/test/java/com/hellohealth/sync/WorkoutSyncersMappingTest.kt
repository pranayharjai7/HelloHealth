package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.PlannedExerciseEntity
import com.hellohealth.data.local.entities.WorkoutDayEntity
import com.hellohealth.data.local.entities.WorkoutPlanEntity
import com.hellohealth.sync.PlannedExerciseSyncer.Companion.toDto
import com.hellohealth.sync.PlannedExerciseSyncer.Companion.toEntity
import com.hellohealth.sync.WorkoutDaySyncer.Companion.toDto
import com.hellohealth.sync.WorkoutDaySyncer.Companion.toEntity
import com.hellohealth.sync.WorkoutPlanSyncer.Companion.toDto
import com.hellohealth.sync.WorkoutPlanSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the three planning syncers. The `push`/`pull` methods need a live [SupabaseClient]
 * (a final class this codebase never fakes — no mockk dependency), so — as with the other syncers —
 * we test the deterministic surface that carries all the risk: the DTO ⇄ entity wire mapping
 * (including tombstones and the full nullable target vocabulary), the round-trip identity a pull
 * relies on, and the [LwwResolver] decision the pull loop branches on. Plain JUnit; no Robolectric,
 * no DB — the mappers are pure.
 */
class WorkoutSyncersMappingTest {

    // ---- clocks: fixed epoch millis so ISO round-trips are exact ----
    private val createdMs = 1_757_000_000_000L
    private val updatedMs = 1_757_000_500_000L
    private val deletedMs = 1_757_000_900_000L

    // ------------------------------------------------------------- Plan

    @Test
    fun `plan entity to DTO to entity round-trips a live row`() {
        val entity = WorkoutPlanEntity(
            id = "p1", userId = "u1", name = "Hypertrophy", isActive = true, planType = "WEEKLY",
            createdAtEpochMs = createdMs, updatedAtEpochMs = updatedMs,
            updatedAtTzOffsetMinutes = 330, deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("p1", dto.id)
        assertEquals("u1", dto.user_id)
        assertEquals("WEEKLY", dto.plan_type)
        assertTrue(dto.is_active)
        assertNull("live row carries no deleted_at", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals("Hypertrophy", back.name)
        assertEquals("WEEKLY", back.planType)
        assertEquals(createdMs, back.createdAtEpochMs)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertNull(back.deletedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `plan tombstone carries deleted_at across the wire`() {
        val tombstone = WorkoutPlanEntity(
            id = "p1", userId = "u1", name = "Old", isActive = false, planType = "CUSTOM",
            createdAtEpochMs = createdMs, updatedAtEpochMs = deletedMs,
            updatedAtTzOffsetMinutes = 330, deletedAtEpochMs = deletedMs, isSynced = false,
        )

        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at so the delete propagates", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals(deletedMs, back.deletedAtEpochMs)
    }

    // ------------------------------------------------------------- Day

    @Test
    fun `day entity to DTO to entity round-trips`() {
        val entity = WorkoutDayEntity(
            id = "d1", planId = "p1", userId = "u1", slotKey = "MONDAY", name = "Push",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("p1", dto.plan_id)
        assertEquals("MONDAY", dto.slot_key)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals("d1", back.id)
        assertEquals("p1", back.planId)
        assertEquals("MONDAY", back.slotKey)
        assertEquals("Push", back.name)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue(back.isSynced)
    }

    // ------------------------------------------------- Planned exercise

    @Test
    fun `planned exercise round-trips the full target vocabulary`() {
        val entity = PlannedExerciseEntity(
            id = "pe1", dayId = "d1", userId = "u1", exerciseId = "bench", orderIndex = 2,
            targetSets = 5, targetReps = 8, targetWeightKg = 80f, targetDurationSeconds = 60,
            targetDistanceKm = 1.5f, targetSpeedKmh = 10f, targetIncline = 2f,
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("bench", dto.exercise_id)
        assertEquals(2, dto.order_index)
        assertEquals(5, dto.target_sets)
        assertEquals(80f, dto.target_weight_kg)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals(8, back.targetReps)
        assertEquals(80f, back.targetWeightKg)
        assertEquals(60, back.targetDurationSeconds)
        assertEquals(1.5f, back.targetDistanceKm)
        assertEquals(10f, back.targetSpeedKmh)
        assertEquals(2f, back.targetIncline)
    }

    @Test
    fun `planned exercise with all nullable targets null round-trips as null`() {
        val entity = PlannedExerciseEntity(
            id = "pe2", dayId = "d1", userId = "u1", exerciseId = "plank", orderIndex = 0,
            targetSets = 3, // only the non-null target
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val back = entity.toDto().toEntity(Timestamps.parseServerTimestamp(entity.toDto().updated_at))
        assertEquals(3, back.targetSets)
        assertNull(back.targetReps)
        assertNull(back.targetWeightKg)
        assertNull(back.targetDurationSeconds)
        assertNull(back.targetDistanceKm)
        assertNull(back.targetSpeedKmh)
        assertNull(back.targetIncline)
    }

    // --------------------------------------------------------- LWW pull

    @Test
    fun `pull applies a strictly-newer remote row and skips an older or tied one`() {
        // Remote strictly newer → REMOTE wins (apply).
        assertEquals(LwwResolver.Winner.REMOTE, LwwResolver.resolve(updatedMs, updatedMs + 1))
        // Remote older → LOCAL wins (skip).
        assertEquals(LwwResolver.Winner.LOCAL, LwwResolver.resolve(updatedMs, updatedMs - 1))
        // Exact-millis tie → LOCAL wins (skip), matching the push-before-pull contract.
        assertEquals(LwwResolver.Winner.LOCAL, LwwResolver.resolve(updatedMs, updatedMs))
    }

    @Test
    fun `server without updated_at never lets remote win - push-only lossless`() {
        // parseServerTimestamp(null) → null → LwwResolver treats it as MIN_VALUE, so local wins.
        val remoteMissing = Timestamps.parseServerTimestamp(null)
        assertEquals(LwwResolver.Winner.LOCAL, LwwResolver.resolve(updatedMs, remoteMissing))
        // ...even against a brand-new local row with no prior clock, a null remote can't win.
        assertEquals(LwwResolver.Winner.LOCAL, LwwResolver.resolve(null, remoteMissing))
    }
}
