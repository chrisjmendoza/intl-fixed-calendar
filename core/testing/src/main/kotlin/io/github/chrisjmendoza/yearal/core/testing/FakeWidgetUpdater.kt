package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.widget.WidgetUpdater

/** [WidgetUpdater] that schedules nothing and counts how often it was asked to. */
public class FakeWidgetUpdater : WidgetUpdater {
    /** Number of [requestUpdate] calls so far. */
    public var requestCount: Int = 0
        private set

    /** Counts the call and does nothing else. */
    override fun requestUpdate() {
        requestCount++
    }
}
