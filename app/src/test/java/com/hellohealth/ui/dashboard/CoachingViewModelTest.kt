package com.hellohealth.ui.dashboard

import app.cash.turbine.test
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

    private class FakeCoaching(private val insight: CoachingInsight) : CoachingRepository {
        override suspend fun buildTodayContext(): CoachingContext = CoachingContext.EMPTY
        override suspend fun dailyInsight(): CoachingInsight = insight
        override suspend fun ask(question: String): CoachingInsight = insight
        override suspend fun isEnabled(): Boolean = false
    }

    @Test
    fun `loads an LLM insight on init`() = runTest {
        val vm = CoachingViewModel(FakeCoaching(CoachingInsight("You're crushing it today.", CoachingInsight.Source.LLM)))
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
        val vm = CoachingViewModel(FakeCoaching(CoachingInsight("Log a meal to see patterns.", CoachingInsight.Source.RULE_BASED)))
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertTrue(state.isRuleBased)
            assertTrue(state.hasInsight)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
