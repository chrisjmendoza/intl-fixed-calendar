package io.github.chrisjmendoza.yearal.feature.calendar.today

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.AgendaItemUi
import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit

/**
 * What the Today screen shows (docs/FEATURES.md T1–T4; docs/ARCHITECTURE.md §4 "Screen behaviors").
 * Immutable; a new value is built for every date the `DateTicker` emits, so "today" is never cached
 * across midnight (T6).
 */
sealed interface TodayUiState {
    /** Before the first date tick, which arrives immediately on collection. */
    data object Loading : TodayUiState

    /**
     * Today, fully formatted by [IfcDateFormatter] so the screen renders text only and computes nothing.
     *
     * @property date today in the IFC, the source of every other property.
     * @property gregorianDate the same physical day in the Gregorian calendar.
     * @property heroDate the long style (`September 8, 2026`, `Leap Day, 2028`, `Year Day, 2026`).
     * @property mediumDate the medium style (`Sep 8, 2026`).
     * @property numericDate the canonical numeric style with its mandatory prefix (`IFC 2026-10-08`).
     * @property gregorianLongDate the Gregorian date with its real weekday (`Thursday, September 17, 2026`).
     * @property nominalWeekday the labelled IFC weekday (`IFC weekday: Sunday`) or `no IFC weekday` on
     * Leap Day and Year Day. **Not the real weekday** — spec §4.1.
     * @property actualWeekday the labelled real weekday (`Actual weekday: Thursday`).
     * @property weekdaysDescription both weekdays in spoken form (`IFC Sunday, actual Thursday`).
     * @property dayAndWeek `Day 260 · Week 38 of 52`, or `Day 169 · outside the weeks` (spec §7.4).
     * @property quarter `Q3` (spec §7.5).
     * @property yearProgress fraction of the year elapsed, day of year over the year's length, in (0, 1].
     * @property yearProgressLabel [yearProgress] as text, `71% of the year`.
     * @property countdown days until the next Leap Day or Year Day, whichever comes first
     * (`105 days until Year Day`); `null` only when there is none inside the supported year range.
     * @property agenda today's own event occurrences (FEATURES T5), all-day first then by start time;
     * empty when there are none.
     * @property holidays display labels of today's enabled holidays, in holiday-engine order; empty
     * when there are none.
     * @property nextHolidayDays days from today until [nextHolidayName] (FEATURES T5), `> 0`; `null`
     * when no enabled set has an upcoming holiday in the search window.
     * @property nextHolidayName the next upcoming holiday's display label, paired with
     * [nextHolidayDays]; `null` exactly when that is.
     */
    data class Loaded(
        val date: IfcDate,
        val gregorianDate: LocalDate,
        val heroDate: String,
        val mediumDate: String,
        val numericDate: String,
        val gregorianLongDate: String,
        val nominalWeekday: String,
        val actualWeekday: String,
        val weekdaysDescription: String,
        val dayAndWeek: String,
        val quarter: String,
        val yearProgress: Float,
        val yearProgressLabel: String,
        val countdown: String?,
        val agenda: List<AgendaItemUi> = emptyList(),
        val holidays: List<String> = emptyList(),
        val nextHolidayDays: Int? = null,
        val nextHolidayName: String? = null,
    ) : TodayUiState
}

/**
 * Builds the [TodayUiState.Loaded] for the Gregorian date [today]. The IFC date comes from
 * `:core:calendar` and every string from [formatter]; nothing here formats or computes a date itself
 * (CLAUDE.md rules 1 and 9).
 *
 * @param nextHoliday the next upcoming holiday and its label ([io.github.chrisjmendoza.yearal.feature.calendar.holiday.HolidayCatalog.nextHoliday]), or `null` for none.
 */
fun buildTodayUiState(
    today: LocalDate,
    formatter: IfcDateFormatter,
    holidays: List<String> = emptyList(),
    nextHoliday: Pair<LocalDate, String>? = null,
    agenda: List<AgendaItemUi> = emptyList(),
): TodayUiState.Loaded {
    val date = IfcDate.from(today)
    return TodayUiState.Loaded(
        date = date,
        gregorianDate = today,
        heroDate = formatter.formatLong(date),
        mediumDate = formatter.formatMedium(date),
        numericDate = formatter.formatNumeric(date),
        gregorianLongDate = formatter.formatGregorianLong(today),
        nominalWeekday = formatter.nominalWeekday(date),
        actualWeekday = formatter.actualWeekday(date),
        weekdaysDescription = formatter.weekdaysDescription(date),
        dayAndWeek = formatter.dayAndWeek(date),
        quarter = formatter.quarter(date),
        yearProgress = IfcDateFormatter.yearProgressFraction(date),
        yearProgressLabel = formatter.yearProgress(date),
        countdown = nextIntercalaryDay(date)?.let { formatter.countdown(date, it) },
        agenda = agenda,
        holidays = holidays,
        nextHolidayDays = nextHoliday?.let { ChronoUnit.DAYS.between(today, it.first).toInt() },
        nextHolidayName = nextHoliday?.second,
    )
}

/**
 * The first intercalary day strictly after [date] (FEATURES T4): Leap Day if [date] is in a leap year
 * and before it (June 28 or earlier), else Year Day of the same year; after Year Day, the first
 * intercalary day of the next year. `null` only past the last supported year.
 *
 * Spec: `docs/calendar-spec.md` §2.4 (Leap Day exists only in leap years, after June 28).
 */
internal fun nextIntercalaryDay(date: IfcDate): IfcDate? =
    when (date) {
        is IfcDate.Regular -> {
            // Leap Day follows June 28, so every day of January..June precedes it (in a leap year).
            val beforeLeapDay = date.month <= IfcMonth.JUNE
            (if (beforeLeapDay) leapDayOf(date.year) else null) ?: IfcDate.YearDay(date.year)
        }

        is IfcDate.LeapDay -> {
            IfcDate.YearDay(date.year)
        }

        is IfcDate.YearDay -> {
            val next = date.year + 1
            if (next > IfcDate.MAX_YEAR) null else leapDayOf(next) ?: IfcDate.YearDay(next)
        }
    }

/** Leap Day of [year], or `null` in a common year — the only year a Leap Day exists in (CLAUDE.md rule 6). */
private fun leapDayOf(year: Int): IfcDate.LeapDay? = if (Year.isLeap(year.toLong())) IfcDate.LeapDay(year) else null
