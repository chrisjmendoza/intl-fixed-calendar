package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import java.time.LocalDate

/**
 * A recurrence anchored to the International Fixed Calendar: "every Sol 13", "every Year Day", "the
 * 13th of every IFC month". This is the only IFC data the app keeps at rest (CLAUDE.md rule 4); it
 * is stored and exported as the rule text of [IfcRuleText].
 *
 * ## Semantics (frozen; evaluated by [RecurrenceExpander], never here)
 *
 * - **The event's start is occurrence 1.** An event carrying an IFC rule must start on a position
 *   the rule names ([isAnchoredOn]); [Event] rejects anything else at construction. The rule is
 *   evaluated on the event's own wall-clock dates (its [EventTiming.Timed.zone], or floating), and
 *   every occurrence keeps the anchor's time of day, duration and zone.
 * - **[interval]** counts IFC years for the yearly rules (rule years are `anchorYear + k × interval`,
 *   `k ≥ 0`) and IFC months for [MonthlyOnDay] (month index `year × 13 + monthNumber − 1` advances by
 *   [interval] from the anchor's).
 * - **[end]** is [RecurrenceEnd.Never], an inclusive Gregorian [RecurrenceEnd.Until] date compared
 *   with each occurrence's start date, or a [RecurrenceEnd.Count] of occurrences counted from the
 *   anchor. Excluded occurrences ([Event.exdates]) still count, as in RFC 5545; a rule year that
 *   yields nothing (Leap Day under [LeapDayPolicy.SKIP] in a common year) does not.
 * - Occurrences are built by direct construction of the [IfcDate] for the rule year or month and
 *   converted with `:core:calendar`, so century years (2100 has no Leap Day) are right by
 *   construction. Occurrences after year [IfcDate.MAX_YEAR] do not exist.
 *
 * **Trap:** IFC month numbers 8–13 are not the Gregorian month of the same number (CLAUDE.md rule
 * 5): [YearlyOnDate] with [IfcMonth.JULY] is month **8**.
 *
 * Spec: `docs/ARCHITECTURE.md` §3.2 and reconciled decision 4; `docs/calendar-spec.md` §7.7
 * "Recurrence" and §7.9; `docs/holidays-and-import.md` §5; `docs/adr/0005-events-contract.md`.
 */
public sealed interface IfcRecurrence : Recurrence {
    /** Step between rule years (yearly rules) or IFC months ([MonthlyOnDay]); always `≥ 1`. */
    public val interval: Int

    /** When the rule stops producing occurrences. */
    public val end: RecurrenceEnd

    /**
     * `true` if [date] is a position this rule names, so that an event starting on [date] may carry
     * it: the same IFC month and day for [YearlyOnDate], the same day of any IFC month for
     * [MonthlyOnDay], Year Day or Leap Day itself for [YearlyOnIntercalary]. [interval] and [end]
     * are not considered.
     *
     * **A Leap Day rule is anchored only on a real Leap Day** (Gregorian June 17 of a leap year),
     * whatever its [LeapDayPolicy]: the June 28 and Sol 1 fallbacks are produced in later common
     * years, never chosen as the anchor. Intercalary days are never a [MonthlyOnDay] position, since
     * they belong to no month (`docs/holidays-and-import.md` §5.2).
     *
     * Returns `false`, rather than throwing, for a [date] outside years
     * [IfcDate.MIN_YEAR]..[IfcDate.MAX_YEAR].
     */
    public fun isAnchoredOn(date: LocalDate): Boolean

    /** The canonical rule text of this rule, [IfcRuleText.format]. */
    public fun toRuleText(): String = IfcRuleText.format(this)

    /**
     * Every [interval] years on a regular IFC date, e.g. `(SOL, 13)`. Exists in every year, so it
     * needs no fallback policy.
     *
     * @property month the IFC month (13 of them; **not** a Gregorian month).
     * @property day day of that month, 1..28. Day 29 is never a regular day; use
     *   [YearlyOnIntercalary].
     * @property interval years between occurrences, `≥ 1`.
     * @property end the end condition.
     * @throws IllegalArgumentException if [day] is outside 1..28 or [interval] is below 1.
     */
    public data class YearlyOnDate(
        val month: IfcMonth,
        val day: Int,
        override val interval: Int = 1,
        override val end: RecurrenceEnd = RecurrenceEnd.Never,
    ) : IfcRecurrence {
        init {
            requireDay(day)
            requireInterval(interval)
        }

        override fun isAnchoredOn(date: LocalDate): Boolean =
            when (val ifc = ifcDateOrNull(date)) {
                is IfcDate.Regular -> ifc.month == month && ifc.dayOfMonth == day
                is IfcDate.LeapDay, is IfcDate.YearDay, null -> false
            }
    }

