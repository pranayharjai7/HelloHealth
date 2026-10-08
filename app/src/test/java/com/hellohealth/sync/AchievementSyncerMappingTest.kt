package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.AchievementEntity
import com.hellohealth.sync.AchievementSyncer.Companion.toDto
import com.hellohealth.sync.AchievementSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AchievementSyncer]'s DTO ⇄ entity wire mapping — snake_case columns, the
 * unlocked_at ISO round-trip, tombstone propagation, pulled-rows-are-synced. Pure — no DB.
 */
class AchievementSyncerMappingTest {

    private val unlockedMs = 1_760_000_100_000L
    private val updatedMs = 1_760_000_200_000L
    private val deletedMs = 1_760_000_900_000L

    @Test
    fun `entity to DTO to entity round-trips an earned achievement`() {
        val entity = AchievementEntity(
            id = "u1|ach|first_workout", userId = "u1", code = "first_workout",
            unlockedAtEpochMs = unlockedMs,
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("u1|ach|first_workout", dto.id)
        assertEquals("first_workout", dto.code)
        assertNull(dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals("first_workout", back.code)
        assertEquals("unlocked_at round-trips to the same epoch", unlockedMs, back.unlockedAtEpochMs)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `tombstone carries deleted_at across the wire`() {
        val tombstone = AchievementEntity(
            id = "u1|ach|first_workout", userId = "u1", code = "first_workout",
            unlockedAtEpochMs = unlockedMs,
            updatedAtEpochMs = deletedMs, updatedAtTzOffsetMinutes = 0, deletedAtEpochMs = deletedMs,
        )
        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at", dto.deleted_at)
        assertEquals(deletedMs, dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at)).deletedAtEpochMs)
    }
}
