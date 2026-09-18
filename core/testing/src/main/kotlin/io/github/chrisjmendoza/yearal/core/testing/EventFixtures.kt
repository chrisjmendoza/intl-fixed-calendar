package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.domain.event.AgendaEntry
import io.github.chrisjmendoza.yearal.core.domain.event.DayAgenda
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventCategory
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.IntercalaryDay
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.domain.event.Occurrence
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import io.github.chrisjmendoza.yearal.core.domain.event.Reminder
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ready-made [Event]s for tests, covering the cases every events screen must handle: the two floating
 * days, each Leap Day policy, Sol, a zoned timed event and a floating multi-day one (CLAUDE.md rule
 * 6; FEATURES E3, E5, E6, E7).
 *
 * All dates are Gregorian, with the IFC date in the name and the KDoc. The mapping is fixed by
 * `docs/holidays-and-import.md` §5.1: Sol 1 is June 18, Year Day is December 31, Leap Day is June 17
 * of a leap year.
 *
 * Every builder takes the [Event.id] to use (`0` for "not stored yet") and derives a distinct
 * [Event.uid] from the fixture's name and that id, so fixtures can be stored side by side in a
 * [FakeEventRepository].
 */
public object EventFixtures {
    /** IFC Sol 13, 2026 (nominal Friday) — Gregorian Tuesday, June 30, 2026. */
    public val SOL_13_2026: LocalDate = LocalDate.of(2026, 6, 30)

    /** Year Day 2026 — Gregorian Thursday, December 31, 2026. No IFC weekday. */
    public val YEAR_DAY_2026: LocalDate = LocalDate.of(2026, 12, 31)

    /** Leap Day 2024 — Gregorian Monday, June 17, 2024. No IFC weekday. */
    public val LEAP_DAY_2024: LocalDate = LocalDate.of(2024, 6, 17)

    /** Leap Day 2028 — Gregorian Saturday, June 17, 2028. */
    public val LEAP_DAY_2028: LocalDate = LocalDate.of(2028, 6, 17)

    /** A zone with daylight saving, for zoned fixtures. */
    public val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

    /**
     * A one-off all-day event; the base every other all-day fixture is built from.
     *
     * @param days number of dates covered, from [date].
     */
    public fun allDay(
        id: Long = Event.NEW_ID,
        date: LocalDate = SOL_13_2026,
        days: Int = 1,
        title: String = "All-day event",
        recurrence: Recurrence = Recurrence.None,
        calendarId: Long = EventCalendar.DEFAULT_ID,
        category: EventCategory = EventCategory.EVENT,
        reminders: Set<Reminder> = emptySet(),
    ): Event =
        Event(
            id = id,
            uid = "fixture-${title.lowercase().replace(' ', '-')}-$id",
            calendarId = calendarId,
            title = title,
            category = category,
            timing = EventTiming.AllDay(date, days),
            recurrence = recurrence,
            reminders = reminders,
        )

