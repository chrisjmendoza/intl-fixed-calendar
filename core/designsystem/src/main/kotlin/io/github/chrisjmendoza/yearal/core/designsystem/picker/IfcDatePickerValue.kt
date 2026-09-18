package io.github.chrisjmendoza.yearal.core.designsystem.picker

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import java.time.LocalDate

/**
 * The year range every date picker and the converter accept: 1583..9999 (docs/ARCHITECTURE.md
 * "Reconciled decisions" 6; `docs/calendar-spec.md` §7.1). `:core:calendar` itself accepts 1..9999; the
 * UI stops at the first full year of the Gregorian calendar.
 */
object DatePickerRange {
    /** The first year a picker offers: 1583, the first full year of the Gregorian calendar (§7.1). */
    const val MIN_YEAR: Int = 1583

    /** The last year a picker offers, [IfcDate.MAX_YEAR] (9999). */
    const val MAX_YEAR: Int = IfcDate.MAX_YEAR

    /** [MIN_YEAR]..[MAX_YEAR]. */
    val years: IntRange = MIN_YEAR..MAX_YEAR

    /** Gregorian January 1 of [MIN_YEAR] = IFC January 1 of the same year (§2.1 R1). */
    val firstDate: LocalDate = LocalDate.of(MIN_YEAR, 1, 1)

    /** Gregorian December 31 of [MAX_YEAR] = Year Day of the same year (§2.4 R8). */
    val lastDate: LocalDate = LocalDate.of(MAX_YEAR, 12, 31)

    /** Whether [date] lies in [firstDate]..[lastDate], i.e. a picker can show it and the converter accepts it. */
    fun contains(date: LocalDate): Boolean = !date.isBefore(firstDate) && !date.isAfter(lastDate)
}

/**
 * Which day of an IFC year an [IfcDatePicker] has selected, independent of the year: a regular day of
 * one of the 13 months, or one of the two intercalary days. Sealed so every `when` over it handles
 * Leap Day and Year Day (CLAUDE.md rule 6).
 *
 * [monthNumber] and [dayOfMonth] are the numeric pseudo-fields of `docs/calendar-spec.md` §7.3 (Leap
 * Day = `06-29`, Year Day = `13-29`), which makes them a lossless way to save a selection; [of] is the
 * inverse.
 */
sealed interface IfcDaySelection {
    /** Month number 1..13 (7 = Sol); 6 for [LeapDay] and 13 for [YearDay]. **Not** a Gregorian month number. */
    val monthNumber: Int

    /** 1..28 for a [Regular] day; 29 for [LeapDay] and [YearDay]. */
    val dayOfMonth: Int

    /**
     * One of the 364 days that belong to a month.
     *
     * @property month the month, Sol included.
     * @property dayOfMonth 1..28.
     * @throws IllegalArgumentException if [dayOfMonth] is outside 1..28.
     */
    data class Regular(
        val month: IfcMonth,
        override val dayOfMonth: Int,
    ) : IfcDaySelection {
        init {
            require(dayOfMonth in 1..IfcMonth.DAYS_PER_MONTH) { "Invalid IFC day of month: $dayOfMonth" }
        }

        override val monthNumber: Int get() = month.number
    }

    /** The day after June 28, which exists **only in leap years** (§2.4 R9). */
    data object LeapDay : IfcDaySelection {
        override val monthNumber: Int get() = IfcMonth.JUNE.number
        override val dayOfMonth: Int get() = INTERCALARY_DAY_OF_MONTH
    }

    /** The day after December 28, the last day of every year (§2.4 R8). */
    data object YearDay : IfcDaySelection {
        override val monthNumber: Int get() = IfcMonth.DECEMBER.number
        override val dayOfMonth: Int get() = INTERCALARY_DAY_OF_MONTH
    }

