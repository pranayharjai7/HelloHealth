package com.hellohealth.ui.wellness

import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.model.WellnessSnapshot
import com.hellohealth.domain.repository.WellnessRepository
import com.hellohealth.domain.wellness.WellnessBand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Tests that [WellnessViewModel] reacts to [SelectedDateHolder]: switching the selected date re-scopes
 * the observed snapshot to that date. Fake repository returns a per-date snapshot; a background
 * collector keeps the WhileSubscribed state hot.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WellnessViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeRepo : WellnessRepository {
        override fun observeSnapshot(date: LocalDate): Flow<WellnessSnapshot> = flowOf(
            WellnessSnapshot(
                localDate = date.toString(),
                score = if (date == LocalDate.now()) 80 else 40,
                band = if (date == LocalDate.now()) WellnessBand.THRIVING else WellnessBand.BUILDING,
                pillars = emptyList(),
                streaks = emptyList(),
                totalPoints = 0,
                earnedAchievements = emptyList(),
            )
        )
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `snapshot reacts to the selected date`() = runTest(dispatcher) {
        val holder = SelectedDateHolder()
        val vm = WellnessViewModel(FakeRepo(), holder)
        val job = launch { vm.snapshot.collect {} }

        advanceUntilIdle()
        assertEquals(80, vm.snapshot.value?.score) // today

        holder.set(LocalDate.now().minusDays(2))
        advanceUntilIdle()
        assertEquals("past day re-scopes the snapshot", 40, vm.snapshot.value?.score)

        job.cancel()
    }
}
