package io.github.chrisjmendoza.yearal.feature.events.list

import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy

/**
 * What the events list (`EventListKey`, `docs/FEATURES.md` E1, E3, E5–E7, E9) shows
 * (`docs/ARCHITECTURE.md` §4 "State management"). Immutable; a new value is built for every change of
 * [io.github.chrisjmendoza.yearal.core.domain.event.EventRepository.observeEvents],
 * [io.github.chrisjmendoza.yearal.core.domain.event.EventRepository.observeCalendars] or the search text.
 */
sealed interface EventListUiState {
    /** Before the repository's flows have emitted their first value. */
    data object Loading : EventListUiState

    /**
     * @property items the rows to show, already filtered by [query] and in
     * [io.github.chrisjmendoza.yearal.core.domain.event.Event.LIST_ORDER] (the contract's list order).
     * @property query the search text as typed; empty means "no filter".
     * @property hasAnyEvents `false` only when the repository has no events at all — distinguishes the
     * "create your first event" empty state from "no matches" for [query].
     */
    data class Loaded(
        val items: List<EventListItem>,
        val query: String,
        val hasAnyEvents: Boolean,
    ) : EventListUiState
}

/**
 * One row of the events list: an event joined with its calendar's colour and visibility, and the
 * date/time/recurrence text pre-formatted through
 * [io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter] so the screen renders text
 * only (CLAUDE.md rules 1, 5).
 *
 * @property eventId the event's id, for navigating to the editor (CLAUDE.md rule 8: ids only).
 * @property title the event's own title; blank when the user left it blank — the screen shows a
 * localized placeholder (`docs/contracts/Events.md` T5 guidance).
 * @property calendarName the event's calendar name; blank for the built-in calendar until renamed —
 * the screen shows a localized placeholder, matching [io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar].
 * @property calendarColorArgb the event's own colour, or its calendar's, as `0xAARRGGBB`.
 * @property calendarHidden `true` when the event's calendar is not visible ([calendarColorArgb] and
 * the rest are still shown — the events list is a management view, unlike the agenda).
 * @property ifcLong the start date in the IFC long style, e.g. `September 8, 2026`.
 * @property ifcNumeric the same start date in the canonical numeric style with its mandatory `IFC`
 * prefix, e.g. `IFC 2026-10-08` ([io.github.chrisjmendoza.yearal.core.calendar.IfcDate.toPrefixedString], CLAUDE.md rule 5).
 * @property gregorianLong the same start date in the Gregorian long style, weekday included.
 * @property isAllDay `true` for an all-day event; the screen shows the "all day" label instead of [timeLabel].
 * @property timeLabel the start time in the locale's short style (e.g. `9:30 AM`), or `null` for an
 * all-day event.
 * @property zoneLabel the fixed zone id, or `null` when floating or all-day; shown next to [timeLabel].
 * @property recurrenceSummary how the event repeats, or `null` for a one-off event.
 */
data class EventListItem(
    val eventId: Long,
    val title: String,
    val calendarName: String,
    val calendarColorArgb: Int,
    val calendarHidden: Boolean,
    val ifcLong: String,
    val ifcNumeric: String,
    val gregorianLong: String,
    val isAllDay: Boolean,
    val timeLabel: String?,
    val zoneLabel: String?,
    val recurrenceSummary: RecurrenceSummary?,
)

/**
 * How an event repeats, in the shape the screen turns into localized text (CLAUDE.md rule 9): every
 * branch carries only the data a string resource needs, never English text (CLAUDE.md rule 6 — every
 * [io.github.chrisjmendoza.yearal.core.domain.event.Recurrence] and
 * [io.github.chrisjmendoza.yearal.core.domain.event.IntercalaryDay] shape has one).
 */
sealed interface RecurrenceSummary {
    /** Yearly on a regular IFC date. [dayLabel] is the day named in its month, e.g. `Sol 13`. */
    data class YearlyIfc(
        val dayLabel: String,
    ) : RecurrenceSummary

    /** Yearly on Year Day. */
    data object YearlyYearDay : RecurrenceSummary

    /** Yearly on Leap Day, with the policy that applies in a common rule year. */
    data class YearlyLeapDay(
        val policy: LeapDayPolicy,
    ) : RecurrenceSummary

    /** Monthly on the same IFC day of every month. */
    data class MonthlyIfc(
        val day: Int,
    ) : RecurrenceSummary

    /** Yearly on a Gregorian date. [dayLabel] is the Gregorian month and day, e.g. `Jun 18`. */
    data class YearlyGregorian(
        val dayLabel: String,
    ) : RecurrenceSummary

    /** Weekly, the real seven-day week. */
    data object Weekly : RecurrenceSummary

    /** A Gregorian rule this app's editor did not create (an import, in a later release). */
    data object OtherRecurring : RecurrenceSummary
}
