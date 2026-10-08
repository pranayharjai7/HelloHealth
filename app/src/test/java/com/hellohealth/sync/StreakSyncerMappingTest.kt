package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.StreakEntity
import com.hellohealth.sync.StreakSyncer.Companion.toDto
import com.hellohealth.sync.StreakSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StreakSyncer]'s DTO ⇄ entity wire mapping — snake_case columns, nullable
 * last-hit-date, tombstone propagation, pulled-rows-are-synced. Pure — no DB.
 */
class StreakSyncerMappingTest {

    private val updatedMs = 1_760_000_200_000L
    private val deletedMs = 1_760_000_900_000L

    @Test
    fun `entity to DTO to entity round-trips a pillar streak`() {
        val entity = StreakEntity(
            id = "u1|streak|activity", userId = "u1", pillar = "activity",
            currentCount = 5, longestCount = 9, lastHitLocalDate = "2026-10-07",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("u1|streak|activity", dto.id)
        assertEquals("activity", dto.pillar)
        assertEquals(5, dto.current_count)
        assertEquals(9, dto.longest_count)
        assertEquals("2026-10-07", dto.last_hit_local_date)
        assertNull("live row carries no deleted_at", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals(5, back.currentCount)
        assertEquals(9, back.longestCount)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `a balanced streak with no last-hit round-trips with null`() {
        val entity = StreakEntity(
            id = "u1|streak|balanced", userId = "u1", pillar = "balanced",
            currentCount = 0, longestCount = 0, lastHitLocalDate = null,
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 0,
        )
        val dto = entity.toDto()
        assertNull(dto.last_hit_local_date)
        assertNull(dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at)).lastHitLocalDate)
    }

    @Test
    fun `tombstone carries deleted_at across the wire`() {
        val tombstone = StreakEntity(
            id = "u1|streak|activity", userId = "u1", pillar = "activity",
            currentCount = 0, longestCount = 9, lastHitLocalDate = null,
            updatedAtEpochMs = deletedMs, updatedAtTzOffsetMinutes = 0, deletedAtEpochMs = deletedMs,
        )
        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at", dto.deleted_at)
        assertEquals(deletedMs, dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at)).deletedAtEpochMs)
    }
}
