package com.hellohealth.ui.moodtimeline

import app.cash.turbine.test
import com.hellohealth.domain.model.EmotionRecord
import com.hellohealth.domain.model.EmotionType
import com.hellohealth.domain.repository.EmotionsRepository
import com.hellohealth.domain.usecase.EmotionInsightsUseCase
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
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Verifies [MoodTimelineViewModel] day-grouping and ordering. [EmotionsRepository.observeWindow]
 * delivers records OLDEST-first; the VM must regroup them into day sections newest-day-first with
 * newest-record-first inside each day, deriving each record's local day from its own captured
 * tz offset (not today's zone) so grouping survives DST/travel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodTimelineViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeEmotions(private val window: List<EmotionRecord>) : EmotionsRepository {
        val deleted = mutableListOf<String>()
        override fun observeToday(): Flow<List<EmotionRecord>> = flowOf(emptyList())
        override fun observeLatest(): Flow<EmotionRecord?> = flowOf(null)
        override fun observeWindow(startEpochDay: Long, endEpochDay: Long): Flow<List<EmotionRecord>> = flowOf(window)
        override suspend fun logEmotion(emotion: EmotionType, confidence: Double, source: String, note: String?, visibility: String): String? = null
        override suspend fun delete(id: String) { deleted += id }
    }

    /** Epoch millis for a local date + hour at a fixed +00:00 offset (deterministic, no clock read). */
    private fun tsAt(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun record(id: String, emotion: EmotionType, date: LocalDate, hour: Int) = EmotionRecord(
        id = id,
        userId = "u1",
        timestampUtcEpochMs = tsAt(date, hour),
        tzOffsetMinutes = 0,
        emotion = emotion
    )

    @Test
    fun `records group by local day, newest day first, newest record first within a day`() = runTest {
        val d1 = LocalDate.of(2026, 3, 10)
        val d2 = LocalDate.of(2026, 3, 11)
        // Oldest-first, as observeWindow delivers.
        val window = listOf(
            record("d1-morning", EmotionType.SADNESS, d1, hour = 9),
            record("d1-evening", EmotionType.CALM, d1, hour = 20),
            record("d2-morning", EmotionType.HAPPINESS, d2, hour = 8)
        )
        val vm = MoodTimelineViewModel(FakeEmotions(window), EmotionInsightsUseCase())
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.daySections.size)
            // Newest day (d2) first.
            assertEquals(d2, state.daySections[0].date)
            assertEquals(listOf("d2-morning"), state.daySections[0].records.map { it.id })
            // Older day (d1) second, with its records newest-first (evening before morning).
            assertEquals(d1, state.daySections[1].date)
            assertEquals(listOf("d1-evening", "d1-morning"), state.daySections[1].records.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `insights are computed over the whole window`() = runTest {
        val d1 = LocalDate.of(2026, 3, 10)
        val window = listOf(
            record("a", EmotionType.HAPPINESS, d1, hour = 9),
            record("b", EmotionType.HAPPINESS, d1, hour = 10)
        )
        val vm = MoodTimelineViewModel(FakeEmotions(window), EmotionInsightsUseCase())
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.insights.total)
            assertEquals(EmotionType.HAPPINESS, state.insights.dominant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty window yields no sections and empty insights`() = runTest {
        val vm = MoodTimelineViewModel(FakeEmotions(emptyList()), EmotionInsightsUseCase())
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<DaySection>(), state.daySections)
            assertEquals(0, state.insights.total)
            assertEquals(false, state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stage then commit tombstones the record and hides the staged row immediately`() = runTest {
        val d1 = LocalDate.of(2026, 3, 10)
        val window = listOf(
            record("a", EmotionType.HAPPINESS, d1, hour = 9),
            record("b", EmotionType.CALM, d1, hour = 10)
        )
        val fake = FakeEmotions(window)
        val vm = MoodTimelineViewModel(fake, EmotionInsightsUseCase())
        vm.uiState.test {
            assertEquals(2, awaitItem().daySections[0].records.size)
            // Staging hides the row immediately, before any Room write.
            vm.stageDelete("b")
            assertEquals(listOf("a"), awaitItem().daySections[0].records.map { it.id })
            assertEquals(emptyList<String>(), fake.deleted)
            // Committing performs the tombstone.
            vm.commitDelete("b")
            assertEquals(listOf("b"), fake.deleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stage then undo restores the row and never writes`() = runTest {
        val d1 = LocalDate.of(2026, 3, 10)
        val window = listOf(
            record("a", EmotionType.HAPPINESS, d1, hour = 9),
            record("b", EmotionType.CALM, d1, hour = 10)
        )
        val fake = FakeEmotions(window)
        val vm = MoodTimelineViewModel(fake, EmotionInsightsUseCase())
        vm.uiState.test {
            assertEquals(2, awaitItem().daySections[0].records.size)
            vm.stageDelete("b")
            assertEquals(listOf("a"), awaitItem().daySections[0].records.map { it.id })
            vm.undoDelete("b")
            assertEquals(listOf("b", "a"), awaitItem().daySections[0].records.map { it.id })
            // A later commit for an undone id is a no-op.
            vm.commitDelete("b")
            assertEquals(emptyList<String>(), fake.deleted)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
