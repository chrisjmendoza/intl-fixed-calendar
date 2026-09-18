package io.github.chrisjmendoza.yearal.core.designsystem.format

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Renders [IfcDate]s as user-visible text, following the conventions in `docs/calendar-spec.md` §7.3
 * (styles), §7.4 (day and week), §7.5 (quarter) and §7.6 (locale). Every word a user can read comes
 * from string resources or from `java.time`'s localized names (CLAUDE.md rule 9); patterns are
 * positional resources, never concatenation.
 *
 * The class has no Compose dependency so ViewModels can take it by injection; UI code uses
 * [rememberIfcDateFormatter]. It computes no dates itself: every field comes from `:core:calendar`
 * (CLAUDE.md rule 1).
 *
 * @property resources where the patterns and calendar terms are read from.
 * @property locale the locale for month and weekday names, numerals in the long and medium forms, and
 * the Gregorian date; the canonical numeric form ignores it (ASCII digits only, §7.6).
 */
class IfcDateFormatter(
    private val resources: Resources,
    private val locale: Locale,
) {
    /** Length of a month name, as in [java.time.format.TextStyle]. There is no narrow style (§7.6). */
    enum class MonthNameStyle {
        /** "September", "Sol". */
        FULL,

        /** "Sep", "Sol". */
        SHORT,
    }

    /** Length of a weekday name, as in [java.time.format.TextStyle]. */
    enum class WeekdayNameStyle {
        /** "Thursday". */
        FULL,

        /** "Thu" — the grid's column headers. */
        SHORT,
    }

    /** The marker word ("IFC") that precedes numeric dates and labels IFC text next to Gregorian text (§7.3). */
    val marker: String get() = resources.getString(R.string.ifc_marker)

    /**
     * The localized standalone name of a weekday, unlabelled. **Trap:** the caller must say which
     * kind of weekday it is showing — [IfcDate.nominalDayOfWeek] or [IfcDate.actualDayOfWeek] — with
     * a label or header (§4.1 items 3–4); prefer [nominalWeekday] and [actualWeekday] for body text.
     */
    fun weekdayName(
        day: DayOfWeek,
        style: WeekdayNameStyle = WeekdayNameStyle.FULL,
    ): String =
        when (style) {
            WeekdayNameStyle.FULL -> day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            WeekdayNameStyle.SHORT -> day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
        }

    /**
     * A bare day number in the locale's digits (`13`; Arabic-Indic digits under an `ar` locale), for
     * grid cells where the month is given by the heading. Never for a whole date: those go through
     * the styles of §7.3 so the `IFC` marker and field order are right.
     */
    fun formatNumber(value: Int): String = String.format(locale, "%d", value)

    /** The month grid's heading: the full month name and the year, `Sol 2028`. */
    fun monthTitle(month: IfcYearMonth): String = format(R.string.month_title, monthName(month.month), month.year)

    /**
     * A day named within its month, without the year: `Sol 13`, `Leap Day`, `Year Day`. The first
     * element of a day cell's spoken description (docs/ARCHITECTURE.md §4 "Accessibility").
     */
    fun formatDay(date: IfcDate): String =
        when (date) {
            is IfcDate.Regular -> format(R.string.date_month_day, monthName(date.month), date.dayOfMonth)
            is IfcDate.LeapDay -> leapDayName()
            is IfcDate.YearDay -> yearDayName()
        }

    /**
     * The merged accessibility description of a day cell or intercalary band, in the form fixed by
     * docs/ARCHITECTURE.md §4 "Accessibility": `Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026.
     * 2 events. Holiday: Canada Day.` — each sentence its own resource, joined in this order:
     *
     * 1. the day and its labelled IFC weekday (`Year Day, no IFC weekday.` for intercalary days, §4.1
     *    item 5);
     * 2. the Gregorian date with its real weekday;
     * 3. the event count, only when [eventCount] is above zero;
     * 4. `Holiday: <name>.`, only when [holidayName] is not null;
     * 5. `Today.`, only when [isToday] — the visual today mark is a ring a screen reader cannot see.
     *
     * The real weekday comes from [IfcDate.actualDayOfWeek] and the IFC one from
     * [IfcDate.nominalDayOfWeek]; nothing is derived from the other (§4.1).
     */
    fun dayDescription(
        date: IfcDate,
        isToday: Boolean = false,
        eventCount: Int = 0,
        holidayName: String? = null,
    ): String {
        val dayAndWeekday =
            when (val nominal = date.nominalDayOfWeek) {
                null -> {
                    format(
                        R.string.day_description_intercalary,
                        formatDay(date),
                        resources.getString(R.string.weekday_nominal_none),
                    )
                }

                else -> {
                    format(R.string.day_description_regular, formatDay(date), weekdayName(nominal))
                }
            }
        val sentences =
            listOfNotNull(
                dayAndWeekday,
                format(R.string.day_description_gregorian, formatGregorianLong(date.toLocalDate())),
                eventCount.takeIf { it > 0 }?.let { count ->
                    String.format(locale, resources.getQuantityString(R.plurals.day_description_events, count), count)
                },
                holidayName?.let { format(R.string.day_description_holiday, it) },
                resources.getString(R.string.day_description_today).takeIf { isToday },
            )
        return sentences.joinToString(SENTENCE_SEPARATOR)
    }

    /**
     * The intercalary band's second line (spec §7.2): the Gregorian date with its real weekday and
     * the `no IFC weekday` note, `Thu, Dec 31, 2026 · no IFC weekday`.
     */
    fun intercalarySubtitle(date: IfcDate): String =
        format(
            R.string.intercalary_band_subtitle,
            formatGregorianMedium(date.toLocalDate()),
            resources.getString(R.string.weekday_nominal_none),
        )

    /**
     * The Gregorian dates a month covers, `Jun 18 – Jul 15`, for the reserved band slot of months
     * without an intercalary day (spec §7.2). The range comes from [IfcYearMonth.gregorianRange]; an
     * IFC month never crosses a Gregorian year boundary, so the year is omitted.
     */
    fun gregorianSpan(range: ClosedRange<LocalDate>): String =
        format(
            R.string.gregorian_span,
            formatGregorianMonthDay(range.start),
            formatGregorianMonthDay(range.endInclusive),
        )

    /**
     * The localized name of [month]: the Gregorian namesake's standalone name for the twelve shared
     * months, and the `month_sol` / `month_sol_short` resource for [IfcMonth.SOL] (§7.6).
     */
    fun monthName(
        month: IfcMonth,
        style: MonthNameStyle = MonthNameStyle.FULL,
    ): String {
        val namesake = month.gregorianNamesake
        return when {
            namesake == null && style == MonthNameStyle.FULL -> resources.getString(R.string.month_sol)
            namesake == null -> resources.getString(R.string.month_sol_short)
            style == MonthNameStyle.FULL -> namesake.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            else -> namesake.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
        }
    }

    /**
     * The long style of §7.3: `September 8, 2026`, `Leap Day, 2024`, `Year Day, 2026` (field order per
     * locale). **Does not include the nominal weekday** — show that separately and labelled, via
     * [nominalWeekday].
     */
    fun formatLong(date: IfcDate): String =
        when (date) {
            is IfcDate.Regular -> {
                format(R.string.date_long_regular, monthName(date.month), date.dayOfMonth, date.year)
            }

            is IfcDate.LeapDay -> {
                format(R.string.date_long_intercalary, leapDayName(), date.year)
            }

            is IfcDate.YearDay -> {
                format(R.string.date_long_intercalary, yearDayName(), date.year)
            }
        }

    /** The medium style of §7.3: `Sep 8, 2026`, `Sol 8, 2026`, `Leap Day 2024`, `Year Day 2026`. */
    fun formatMedium(date: IfcDate): String =
        when (date) {
            is IfcDate.Regular -> {
                format(
                    R.string.date_medium_regular,
                    monthName(date.month, MonthNameStyle.SHORT),
                    date.dayOfMonth,
                    date.year,
                )
            }

            is IfcDate.LeapDay -> {
                format(R.string.date_medium_intercalary, leapDayName(), date.year)
            }

            is IfcDate.YearDay -> {
                format(R.string.date_medium_intercalary, yearDayName(), date.year)
            }
        }

    /**
     * The canonical numeric style of §7.3 with its mandatory `IFC ` prefix: `IFC 2026-10-08`,
     * `IFC 2024-06-29`, `IFC 2026-13-29`. Exactly [IfcDate.toPrefixedString]; the prefix is never
     * stripped because the bare form is indistinguishable from an ISO date (CLAUDE.md rule 5).
     */
    fun formatNumeric(date: IfcDate): String = date.toPrefixedString()

    /**
     * The weekday the IFC assigns to [date], labelled so it cannot be read as the real weekday
     * (§4.1 item 4): `IFC weekday: Sunday`. Intercalary days get the `no IFC weekday` text
     * (§4.1 item 5), never blank.
     */
    fun nominalWeekday(date: IfcDate): String =
        when (val nominal = date.nominalDayOfWeek) {
            null -> resources.getString(R.string.weekday_nominal_none)
            else -> format(R.string.weekday_nominal_labelled, weekdayName(nominal))
        }

    /** The real-world weekday of [date], labelled (§4.1 item 4): `Actual weekday: Thursday`. */
    fun actualWeekday(date: IfcDate): String =
        format(R.string.weekday_actual_labelled, weekdayName(date.actualDayOfWeek))

    /**
     * Both weekdays in the spoken form for accessibility services (§4.1 item 7):
     * `IFC Sunday, actual Thursday`, or `no IFC weekday, actual Thursday` for intercalary days.
     */
    fun weekdaysDescription(date: IfcDate): String {
        val actual = weekdayName(date.actualDayOfWeek)
        return when (val nominal = date.nominalDayOfWeek) {
            null -> format(R.string.weekdays_description_intercalary, actual)
            else -> format(R.string.weekdays_description, weekdayName(nominal), actual)
        }
    }

    /**
     * Day of year and IFC week (§7.4): `Day 260 · Week 38 of 52`, or `Day 169 · outside the weeks`
     * for intercalary days, which have no week number.
     */
    fun dayAndWeek(date: IfcDate): String =
        when (val week = date.weekOfYear) {
            null -> format(R.string.day_outside_weeks, date.dayOfYear)
            else -> format(R.string.day_and_week, date.dayOfYear, week, WEEKS_PER_YEAR)
        }

    /** The 13-week quarter (§7.5): `Q3`. Leap Day is Q2 and Year Day is Q4. */
    fun quarter(date: IfcDate): String = format(R.string.quarter, date.quarter)

    /**
     * How far through its year [date] is, as `71% of the year`, from [IfcDate.dayOfYear] over the
     * year's length (365 or 366), rounded to a whole percent. Year Day is always `100%`.
     */
    fun yearProgress(date: IfcDate): String = format(R.string.year_progress, yearProgressPercent(date))

    /**
     * A countdown from [from] to the later date [to] (FEATURES T4): `105 days until Year Day`,
     * `1 day until Leap Day`; a regular target is named in the long style. The count is
     * [IfcDate.daysUntil], so intercalary days in between are counted.
     *
     * @throws IllegalArgumentException if [to] is not strictly after [from].
     */
    fun countdown(
        from: IfcDate,
        to: IfcDate,
    ): String {
        val days = from.daysUntil(to)
        require(days > 0) { "countdown target must be after the start: $from -> $to" }
        val target =
            when (to) {
                is IfcDate.Regular -> formatLong(to)
                is IfcDate.LeapDay -> leapDayName()
                is IfcDate.YearDay -> yearDayName()
            }
        val count = days.toInt()
        return String.format(locale, resources.getQuantityString(R.plurals.days_until, count), count, target)
    }

    /**
     * The Gregorian date in the locale's full style, weekday included:
     * `Thursday, September 17, 2026`. This is the only place a real weekday appears unlabelled, and it
     * is safe because the whole string is unmistakably Gregorian.
     */
    fun formatGregorianLong(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

    /**
     * The Gregorian date in a medium style **with** its real weekday, in the locale's field order:
     * `Thu, Dec 31, 2026`. Used where space is short (the intercalary band). Safe unlabelled for
     * the same reason as [formatGregorianLong].
     */
    fun formatGregorianMedium(date: LocalDate): String = skeletonFormatter(SKELETON_WEEKDAY_MONTH_DAY_YEAR).format(date)

    /** The Gregorian month and day without the year, in the locale's field order: `Jun 18`. */
    fun formatGregorianMonthDay(date: LocalDate): String = skeletonFormatter(SKELETON_MONTH_DAY).format(date)

    // `java.time` has no year-less or weekday-plus-medium localized style below Java 19, so the
    // pattern comes from the platform's ICU skeleton resolver (API 18+), which orders and punctuates
    // the fields for the locale ("MMM d" in en-US, "d MMM" in en-GB, "M月d日" in ja).
    private fun skeletonFormatter(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private fun leapDayName(): String = resources.getString(R.string.intercalary_leap_day)

    private fun yearDayName(): String = resources.getString(R.string.intercalary_year_day)

    // Formats with the injected locale rather than Resources.getString(id, args) so that numerals follow
    // the same locale as the month and weekday names (§7.6), whatever the resource configuration says.
    private fun format(
        pattern: Int,
        vararg args: Any,
    ): String = String.format(locale, resources.getString(pattern), *args)

    /** Constants and helpers shared with callers that build progress indicators. */
    companion object {
        /** Number of IFC weeks in every year (§7.4). */
        const val WEEKS_PER_YEAR: Int = 52

        private const val PERCENT = 100

        // Sentences of a spoken description are whole resources; only the joining space is fixed.
        private const val SENTENCE_SEPARATOR = " "
        private const val SKELETON_WEEKDAY_MONTH_DAY_YEAR = "EEEMMMdy"
        private const val SKELETON_MONTH_DAY = "MMMd"

        /** The fraction of the year elapsed at [date], 0 < value ≤ 1: day of year over the year's length. */
        fun yearProgressFraction(date: IfcDate): Float = date.dayOfYear.toFloat() / date.toLocalDate().lengthOfYear()

        private fun yearProgressPercent(date: IfcDate): Int = Math.round(yearProgressFraction(date) * PERCENT)
    }
}

/**
 * An [IfcDateFormatter] for the current resources and locale, remembered across recompositions and
 * rebuilt when the configuration (and so the locale) changes.
 */
@Composable
fun rememberIfcDateFormatter(): IfcDateFormatter {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { IfcDateFormatter(resources, configuration.locales[0]) }
}
