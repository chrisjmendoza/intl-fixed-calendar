package io.github.chrisjmendoza.yearal.core.domain.holiday

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

/**
 * Evaluates [HolidaySet]s into [HolidayOccurrence]s. Holidays are computed, never stored
 * (`docs/ARCHITECTURE.md` §3.3); results are memoised per set and year, so repeated month renders
 * cost a map lookup.
 *
 * All rules are evaluated in the Gregorian calendar; the IFC rule goes through [IfcDate] and nothing
 * here converts dates itself (CLAUDE.md rule 1). Weekday rules use real Gregorian weekdays, never IFC
 * nominal ones (CLAUDE.md rule 3).
 *
 * Instances are thread-safe. One engine per process is the intended use; every instance carries its
 * own cache, which grows by one entry per (set, year) evaluated and is never evicted unless
 * [clearCache] is called.
 *
 * Spec: `docs/holidays-and-import.md` §2.2, §5; `docs/adr/0003-holiday-rule-model.md`.
 */
public class HolidayEngine {
    private class CacheKey(
        val setId: String,
        val year: Int,
    ) {
        override fun equals(other: Any?): Boolean = other is CacheKey && other.setId == setId && other.year == year

        override fun hashCode(): Int = 31 * setId.hashCode() + year
    }

    private class CacheEntry(
        val set: HolidaySet,
        val occurrences: List<HolidayOccurrence>,
    )

    private val cache = ConcurrentHashMap<CacheKey, CacheEntry>()

    /**
     * Returns every occurrence of [set]'s holidays **for rule year [year]**, in [HolidayOccurrence]
     * order.
     *
     * For each holiday that [HolidayDefinition.existsIn] [year] (`since`/`until`/`yearFilter`), the
     * rule is evaluated for [year] to an anchor date; holidays whose rule yields no date that year
     * contribute nothing. The anchor is expanded to `durationDays` consecutive occurrences
     * (`dayIndex` 0..n-1), and if [HolidayDefinition.observed] shifts the anchor, one more occurrence
     * with `observed = true` is added on the shifted date. Only the anchor is subject to the observed
     * shift, never the later days of a multi-day holiday.
     *
     * **The returned dates are not all inside [year].** The anchor itself lies in [year] for every
     * rule type except [HolidayRule.Offset], which may push it into an adjacent year, and
     * [HolidayRule.WeekdayRelative], whose search can cross the year boundary by up to six days (the
     * Monday on or before Jan 1 is usually in the previous December); expansion and the
     * observed shift can spill further: New Year's Day 2022 (a Saturday) yields its observed entry on
     * 2021-12-31, and Kwanzaa 2021 runs 2021-12-26 to 2022-01-01. Conversely, this call never returns
     * the spill-over from year [year]−1 or [year]+1. Callers that want "what is on these dates" use
     * the range overload, which evaluates the neighbouring years for exactly this reason.
     *
     * Occurrences whose date falls outside years [IfcDate.MIN_YEAR]..[IfcDate.MAX_YEAR] are dropped,
     * so every returned date converts with [IfcDate.from].
     *
     * Results are memoised per ([HolidaySet.id], [year]); a set whose contents changed under the same
     * id is detected by value equality and re-evaluated.
     *
     * @throws DateTimeException if [year] is outside [IfcDate.MIN_YEAR]..[IfcDate.MAX_YEAR].
     */
    public fun occurrences(
        set: HolidaySet,
        year: Int,
    ): List<HolidayOccurrence> {
        if (year !in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) throw DateTimeException("Holiday year out of range: $year")
        val key = CacheKey(set.id, year)
        val cached = cache[key]
        if (cached != null && cached.set == set) return cached.occurrences
        val computed = evaluate(set, year)
        cache[key] = CacheEntry(set, computed)
        return computed
    }

