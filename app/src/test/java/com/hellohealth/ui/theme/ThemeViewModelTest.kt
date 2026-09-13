package com.hellohealth.ui.theme

import app.cash.turbine.test
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.model.UserProfile
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Verifies [ThemeViewModel] resolves the accent from (latest emotion, dynamic-theme flag):
 * green when the flag is off or no mood is logged, the mapped palette color when on with a mood.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeEmotions(private val latest: EmotionRecord?) : EmotionsRepository {
        override fun observeToday(): Flow<List<EmotionRecord>> = flowOf(emptyList())
        override fun observeLatest(): Flow<EmotionRecord?> = flowOf(latest)
        override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = flowOf(emptyList())
        override suspend fun logEmotion(emotion: EmotionType, confidence: Double, source: String, note: String?, visibility: String): String? = null
        override suspend fun delete(id: String) {}
    }

    private class FakeProfile(private val dynamicOn: Boolean) : ProfileRepository {
        override suspend fun getProfile(): UserProfile? = null
        override suspend fun upsertProfile(profile: UserProfile) {}
        override suspend fun setDynamicTheme(enabled: Boolean) {}
        override fun observeDynamicTheme(): Flow<Boolean> = flowOf(dynamicOn)
    }

    private fun record(emotion: EmotionType) = EmotionRecord(
        id = "x", userId = "u1", timestampUtcEpochMs = 1L, tzOffsetMinutes = 0, emotion = emotion
    )

    @Test
    fun `dynamic on with a mood yields that emotion's accent`() = runTest {
        val vm = ThemeViewModel(FakeEmotions(record(EmotionType.HAPPINESS)), FakeProfile(dynamicOn = true))
        vm.accent.test {
            assertEquals(moodAccentFor(EmotionType.HAPPINESS).accent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dynamic off falls back to the base green even with a mood`() = runTest {
        val vm = ThemeViewModel(FakeEmotions(record(EmotionType.ANGER)), FakeProfile(dynamicOn = false))
        vm.accent.test {
            assertEquals(NeutralMoodAccent.accent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dynamic on but no mood logged yields the base green`() = runTest {
        val vm = ThemeViewModel(FakeEmotions(latest = null), FakeProfile(dynamicOn = true))
        vm.accent.test {
            assertEquals(NeutralMoodAccent.accent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `neutral mood maps to the base green`() = runTest {
        val vm = ThemeViewModel(FakeEmotions(record(EmotionType.NEUTRAL)), FakeProfile(dynamicOn = true))
        vm.accent.test {
            assertEquals(NeutralMoodAccent.accent, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
