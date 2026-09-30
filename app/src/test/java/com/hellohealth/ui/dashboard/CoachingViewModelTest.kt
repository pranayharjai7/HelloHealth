package com.hellohealth.ui.dashboard

import app.cash.turbine.test
import com.hellohealth.core.date.SelectedDateHolder
import com.hellohealth.domain.model.coaching.CoachingContext
import com.hellohealth.domain.model.coaching.CoachingInsight
import com.hellohealth.domain.repository.CoachingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Verifies [CoachingViewModel] loads a daily insight on init and surfaces its source. The repository
 * never throws (its rule-based fallback guarantees non-empty text), so the VM needs no error state —
 * these tests pin that the LLM vs rule-based distinction reaches the UI, and that loading resolves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoachingViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeCoaching(
        private val insight: CoachingInsight,
        private val enabled: Boolean = false,
    ) : CoachingRepository {
        var dailyInsightCalls = 0
        override suspend fun buildTodayContext(): CoachingContext = CoachingContext.EMPTY
        override suspend fun dailyInsight(): CoachingInsight { dailyInsightCalls++; return insight }
        override suspend fun ask(question: String): CoachingInsight = insight
        override suspend fun isEnabled(): Boolean = enabled
    }

    @Test
    fun `loads an LLM insight on init`() = runTest {
        val vm = CoachingViewModel(FakeCoaching(CoachingInsight("You're crushing it today.", CoachingInsight.Source.LLM)), SelectedDateHolder())
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals("You're crushing it today.", state.insight)
            assertFalse(state.isRuleBased)
            assertTrue(state.hasInsight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flags a rule-based insight so the card can show the offline note`() = runTest {
        val vm = CoachingViewModel(FakeCoaching(CoachingInsight("Log a meal to see patterns.", CoachingInsight.Source.RULE_BASED)), SelectedDateHolder())
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertTrue(state.isRuleBased)
            assertTrue(state.hasInsight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a past day shows a neutral state and never calls the LLM`() = runTest {
        val fake = FakeCoaching(CoachingInsight("should not appear", CoachingInsight.Source.LLM))
        val holder = SelectedDateHolder()
        val vm = CoachingViewModel(fake, holder)
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            // Init is today → one insight load.
            assertEquals(1, fake.dailyInsightCalls)

            holder.set(java.time.LocalDate.now().minusDays(2))
            while (!state.isPastDay) state = awaitItem()
            assertFalse(state.hasInsight)
            assertEquals(java.time.LocalDate.now().minusDays(2), state.viewingDate)
            // No additional dailyInsight() call for the past day — coaching is today-only.
            assertEquals(1, fake.dailyInsightCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
