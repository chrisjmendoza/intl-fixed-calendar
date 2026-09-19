package io.github.chrisjmendoza.yearal.widget

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [DebouncedWidgetUpdater] is the [io.github.chrisjmendoza.yearal.core.domain.widget.WidgetUpdater]
 * `RoomEventRepository` calls after every successful write (ROADMAP M5 T6;
 * docs/ARCHITECTURE.md §5 "Data"). These tests run the debounce collector on `runTest`'s own virtual-time
 * dispatcher (passed in as [DebouncedWidgetUpdater]'s scope) so the one-second window is proven without a
 * real sleep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedWidgetUpdaterTest {
    private class RecordingRefresher : WidgetRefresher {
        var calls: Int = 0

        override suspend fun refreshAll() {
            calls++
        }
    }

    private val windowMillis = DebouncedWidgetUpdater.DEBOUNCE_WINDOW.inWholeMilliseconds

    @Test
    fun `a burst of requests collapses into exactly one refresh after the debounce window`() =
        runTest {
            val refresher = RecordingRefresher()
            val updater = DebouncedWidgetUpdater(refresher, backgroundScope)

            repeat(20) { updater.requestUpdate() }
            advanceTimeBy(windowMillis + 1)

            refresher.calls shouldBe 1
        }

    @Test
    fun `two bursts separated by more than the debounce window refresh exactly twice`() =
        runTest {
            val refresher = RecordingRefresher()
            val updater = DebouncedWidgetUpdater(refresher, backgroundScope)

            updater.requestUpdate()
            advanceTimeBy(windowMillis + 1)
            updater.requestUpdate()
            advanceTimeBy(windowMillis + 1)

            refresher.calls shouldBe 2
        }

    @Test
    fun `requests inside the window keep postponing the refresh, still only one`() =
        runTest {
            val refresher = RecordingRefresher()
            val updater = DebouncedWidgetUpdater(refresher, backgroundScope)

            updater.requestUpdate()
            advanceTimeBy(windowMillis / 2)
            updater.requestUpdate() // resets the window before it elapsed
            advanceTimeBy(windowMillis / 2)
            refresher.calls shouldBe 0 // the reset means the first window never fired

            advanceTimeBy(windowMillis)
            refresher.calls shouldBe 1
        }

    @Test
    fun `no requests means no refresh, ever`() =
        runTest {
            val refresher = RecordingRefresher()
            DebouncedWidgetUpdater(refresher, backgroundScope)

            advanceTimeBy(windowMillis * 10)

            refresher.calls shouldBe 0
        }
}