    /**
     * Returns every occurrence from every set in [sets] whose date lies in [range] (both ends
     * inclusive), sorted by date, then set id, then holiday id ([HolidayOccurrence] order).
     *
     * Rule years from `range.start.year - 1` to `range.endInclusive.year + 1` (clamped to
     * [IfcDate.MIN_YEAR]..[IfcDate.MAX_YEAR]) are evaluated, so spill-over from neighbouring years is
     * included: a December range sees New Year's Day observed on Dec 31, a January range sees the tail
     * of Kwanzaa. A rule whose result drifts more than one year from its rule year (a nested
     * [HolidayRule.Offset] of hundreds of days, a `durationDays` longer than a year) is outside this
     * guarantee.
     *
     * An empty [range] (`start > endInclusive`) yields an empty list. Never throws for a range that
     * is partly or wholly outside the supported years; such dates simply have no occurrences. A set
     * listed more than once in [sets] (by value) is evaluated once.
     */
    public fun occurrences(
        sets: Collection<HolidaySet>,
        range: ClosedRange<LocalDate>,
    ): List<HolidayOccurrence> {
        if (range.isEmpty()) return emptyList()
        val firstYear = (range.start.year - 1).coerceAtLeast(IfcDate.MIN_YEAR)
        val lastYear = (range.endInclusive.year + 1).coerceAtMost(IfcDate.MAX_YEAR)
        if (firstYear > lastYear) return emptyList()
        val result = ArrayList<HolidayOccurrence>()
        for (set in sets.distinct()) {
            for (year in firstYear..lastYear) {
                for (occurrence in occurrences(set, year)) {
                    if (occurrence.date in range) result.add(occurrence)
                }
            }
        }
        result.sort()
        return result
    }

    /** Forgets every memoised result. Only needed if the same set id is re-used for a different pack. */
    public fun clearCache() {
        cache.clear()
    }

    private fun evaluate(
        set: HolidaySet,
        year: Int,
    ): List<HolidayOccurrence> {
        val result = ArrayList<HolidayOccurrence>()
        for (holiday in set.holidays) {
            if (!holiday.existsIn(year)) continue
            val anchor = holiday.rule.anchorIn(year) ?: continue
            for (dayIndex in 0 until holiday.durationDays) {
                val date = anchor.plusDays(dayIndex.toLong())
                result.add(HolidayOccurrence(holiday, set.id, date, observed = false, dayIndex = dayIndex))
            }
            val observedDate = holiday.observed.observedDate(anchor)
            if (observedDate != null) {
                result.add(HolidayOccurrence(holiday, set.id, observedDate, observed = true, dayIndex = 0))
            }
        }
        result.removeAll { it.date.year !in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR }
        result.sort()
        return result
    }

    private companion object {
        /**
         * The anchor date of this rule in [year], or `null` if the rule yields none that year.
         * [year] is a valid IFC year, so the `Ifc` branch cannot throw.
         */
        fun HolidayRule.anchorIn(year: Int): LocalDate? =
            when (this) {
                is HolidayRule.Fixed -> {
                    gregorianDateOrNull(year, month, day)
                }

                is HolidayRule.NthWeekday -> {
                    nthWeekdayOrNull(year, month, weekday, n)
                }

                is HolidayRule.WeekdayRelative -> {
                    val adjuster =
                        when (direction) {
                            HolidayRule.WeekdayRelative.Direction.ON_OR_AFTER -> {
                                TemporalAdjusters.nextOrSame(weekday)
                            }

                            HolidayRule.WeekdayRelative.Direction.ON_OR_BEFORE -> {
                                TemporalAdjusters.previousOrSame(weekday)
                            }
                        }
                    gregorianDateOrNull(year, month, day)?.with(adjuster)
                }

                is HolidayRule.Offset -> {
                    base.anchorIn(year)?.plusDays(days.toLong())
                }

                is HolidayRule.Easter -> {
                    when (calendar) {
                        EasterCalendar.WESTERN -> Computus.western(year)
                        EasterCalendar.ORTHODOX -> Computus.orthodox(year)
                    }.plusDays(offset.toLong())
                }

                is HolidayRule.Table -> {
                    dates[year]
                }

                is HolidayRule.Ifc.Regular -> {
                    IfcDate.Regular(year, month, day).toLocalDate()
                }

                is HolidayRule.Ifc.YearDay -> {
                    IfcDate.YearDay(year).toLocalDate()
                }

                is HolidayRule.Ifc.LeapDay -> {
                    if (Year.isLeap(year.toLong())) IfcDate.LeapDay(year).toLocalDate() else null
                }
            }

        /** The Gregorian date, or `null` when the day does not exist that year (Feb 29 in a common year). */
        fun gregorianDateOrNull(
            year: Int,
            month: Int,
            day: Int,
        ): LocalDate? {
            val lengthOfMonth = Month.of(month).length(Year.isLeap(year.toLong()))
            return if (day > lengthOfMonth) null else LocalDate.of(year, month, day)
        }

        /**
         * The nth [weekday] of the month, or `null` if there is no such day. `dayOfWeekInMonth` wraps
         * into the neighbouring month for an ordinal the month does not have, which the month check
         * catches.
         */
        fun nthWeekdayOrNull(
            year: Int,
            month: Int,
            weekday: DayOfWeek,
            n: Int,
        ): LocalDate? {
            val candidate = LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, weekday))
            return if (candidate.monthValue == month && candidate.year == year) candidate else null
        }
    }
}
