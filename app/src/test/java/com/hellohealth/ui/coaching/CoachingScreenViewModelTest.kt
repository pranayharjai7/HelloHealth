package com.hellohealth.ui.coaching

import app.cash.turbine.test
import com.hellohealth.domain.model.coaching.CoachingContext
import com.hellohealth.domain.model.coaching.CoachingInsight
import com.hellohealth.domain.repository.CoachingRepository
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.ProfileRepository
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

/**
 * Verifies [CoachingScreenViewModel]: initial load, the consent toggle round-trip (persist + reload),
 * and the chat send flow (user turn appended, then coach reply). The fake coaching repo returns LLM
 * text only when "enabled", so the toggle's effect on the source is observable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoachingScreenViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    /** Enabled-aware fake: LLM source when enabled, rule-based otherwise. `ask` echoes the question. */
    private class FakeCoaching(var enabled: Boolean) : CoachingRepository {
        override suspend fun buildTodayContext(): CoachingContext = CoachingContext.EMPTY
        override suspend fun dailyInsight(): CoachingInsight =
            if (enabled) CoachingInsight("personalised insight", CoachingInsight.Source.LLM)
            else CoachingInsight("offline insight", CoachingInsight.Source.RULE_BASED)
        override suspend fun ask(question: String): CoachingInsight =
            if (enabled) CoachingInsight("answer to: $question", CoachingInsight.Source.LLM)
            else CoachingInsight("offline answer", CoachingInsight.Source.RULE_BASED)
        override suspend fun isEnabled(): Boolean = enabled
    }

    private class FakeProfile(private val coaching: FakeCoaching) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? = null
        override suspend fun upsertProfile(profile: UserProfile) = Unit
        override suspend fun setDynamicTheme(enabled: Boolean) = Unit
        override fun observeDynamicTheme(): Flow<Boolean> = flowOf(true)
        // Persisting consent flips the coaching fake so the reload observes the new state.
        override suspend fun setAiCoachingEnabled(enabled: Boolean) { coaching.enabled = enabled }
        override fun observeAiCoachingEnabled(): Flow<Boolean> = flowOf(coaching.enabled)
    }

    private fun vm(enabled: Boolean = false): CoachingScreenViewModel {
        val coaching = FakeCoaching(enabled)
        return CoachingScreenViewModel(coaching, FakeProfile(coaching))
    }

    @Test
    fun `initial load surfaces the rule-based insight when coaching is off`() = runTest {
        vm(enabled = false).uiState.test {
            var s = awaitItem()
            while (s.insightLoading) s = awaitItem()
            assertEquals("offline insight", s.insight)
            assertTrue(s.insightIsRuleBased)
            assertFalse(s.enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `enabling consent persists and reloads a personalised insight`() = runTest {
        val vm = vm(enabled = false)
        vm.uiState.test {
            var s = awaitItem()
            while (s.insightLoading) s = awaitItem()
            assertFalse(s.enabled)

            vm.setEnabled(true)
            // Drain until enabled + a fresh non-loading LLM insight arrives.
            s = awaitItem()
            while (!s.enabled || s.insightLoading || s.insightIsRuleBased) s = awaitItem()
            assertEquals("personalised insight", s.insight)
            assertFalse(s.insightIsRuleBased)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `send appends the user turn then the coach reply`() = runTest {
        val vm = vm(enabled = true)
        vm.uiState.test {
            var s = awaitItem()
            while (s.insightLoading) s = awaitItem()

            vm.send("How is my week?")
            // Wait until both turns are present and sending settled.
            s = awaitItem()
            while (s.transcript.size < 2 || s.sending) s = awaitItem()

            assertEquals(2, s.transcript.size)
            assertTrue(s.transcript[0].fromUser)
            assertEquals("How is my week?", s.transcript[0].text)
            assertFalse(s.transcript[1].fromUser)
            assertEquals("answer to: How is my week?", s.transcript[1].text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank send is ignored`() = runTest {
        val vm = vm(enabled = true)
        vm.uiState.test {
            var s = awaitItem()
            while (s.insightLoading) s = awaitItem()
            vm.send("   ")
            assertEquals(0, s.transcript.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
