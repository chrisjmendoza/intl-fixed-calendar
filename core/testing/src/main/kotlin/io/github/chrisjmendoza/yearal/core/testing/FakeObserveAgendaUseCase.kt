package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.event.AgendaEntry
import io.github.chrisjmendoza.yearal.core.domain.event.DayAgenda
import io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayOccurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * A hand-driven [ObserveAgendaUseCase] for ViewModel and UI tests: the test states what is on which
 * day, and the fake serves any requested range from that, with the guarantees of the interface —
 * only non-empty dates inside the range, ascending keys, [DayAgenda.ENTRY_ORDER], re-emission on
 * every change, an empty map for an empty range.
 *
 * Nothing is expanded or bucketed by rule here. [putEntry] does place an entry on every date its
 * occurrence touches ([AgendaEntry.firstDate]..[AgendaEntry.lastDate]), which is exactly what the
 * production use case does with a multi-day occurrence.
 *
 * Every requested range is recorded in [requestedRanges], so a test can assert that a screen asked
 * for `IfcYearMonth.gregorianRange` (29 days in December and in a leap-year June).
 */
public class FakeObserveAgendaUseCase : ObserveAgendaUseCase {
    private val agendas = MutableStateFlow<Map<LocalDate, DayAgenda>>(emptyMap())
    private val requested = mutableListOf<ClosedRange<LocalDate>>()

    /** Every range passed to [invoke] or [presence], oldest first. */
    public val requestedRanges: List<ClosedRange<LocalDate>> get() = requested.toList()

    /** Replaces everything staged so far with [days]; an empty agenda removes its date. */
    public fun setAgendas(days: Collection<DayAgenda>) {
        agendas.value = days.filterNot { it.isEmpty }.associateBy { it.date }
    }

    /** Adds [entry] to the agenda of every date it touches, keeping what is already there. */
    public fun putEntry(entry: AgendaEntry) {
        agendas.update { current ->
            var next = current
            var date = entry.firstDate
            while (!date.isAfter(entry.lastDate)) {
                val existing = next[date]
                next =
                    next +
                    (date to DayAgenda.of(date, existing?.entries.orEmpty() + entry, existing?.holidays.orEmpty()))
                date = date.plusDays(1)
            }
            next
        }
    }

    /** Adds [holiday] to the agenda of its date, keeping what is already there. */
    public fun putHoliday(holiday: HolidayOccurrence) {
        agendas.update { current ->
            val existing = current[holiday.date]
            current +
                (
                    holiday.date to
                        DayAgenda.of(holiday.date, existing?.entries.orEmpty(), existing?.holidays.orEmpty() + holiday)
                )
        }
    }

    /** Removes everything staged; collectors see an empty map. */
    public fun clear() {
        agendas.value = emptyMap()
    }

    /** The staged agendas whose date lies in [range], ascending; records [range]. */
    override fun invoke(range: ClosedRange<LocalDate>): Flow<Map<LocalDate, DayAgenda>> {
        requested += range
        return agendas.map { all -> within(all, range) }.distinctUntilChanged()
    }

    /** The dates of [range] whose staged agenda has at least one entry (holidays ignored); records [range]. */
    override fun presence(range: ClosedRange<LocalDate>): Flow<Set<LocalDate>> {
        requested += range
        return agendas
            .map { all -> within(all, range).filterValues { it.entries.isNotEmpty() }.keys }
            .distinctUntilChanged()
    }

    // A sorted map, so keys iterate in ascending date order as the contract promises.
    private fun within(
        all: Map<LocalDate, DayAgenda>,
        range: ClosedRange<LocalDate>,
    ): Map<LocalDate, DayAgenda> = all.filterKeys { it in range }.toSortedMap()
}
