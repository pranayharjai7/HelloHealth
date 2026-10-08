package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.PointsLedgerEntity
import com.hellohealth.sync.PointsLedgerSyncer.Companion.toDto
import com.hellohealth.sync.PointsLedgerSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PointsLedgerSyncer]'s DTO ⇄ entity wire mapping — snake_case columns, tombstone
 * propagation, pulled-rows-are-synced. Pure — no DB.
 */
class PointsLedgerSyncerMappingTest {

    private val updatedMs = 1_760_000_200_000L
    private val deletedMs = 1_760_000_900_000L

    @Test
    fun `entity to DTO to entity round-trips a ledger award`() {
        val entity = PointsLedgerEntity(
            id = "u1|pts|2026-10-07|activity", userId = "u1", localDate = "2026-10-07",
            source = "activity", points = 30,
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("u1|pts|2026-10-07|activity", dto.id)
        assertEquals("2026-10-07", dto.local_date)
        assertEquals("activity", dto.source)
        assertEquals(30, dto.points)
        assertNull(dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals(30, back.points)
        assertEquals("activity", back.source)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `tombstone carries deleted_at across the wire`() {
        val tombstone = PointsLedgerEntity(
            id = "u1|pts|2026-10-07|activity", userId = "u1", localDate = "2026-10-07",
            source = "activity", points = 30,
            updatedAtEpochMs = deletedMs, updatedAtTzOffsetMinutes = 0, deletedAtEpochMs = deletedMs,
        )
        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at", dto.deleted_at)
        assertEquals(deletedMs, dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at)).deletedAtEpochMs)
    }
}
