package com.hellohealth.ui.body

import com.hellohealth.domain.model.BodyMetric
import com.hellohealth.domain.repository.BodyLogUndo
import com.hellohealth.domain.repository.BodyMetricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests [BodyTrendsViewModel] — it just reads the recent body-metric history into uiState (the charts
 * do the per-metric selection). StandardTestDispatcher + a background collector keep the
 * WhileSubscribed flow hot. Fake repo, no mockk.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BodyTrendsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeBody(private val flow: Flow<List<BodyMetric>>) : BodyMetricsRepository {
        override fun observeRecentBodyMetrics(days: Int): Flow<List<BodyMetric>> = flow
        override fun observeLatest(): Flow<BodyMetric?> = flowOf(null)
        override suspend fun logWeight(localDate: String, weightKg: Double, waistCm: Double?): BodyLogUndo? = null
        override suspend fun undoLog(token: BodyLogUndo) = Unit
        override suspend fun upsertFromHealthConnect(localDate: String, weightKg: Double?, heightCm: Double?, bodyFatPct: Double?, leanMassKg: Double?, fatMassKg: Double?, bodyWaterKg: Double?, boneMassKg: Double?, bmr: Double?, bmi: Double?, vo2max: Double?) = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun metric(date: String, weight: Double?) = BodyMetric(
        localDate = date, weightKg = weight, heightCm = 180.0, bodyFatPct = null, leanMassKg = null,
        fatMassKg = null, bodyWaterKg = null, boneMassKg = null, bmr = null, bmi = null,
        waistCm = null, vo2max = null, source = "manual",
    )

    @Test
    fun `empty history yields an empty state`() = runTest(dispatcher) {
        val vm = BodyTrendsViewModel(FakeBody(flowOf(emptyList())))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(vm.uiState.value.metrics.isEmpty())
        job.cancel()
    }

    @Test
    fun `history is exposed in order for the charts`() = runTest(dispatcher) {
        val rows = listOf(metric("2026-10-05", 80.0), metric("2026-10-07", 79.5), metric("2026-10-09", 79.0))
        val vm = BodyTrendsViewModel(FakeBody(flowOf(rows)))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.metrics.size)
        assertEquals("2026-10-05", vm.uiState.value.metrics.first().localDate)
        assertEquals(79.0, vm.uiState.value.metrics.last().weightKg!!, 0.0001)
        job.cancel()
    }

    @Test
    fun `a new emission updates the state`() = runTest(dispatcher) {
        val flow = MutableStateFlow(listOf(metric("2026-10-09", 79.0)))
        val vm = BodyTrendsViewModel(FakeBody(flow))
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.metrics.size)

        flow.value = listOf(metric("2026-10-09", 79.0), metric("2026-10-10", 78.8))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.metrics.size)
        job.cancel()
    }
}