    /** "Every Year Day": all-day, yearly on the IFC intercalary day, anchored on Year Day 2026. */
    public fun yearDayYearly(id: Long = Event.NEW_ID): Event =
        allDay(
            id = id,
            date = YEAR_DAY_2026,
            title = "Year Day party",
            recurrence = IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay),
            category = EventCategory.OBSERVANCE,
        )

    /**
     * "Every Leap Day" with the given common-year [policy], anchored on Leap Day 2024. In 2025–2027
     * the rule yields IFC June 28 (Gregorian June 17), nothing, or Sol 1 (June 18) by policy; in 2028
     * it is Leap Day again.
     */
    public fun leapDayYearly(
        id: Long = Event.NEW_ID,
        policy: LeapDayPolicy = LeapDayPolicy.JUNE_28,
    ): Event =
        allDay(
            id = id,
            date = LEAP_DAY_2024,
            title = "Leap Day birthday ${policy.name}",
            recurrence = IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy)),
            category = EventCategory.BIRTHDAY,
        )

    /** "Every Sol 13": all-day, yearly on the IFC date, anchored on Sol 13, 2026 (ROADMAP M4 exit). */
    public fun sol13Yearly(id: Long = Event.NEW_ID): Event =
        allDay(
            id = id,
            date = SOL_13_2026,
            title = "Sol 13 picnic",
            recurrence = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13),
        )

    /** "The 13th of every IFC month": 13 occurrences a year, anchored on Sol 13, 2026 (FEATURES E7). */
    public fun thirteenthMonthly(id: Long = Event.NEW_ID): Event =
        allDay(
            id = id,
            date = SOL_13_2026,
            title = "Friday the 13th",
            recurrence = IfcRecurrence.MonthlyOnDay(13),
        )

    /**
     * A timed event fixed in New York: 09:30–10:15 on Sunday, March 8, 2026 — the day daylight
     * saving starts there, so the offset is already EDT (UTC−4) — with a 10-minute reminder. Seen from
     * `Asia/Tokyo` it is 22:30–23:15 the same day.
     */
    public fun timedZoned(id: Long = Event.NEW_ID): Event =
        Event(
            id = id,
            uid = "fixture-timed-zoned-$id",
            title = "Call with New York",
            location = "Video",
            timing = EventTiming.Timed(LocalDate.of(2026, 3, 8), 9 * 60 + 30, 45, NEW_YORK),
            reminders = setOf(Reminder(10)),
        )

    /** A Gregorian weekly event: floating, Mondays 18:00–19:00 from January 5, 2026 (ROADMAP M4 exit). */
    public fun weeklyGregorian(id: Long = Event.NEW_ID): Event =
        Event(
            id = id,
            uid = "fixture-weekly-gregorian-$id",
            title = "Choir practice",
            timing = EventTiming.Timed(LocalDate.of(2026, 1, 5), 18 * 60, 60),
            recurrence = Recurrence.Gregorian("FREQ=WEEKLY;BYDAY=MO"),
        )

    /**
     * A floating all-day event over three dates that straddles the year: December 30, **Year Day**
     * (December 31) and January 1, 2027. On the grid it covers IFC December 28, the Year Day band, and
     * January 1 of the next IFC year.
     */
    public fun floatingMultiDay(id: Long = Event.NEW_ID): Event =
        allDay(id = id, date = LocalDate.of(2026, 12, 30), days = 3, title = "New Year trip")

    /**
     * One of every fixture above, **not stored yet** (id `0`), in a stable order: Year Day, Leap Day
     * with `JUNE_28`, `SKIP` and `SOL_1`, Sol 13 yearly, the 13th monthly, timed zoned, weekly
     * Gregorian, floating multi-day. [FakeEventRepository.seed] stores them as ids 1..9 in that order.
     */
    public fun all(): List<Event> =
        listOf(
            yearDayYearly(),
            leapDayYearly(policy = LeapDayPolicy.JUNE_28),
            leapDayYearly(policy = LeapDayPolicy.SKIP),
            leapDayYearly(policy = LeapDayPolicy.SOL_1),
            sol13Yearly(),
            thirteenthMonthly(),
            timedZoned(),
            weeklyGregorian(),
            floatingMultiDay(),
        )

    /**
     * An [AgendaEntry] for [event], by default for its own first occurrence, in the default calendar
     * colour, seen from [deviceZone].
     */
    public fun entry(
        event: Event,
        occurrence: Occurrence = event.firstOccurrence(),
        deviceZone: ZoneId = ZoneId.of("UTC"),
        colorArgb: Int = event.colorArgb ?: EventCalendar.DEFAULT_COLOR_ARGB,
    ): AgendaEntry = AgendaEntry(event, occurrence, colorArgb, deviceZone)

    /** A [DayAgenda] for [date] holding the first occurrence of each of [events]. */
    public fun dayAgenda(
        date: LocalDate,
        vararg events: Event,
    ): DayAgenda = DayAgenda.of(date, events.map { entry(it) })
}
