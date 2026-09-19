package io.github.chrisjmendoza.yearal.feature.calendar.year

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange
import java.time.LocalDate

/**
 * What the Year overview shows (docs/FEATURES.md C6; docs/ARCHITECTURE.md §4 "Screen behaviors";
 * `docs/calendar-spec.md` §7.2). Immutable; a new value is built whenever the shown year, the ticker's
 * date or the year's event presence change, so "today" is never cached across midnight (CLAUDE.md
 * rule 2) and a year rollover (December 31 → January 1) is a plain change of [year] and [today] like
 * any other date.
 *
 * @property year the IFC year shown, always inside [DatePickerRange] (1583..9999); [io.github.chrisjmendoza.yearal.feature.calendar.year.YearViewModel]
 * clamps it, so the screen never has to.
 * @property today the real today as a Gregorian date, or `null` before the first date tick.
 * @property eventDates the dates of [year] with at least one event occurrence, from
 * `ObserveAgendaUseCase.presence` for the whole year in one query (docs/ARCHITECTURE.md §3.4).
 * Holidays are not included (CLAUDE.md rule 6 does not apply here: this set is events only, by design).
 * @property canGoPrevious whether [year] `- 1` is still inside [DatePickerRange] (the "previous year"
 * action is disabled otherwise).
 * @property canGoNext whether [year] `+ 1` is still inside [DatePickerRange].
 */
data class YearUiState(
    val year: Int,
    val today: LocalDate?,
    val eventDates: Set<LocalDate> = emptySet(),
    val canGoPrevious: Boolean = year > DatePickerRange.MIN_YEAR,
    val canGoNext: Boolean = year < DatePickerRange.MAX_YEAR,
) {
    /**
     * The 13 IFC months of [year] in calendar order (Sol between June and July), the grid's first 13
     * tiles; Year Day is the grid's 14th item, built separately by the screen since it is not a month.
     */
    val months: List<IfcYearMonth> get() = IfcMonth.entries.map { month -> IfcYearMonth(year, month) }
}
