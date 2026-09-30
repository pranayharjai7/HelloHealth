package com.hellohealth.data.local

import com.hellohealth.domain.model.HealthSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the `summaryJson` round-trip for the P3 vitals fields. The risk being covered: adding a
 * field to [SnapshotSummaryJson] but forgetting to map it in either [toJsonModel] or [toDomain]
 * silently drops it on encode/decode. These assertions fail loudly if that happens.
 */
class SnapshotJsonTest {

    @Test
    fun `vitals fields survive an encode-decode round trip`() {
        val summary = HealthSummary(
            steps = 1234,
            restingHeartRate = 58.0,
            hrvRmssd = 62.5,
            respiratoryRate = 14.2,
            bodyTemperature = 36.6,
            hydrationMl = 1500.0
        )

        val restored = SnapshotJson.decode(SnapshotJson.encode(summary))

        assertEquals(58.0, restored.restingHeartRate!!, 0.0001)
        assertEquals(62.5, restored.hrvRmssd!!, 0.0001)
        assertEquals(14.2, restored.respiratoryRate!!, 0.0001)
        assertEquals(36.6, restored.bodyTemperature!!, 0.0001)
        assertEquals(1500.0, restored.hydrationMl!!, 0.0001)
    }

    @Test
    fun `older snapshots without vitals decode to null`() {
        // A payload written before P3 has none of the new keys; ignoreUnknownKeys + defaults must
        // yield nulls rather than throwing.
        val legacy = """{"steps":100,"stepsGoal":10000,"lastUpdated":42}"""

        val restored = SnapshotJson.decode(legacy)

        assertEquals(100L, restored.steps)
        assertEquals(null, restored.restingHeartRate)
        assertEquals(null, restored.hydrationMl)
    }
}
