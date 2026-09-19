package io.github.chrisjmendoza.yearal.feature.calendar.day

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.AgendaItemUi
import java.time.LocalDate

/**
 * What the Day detail sheet shows (docs/FEATURES.md C5; docs/ARCHITECTURE.md §4 "Screen behaviors").
 * Immutable; a new value is built for every date the `DateTicker` emits and every settings change,
 * so [Loaded.isToday] is never cached across midnight.
 */
sealed interface DayUiState {
    /** Before the first date tick, which arrives immediately on collection. */
    data object Loading : DayUiState

    /**
     * One day, fully formatted by [IfcDateFormatter] so the sheet renders text only and computes
     * nothing.
     *
     * @property date the day in the IFC, the source of every other property.
     * @property gregorianDate the same physical day in the Gregorian calendar.
     * @property ifcLong the long style (`September 8, 2026`, `Leap Day, 2028`, `Year Day, 2026`).
     * @property numeric the canonical numeric style with its mandatory prefix (`IFC 2026-10-08`).
     * @property gregorianLong the Gregorian date with its real weekday (`Thursday, September 17, 2026`).
     * @property nominalWeekday the labelled IFC weekday (`IFC weekday: Sunday`) or `no IFC weekday` on
     * Leap Day and Year Day. **Not the real weekday** — spec §4.1.
     * @property actualWeekday the labelled real weekday (`Actual weekday: Thursday`).
     * @property weekdaysDescription both weekdays in spoken form (`IFC Sunday, actual Thursday`).
     * @property dayAndWeek `Day 260 · Week 38 of 52`, or `Day 169 · outside the weeks` (spec §7.4).
     * @property quarter `Q3` (spec §7.5).
     * @property isToday whether [gregorianDate] is the ticker's current date.
     * @property holidays display labels of the enabled holidays on this day, in holiday-engine order
     * (`Independence Day (observed)`); empty when there are none.
     * @property agenda the day's event occurrences (FEATURES C5), all-day first then by start time
     * ([io.github.chrisjmendoza.yearal.core.domain.event.DayAgenda.ENTRY_ORDER]); empty when there
     * are none.
     */
    data class Loaded(
        val date: IfcDate,
        val gregorianDate: LocalDate,
        val ifcLong: String,
        val numeric: String,
        val gregorianLong: String,
        val nominalWeekday: String,
        val actualWeekday: String,
        val weekdaysDescription: String,
        val dayAndWeek: String,
        val quarter: String,
        val isToday: Boolean,
        val holidays: List<String>,
        val agenda: List<AgendaItemUi> = emptyList(),
    ) : DayUiState
}

/**
 * Builds the [DayUiState.Loaded] for the Gregorian date [day] when the real today is [today]. The
 * IFC date comes from `:core:calendar` and every string from [formatter]; nothing here formats or
 * computes a date itself (CLAUDE.md rules 1 and 9).
 */
fun buildDayUiState(
    day: LocalDate,
    today: LocalDate,
    formatter: IfcDateFormatter,
    holidays: List<String>,
    agenda: List<AgendaItemUi> = emptyList(),
): DayUiState.Loaded {
    val date = IfcDate.from(day)
    return DayUiState.Loaded(
        date = date,
        gregorianDate = day,
        ifcLong = formatter.formatLong(date),
        numeric = formatter.formatNumeric(date),
        gregorianLong = formatter.formatGregorianLong(day),
        nominalWeekday = formatter.nominalWeekday(date),
        actualWeekday = formatter.actualWeekday(date),
        weekdaysDescription = formatter.weekdaysDescription(date),
        dayAndWeek = formatter.dayAndWeek(date),
        quarter = formatter.quarter(date),
        isToday = day == today,
        holidays = holidays,
        agenda = agenda,
    )
}
