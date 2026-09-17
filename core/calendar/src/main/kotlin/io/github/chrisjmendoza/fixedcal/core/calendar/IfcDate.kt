package io.github.chrisjmendoza.fixedcal.core.calendar

import java.time.Clock
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeParseException

/**
 * A date in the International Fixed Calendar (IFC).
 *
 * A date is exactly one of three shapes: a [Regular] day inside a month, or one of the two intercalary
 * days, [LeapDay] and [YearDay], which belong to no week and have no weekday. The sealed hierarchy forces
 * every `when` over a date to handle the intercalary days.
 *
 * The IFC year and day of year always equal the Gregorian ones, so conversion is pure integer arithmetic.
 * Instances are immutable and totally ordered by ([year], [dayOfYear]).
 *
 * There is deliberately **no bare `dayOfWeek`**: use [nominalDayOfWeek] for the weekday the IFC assigns
 * and [actualDayOfWeek] for the real-world weekday. They differ in most years, and neither may be
 * derived from the other.
 *
 * Spec: `docs/calendar-spec.md` §3 (algorithms), §4 (this model), §7.3 (numeric form).
 */
public sealed interface IfcDate : Comparable<IfcDate> {
    /** The year, [MIN_YEAR]..[MAX_YEAR]. Always equal to the Gregorian year of the same physical day. */
    public val year: Int

    /** Day of year, 1..365 or 1..366. Always equal to `toLocalDate().dayOfYear`. */
    public val dayOfYear: Int

    /**
     * Month number 1..13. Intercalary days report the month they follow: [LeapDay] is 6, [YearDay] is 13.
     *
     * A pseudo-field for numeric formatting, parsing and sorting; intercalary days belong to no month.
     */
    public val monthNumber: Int

    /** Day of month: 1..28 for regular days, 29 for the intercalary days ("June 29", "December 29"). */
    public val dayOfMonth: Int

    /**
     * The weekday the IFC assigns to this date, fixed by [dayOfMonth] and identical in every month and
     * year (the 1st is Sunday, the 13th is Friday). `null` for intercalary days.
     *
     * This is **not** the real-world weekday; see [actualDayOfWeek].
     */
    public val nominalDayOfWeek: DayOfWeek?

    /** IFC week of year, 1..52, or `null` for intercalary days. Not an ISO-8601 week number. */
    public val weekOfYear: Int?

    /** The 13-week quarter, 1..4. Intercalary days count in the quarter they follow (2 and 4). */
    public val quarter: Int

    /** `true` for [LeapDay] and [YearDay]. */
    public val isIntercalary: Boolean

    /** The real-world weekday of this physical day, from the continuous seven-day cycle. Never null. */
    public val actualDayOfWeek: DayOfWeek get() = toLocalDate().dayOfWeek

    /** Returns the same physical day in the Gregorian (ISO) calendar. */
    public fun toLocalDate(): LocalDate = LocalDate.ofYearDay(year, dayOfYear)

    /**
     * Returns the canonical numeric form `YYYY-MM-DD` with months `01`..`13`, Leap Day as `06-29` and
     * Year Day as `13-29`. It sorts correctly as text.
     *
     * This form is indistinguishable from an ISO date, so anything a user can see must use
     * [toPrefixedString] instead.
     */
    public fun toNumericString(): String = "%04d-%02d-%02d".format(year, monthNumber, dayOfMonth)

    /** Returns [toNumericString] with the mandatory user-visible marker, e.g. `IFC 2026-10-08`. */
    public fun toPrefixedString(): String = NUMERIC_PREFIX + toNumericString()

    /** Orders dates chronologically by ([year], [dayOfYear]). */
    override fun compareTo(other: IfcDate): Int = compareValuesBy(this, other, IfcDate::year, IfcDate::dayOfYear)

    /**
     * One of the 364 days of a year that belong to a month and a week.
     *
     * @property month the month this day belongs to.
     * @throws DateTimeException if [year] is out of range or [dayOfMonth] is not in 1..28.
     */
    public data class Regular(
        override val year: Int,
        val month: IfcMonth,
        override val dayOfMonth: Int,
    ) : IfcDate {
        init {
            requireYear(year)
            if (dayOfMonth !in 1..IfcMonth.DAYS_PER_MONTH) {
                throw DateTimeException("Invalid IFC day of month: $dayOfMonth")
            }
        }

        /** Index among the regular days of the year, 1..364. Intercalary days are not counted. */
        val regularDayIndex: Int get() = (month.number - 1) * IfcMonth.DAYS_PER_MONTH + dayOfMonth

        override val monthNumber: Int get() = month.number

        override val dayOfYear: Int
            get() =
                if (Year.isLeap(year.toLong()) && regularDayIndex > LAST_REGULAR_DAY_BEFORE_LEAP_DAY) {
                    regularDayIndex + 1
                } else {
                    regularDayIndex
                }

        override val nominalDayOfWeek: DayOfWeek get() = NOMINAL_WEEK[(dayOfMonth - 1) % DAYS_PER_WEEK]

        override val weekOfYear: Int get() = (regularDayIndex - 1) / DAYS_PER_WEEK + 1

        override val quarter: Int get() = (regularDayIndex - 1) / DAYS_PER_QUARTER + 1

        override val isIntercalary: Boolean get() = false
    }

