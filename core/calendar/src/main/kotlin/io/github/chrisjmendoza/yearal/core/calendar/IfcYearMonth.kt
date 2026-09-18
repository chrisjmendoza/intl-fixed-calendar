package io.github.chrisjmendoza.yearal.core.calendar

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year

/**
 * One month of one IFC year: the unit the month grid and event range queries are built on.
 *
 * A month is always 28 regular days laid out as four Sunday-to-Saturday weeks. June in leap years and
 * every December are followed by an intercalary day, exposed as [trailingIntercalary]; it is part of the
 * month's [gregorianRange] but not of its weeks.
 *
 * Spec: `docs/calendar-spec.md` §2.2–§2.4; `docs/ARCHITECTURE.md` §3.1.
 *
 * @property year the year, [IfcDate.MIN_YEAR]..[IfcDate.MAX_YEAR].
 * @property month the month.
 * @throws DateTimeException if [year] is out of range.
 */
public data class IfcYearMonth(
    val year: Int,
    val month: IfcMonth,
) : Comparable<IfcYearMonth> {
    init {
        IfcDate.requireYear(year)
    }

    /** The 1st of this month, always a nominal Sunday. */
    val firstDay: IfcDate.Regular get() = IfcDate.Regular(year, month, 1)

    /** The 28th of this month, always a nominal Saturday. */
    val lastRegularDay: IfcDate.Regular get() = IfcDate.Regular(year, month, IfcMonth.DAYS_PER_MONTH)

    /**
     * The intercalary day that follows this month: [IfcDate.LeapDay] after June in leap years,
     * [IfcDate.YearDay] after December, otherwise `null`.
     */
    val trailingIntercalary: IfcDate?
        get() =
            when {
                month == IfcMonth.JUNE && Year.isLeap(year.toLong()) -> IfcDate.LeapDay(year)
                month == IfcMonth.DECEMBER -> IfcDate.YearDay(year)
                else -> null
            }

    /**
     * The Gregorian dates this month covers: 28 days, or 29 including [trailingIntercalary].
     * The range is always contiguous, so it can drive a single storage query.
     */
    val gregorianRange: ClosedRange<LocalDate>
        get() = firstDay.toLocalDate()..(trailingIntercalary ?: lastRegularDay).toLocalDate()

    /** The 28 regular days in order, followed by [trailingIntercalary] when there is one. */
    val days: List<IfcDate>
        get() =
            buildList {
                for (day in 1..IfcMonth.DAYS_PER_MONTH) add(IfcDate.Regular(year, month, day))
                trailingIntercalary?.let(::add)
            }

    /**
     * Returns the real-world weekday of every day in grid [column] (0 = the nominal Sunday column,
     * 6 = the nominal Saturday column). It is constant down the column because a month is four whole
     * weeks.
     *
     * @throws IllegalArgumentException if [column] is not in 0..6.
     */
    public fun actualDayOfWeek(column: Int): DayOfWeek {
        require(column in 0 until DAYS_PER_WEEK) { "Column out of range: $column" }
        return IfcDate.Regular(year, month, column + 1).actualDayOfWeek
    }

    /**
     * Returns the month [months] after this one (before it, if negative), rolling over year boundaries.
     *
     * @throws DateTimeException if the result is outside the supported year range.
     */
    public fun plusMonths(months: Long): IfcYearMonth {
        val index =
            try {
                Math.addExact(year.toLong() * IfcMonth.MONTHS_PER_YEAR + month.ordinal, months)
            } catch (e: ArithmeticException) {
                throw DateTimeException("IFC year out of range after adding $months months", e)
            }
        val newYear = Math.floorDiv(index, IfcMonth.MONTHS_PER_YEAR.toLong())
        if (newYear !in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) throw DateTimeException("IFC year out of range: $newYear")
        val newMonth = Math.floorMod(index, IfcMonth.MONTHS_PER_YEAR.toLong()).toInt()
        return IfcYearMonth(newYear.toInt(), IfcMonth.entries[newMonth])
    }

    /** Orders months chronologically. */
    override fun compareTo(other: IfcYearMonth): Int =
        compareValuesBy(this, other, IfcYearMonth::year, IfcYearMonth::month)

    /** Factories for [IfcYearMonth]. */
    public companion object {
        private const val DAYS_PER_WEEK = 7

        /**
         * Returns the month whose grid shows [date]. An intercalary day belongs to the month it follows,
         * so Leap Day maps to June and Year Day to December.
         */
        public fun from(date: IfcDate): IfcYearMonth = IfcYearMonth(date.year, IfcMonth.of(date.monthNumber))
    }
}
