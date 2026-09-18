package io.github.chrisjmendoza.yearal.core.domain.holiday

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * How a holiday's date is found for a given year.
 *
 * A rule is pure data; [HolidayEngine] evaluates it. Every rule yields at most **one anchor date per
 * year** (multi-day holidays are expanded from the anchor by [HolidayDefinition.durationDays]), and a
 * rule may yield **no date** in a year (a 5th weekday that does not exist, Feb 29 in a common year, a
 * year missing from a [Table], Leap Day in a common year).
 *
 * **Weekday rules are evaluated on Gregorian civil weekdays and Gregorian months, never on IFC
 * nominal weekdays** (CLAUDE.md rule 3). "The 4th Thursday of November" is a real Thursday in
 * Gregorian November, wherever that lands on the IFC grid.
 *
 * The `calendar` rule type of the spec (Hebrew, Islamic, Chinese) is deliberately absent: those
 * holidays ship as pre-generated [Table]s (FEATURES H4, `docs/holidays-and-import.md` §2.3).
 *
 * Spec: `docs/holidays-and-import.md` §2.2 (rule types), §5 (IFC-native rules);
 * `docs/adr/0003-holiday-rule-model.md` for the decisions the spec left open.
 */
public sealed interface HolidayRule {
    /**
     * A fixed Gregorian month and day, e.g. Independence Day (7, 4).
     *
     * **February 29 yields no occurrence in common years**; it is not moved to Feb 28 or Mar 1
     * (`docs/adr/0003-holiday-rule-model.md`).
     *
     * @property month Gregorian month, 1..12. Not an IFC month number.
     * @property day day of that month, 1..31, at most the month's longest length (Feb 29 is allowed).
     * @throws IllegalArgumentException if [month] or [day] is out of range.
     */
    public data class Fixed(
        val month: Int,
        val day: Int,
    ) : HolidayRule {
        init {
            requireGregorianMonthDay(month, day)
        }
    }

    /**
     * The nth occurrence of a weekday in a Gregorian month, e.g. Thanksgiving (11, THURSDAY, 4) or
     * Memorial Day (5, MONDAY, -1).
     *
     * A positive [n] counts from the start of the month; a negative [n] counts from the end (-1 is the
     * last). If the month has no such weekday (a 5th Monday in a month with only four), the rule yields
     * no occurrence that year.
     *
     * @property month Gregorian month, 1..12.
     * @property weekday the real (Gregorian civil) weekday.
     * @property n 1..5 or -5..-1; never 0.
     * @throws IllegalArgumentException if [month] or [n] is out of range.
     */
    public data class NthWeekday(
        val month: Int,
        val weekday: DayOfWeek,
        val n: Int,
    ) : HolidayRule {
        init {
            require(month in 1..MONTHS_PER_GREGORIAN_YEAR) { "Gregorian month out of range: $month" }
            require(n in 1..MAX_WEEKDAY_ORDINAL || n in -MAX_WEEKDAY_ORDINAL..-1) {
                "n must be in 1..$MAX_WEEKDAY_ORDINAL or -$MAX_WEEKDAY_ORDINAL..-1: $n"
            }
        }
    }

    /**
     * The first [weekday] on or after, or on or before, a fixed Gregorian date, e.g. Victoria Day, the
     * last Monday before May 25, which is (MONDAY, 5, 24, ON_OR_BEFORE).
     *
     * The reference date follows [Fixed]: if it does not exist in a year (Feb 29 in a common year) the
     * rule yields no occurrence that year.
     *
     * @property weekday the real (Gregorian civil) weekday to find.
     * @property month Gregorian month of the reference date, 1..12.
     * @property day day of the reference date, 1..31, at most the month's longest length.
     * @property direction which side of the reference date to search.
     * @throws IllegalArgumentException if [month] or [day] is out of range.
     */
    public data class WeekdayRelative(
        val weekday: DayOfWeek,
        val month: Int,
        val day: Int,
        val direction: Direction,
    ) : HolidayRule {
        init {
            requireGregorianMonthDay(month, day)
        }

        /** Search direction for [WeekdayRelative]. The reference date itself qualifies in both. */
        public enum class Direction {
            /** The first matching weekday on the reference date or after it. */
            ON_OR_AFTER,

            /** The last matching weekday on the reference date or before it. */
            ON_OR_BEFORE,
        }
    }

    /**
     * A fixed number of days from another rule, e.g. Black Friday (1, Thanksgiving) or Election Day
     * (1, first Monday of November).
     *
     * If [base] yields no occurrence in a year, neither does this rule. **The result may fall in an
     * adjacent year** (Jan 1 minus one day is Dec 31 of the previous year); see
     * [HolidayEngine.occurrences] for how that interacts with per-year evaluation.
     *
     * @property days the shift in days; negative moves earlier. Zero is allowed and is a no-op.
     * @property base the rule the offset applies to; may itself be an [Offset].
     */
    public data class Offset(
        val days: Int,
        val base: HolidayRule,
    ) : HolidayRule

