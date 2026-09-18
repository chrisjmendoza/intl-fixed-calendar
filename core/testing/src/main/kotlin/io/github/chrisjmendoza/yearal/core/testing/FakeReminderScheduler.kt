package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler

/** [ReminderScheduler] that schedules nothing and counts how often it was asked to. */
public class FakeReminderScheduler : ReminderScheduler {
    /** Number of [reschedule] calls so far. */
    public var rescheduleCount: Int = 0
        private set

    /** Counts the call and does nothing else. */
    override suspend fun reschedule() {
        rescheduleCount++
    }
}
