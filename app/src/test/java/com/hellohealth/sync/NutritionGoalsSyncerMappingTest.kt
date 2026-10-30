package com.hellohealth.sync

import com.hellohealth.data.local.entities.NutritionGoalsEntity
import com.hellohealth.data.repository.NutritionGoalsRepositoryImpl
import com.hellohealth.domain.health.MicronutrientTargets.Micro
import com.hellohealth.sync.NutritionGoalsSyncer.Companion.toDto
import com.hellohealth.sync.NutritionGoalsSyncer.Companion.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DTO ⇄ entity wire mapping for [NutritionGoalsSyncer] (snake_case columns, nullable overrides, CSV
 * micros, bool flags) + the repository's defensive micro-CSV parsing. Pure — no DB/Supabase.
 */
class NutritionGoalsSyncerMappingTest {

    private val updatedMs = 1_757_000_500_000L

    @Test
    fun `entity to DTO to entity round-trips the override fields`() {
        val entity = NutritionGoalsEntity(
            userId = "u1",
            useCustomCalories = true, calorieBudget = 2000,
            useCustomMacros = true, macroMode = "PERCENT",
            proteinG = 180, carbsG = 200, fatG = 60, fiberG = 30,
            trackedMicros = "IRON,VITAMIN_C,SODIUM",
            updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 330,
            deletedAtEpochMs = null, isSynced = false,
        )

        val dto = entity.toDto()
        assertEquals("u1", dto.user_id)
        assertTrue(dto.use_custom_calories)
        assertEquals(2000, dto.calorie_budget)
        assertEquals("PERCENT", dto.macro_mode)
        assertEquals(180, dto.protein_g)
        assertEquals("IRON,VITAMIN_C,SODIUM", dto.tracked_micros)
        assertNull("live row carries no deleted_at", dto.deleted_at)

        val back = dto.toEntity(updatedMs)
        assertEquals(2000, back.calorieBudget)
        assertEquals(60, back.fatG)
        assertEquals(30, back.fiberG)
        assertEquals("IRON,VITAMIN_C,SODIUM", back.trackedMicros)
        assertTrue("pulled rows are already synced", back.isSynced)
    }

    @Test
    fun `null overrides survive the round-trip`() {
        val entity = NutritionGoalsEntity(
            userId = "u1", useCustomCalories = false, calorieBudget = null,
            useCustomMacros = false, macroMode = "GRAMS",
            proteinG = null, carbsG = null, fatG = null, fiberG = null,
            trackedMicros = "", updatedAtEpochMs = updatedMs, updatedAtTzOffsetMinutes = 0,
            deletedAtEpochMs = null, isSynced = false,
        )
        val back = entity.toDto().toEntity(updatedMs)
        assertNull(back.calorieBudget)
        assertNull(back.proteinG)
        assertEquals("", back.trackedMicros)
    }

    @Test
    fun `repo micro-CSV parse drops unknown names and treats blank as all`() {
        assertEquals(
            setOf(Micro.IRON, Micro.VITAMIN_C),
            NutritionGoalsRepositoryImpl.parseMicros("IRON, VITAMIN_C, BOGUS"),
        )
        // Blank → all (never hide everything).
        assertEquals(Micro.entries.toSet(), NutritionGoalsRepositoryImpl.parseMicros(""))
    }
}
