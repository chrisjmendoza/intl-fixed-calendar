package io.github.chrisjmendoza.yearal.core.calendar

import java.time.DateTimeException
import java.time.Month

/**
 * The 13 months of the International Fixed Calendar, in calendar order.
 *
 * Every month has exactly [DAYS_PER_MONTH] days. [SOL] sits between [JUNE] and [JULY], so months after
 * June have a different [number] than the Gregorian month of the same name (IFC September is month 10).
 *
 * Spec: `docs/calendar-spec.md` §2.2.
 */
public enum class IfcMonth {
    /** Month 1. */
    JANUARY,

    /** Month 2. */
    FEBRUARY,

    /** Month 3. */
    MARCH,

    /** Month 4. */
    APRIL,

    /** Month 5. */
    MAY,

    /** Month 6. In leap years Leap Day follows June 28. */
    JUNE,

    /** Month 7, the month the Gregorian calendar does not have. Sol 1 is always Gregorian June 18. */
    SOL,

    /** Month 8. */
    JULY,

    /** Month 9. */
    AUGUST,

    /** Month 10. */
    SEPTEMBER,

    /** Month 11. */
    OCTOBER,

    /** Month 12. */
    NOVEMBER,

    /** Month 13. Year Day follows December 28. */
    DECEMBER,
    ;

    /** The month number, 1..13. Not interchangeable with [java.time.Month.getValue] after June. */
    public val number: Int get() = ordinal + 1

    /**
     * The Gregorian month with the same name, for looking up localized display names, or `null` for [SOL].
     *
     * This is a naming aid only. It says nothing about which Gregorian dates the IFC month covers.
     */
    public val gregorianNamesake: Month?
        get() = if (this == SOL) null else Month.valueOf(name)

    /** Constants and factories for [IfcMonth]. */
    public companion object {
        /** Number of regular days in every IFC month. */
        public const val DAYS_PER_MONTH: Int = 28

        /** Number of months in an IFC year. */
        public const val MONTHS_PER_YEAR: Int = 13

        /**
         * Returns the month with the given [number].
         *
         * @throws DateTimeException if [number] is not in 1..13.
         */
        public fun of(number: Int): IfcMonth =
            entries.getOrNull(number - 1) ?: throw DateTimeException("Invalid IFC month: $number")
    }
}
