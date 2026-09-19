package io.github.chrisjmendoza.yearal.core.data.event

import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository
import io.github.chrisjmendoza.yearal.core.testing.MutableClock

/**
 * Runs [EventRepositoryContractTest] against
 * `io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository`. Needs no Android framework, so it
 * runs with the plain JUnit4 runner. Its sibling, [RoomEventRepositoryContractTest], runs the same
 * suite against [RoomEventRepository] so the two cannot silently disagree (ROADMAP M4 T2).
 */
public class FakeEventRepositoryContractTest : EventRepositoryContractTest() {
    override val clock: MutableClock = MutableClock(FIXED_INSTANT)
    override val repository: EventRepository = FakeEventRepository(clock = clock)
}
