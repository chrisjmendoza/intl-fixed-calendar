package io.github.chrisjmendoza.yearal.widget

import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [TodayWidgetRolloverListener] is the widget's contribution to the
 * `Set<DayRolloverListener>` multibinding (docs/ARCHITECTURE.md §5). It must refresh exactly once per
 * call, regardless of [DayRolloverTrigger] — the trigger is a hint, never a reason to skip a refresh.
 */
class TodayWidgetRolloverListenerTest {
    private class RecordingRefresher : WidgetRefresher {
        val calls = mutableListOf<Unit>()

        override suspend fun refreshAll() {
            calls += Unit
        }
    }

    @Test
    fun `one trigger refreshes exactly once`() =
        runTest {
            val refresher = RecordingRefresher()
            val listener = TodayWidgetRolloverListener(refresher)

            listener.onDayRollover(DayRolloverTrigger.MIDNIGHT)

            refresher.calls.shouldContainExactly(listOf(Unit))
        }

    @Test
    fun `every trigger kind refreshes, one call each`() =
        runTest {
            val refresher = RecordingRefresher()
            val listener = TodayWidgetRolloverListener(refresher)

            DayRolloverTrigger.entries.forEach { trigger -> listener.onDayRollover(trigger) }

            refresher.calls.size shouldBe DayRolloverTrigger.entries.size
        }

    @Test
    fun `two separate calls refresh exactly twice, not once`() =
        runTest {
            val refresher = RecordingRefresher()
            val listener = TodayWidgetRolloverListener(refresher)

            listener.onDayRollover(DayRolloverTrigger.MIDNIGHT)
            listener.onDayRollover(DayRolloverTrigger.ZONE_CHANGED)

            refresher.calls.shouldContainExactly(listOf(Unit, Unit))
        }
}
