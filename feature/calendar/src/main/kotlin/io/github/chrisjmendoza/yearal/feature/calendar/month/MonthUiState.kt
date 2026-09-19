package io.github.chrisjmendoza.yearal.feature.calendar.month

import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import java.time.LocalDate

/**
 * What the Month pager shows (docs/FEATURES.md C1, C3, C5, C7; docs/ARCHITECTURE.md §4 "Screen
 * behaviors"). Immutable; a new value is built whenever the page, the settings or the ticker's date
 * change, so "today" is never cached across midnight.
 *
 * Every date is Gregorian (CLAUDE.md rule 4): the grid matches today and the selection by Gregorian
 * date and converts each cell through `:core:calendar` itself.
 *
 * @property currentPage the page the pager last reported, `0..MonthPages.LAST_PAGE`; the page the
 * pager starts on when the screen is first composed.
 * @property today the real today, or `null` before the first date tick (no cell is marked).
 * @property todayPage the page showing [today], or `null` before the first tick. The "Today" action's
 * target; the action is hidden while [currentPage] equals it.
 * @property selected the day last tapped, or `null` for no selection.
 * @property weekdayDisplay which header rows the grids show (the user setting).
 * @property holidaysByMonth holiday label per Gregorian date, keyed by month, for the current page
 * and its two neighbours (the pages kept warm), from the enabled holiday sets. A month without a
 * holiday maps to an empty map; a month not yet evaluated is absent.
 * @property eventCountsByMonth number of event occurrences per Gregorian date, keyed by month, for the
 * same warm pages as [holidaysByMonth] (FEATURES C4). A date with no event is absent (read as `0`); a
 * month not yet evaluated is absent entirely.
 */
data class MonthUiState(
    val currentPage: Int,
    val today: LocalDate?,
    val todayPage: Int?,
    val selected: LocalDate?,
    val weekdayDisplay: WeekdayDisplay = UserSettings.DEFAULT.weekdayDisplay,
    val holidaysByMonth: Map<IfcYearMonth, Map<LocalDate, String>> = emptyMap(),
    val eventCountsByMonth: Map<IfcYearMonth, Map<LocalDate, Int>> = emptyMap(),
)
