package io.github.chrisjmendoza.yearal.feature.settings.learn

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.toIfcDate
import java.time.LocalDate

/**
 * The worked-example dates shown on the Learn screen (docs/FEATURES.md L2), computed from
 * `:core:calendar` rather than typed as string literals, so the screen's copy can never drift from the
 * conversion core it is explaining (CLAUDE.md rule 1). [LearnFactsTest] checks every value here against
 * `docs/calendar-spec.md`'s own worked examples and tables (§2.4, §3, §4.1, §5).
 *
 * [COMMON_YEAR_EXAMPLE] and [LEAP_YEAR_EXAMPLE] are the same two reference years the spec's own
 * boundary tables use (§5.1, §5.2), so a reader who checks the screen against the spec sees the same
 * numbers.
 */
internal object LearnFacts {
    /** A common (365-day) reference year — the example year calendar-spec.md §5.1 itself uses. */
    const val COMMON_YEAR_EXAMPLE: Int = 2026

    /** A leap (366-day) reference year — the example year calendar-spec.md §5.2 itself uses. */
    const val LEAP_YEAR_EXAMPLE: Int = 2024

    /**
     * The spec's own illustration of nominal vs. actual weekday (§4.1): Gregorian Thursday,
     * September 17, 2026.
     */
    val weekdayExampleGregorian: LocalDate = LocalDate.of(COMMON_YEAR_EXAMPLE, 9, 17)

    /** [weekdayExampleGregorian] converted through `:core:calendar` — never hard-coded as "September 8". */
    val weekdayExampleIfc: IfcDate = weekdayExampleGregorian.toIfcDate()

    /** Sol 1 of the common-year example. Sol 1 is always Gregorian June 18 (§2.4), leap year or not. */
    val sol1CommonYear: IfcDate.Regular = IfcDate.Regular(COMMON_YEAR_EXAMPLE, IfcMonth.SOL, 1)

    /** Sol 1 of the leap-year example, showing the June 18 rule holds in a leap year too. */
    val sol1LeapYear: IfcDate.Regular = IfcDate.Regular(LEAP_YEAR_EXAMPLE, IfcMonth.SOL, 1)

    /** Year Day of the common-year example. Year Day is always Gregorian December 31 (§2.4, R8). */
    val yearDayExample: IfcDate.YearDay = IfcDate.YearDay(COMMON_YEAR_EXAMPLE)

    /** Leap Day of the leap-year example: Gregorian June 17 (§2.4, R9); it does not exist in common years. */
    val leapDayExample: IfcDate.LeapDay = IfcDate.LeapDay(LEAP_YEAR_EXAMPLE)

    /** Gregorian March 1 in the common-year example, showing the leap-year shift (§2.4): IFC March 4. */
    val march1CommonYear: IfcDate = LocalDate.of(COMMON_YEAR_EXAMPLE, 3, 1).toIfcDate()

    /** Gregorian March 1 in the leap-year example: IFC March 5, one day later than in a common year (§2.4). */
    val march1LeapYear: IfcDate = LocalDate.of(LEAP_YEAR_EXAMPLE, 3, 1).toIfcDate()

    /** The 13th of a regular IFC month is always a nominal Friday (§2.3 R6; §9 "fun facts"). */
    val friday13Example: IfcDate.Regular = IfcDate.Regular(COMMON_YEAR_EXAMPLE, IfcMonth.JANUARY, 13)
}
