package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * A hand-written [DateTicker] test double whose emissions are driven directly by [set], instead of
 * by any real passage of time or a real [java.time.Clock]. Starts by emitting `initial`.
 *
 * Intended for tests of code that only consumes [DateTicker.today] (a ViewModel, a use case) and
 * does not itself need to verify the midnight-rollover computation — that belongs to a test of
 * [io.github.chrisjmendoza.yearal.core.domain.RealDateTicker] against a real [java.time.Clock]
 * fake such as [MutableClock].
 */
public class FakeDateTicker(
    initial: LocalDate,
) : DateTicker {
    private val state = MutableStateFlow(initial)

    /** The date last passed to the constructor or [set]; re-emits on every [set] call. */
    override val today: Flow<LocalDate> = state.asStateFlow()

    /** Emits [date] as the new "today", simulating a midnight rollover or a date/time change. */
    public fun set(date: LocalDate) {
        state.value = date
    }
}