    /** Factories. */
    companion object {
        private const val INTERCALARY_DAY_OF_MONTH = 29

        /** The selection that [date] is, dropping its year. */
        fun of(date: IfcDate): IfcDaySelection =
            when (date) {
                is IfcDate.Regular -> Regular(date.month, date.dayOfMonth)
                is IfcDate.LeapDay -> LeapDay
                is IfcDate.YearDay -> YearDay
            }

        /**
         * The selection with the pseudo-fields [monthNumber] and [dayOfMonth], or `null` when they name no
         * day: `(6, 29)` is [LeapDay], `(13, 29)` is [YearDay], day 29 of any other month and anything
         * outside 1..13 / 1..29 is `null` (§3.3 V2, V5). Whether Leap Day exists depends on the year,
         * which [IfcDatePickerValue] checks.
         */
        fun of(
            monthNumber: Int,
            dayOfMonth: Int,
        ): IfcDaySelection? {
            val month = IfcMonth.entries.getOrNull(monthNumber - 1) ?: return null
            return when {
                dayOfMonth in 1..IfcMonth.DAYS_PER_MONTH -> Regular(month, dayOfMonth)
                dayOfMonth != INTERCALARY_DAY_OF_MONTH -> null
                month == IfcMonth.JUNE -> LeapDay
                month == IfcMonth.DECEMBER -> YearDay
                else -> null
            }
        }
    }
}

/**
 * Everything an [IfcDatePicker] shows and edits, as one immutable value: the year **as typed** and the
 * selected day. The picker is a function of this value, so a caller may keep it wherever its state
 * lives — a ViewModel's `SavedStateHandle` (the converter) or an [IfcDatePickerState] in the composition.
 *
 * The year is text because the user types it: while it is empty, partial (`202`) or outside
 * [DatePickerRange], [year] and [date] are `null` — invalid input is a state, never an exception.
 * Every [IfcDate] comes from `:core:calendar` (CLAUDE.md rule 1); this class only chooses which
 * constructor to call.
 *
 * **Leap Day rule** (`docs/calendar-spec.md` §7.10, consistent with §7.7): Leap Day is offered only
 * when the year is a leap year. When Leap Day is selected and the year changes to a valid **common**
 * year, the selection is clamped to **June 28** and [leapDayClamped] becomes `true` so the picker can
 * say so; the flag is cleared by the next change. While the year is merely incomplete or out of range
 * nothing is clamped — a user retyping `2024` into `2028` keeps Leap Day — and [isLeapDayOffered] stays
 * `true` only so the still-selected option remains visible.
 *
 * Instances are created with [of] or [restore] and changed with the `with…` functions, which keep the
 * invariant "Leap Day is never selected in a valid common year".
 *
 * @property yearText the year field's content: ASCII digits only, at most four.
 * @property selection the selected day of the year.
 * @property leapDayClamped whether the last change moved a Leap Day selection to June 28.
 */
