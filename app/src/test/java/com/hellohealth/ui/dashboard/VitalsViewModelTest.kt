package com.hellohealth.ui.dashboard

import app.cash.turbine.test
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.model.vitals.HealthMetricsData
import com.hellohealth.domain.model.vitals.LatestVitals
import com.hellohealth.domain.model.vitals.ReadinessScore
import com.hellohealth.domain.model.vitals.ReadinessStatus
import com.hellohealth.domain.repository.VitalsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Verifies [VitalsViewModel] is date-aware: the readiness ring + chips reflect the day selected in
 * [SelectedDateHolder]. A day with a rollup shows its readiness + formatted chips; a day without one
 * dashes the chips and reports INSUFFICIENT_DATA.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VitalsViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    /** Per-day fake: readiness + vitals keyed by ISO date; absent day → INSUFFICIENT_DATA / null. */
    private class FakeVitals(
        private val readinessByDay: Map<String, ReadinessScore>,
        private val vitalsByDay: Map<String, LatestVitals>,
    ) : VitalsRepository {
        override fun observeReadiness(): Flow<ReadinessScore?> = flowOf(null)
        override fun observeReadinessAsOf(date: LocalDate): Flow<ReadinessScore?> =
            flowOf(readinessByDay[date.toString()]
                ?: ReadinessScore(score = 0, status = ReadinessStatus.INSUFFICIENT_DATA))
        override fun observeVitalsForDay(localDate: String): Flow<LatestVitals?> = flowOf(vitalsByDay[localDate])
        override fun observeRecentRollups(days: Int): Flow<List<HealthMetricsData>> = flowOf(emptyList())
        override fun observeRecentVitals(days: Int): Flow<List<LatestVitals>> = flowOf(emptyList())
        override fun observeLatestVitals(): Flow<LatestVitals?> = flowOf(null)
        override suspend fun upsertRollup(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?, sleepDurationMinutes: Int?, deepSleepMinutes: Int?) = Unit
        override suspend fun upsertSample(localDate: String, timestampUtcEpochMs: Long, restingHeartRate: Double?, hrvRmssd: Double?, respiratoryRate: Double?, bodyTemperature: Double?, hydrationMl: Double?, spo2: Double?) = Unit
    }

    private fun vitals(date: String, rhr: Double? = null, spo2: Double? = null) =
        LatestVitals(localDate = date, restingHeartRate = rhr, hrvRmssd = null, respiratoryRate = null, bodyTemperature = null, hydrationMl = null, spo2 = spo2)

    @Test
    fun `today shows its readiness and formatted chips`() = runTest {
        val today = LocalDate.now().toString()
        val vm = VitalsViewModel(
            FakeVitals(
                readinessByDay = mapOf(today to ReadinessScore(score = 82, status = ReadinessStatus.GOOD)),
                vitalsByDay = mapOf(today to vitals(today, rhr = 58.0, spo2 = 97.0)),
            ),
            SelectedDateHolder(),
        )
        vm.uiState.test {
            var s = awaitItem()
            while (!s.hasReadiness) s = awaitItem()
            assertEquals(82, s.readinessScore)
            assertEquals(ReadinessStatus.GOOD, s.status)
            assertEquals("58 bpm", s.restingHeartRate)
            assertEquals("97%", s.spo2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching to a day without a rollup dashes the chips and readiness is insufficient`() = runTest {
        val today = LocalDate.now()
        val past = today.minusDays(4)
        val holder = SelectedDateHolder()
        val vm = VitalsViewModel(
            FakeVitals(
                readinessByDay = mapOf(today.toString() to ReadinessScore(score = 70, status = ReadinessStatus.MODERATE)),
                vitalsByDay = mapOf(today.toString() to vitals(today.toString(), rhr = 60.0)),
            ),
            holder,
        )
        vm.uiState.test {
            var s = awaitItem()
            while (!s.hasReadiness) s = awaitItem()
            assertEquals("60 bpm", s.restingHeartRate)

            holder.set(past) // no rollup for the past day
            while (s.status != ReadinessStatus.INSUFFICIENT_DATA) s = awaitItem()
            assertEquals(VitalsUiState.DASH, s.restingHeartRate)
            assertEquals(VitalsUiState.DASH, s.spo2)
            assertEquals(0, s.readinessScore)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
