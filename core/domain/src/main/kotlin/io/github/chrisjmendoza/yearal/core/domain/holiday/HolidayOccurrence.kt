package io.github.chrisjmendoza.yearal.core.domain.holiday

import java.time.LocalDate

/**
 * One calendar day on which a holiday is shown: the result of evaluating a [HolidayDefinition] for a
 * year, expanded to its days and observed shift.
 *
 * A single-day holiday with no shift yields one occurrence. A seven-day holiday yields seven, with
 * [dayIndex] 0..6 on consecutive dates. A weekend anchor under a shifting [ObservedPolicy] yields the
 * actual day **and** a second occurrence with [observed] `true` on the shifted date.
 *
 * The date is Gregorian (CLAUDE.md rule 4); convert with `IfcDate.from` for display.
 *
 * Ordering (also the order [HolidayEngine] returns): by [date], then [setId], then [holiday] id, then
 * actual before [observed], then [dayIndex]. Only the first three matter in practice; the rest make
 * the order total.
 *
 * Spec: `docs/holidays-and-import.md` §2.2; `docs/adr/0003-holiday-rule-model.md`.
 *
 * @property holiday the definition this occurrence came from (names, category, hints).
 * @property setId the [HolidaySet.id] the holiday belongs to.
 * @property date the Gregorian date of this day.
 * @property observed `true` for the extra "(observed)" entry produced by [HolidayDefinition.observed];
 *   `false` for the real day.
 * @property dayIndex 0-based position within a multi-day holiday (`0 until durationDays`); always 0
 *   for an observed entry.
 */
public data class HolidayOccurrence(
    val holiday: HolidayDefinition,
    val setId: String,
    val date: LocalDate,
    val observed: Boolean,
    val dayIndex: Int,
) : Comparable<HolidayOccurrence> {
    /** Orders by [date], [setId], holiday id, actual-before-observed, then [dayIndex]. */
    override fun compareTo(other: HolidayOccurrence): Int =
        compareValuesBy(
            this,
            other,
            { it.date },
            { it.setId },
            { it.holiday.id },
            { it.observed },
            { it.dayIndex },
        )
}