    /**
     * Every [interval] years on an intercalary day: Year Day (Gregorian December 31, every year) or
     * Leap Day (Gregorian June 17, leap years only, with the [IntercalaryDay.LeapDay.commonYearPolicy]
     * deciding what happens in a common rule year).
     *
     * @property day which intercalary day, and for Leap Day its common-year policy.
     * @property interval years between rule years, `≥ 1`. With Leap Day and `interval = 4` anchored
     *   on a leap year, every rule year is a leap year except century years such as 2100, where the
     *   policy applies.
     * @property end the end condition.
     * @throws IllegalArgumentException if [interval] is below 1.
     */
    public data class YearlyOnIntercalary(
        val day: IntercalaryDay,
        override val interval: Int = 1,
        override val end: RecurrenceEnd = RecurrenceEnd.Never,
    ) : IfcRecurrence {
        init {
            requireInterval(interval)
        }

        override fun isAnchoredOn(date: LocalDate): Boolean =
            when (ifcDateOrNull(date)) {
                is IfcDate.YearDay -> day is IntercalaryDay.YearDay
                is IfcDate.LeapDay -> day is IntercalaryDay.LeapDay
                is IfcDate.Regular, null -> false
            }
    }

    /**
     * Every [interval] IFC months on the same day of the month: 13 occurrences a year at
     * `interval = 1`, always on the same nominal IFC weekday (`docs/calendar-spec.md` §7.7; FEATURES
     * E7). **Never produces Year Day or Leap Day**, which belong to no month; the step from IFC June
     * to Sol in a leap year is 29 real days.
     *
     * @property day day of the month, 1..28.
     * @property interval IFC months between occurrences, `≥ 1` (13 is the same as yearly).
     * @property end the end condition.
     * @throws IllegalArgumentException if [day] is outside 1..28 or [interval] is below 1.
     */
    public data class MonthlyOnDay(
        val day: Int,
        override val interval: Int = 1,
        override val end: RecurrenceEnd = RecurrenceEnd.Never,
    ) : IfcRecurrence {
        init {
            requireDay(day)
            requireInterval(interval)
        }

        override fun isAnchoredOn(date: LocalDate): Boolean =
            when (val ifc = ifcDateOrNull(date)) {
                is IfcDate.Regular -> ifc.dayOfMonth == day
                is IfcDate.LeapDay, is IfcDate.YearDay, null -> false
            }
    }

    /** Builders that derive a rule from the date the user picked, so the anchor always matches. */
    public companion object {
        /**
         * The yearly rule whose position is the IFC date of [date]: [YearlyOnDate] for a regular day,
         * [YearlyOnIntercalary] for Year Day and Leap Day. The result always satisfies
         * [isAnchoredOn]`(date)`.
         *
         * @param leapDayPolicy used only when [date] is a Leap Day; the default is the one for
         *   user-created events (`docs/ARCHITECTURE.md` reconciled decision 4).
         * @throws java.time.DateTimeException if [date] is outside years 1..9999.
         * @throws IllegalArgumentException if [interval] is below 1.
         */
        public fun yearlyOn(
            date: LocalDate,
            leapDayPolicy: LeapDayPolicy = LeapDayPolicy.JUNE_28,
            interval: Int = 1,
            end: RecurrenceEnd = RecurrenceEnd.Never,
        ): IfcRecurrence =
            when (val ifc = IfcDate.from(date)) {
                is IfcDate.Regular -> YearlyOnDate(ifc.month, ifc.dayOfMonth, interval, end)
                is IfcDate.LeapDay -> YearlyOnIntercalary(IntercalaryDay.LeapDay(leapDayPolicy), interval, end)
                is IfcDate.YearDay -> YearlyOnIntercalary(IntercalaryDay.YearDay, interval, end)
            }

        /**
         * The monthly rule on the IFC day of month of [date], or **`null` when [date] is Year Day or
         * Leap Day**, which belong to no month and cannot anchor a monthly rule. An editor must hide
         * or disable "monthly (IFC)" for those dates.
         *
         * @throws java.time.DateTimeException if [date] is outside years 1..9999.
         * @throws IllegalArgumentException if [interval] is below 1.
         */
        public fun monthlyOn(
            date: LocalDate,
            interval: Int = 1,
            end: RecurrenceEnd = RecurrenceEnd.Never,
        ): MonthlyOnDay? =
            when (val ifc = IfcDate.from(date)) {
                is IfcDate.Regular -> MonthlyOnDay(ifc.dayOfMonth, interval, end)
                is IfcDate.LeapDay, is IfcDate.YearDay -> null
            }

        private fun requireDay(day: Int) {
            require(day in 1..IfcMonth.DAYS_PER_MONTH) { "IFC day of month out of range: $day" }
        }

        private fun requireInterval(interval: Int) {
            require(interval >= 1) { "Recurrence interval must be at least 1: $interval" }
        }

        // IfcDate.from throws outside years 1..9999; an anchor check answers "no" instead.
        private fun ifcDateOrNull(date: LocalDate): IfcDate? =
            if (date.year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) IfcDate.from(date) else null
    }
}

