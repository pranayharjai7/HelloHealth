package com.hellohealth.ui.dashboard

import app.cash.turbine.test
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionsRepository
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
import org.junit.Before
import org.junit.Test

/**
 * Verifies [EmotionsViewModel.dominantOf] via the exposed [EmotionsCardUiState]. The critical case
 * is the tie-break: [EmotionsRepository.observeToday] delivers records NEWEST-FIRST (the DAO orders
 * DESC), so on a tie the dashboard must surface the most recent mood — the same winner the ASC-fed
 * insights screen picks — not the oldest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmotionsViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeEmotions(private val today: List<EmotionRecord>) : EmotionsRepository {
        val logged = mutableListOf<EmotionType>()
        var loggedSource: String? = null
        val deleted = mutableListOf<String>()
        override fun observeToday(): Flow<List<EmotionRecord>> = flowOf(today)
        override fun observeLatest(): Flow<EmotionRecord?> = flowOf(today.firstOrNull())
        override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = flowOf(emptyList())
        override suspend fun logEmotion(emotion: EmotionType, confidence: Double, source: String, note: String?, visibility: String): String? {
            logged += emotion
            loggedSource = source
            return "id-${logged.size}"
        }
        override suspend fun delete(id: String) { deleted += id }
    }

    private fun record(id: String, emotion: EmotionType, ts: Long) = EmotionRecord(
        id = id, userId = "u1", timestampUtcEpochMs = ts, tzOffsetMinutes = 0, emotion = emotion
    )

    @Test
    fun `tie breaks toward the most recent mood (newest-first input)`() = runTest {
        // Newest-first, as the DAO delivers: CALM at 10:00 (newest), HAPPINESS at 09:00. One each.
        val today = listOf(
            record("b", EmotionType.CALM, ts = 10_000),
            record("a", EmotionType.HAPPINESS, ts = 9_000)
        )
        val vm = EmotionsViewModel(FakeEmotions(today))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(EmotionType.CALM, state.dominantToday)
            assertEquals(2, state.todayCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `most-frequent mood wins outright`() = runTest {
        val today = listOf(
            record("c", EmotionType.SADNESS, ts = 12_000),
            record("b", EmotionType.HAPPINESS, ts = 11_000),
            record("a", EmotionType.HAPPINESS, ts = 10_000)
        )
        val vm = EmotionsViewModel(FakeEmotions(today))
        vm.uiState.test {
            assertEquals(EmotionType.HAPPINESS, awaitItem().dominantToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no moods today yields null dominant`() = runTest {
        val vm = EmotionsViewModel(FakeEmotions(emptyList()))
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(null, state.dominantToday)
            assertEquals(0, state.todayCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `today's records are exposed for the mood-dot strip`() = runTest {
        val today = listOf(
            record("b", EmotionType.CALM, ts = 10_000),
            record("a", EmotionType.HAPPINESS, ts = 9_000)
        )
        val vm = EmotionsViewModel(FakeEmotions(today))
        vm.uiState.test {
            assertEquals(today, awaitItem().today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `quickLog routes through logEmotion with the manual source and returns the new id`() = runTest {
        val fake = FakeEmotions(emptyList())
        val vm = EmotionsViewModel(fake)
        val id = vm.quickLog(EmotionType.HAPPINESS)
        assertEquals(listOf(EmotionType.HAPPINESS), fake.logged)
        assertEquals(EmotionRecord.SOURCE_MANUAL, fake.loggedSource)
        // The returned id is exactly what the repository minted, so the Undo host can delete it.
        assertEquals("id-1", id)
    }

    @Test
    fun `delete forwards the id to the repository`() = runTest {
        val fake = FakeEmotions(emptyList())
        val vm = EmotionsViewModel(fake)
        vm.delete("rec-42")
        assertEquals(listOf("rec-42"), fake.deleted)
    }

    @Test
    fun `the id returned by quickLog round-trips through delete`() = runTest {
        // Mirrors the Dashboard Undo host: capture the id quickLog returns, then delete exactly it.
        val fake = FakeEmotions(emptyList())
        val vm = EmotionsViewModel(fake)
        val id = vm.quickLog(EmotionType.HAPPINESS)
        vm.delete(id!!)
        assertEquals(listOf("id-1"), fake.deleted)
    }
}
