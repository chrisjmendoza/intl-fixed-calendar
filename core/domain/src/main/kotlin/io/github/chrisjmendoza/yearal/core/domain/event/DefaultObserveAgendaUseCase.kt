package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.ZoneId

/**
 * The production [ObserveAgendaUseCase]: [EventRepository.observeAgendaCandidates] expanded by
 * [RecurrenceExpander], coloured from [EventRepository.observeCalendars], combined with
 * [HolidayEngine] evaluated over [HolidaySetProvider]'s enabled sets, and bucketed by device-zone date
 * with [Occurrence.dates] — exactly the pipeline of `docs/ARCHITECTURE.md` §3.4.
 *
 * It is a plain value with no clock of its own (CLAUDE.md rule 2): [zoneProvider] is read again on
 * every recomputation, never cached, so a zone change is picked up the next time a screen re-collects.
 * It does no I/O and logs no event content (rule 8).
 *
 * Spec: `docs/ARCHITECTURE.md` §3.4; `docs/contracts/Events.md` §5 "ObserveAgendaUseCase".
 */
public class DefaultObserveAgendaUseCase(
    private val eventRepository: EventRepository,
    private val recurrenceExpander: RecurrenceExpander,
    private val holidayEngine: HolidayEngine,
    private val holidaySetProvider: HolidaySetProvider,
    private val zoneProvider: ZoneProvider,
) : ObserveAgendaUseCase {
    /**
     * The agenda of every date of [range] with an entry or a holiday, re-emitted whenever the
     * candidate events, the calendars (colour, visibility) or the enabled holiday sets change. Work
     * happens off the calling thread ([Dispatchers.Default]).
     */
    override fun invoke(range: ClosedRange<LocalDate>): Flow<Map<LocalDate, DayAgenda>> =
        combine(
            eventRepository.observeAgendaCandidates(range),
            eventRepository.observeCalendars(),
            holidaySetProvider.enabledSets(),
        ) { candidates, calendars, sets ->
            val zone = zoneProvider.currentZone()
            val entriesByDate = entriesByDate(candidates, calendars, range, zone)
            val holidaysByDate =
                if (range.isEmpty()) {
                    emptyMap()
                } else {
                    holidayEngine.occurrences(sets, range).groupBy { it.date }
                }
            val dates = entriesByDate.keys + holidaysByDate.keys
            dates
                .associateWith { date ->
                    DayAgenda.of(date, entriesByDate[date].orEmpty(), holidaysByDate[date].orEmpty())
                }.toSortedMap()
        }.flowOn(Dispatchers.Default)

    /**
     * The dates of [range] with at least one event occurrence, ignoring holidays; re-emitted on the
     * same changes as [invoke] except the enabled holiday sets, which this never reads.
     */
    override fun presence(range: ClosedRange<LocalDate>): Flow<Set<LocalDate>> =
        combine(
            eventRepository.observeAgendaCandidates(range),
            eventRepository.observeCalendars(),
        ) { candidates, calendars ->
            entriesByDate(candidates, calendars, range, zoneProvider.currentZone()).keys.toSortedSet()
        }.flowOn(Dispatchers.Default)

    /**
     * Expands every candidate that has a calendar, resolves its colour, and buckets its occurrences by
     * the dates of [range] they touch ([Occurrence.dates]). A candidate whose calendar has vanished
     * since the query was built (a concurrent delete) is skipped rather than crashing the flow — the
     * same fail-soft rule `docs/contracts/Events.md` asks of corrupted storage rows.
     */
    private fun entriesByDate(
        candidates: List<Event>,
        calendars: List<EventCalendar>,
        range: ClosedRange<LocalDate>,
        zone: ZoneId,
    ): Map<LocalDate, List<AgendaEntry>> {
        if (range.isEmpty()) return emptyMap()
        val calendarsById = calendars.associateBy { it.id }
        val result = LinkedHashMap<LocalDate, MutableList<AgendaEntry>>()
        for (event in candidates) {
            val calendar = calendarsById[event.calendarId] ?: continue
            val colorArgb = event.colorArgb ?: calendar.colorArgb
            for (occurrence in recurrenceExpander.expand(event, range, zone)) {
                val entry = AgendaEntry(event, occurrence, colorArgb, zone)
                val touched = occurrence.dates(zone)
                var date = maxOf(touched.start, range.start)
                val last = minOf(touched.endInclusive, range.endInclusive)
                while (!date.isAfter(last)) {
                    result.getOrPut(date) { mutableListOf() }.add(entry)
                    date = date.plusDays(1)
                }
            }
        }
        return result
    }
}