/**
 * Which intercalary day a [IfcRecurrence.YearlyOnIntercalary] rule names. A sealed type rather than
 * an enum so that the common-year policy exists only where it means something: Year Day occurs every
 * year and has no policy.
 */
public sealed interface IntercalaryDay {
    /** Year Day, the day after IFC December 28: Gregorian December 31 of every year. */
    public data object YearDay : IntercalaryDay

    /**
     * Leap Day, the day after IFC June 28 in leap years: Gregorian June 17.
     *
     * @property commonYearPolicy what a rule year without a Leap Day produces. Defaults to
     *   [LeapDayPolicy.JUNE_28], the default for user-created events.
     */
    public data class LeapDay(
        val commonYearPolicy: LeapDayPolicy = LeapDayPolicy.JUNE_28,
    ) : IntercalaryDay
}

/**
 * What a yearly Leap Day rule produces in a rule year that has no Leap Day (a common year, including
 * century years such as 2100). FEATURES E6; `docs/ARCHITECTURE.md` reconciled decision 4.
 *
 * These are the `BACKWARD` / `OMIT` / `FORWARD` policies of `docs/holidays-and-import.md` §5.3 under
 * the names the architecture ruled on. The built-in Leap Day *holiday* is not an event and is simply
 * absent in common years.
 */
public enum class LeapDayPolicy {
    /**
     * IFC June 28, the day before where Leap Day would be. That is Gregorian June 17 in a common
     * year, the same Gregorian date Leap Day has in leap years, so the Gregorian date never moves.
     * The default for user-created events.
     */
    JUNE_28,

    /** No occurrence: the event happens only in leap years (the RFC 5545 rule for invalid dates). */
    SKIP,

    /** IFC Sol 1, the day after where Leap Day would be: Gregorian June 18. */
    SOL_1,
}

/**
 * When an [IfcRecurrence] stops. At most one of "until" and "count" can apply, which the sealed type
 * makes unrepresentable otherwise.
 */
public sealed interface RecurrenceEnd {
    /** The rule never ends on its own (it still stops after year 9999). */
    public data object Never : RecurrenceEnd

    /**
     * The last day an occurrence may start on, **inclusive**, compared with the occurrence's own
     * wall-clock start date ([Occurrence.occurrenceDate]). A Gregorian date (CLAUDE.md rule 4).
     *
     * @property date the inclusive limit, within years 1..9999. An [Event] additionally requires it
     *   to be on or after the event's start, so that occurrence 1 always exists.
     * @throws IllegalArgumentException if [date] is outside years 1..9999.
     */
    public data class Until(
        val date: LocalDate,
    ) : RecurrenceEnd {
        init {
            require(date.year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) { "UNTIL year out of range: ${date.year}" }
        }
    }

    /**
     * The total number of occurrences, counted from the event's start as occurrence 1. Excluded
     * occurrences count; rule years that yield nothing do not (see [IfcRecurrence]).
     *
     * @property count `≥ 1`; `1` means the event happens once.
     * @throws IllegalArgumentException if [count] is below 1.
     */
    public data class Count(
        val count: Int,
    ) : RecurrenceEnd {
        init {
            require(count >= 1) { "Recurrence count must be at least 1: $count" }
        }
    }
}
