package io.github.chrisjmendoza.yearal.feature.converter

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange
import io.github.chrisjmendoza.yearal.core.designsystem.picker.IfcDatePickerValue
import java.time.LocalDate

/** Which calendar the user enters a date in (docs/FEATURES.md D1); the result is in the other one. */
enum class ConversionDirection {
    /** A Gregorian date is picked and its IFC date is the result. */
    GREGORIAN_TO_IFC,

    /** An IFC date is picked — Leap Day and Year Day included — and its Gregorian date is the result. */
    IFC_TO_GREGORIAN,
}

/**
 * What the converter shows (docs/FEATURES.md D1, D2, D4; docs/ARCHITECTURE.md §4 "State management").
 * Immutable; a new value is built for every input change and every date the `DateTicker` emits, so
 * the "today" default is never cached across midnight.
 */
sealed interface ConverterUiState {
    /** Before the first date tick, which arrives immediately on collection. */
    data object Loading : ConverterUiState

    /**
     * Both inputs and the conversion of the active one. The two inputs stay in step: while the IFC
     * input is a valid date it is the same physical day as [gregorianInput], so switching
     * [direction] never jumps to another day.
     *
     * @property direction which input is active.
     * @property gregorianInput the Gregorian input: the last valid date picked in either calendar, or
     * today. May lie outside [DatePickerRange] only if a caller passed such a date in; then [result] is
     * [ConversionResult.Invalid].
     * @property gregorianInputLabel [gregorianInput] in the locale's full style, weekday included, for
     * the button that opens the Gregorian picker.
     * @property ifcInput what the IFC picker shows. Its year is text as typed, so it may hold no date.
     * @property followsToday `true` until the user (or a prefill) chooses a date: the inputs then track
     * the `DateTicker` across midnight.
     * @property result the conversion of the active input.
     */
    data class Loaded(
        val direction: ConversionDirection,
        val gregorianInput: LocalDate,
        val gregorianInputLabel: String,
        val ifcInput: IfcDatePickerValue,
        val followsToday: Boolean,
        val result: ConversionResult,
    ) : ConverterUiState
}

/** The outcome of converting the active input. Invalid input is a state, never an exception. */
sealed interface ConversionResult {
    /**
     * The active input is not a date the converter accepts: the IFC year is empty, partial or outside
     * 1583..9999, or the Gregorian date lies outside that range (`docs/calendar-spec.md` §7.1).
     */
    data object Invalid : ConversionResult

    /**
     * One physical day in both calendars, fully formatted by [IfcDateFormatter] so the screen renders
     * text only and computes nothing.
     *
     * @property date the day in the IFC, from `:core:calendar`.
     * @property gregorianDate the same physical day in the Gregorian calendar.
     * @property ifcLong the long style (`September 8, 2026`, `Leap Day, 2028`, `Year Day, 2026`).
     * @property numeric the canonical numeric style with its mandatory prefix (`IFC 2026-10-08`);
     * [IfcDate.toPrefixedString], never a locale-style numeric date (CLAUDE.md rule 5).
     * @property gregorianLong the Gregorian date with its real weekday (`Thursday, September 17, 2026`).
     * @property nominalWeekday the labelled IFC weekday (`IFC weekday: Sunday`), or `no IFC weekday` on
     * Leap Day and Year Day. **Not the real weekday** — spec §4.1.
     * @property actualWeekday the labelled real weekday (`Actual weekday: Thursday`), from
     * [IfcDate.actualDayOfWeek]; never derived from [nominalWeekday].
     * @property weekdaysDescription both weekdays in spoken form (`IFC Sunday, actual Thursday`).
     * @property dayAndWeek `Day 260 · Week 38 of 52`, or `Day 169 · outside the weeks` (spec §7.4).
     * @property showProlepticNote whether to show the proleptic-calendar note (FEATURES D2): the year is
     * not after [PROLEPTIC_NOTE_LAST_YEAR].
     */
    data class Converted(
        val date: IfcDate,
        val gregorianDate: LocalDate,
        val ifcLong: String,
        val numeric: String,
        val gregorianLong: String,
        val nominalWeekday: String,
        val actualWeekday: String,
        val weekdaysDescription: String,
        val dayAndWeek: String,
        val showProlepticNote: Boolean,
    ) : ConversionResult
}

/**
 * The last year whose conversions carry the proleptic-calendar note: 1923, the latest Gregorian
 * adoption `docs/calendar-spec.md` §7.1 names (Britain 1752, Russia 1918, Greece 1923). Up to that year
 * a date may precede the Gregorian calendar's adoption in a country the spec lists, so it may be
 * proleptic there; the spec gives no later adoption, and none is invented here.
 */
const val PROLEPTIC_NOTE_LAST_YEAR: Int = 1923

/**
 * Gregorian → IFC: converts [gregorianDate] with [IfcDate.from] and formats both sides, or returns
 * [ConversionResult.Invalid] when it lies outside [DatePickerRange]. Every string comes from
 * [formatter]; nothing here computes or formats a date itself (CLAUDE.md rules 1, 3, 5 and 9).
 */
fun convertGregorian(
    gregorianDate: LocalDate,
    formatter: IfcDateFormatter,
): ConversionResult =
    if (DatePickerRange.contains(gregorianDate)) {
        converted(IfcDate.from(gregorianDate), gregorianDate, formatter)
    } else {
        ConversionResult.Invalid
    }

/**
 * IFC → Gregorian: converts [date] — Leap Day and Year Day included — with [IfcDate.toLocalDate] and
 * formats both sides, or returns [ConversionResult.Invalid] when its year lies outside
 * [DatePickerRange] (the library accepts years from 1; the UI does not, spec §7.1).
 */
fun convertIfc(
    date: IfcDate,
    formatter: IfcDateFormatter,
): ConversionResult =
    if (date.year in DatePickerRange.years) {
        converted(date, date.toLocalDate(), formatter)
    } else {
        ConversionResult.Invalid
    }

private fun converted(
    date: IfcDate,
    gregorianDate: LocalDate,
    formatter: IfcDateFormatter,
): ConversionResult.Converted =
    ConversionResult.Converted(
        date = date,
        gregorianDate = gregorianDate,
        ifcLong = formatter.formatLong(date),
        numeric = formatter.formatNumeric(date),
        gregorianLong = formatter.formatGregorianLong(gregorianDate),
        nominalWeekday = formatter.nominalWeekday(date),
        actualWeekday = formatter.actualWeekday(date),
        weekdaysDescription = formatter.weekdaysDescription(date),
        dayAndWeek = formatter.dayAndWeek(date),
        showProlepticNote = gregorianDate.year <= PROLEPTIC_NOTE_LAST_YEAR,
    )
