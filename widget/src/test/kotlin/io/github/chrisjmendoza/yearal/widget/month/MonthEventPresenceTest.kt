package io.github.chrisjmendoza.yearal.widget.month

import io.github.chrisjmendoza.yearal.core.domain.event.DayAgenda
import io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.FakeObserveAgendaUseCase
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.beGreaterThanOrEqualTo
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/**
 * [fetchMonthEventPresence] is what keeps a slow or broken database from ever hanging or crashing the
 * Month widget's render (ROADMAP M5 T6): a timeout and a catch, both proven here with virtual time so
 * the test itself does not wait three real seconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthEventPresenceTest {
    private val range = LocalDate.of(2026, 9, 10)..LocalDate.of(2026, 10, 7)

    /** Never emits and never completes -- a database call that hangs forever. */
    private class NeverEmittingUseCase : ObserveAgendaUseCase {
        override fun invoke(range: ClosedRange<LocalDate>): Flow<Map<LocalDate, DayAgenda>> =
            throw UnsupportedOperationException("not used by this test")

        override fun presence(range: ClosedRange<LocalDate>): Flow<Set<LocalDate>> = MutableSharedFlow()
    }

    /** Throws while the flow is collected -- a use case whose query itself is broken. */
    private class ThrowingUseCase : ObserveAgendaUseCase {
        override fun invoke(range: ClosedRange<LocalDate>): Flow<Map<LocalDate, DayAgenda>> =
            throw UnsupportedOperationException("not used by this test")

        override fun presence(range: ClosedRange<LocalDate>): Flow<Set<LocalDate>> =
            flow { throw IllegalStateException("simulated storage failure") }
    }

    @Test
    fun `a normal presence flow returns its dates`() =
        runTest {
            val useCase = FakeObserveAgendaUseCase()
            val eventDate = LocalDate.of(2026, 9, 17)
            useCase.putEntry(EventFixtures.entry(eventOn(eventDate)))

            val result = fetchMonthEventPresence(useCase, range)

            result shouldContainExactlyInAnyOrder setOf(eventDate)
        }

    @Test
    fun `a flow that never emits renders without dots, within the timeout`() =
        runTest {
            val result = fetchMonthEventPresence(NeverEmittingUseCase(), range)

            result shouldBe emptySet()
            // Proves the timeout -- not some other early exit -- is what ended the wait.
            currentTime should beGreaterThanOrEqualTo(PRESENCE_TIMEOUT_MILLIS)
        }

    @Test
    fun `a throwing use case renders without dots instead of crashing`() =
        runTest {
            val result = fetchMonthEventPresence(ThrowingUseCase(), range)

            result shouldBe emptySet()
        }

    /** A minimal all-day event on [date], used only to exercise [FakeObserveAgendaUseCase.putEntry]. */
    private fun eventOn(date: LocalDate) = EventFixtures.allDay(date = date)
}