    /**
     * Easter Sunday of a tradition, plus an [offset] in days: Good Friday is (WESTERN, -2), Ash
     * Wednesday -46, Mardi Gras -47, Pentecost +49, Orthodox Easter (ORTHODOX, 0).
     *
     * Western Easter uses the Gregorian computus (Meeus/Jones/Butcher). Orthodox Easter uses the Julian
     * computus (Meeus) and shifts the Julian result to the Gregorian calendar by
     * `⌊Y/100⌋ − ⌊Y/400⌋ − 2` days (13 days for 1900–2099, 14 from 2100). Both are total over years
     * 1..9999 (proleptic before the calendars existed).
     *
     * Spec: `docs/holidays-and-import.md` §2.2 rule 5.
     *
     * @property calendar which Easter to start from.
     * @property offset days relative to Easter Sunday; negative is before.
     */
    public data class Easter(
        val calendar: EasterCalendar,
        val offset: Int,
    ) : HolidayRule

    /**
     * An explicit Gregorian date per year, for holidays that cannot be computed here (pre-generated
     * lunisolar dates, curated Diwali/Holi tables, officially announced one-offs).
     *
     * Years absent from [dates] yield no occurrence. Every date must lie in the year it is keyed by,
     * so that a table behaves like every other rule under per-year evaluation.
     *
     * Spec: `docs/holidays-and-import.md` §2.2 rule 7, §2.3.
     *
     * @property dates year → anchor date in that year.
     * @throws IllegalArgumentException if any date's year differs from its key.
     */
    public data class Table(
        val dates: Map<Int, LocalDate>,
    ) : HolidayRule {
        init {
            for ((year, date) in dates) {
                require(date.year == year) { "Table date $date is not in its key year $year" }
            }
        }
    }

    /**
     * An IFC-native date, evaluated by building the [IfcDate] for the year and converting it with
     * `:core:calendar` (CLAUDE.md rule 1). Invariants that follow: [Regular] (SOL, 1) is Gregorian
     * June 18 every year; [YearDay] is December 31 every year; [LeapDay] is June 17 in leap years and
     * **absent in common years** (the OMIT policy of `docs/holidays-and-import.md` §5.3 — the built-in
     * Leap Day holiday is simply not there; the BACKWARD/FORWARD policies belong to user recurrences,
     * not to holiday rules).
     *
     * A regular position in IFC Mar 4 – Jun 28 lands one Gregorian day earlier in leap years than in
     * common years (`docs/holidays-and-import.md` §5.1); positions outside that window are on a fixed
     * Gregorian date.
     *
     * Spec: `docs/holidays-and-import.md` §2.2 rule 8, §5.
     */
    public sealed interface Ifc : HolidayRule {
        /**
         * A day inside an IFC month, e.g. Sol 1 (SOL, 1) or the Friday the 13th of any month (m, 13).
         *
         * @property month the IFC month (13 of them; not a Gregorian month).
         * @property day day of that month, 1..28. Day 29 is never a regular day; use [YearDay] or
         *   [LeapDay].
         * @throws IllegalArgumentException if [day] is out of range.
         */
        public data class Regular(
            val month: IfcMonth,
            val day: Int,
        ) : Ifc {
            init {
                require(day in 1..IfcMonth.DAYS_PER_MONTH) { "IFC day of month out of range: $day" }
            }
        }

        /** Year Day, the day after IFC December 28: Gregorian December 31 every year. */
        public data object YearDay : Ifc

        /**
         * Leap Day, the day after IFC June 28 in leap years: Gregorian June 17. Yields no occurrence in
         * common years (including 2100).
         */
        public data object LeapDay : Ifc
    }

    /** Range constants shared by the Gregorian rule types. */
    public companion object {
        /** Number of months in a Gregorian year; rule months are `1..12`. */
        public const val MONTHS_PER_GREGORIAN_YEAR: Int = 12

        /** Largest ordinal a weekday can have within a month, so [NthWeekday.n] is `±1..±5`. */
        public const val MAX_WEEKDAY_ORDINAL: Int = 5

        private fun requireGregorianMonthDay(
            month: Int,
            day: Int,
        ) {
            require(month in 1..MONTHS_PER_GREGORIAN_YEAR) { "Gregorian month out of range: $month" }
            val maxDay = Month.of(month).maxLength()
            require(day in 1..maxDay) { "Day out of range for Gregorian month $month: $day (1..$maxDay)" }
        }
    }
}

/** Which Easter an [HolidayRule.Easter] rule starts from. */
public enum class EasterCalendar {
    /** Gregorian computus (Meeus/Jones/Butcher): Catholic and Protestant Easter. */
    WESTERN,

    /** Julian computus (Meeus), shifted to the Gregorian calendar: Eastern Orthodox Easter (Pascha). */
    ORTHODOX,
}
