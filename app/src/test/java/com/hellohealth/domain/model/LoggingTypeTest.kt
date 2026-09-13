package com.hellohealth.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the pure [deriveLoggingType] classifier — the single source of truth for how a catalog
 * exercise is logged. The rules are order-sensitive (first match wins) and must exactly mirror
 * TrackMe's, so a routine planned in HelloHealth surfaces the same target fields TrackMe would.
 * Also pins the null/case tolerance that keeps a bad catalog value from crashing a read.
 */
class LoggingTypeTest {

    @Test
    fun `cardio category maps to CARDIO regardless of equipment`() {
        assertEquals(LoggingType.CARDIO, deriveLoggingType("Cardio", "machine"))
        assertEquals(LoggingType.CARDIO, deriveLoggingType("cardio", "body only"))
    }

    @Test
    fun `stretching category maps to TIMED`() {
        assertEquals(LoggingType.TIMED, deriveLoggingType("Stretching", "body only"))
    }

    @Test
    fun `plyometrics category maps to BODYWEIGHT_REPS`() {
        assertEquals(LoggingType.BODYWEIGHT_REPS, deriveLoggingType("Plyometrics", "barbell"))
    }

    @Test
    fun `body only equipment maps to BODYWEIGHT_REPS when category does not match earlier`() {
        assertEquals(LoggingType.BODYWEIGHT_REPS, deriveLoggingType("Strength", "body only"))
    }

    @Test
    fun `default is WEIGHTED_REPS`() {
        assertEquals(LoggingType.WEIGHTED_REPS, deriveLoggingType("Strength", "barbell"))
    }

    @Test
    fun `order matters - cardio wins over body only equipment`() {
        // A cardio exercise using "body only" must classify as CARDIO, not BODYWEIGHT_REPS.
        assertEquals(LoggingType.CARDIO, deriveLoggingType("Cardio", "body only"))
    }

    @Test
    fun `null category and equipment fall back to WEIGHTED_REPS`() {
        assertEquals(LoggingType.WEIGHTED_REPS, deriveLoggingType(null, null))
    }

    @Test
    fun `classification is case-insensitive`() {
        assertEquals(LoggingType.CARDIO, deriveLoggingType("CARDIO", null))
        assertEquals(LoggingType.BODYWEIGHT_REPS, deriveLoggingType("strength", "BODY ONLY"))
    }

    @Test
    fun `exercise extension delegates to the classifier`() {
        val ex = Exercise(
            id = "e1", name = "Running", category = "Cardio",
            primaryMuscles = emptyList(), secondaryMuscles = emptyList(),
            equipment = "none", instructions = emptyList(),
            gifUrl = "", youtubeQuery = ""
        )
        assertEquals(LoggingType.CARDIO, ex.loggingType())
    }
}
