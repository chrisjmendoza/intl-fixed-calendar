package io.github.chrisjmendoza.yearal.core.domain.event

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The read model behind every date surface that shows events: the month grid, Day detail, Today and
 * the Year view (`docs/ARCHITECTURE.md` §3.4). It combines [EventRepository.observeAgendaCandidates],
 * [RecurrenceExpander.expand], the calendars' colours and the holidays of the enabled sets, and
 * buckets the result by device-zone date with [Occurrence.dates].
 *
 * Features take this interface from Hilt and are tested with
 * `io.github.chrisjmendoza.yearal.core.testing.FakeObserveAgendaUseCase`. The production
 * implementation is ROADMAP M4 T6.
 *
 * ## Guarantees
 *
 * - **Dates are Gregorian device-zone dates.** The device zone is read from the app's `ZoneProvider`
 *   at every recomputation, never cached; "today" is not involved here at all.
 * - Both flows are cold, emit the current value on collection, re-emit whenever events, calendars
 *   (colour, visibility) or the enabled holiday sets change, never complete, and do their work off
 *   the main thread. A zone change by itself does not trigger an emission; screens re-collect on
 *   resume, which picks it up.
 * - Events of hidden calendars and excluded occurrences (exdates) never appear.
 * - An empty range (`start > endInclusive`) gives an empty result, not an error.
 */
public interface ObserveAgendaUseCase {
    /**
     * The agenda of every date in [range] that has something on it.
     *
     * The map's keys are **only** dates inside [range] with at least one entry or holiday — a date
     * with nothing on it is absent, so read it as `agenda[date]` being `null` — and iterate in
     * ascending date order. Each value's [DayAgenda.date] is its key. A multi-day occurrence appears
     * under every date of [range] it touches, as the same [AgendaEntry].
     *
     * @param range inclusive device-zone dates, e.g. `IfcYearMonth.gregorianRange` (28 or 29 days,
     *   the Year Day or Leap Day band included) or a single day.
     */
    public operator fun invoke(range: ClosedRange<LocalDate>): Flow<Map<LocalDate, DayAgenda>>

    /**
     * The dates of [range] on which at least one **event occurrence** is shown — the Year view's
     * presence bitmap. **Holidays are not counted**; the grid already marks them from the holiday
     * catalog. Exactly the keys of [invoke] whose [DayAgenda.entries] is not empty, without building
     * the agendas.
     */
    public fun presence(range: ClosedRange<LocalDate>): Flow<Set<LocalDate>>
}