    /**
     * The day after June 28 in leap years (Gregorian June 17). It has no week and no weekday.
     *
     * @throws DateTimeException if [year] is out of range or is not a leap year.
     */
    public data class LeapDay(
        override val year: Int,
    ) : IfcDate {
        init {
            requireYear(year)
            if (!Year.isLeap(year.toLong())) {
                throw DateTimeException("Leap Day does not exist in common year $year")
            }
        }

        override val dayOfYear: Int get() = LEAP_DAY_OF_YEAR

        override val monthNumber: Int get() = IfcMonth.JUNE.number

        override val dayOfMonth: Int get() = INTERCALARY_DAY_OF_MONTH

        override val nominalDayOfWeek: DayOfWeek? get() = null

        override val weekOfYear: Int? get() = null

        override val quarter: Int get() = 2

        override val isIntercalary: Boolean get() = true
    }

    /**
     * The day after December 28, the last day of every year (Gregorian December 31). It has no week and
     * no weekday.
     *
     * @throws DateTimeException if [year] is out of range.
     */
    public data class YearDay(
        override val year: Int,
    ) : IfcDate {
        init {
            requireYear(year)
        }

        override val dayOfYear: Int get() = Year.of(year).length()

        override val monthNumber: Int get() = IfcMonth.DECEMBER.number

        override val dayOfMonth: Int get() = INTERCALARY_DAY_OF_MONTH

        override val nominalDayOfWeek: DayOfWeek? get() = null

        override val weekOfYear: Int? get() = null

        override val quarter: Int get() = 4

        override val isIntercalary: Boolean get() = true
    }

    /** Factories and constants for [IfcDate]. */
    public companion object {
        /** The earliest supported year (proleptic Gregorian, as in `java.time`). */
        public const val MIN_YEAR: Int = 1

        /** The latest supported year. */
        public const val MAX_YEAR: Int = 9999

        /** The marker that must precede the numeric form wherever a user can see it. */
        public const val NUMERIC_PREFIX: String = "IFC "

        private const val DAYS_PER_WEEK = 7
        private const val DAYS_PER_QUARTER = 91
        private const val LAST_REGULAR_DAY_BEFORE_LEAP_DAY = 168
        private const val LEAP_DAY_OF_YEAR = 169
        private const val INTERCALARY_DAY_OF_MONTH = 29

        private val NOMINAL_WEEK =
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            )

        private val NUMERIC_FORM = Regex("""(?:IFC )?(\d{4})-(\d{2})-(\d{2})""")

        /**
         * Converts a Gregorian date to the IFC (spec §3.1). Total over the supported range.
         *
         * @throws DateTimeException if the year of [date] is outside [MIN_YEAR]..[MAX_YEAR].
         */
        public fun from(date: LocalDate): IfcDate {
            val year = date.year
            requireYear(year)
            val leap = date.isLeapYear
            val n = date.dayOfYear
            if (n == date.lengthOfYear()) return YearDay(year)
            if (leap && n == LEAP_DAY_OF_YEAR) return LeapDay(year)
            val r = if (leap && n > LEAP_DAY_OF_YEAR) n - 1 else n
            return Regular(
                year = year,
                month = IfcMonth.of((r - 1) / IfcMonth.DAYS_PER_MONTH + 1),
                dayOfMonth = (r - 1) % IfcMonth.DAYS_PER_MONTH + 1,
            )
        }

        /**
         * Builds a date from the numeric pseudo-fields (spec §3.3 rule V5).
         *
         * Day 29 is accepted only for month 6 in leap years, meaning [LeapDay], and for month 13, meaning
         * [YearDay]. Nothing is resolved leniently: "Sol 29" is an error, not July 1.
         *
         * @throws DateTimeException if the fields do not name a valid IFC date.
         */
        public fun of(
            year: Int,
            monthNumber: Int,
            dayOfMonth: Int,
        ): IfcDate {
            requireYear(year)
            val month = IfcMonth.of(monthNumber)
            if (dayOfMonth != INTERCALARY_DAY_OF_MONTH) return Regular(year, month, dayOfMonth)
            return when (month) {
                IfcMonth.JUNE -> LeapDay(year)
                IfcMonth.DECEMBER -> YearDay(year)
                else -> throw DateTimeException("Day 29 exists only after June (leap years) and December")
            }
        }

        /**
         * Returns the IFC date for the given day of year, which is the same number in both calendars.
         *
         * @throws DateTimeException if [year] or [dayOfYear] is out of range.
         */
        public fun ofYearDay(
            year: Int,
            dayOfYear: Int,
        ): IfcDate {
            requireYear(year)
            return from(LocalDate.ofYearDay(year, dayOfYear))
        }

        /**
         * Returns today's IFC date according to [clock].
         *
         * "Today" depends on both an instant and a time zone, so a clock is mandatory; the app injects
         * one so that tests can cross midnight and change zones (spec §7.8).
         */
        public fun now(clock: Clock): IfcDate = from(LocalDate.now(clock))

        /**
         * Parses the canonical numeric form (spec §7.3), with or without the `IFC ` prefix.
         *
         * The syntax is strict: four-digit year, two-digit month `01`..`13`, two-digit day.
         *
         * @throws DateTimeParseException if [text] is not in that syntax.
         * @throws DateTimeException if the fields do not name a valid IFC date.
         */
        public fun parse(text: CharSequence): IfcDate {
            val match =
                NUMERIC_FORM.matchEntire(text)
                    ?: throw DateTimeParseException("Text is not an IFC numeric date (YYYY-MM-DD)", text, 0)
            val (year, month, day) = match.destructured
            return of(year.toInt(), month.toInt(), day.toInt())
        }

        internal fun requireYear(year: Int) {
            if (year !in MIN_YEAR..MAX_YEAR) throw DateTimeException("IFC year out of range: $year")
        }
    }
}

/** Converts this Gregorian date to the IFC. Shorthand for [IfcDate.from]. */
public fun LocalDate.toIfcDate(): IfcDate = IfcDate.from(this)