class IfcDatePickerValue private constructor(
    val yearText: String,
    val selection: IfcDaySelection,
    val leapDayClamped: Boolean,
) {
    /** The typed year when it is a whole number in [DatePickerRange], otherwise `null`. */
    val year: Int? get() = yearText.toIntOrNull()?.takeIf { it in DatePickerRange.years }

    /**
     * Whether the intercalary chooser shows Leap Day: [year] is a leap year, or the year is not valid
     * yet and Leap Day is what is selected (see the class description). Never `true` in a valid common
     * year (CLAUDE.md rule 6).
     */
    val isLeapDayOffered: Boolean
        get() = year?.let(::hasLeapDay) ?: (selection == IfcDaySelection.LeapDay)

    /**
     * The selected date, or `null` while [year] is `null`. Never throws: the invariant rules out Leap
     * Day in a common year, and a [IfcDaySelection.Regular] day is always 1..28.
     */
    val date: IfcDate?
        get() {
            val validYear = year ?: return null
            return when (val selected = selection) {
                is IfcDaySelection.Regular -> IfcDate.Regular(validYear, selected.month, selected.dayOfMonth)
                IfcDaySelection.LeapDay -> IfcDate.LeapDay(validYear)
                IfcDaySelection.YearDay -> IfcDate.YearDay(validYear)
            }
        }

    /**
     * The value after the user edited the year field to [text]. Characters other than ASCII digits are
     * dropped and the rest is cut to four. Applies the Leap Day rule of the class description.
     */
    fun withYearText(text: String): IfcDatePickerValue = normalized(sanitizeYear(text), selection)

    /**
     * The value after the user chose [newSelection]. Choosing [IfcDaySelection.LeapDay] while it is not
     * offered returns this value unchanged.
     */
    fun withSelection(newSelection: IfcDaySelection): IfcDatePickerValue =
        if (newSelection == IfcDaySelection.LeapDay && !isLeapDayOffered) {
            this
        } else {
            IfcDatePickerValue(yearText, newSelection, leapDayClamped = false)
        }

    /**
     * The value after the user chose [month], keeping the day of the month. From an intercalary day
     * (pseudo-day 29) the day clamps to 28, as month arithmetic does in `docs/calendar-spec.md` §7.7.
     */
    fun withMonth(month: IfcMonth): IfcDatePickerValue {
        val day =
            when (val selected = selection) {
                is IfcDaySelection.Regular -> selected.dayOfMonth
                IfcDaySelection.LeapDay, IfcDaySelection.YearDay -> IfcMonth.DAYS_PER_MONTH
            }
        return withSelection(IfcDaySelection.Regular(month, day))
    }

    /**
     * The value after the user chose day [dayOfMonth] (1..28), keeping the month. From an intercalary
     * day the month is the one it is attached to: June for Leap Day, December for Year Day (§2.4).
     *
     * @throws IllegalArgumentException if [dayOfMonth] is outside 1..28.
     */
    fun withDayOfMonth(dayOfMonth: Int): IfcDatePickerValue =
        withSelection(IfcDaySelection.Regular(IfcMonth.of(selection.monthNumber), dayOfMonth))

    override fun equals(other: Any?): Boolean =
        other is IfcDatePickerValue &&
            yearText == other.yearText &&
            selection == other.selection &&
            leapDayClamped == other.leapDayClamped

    override fun hashCode(): Int {
        var result = yearText.hashCode()
        result = result * HASH_PRIME + selection.hashCode()
        result = result * HASH_PRIME + leapDayClamped.hashCode()
        return result
    }

    override fun toString(): String =
        "IfcDatePickerValue(yearText=$yearText, selection=$selection, leapDayClamped=$leapDayClamped)"

    /** Factories. */
    companion object {
        private const val MAX_YEAR_DIGITS = 4
        private const val HASH_PRIME = 31

        /**
         * The value that shows [date]. A date before [DatePickerRange.MIN_YEAR] (the library accepts
         * years from 1) yields a value whose [year] and [IfcDatePickerValue.date] are `null`.
         */
        fun of(date: IfcDate): IfcDatePickerValue =
            IfcDatePickerValue(date.year.toString(), IfcDaySelection.of(date), leapDayClamped = false)

        /**
         * Rebuilds a value from saved parts (process death, a `Saver`). The parts are untrusted: the
         * year text is sanitized as in [withYearText], and a saved Leap Day in a valid common year is
         * clamped to June 28 rather than restored.
         */
        fun restore(
            yearText: String,
            selection: IfcDaySelection,
            leapDayClamped: Boolean,
        ): IfcDatePickerValue {
            val restored = normalized(sanitizeYear(yearText), selection)
            return if (restored.leapDayClamped || !leapDayClamped) {
                restored
            } else {
                IfcDatePickerValue(restored.yearText, restored.selection, leapDayClamped = true)
            }
        }

        private fun normalized(
            yearText: String,
            selection: IfcDaySelection,
        ): IfcDatePickerValue {
            val year = yearText.toIntOrNull()?.takeIf { it in DatePickerRange.years }
            val clamp = selection == IfcDaySelection.LeapDay && year != null && !hasLeapDay(year)
            return if (clamp) {
                IfcDatePickerValue(
                    yearText,
                    IfcDaySelection.Regular(IfcMonth.JUNE, IfcMonth.DAYS_PER_MONTH),
                    leapDayClamped = true,
                )
            } else {
                IfcDatePickerValue(yearText, selection, leapDayClamped = false)
            }
        }

        private fun sanitizeYear(text: String): String = text.filter { it in '0'..'9' }.take(MAX_YEAR_DIGITS)

        // Asks :core:calendar instead of re-stating the leap-year rule here (CLAUDE.md rule 1): June has
        // a trailing intercalary day exactly in the years that have a Leap Day.
        private fun hasLeapDay(year: Int): Boolean = IfcYearMonth(year, IfcMonth.JUNE).trailingIntercalary != null
    }
}
