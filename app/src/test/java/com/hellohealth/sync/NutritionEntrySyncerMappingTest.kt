package com.hellohealth.sync

import com.hellohealth.core.time.Timestamps
import com.hellohealth.data.local.entities.NutritionEntryEntity
import com.hellohealth.sync.NutritionEntrySyncer.Companion.toDto
import com.hellohealth.sync.NutritionEntrySyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [NutritionEntrySyncer]'s DTO ⇄ entity wire mapping. As with the other syncers,
 * push/pull need a live [io.github.jan.supabase.SupabaseClient] this codebase never fakes, so we
 * test the deterministic surface that carries the risk: the snake_case column mapping (all four
 * nullable macros + the food/water discriminator fields), tombstone propagation, and the
 * pulled-rows-are-synced identity. Pure — no DB.
 */
class NutritionEntrySyncerMappingTest {

    private val timestampMs = 1_757_000_000_000L
    private val updatedMs = 1_757_000_500_000L
    private val deletedMs = 1_757_000_900_000L

    @Test
    fun `food entity to DTO to entity round-trips the full macro vocabulary`() {
        val entity = NutritionEntryEntity(
            id = "n1", userId = "u1", localDate = "2026-09-30",
            timestampUtcEpochMs = timestampMs, tzOffsetMinutes = 330, kind = "food",
            mealCategory = "breakfast", foodId = "seed:oats", foodName = "Oats",
            quantity = 100.0, unit = "g", calories = 389.0,
            proteinG = 16.9, carbsG = 66.3, fatG = 6.9, fibreG = 10.6, waterMl = null,
            entryMethod = "catalog",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("n1", dto.id)
        assertEquals("u1", dto.user_id)
        assertEquals("food", dto.kind)
        assertEquals("breakfast", dto.meal_category)
        assertEquals("seed:oats", dto.food_id)
        assertEquals("Oats", dto.food_name)
        assertEquals(100.0, dto.quantity, 0.0001)
        assertEquals(389.0, dto.calories, 0.0001)
        assertEquals(16.9, dto.protein_g!!, 0.0001)
        assertNull("food row carries no water_ml", dto.water_ml)
        assertNull("live row carries no deleted_at", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals("2026-09-30", back.localDate)
        assertEquals(timestampMs, back.timestampUtcEpochMs)
        assertEquals(66.3, back.carbsG!!, 0.0001)
        assertEquals(6.9, back.fatG!!, 0.0001)
        assertEquals(10.6, back.fibreG!!, 0.0001)
        assertEquals("catalog", back.entryMethod)
        assertEquals(updatedMs, back.updatedAtEpochMs)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `water row round-trips with null macros and set water_ml`() {
        val entity = NutritionEntryEntity(
            id = "n2", userId = "u1", localDate = "2026-09-30",
            timestampUtcEpochMs = 2000L, tzOffsetMinutes = 0, kind = "water",
            mealCategory = null, foodId = null, foodName = "Water",
            quantity = 1.0, unit = "glass", calories = 0.0,
            proteinG = null, carbsG = null, fatG = null, fibreG = null, waterMl = 250.0,
            entryMethod = "water",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 0,
            deletedAtEpochMs = null, isSynced = false,
        )

        val back = entity.toDto().toEntity(Timestamps.parseServerTimestamp(entity.toDto().updated_at))
        assertEquals("water", back.kind)
        assertNull(back.mealCategory)
        assertNull(back.proteinG)
        assertNull(back.carbsG)
        assertNull(back.fatG)
        assertNull(back.fibreG)
        assertEquals(250.0, back.waterMl!!, 0.0001)
    }

    @Test
    fun `tombstone carries deleted_at across the wire`() {
        val tombstone = NutritionEntryEntity(
            id = "n1", userId = "u1", localDate = "2026-09-30",
            timestampUtcEpochMs = timestampMs, tzOffsetMinutes = 0, kind = "food",
            mealCategory = "lunch", foodId = null, foodName = "Sandwich",
            quantity = 1.0, unit = "serving", calories = 350.0,
            entryMethod = "quick_add",
            updatedAtEpochMs = deletedMs, updatedAtTzOffsetMinutes = 0,
            deletedAtEpochMs = deletedMs, isSynced = false,
        )

        val dto = tombstone.toDto()
        assertNotNull("tombstone must push its deleted_at so the delete propagates", dto.deleted_at)

        val back = dto.toEntity(Timestamps.parseServerTimestamp(dto.updated_at))
        assertEquals(deletedMs, back.deletedAtEpochMs)
    }
}
