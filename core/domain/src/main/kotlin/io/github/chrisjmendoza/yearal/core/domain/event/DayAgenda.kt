package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayOccurrence
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One occurrence of one event, ready to show: the [event] for its text, the [occurrence] for its
 * time, the colour already resolved, and the device zone the agenda was built in.
 *
 * A multi-day occurrence is the **same** entry in the [DayAgenda] of every date it touches
 * ([firstDate]..[lastDate]); compare a day with those to render "day 2 of 3".
 *
 * @property event the event, as stored when the agenda was built.
 * @property occurrence which happening of it. Its [Occurrence.eventId] equals [Event.id].
 * @property colorArgb the colour to draw it in, `0xAARRGGBB`: [Event.colorArgb], or the calendar's
 *   colour when that is `null`.
 * @property deviceZone the zone the agenda was built in; [start], [end] and the dates are in it.
 * @throws IllegalArgumentException if [occurrence] belongs to another event.
 */
public data class AgendaEntry(
    val event: Event,
    val occurrence: Occurrence,
    val colorArgb: Int,
    val deviceZone: ZoneId,
) {
    init {
        require(occurrence.eventId == event.id) {
            "Occurrence of event ${occurrence.eventId} paired with event ${event.id}"
        }
    }

    /** `true` when the occurrence names whole dates and has no time to show. */
    public val isAllDay: Boolean get() = occurrence.allDay

    /** The resolved start in [deviceZone] ([Occurrence.start]). For display and for ordering. */
    public val start: ZonedDateTime get() = occurrence.start(deviceZone)

    /** The resolved exclusive end in [deviceZone] ([Occurrence.end]). */
    public val end: ZonedDateTime get() = occurrence.end(deviceZone)

    /** First device-zone date the entry is shown on ([Occurrence.dates]). */
    public val firstDate: LocalDate get() = occurrence.dates(deviceZone).start

    /** Last device-zone date the entry is shown on; equal to [firstDate] for most events. */
    public val lastDate: LocalDate get() = occurrence.dates(deviceZone).endInclusive
}

/**
 * Everything on one device-zone date: the event occurrences that touch it and the holidays on it
 * (`docs/ARCHITECTURE.md` §3.4). The date is Gregorian; map it to a grid cell — including the Year Day
 * and Leap Day bands — with `IfcDate.from` (CLAUDE.md rules 1, 4 and 6).
 *
 * The constructor checks its input rather than sorting it, so an agenda that exists is always in the
 * documented order; build one from unsorted input with [of].
 *
 * @property date the local date, in the zone the agenda was built in.
 * @property entries occurrences touching [date], in [ENTRY_ORDER].
 * @property holidays holidays on [date] from the enabled sets, in [HolidayOccurrence]'s natural order.
 * @throws IllegalArgumentException if an entry does not touch [date], a holiday is on another date,
 *   or either list is out of order.
 */
public data class DayAgenda(
    val date: LocalDate,
    val entries: List<AgendaEntry>,
    val holidays: List<HolidayOccurrence>,
) {
    init {
        require(entries.all { date >= it.firstDate && date <= it.lastDate }) {
            "An agenda entry does not touch $date"
        }
        require(holidays.all { it.date == date }) { "A holiday is not on $date" }
        require(entries == entries.sortedWith(ENTRY_ORDER)) { "Agenda entries for $date are not in ENTRY_ORDER" }
        require(holidays == holidays.sorted()) { "Holidays for $date are not in their natural order" }
    }

    /** `true` when there is nothing on the day. [ObserveAgendaUseCase] never emits such an agenda. */
    public val isEmpty: Boolean get() = entries.isEmpty() && holidays.isEmpty()

    /** Ordering and factories. */
    public companion object {
        /**
         * The order of [entries]: all-day before timed, then by resolved start instant, then
         * [Event.id], then the occurrence's nominal start. Total for the entries of one agenda, and
         * independent of titles, so a rename never reorders a day.
         */
        public val ENTRY_ORDER: Comparator<AgendaEntry> =
            compareBy<AgendaEntry> { !it.isAllDay }
                .thenBy { it.start.toInstant() }
                .thenBy { it.event.id }
                .thenBy { it.occurrence.startLocal }

        /** A [DayAgenda] for [date] from [entries] and [holidays] in any order. */
        public fun of(
            date: LocalDate,
            entries: Collection<AgendaEntry> = emptyList(),
            holidays: Collection<HolidayOccurrence> = emptyList(),
        ): DayAgenda = DayAgenda(date, entries.sortedWith(ENTRY_ORDER), holidays.sorted())
    }
}
