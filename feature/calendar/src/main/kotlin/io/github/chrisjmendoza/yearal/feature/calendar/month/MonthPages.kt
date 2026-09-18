package io.github.chrisjmendoza.yearal.feature.calendar.month

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.navigation.MonthKey

/**
 * The page index space of the month pager: one page per IFC month of the UI year range
 * `docs/calendar-spec.md` §7.1 fixes (1583..9999), page 0 = January 1583, the last page = December
 * 9999. The mapping is arithmetic on the year and the month ordinal only; no date is computed here
 * (CLAUDE.md rule 1).
 */
object MonthPages {
    /** First year the pager can show (spec §7.1: the first full Gregorian year). */
    const val FIRST_YEAR: Int = 1583

    /** Last year the pager can show, the library's own limit. */
    const val LAST_YEAR: Int = IfcDate.MAX_YEAR

    /** Number of pages: 13 per year from [FIRST_YEAR] to [LAST_YEAR]. */
    const val COUNT: Int = (LAST_YEAR - FIRST_YEAR + 1) * IfcMonth.MONTHS_PER_YEAR

    /** The last valid page index, [COUNT] − 1. */
    const val LAST_PAGE: Int = COUNT - 1

    /**
     * The page showing [month]. A year before [FIRST_YEAR] (the library allows 1..9999) lands on the
     * same month of [FIRST_YEAR], so a caller can never scroll outside the pager.
     */
    fun pageOf(month: IfcYearMonth): Int =
        (month.year.coerceIn(FIRST_YEAR, LAST_YEAR) - FIRST_YEAR) * IfcMonth.MONTHS_PER_YEAR + month.month.ordinal

    /**
     * The month shown on [page].
     *
     * @throws IllegalArgumentException if [page] is not in `0..LAST_PAGE`.
     */
    fun monthAt(page: Int): IfcYearMonth {
        require(page in 0..LAST_PAGE) { "Page out of range: $page" }
        return IfcYearMonth(
            year = FIRST_YEAR + page / IfcMonth.MONTHS_PER_YEAR,
            month = IfcMonth.entries[page % IfcMonth.MONTHS_PER_YEAR],
        )
    }

    /**
     * The month a [MonthKey] asks for, clamped into the pager's range: the year into
     * [FIRST_YEAR]..[LAST_YEAR] and the month number into 1..13, so a key built from foreign input (an
     * intent extra) can never crash the screen.
     */
    fun monthOf(key: MonthKey): IfcYearMonth =
        IfcYearMonth(
            year = key.year.coerceIn(FIRST_YEAR, LAST_YEAR),
            month = IfcMonth.of(key.month.coerceIn(1, IfcMonth.MONTHS_PER_YEAR)),
        )
}
