package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
import java.time.ZoneId
import kotlin.random.Random

// An independent oracle for RecurrenceExpander, written from docs/contracts/Events.md §2 "IFC
// recurrence semantics" and §4, docs/adr/0005-events-contract.md decisions 2-5 and the KDoc of
// RecurrenceExpander / IfcRecurrence / Occurrence. It deliberately shares no technique with the
// implementation (ARCHITECTURE §3.2: "IFC rules expand in O(1) per year by direct construction …
// with no iteration"): the oracle walks EVERY Gregorian day from the anchor, converts it with
// :core:calendar and asks "is this day a position of the rule?". Slow, obvious, and unable to share
// a bug with a smart expander.
//
// WORKFLOW.md §6: the test author works from the spec, not from the implementation. Nothing in this
// file was derived from DefaultRecurrenceExpander.kt, which was never opened.
internal object RecurrenceOracle {
    /** First Gregorian date that exists in the IFC (`docs/calendar-spec.md` §7.1). */
    val MIN_DATE: LocalDate = LocalDate.of(IfcDate.MIN_YEAR, 1, 1)

    /** Year Day 9999 — "nothing exists after year 9999" (`RecurrenceExpander` KDoc, "Year range"). */
    val MAX_DATE: LocalDate = LocalDate.of(IfcDate.MAX_YEAR, 12, 31)

    /**
     * Builds the occurrence of [event] that starts on [date]: "every occurrence keeps the anchor's
     * time of day, nominal length, zone and all-day flag" (Events.md §2).
     */
    fun occurrenceOn(
        event: Event,
        date: LocalDate,
    ): Occurrence =
        when (val timing = event.timing) {
            is EventTiming.AllDay -> {
                Occurrence(
                    eventId = event.id,
                    startLocal = date.atStartOfDay(),
                    endLocal = date.plusDays(timing.days.toLong()).atStartOfDay(),
                    zone = null,
                    allDay = true,
                )
            }

            is EventTiming.Timed -> {
                val start = date.atTime(LocalTime.ofSecondOfDay(timing.startMinuteOfDay * SECONDS_PER_MINUTE))
                Occurrence(
                    eventId = event.id,
                    startLocal = start,
                    endLocal = start.plusMinutes(timing.durationMinutes.toLong()),
                    zone = timing.zone,
                    allDay = false,
                )
            }
        }

    /**
     * Is [day] a position of [rule] anchored on [anchor]? This is the rule definition of Events.md
     * §2 read literally, on the IFC date of [day].
     */
    fun isPosition(
        rule: IfcRecurrence,
        anchor: IfcDate,
        day: LocalDate,
    ): Boolean {
        val ifc = IfcDate.from(day)
        return when (rule) {
            // "rule years anchorYear + k × interval; the occurrence is IfcDate.Regular(year, m, d)".
            is IfcRecurrence.YearlyOnDate -> {
                ifc is IfcDate.Regular &&
                    ifc.month == rule.month &&
                    ifc.dayOfMonth == rule.day &&
                    inYearStep(anchor.year, ifc.year, rule.interval)
            }

            is IfcRecurrence.YearlyOnIntercalary -> {
                inYearStep(anchor.year, ifc.year, rule.interval) &&
                    when (val intercalary = rule.day) {
                        // "December 31 of every rule year."
                        IntercalaryDay.YearDay -> {
                            ifc is IfcDate.YearDay
                        }

                        is IntercalaryDay.LeapDay -> {
                            if (Year.isLeap(ifc.year.toLong())) {
                                ifc is IfcDate.LeapDay
                            } else {
                                // Common rule year, including 2100/2200/2300: the policy decides.
                                when (intercalary.commonYearPolicy) {
                                    LeapDayPolicy.JUNE_28 -> ifc.isRegular(IfcMonth.JUNE, LAST_DAY_OF_MONTH)
                                    LeapDayPolicy.SKIP -> false
                                    LeapDayPolicy.SOL_1 -> ifc.isRegular(IfcMonth.SOL, 1)
                                }
                            }
                        }
                    }
            }

            // "IFC month index year × 13 + (monthNumber − 1) advances by interval from the anchor's".
            is IfcRecurrence.MonthlyOnDay -> {
                ifc is IfcDate.Regular &&
                    ifc.dayOfMonth == rule.day &&
                    inMonthStep(monthIndex(anchor), monthIndex(ifc), rule.interval)
            }
        }
    }

    /**
     * Every occurrence of [event]'s IFC rule that starts on or before [walkEnd], in ascending order.
     * **Exdates are not applied**, because `COUNT` counts excluded occurrences (Events.md §2).
     */
    fun occurrences(
        event: Event,
        walkEnd: LocalDate,
    ): List<Occurrence> {
        val found = ArrayList<Occurrence>()
        walk(event, walkEnd) { occurrence ->
            found.add(occurrence)
            true
        }
        return found
    }

    /**
     * The first occurrence with `occurrenceDate >= from` once exdates are removed, or `null` if the
     * walk reaches [walkEnd] first — the definition of [RecurrenceExpander.nextOccurrence] for an IFC
     * rule.
     */
    fun firstOnOrAfter(
        event: Event,
        from: LocalDate,
        walkEnd: LocalDate,
    ): Occurrence? {
        var answer: Occurrence? = null
        walk(event, walkEnd) { occurrence ->
            val excluded = occurrence.occurrenceDate in event.exdates
            if (!excluded && !occurrence.occurrenceDate.isBefore(from)) {
                answer = occurrence
                false
            } else {
                true
            }
        }
        return answer
    }

    /**
     * [RecurrenceExpander.recurrenceEndDate] for an IFC or absent recurrence: the last date the last
     * occurrence touches, exdates ignored, `null` when the rule never ends.
     */
    fun endDate(event: Event): LocalDate? =
        when (val recurrence = event.recurrence) {
            Recurrence.None -> {
                event.endDate
            }

            is IfcRecurrence -> {
                when (val end = recurrence.end) {
                    RecurrenceEnd.Never -> null
                    is RecurrenceEnd.Until -> occurrences(event, end.date).last().lastDate
                    is RecurrenceEnd.Count -> occurrences(event, MAX_DATE).last().lastDate
                }
            }

            // A Gregorian rule is not this oracle's job; the tests handle it separately.
            is Recurrence.Gregorian -> {
                error("RecurrenceOracle does not evaluate Gregorian rules")
            }
        }

    /** The occurrences of [event] that are shown on at least one date of [range] in [deviceZone]. */
    fun expand(
        event: Event,
        range: ClosedRange<LocalDate>,
        deviceZone: ZoneId,
    ): List<Occurrence> {
        if (range.start > range.endInclusive) return emptyList()
        // An occurrence starting after the range can still be shown inside it: a zoned occurrence is
        // seen up to ZONE_SKEW_DAYS earlier in the device zone (ADR 0005 decision 4).
        val walkEnd = minOf(range.endInclusive.plusDays(EventRepository.ZONE_SKEW_DAYS), MAX_DATE)
        return occurrences(event, walkEnd)
            .filter { it.occurrenceDate !in event.exdates }
            .filter { it.dates(deviceZone) intersects range }
    }

    /**
     * The shared walk: every day from the anchor to [walkEnd], stopping on `UNTIL`, on `COUNT`, or
     * when [onOccurrence] returns `false`.
     */
    private inline fun walk(
        event: Event,
        walkEnd: LocalDate,
        onOccurrence: (Occurrence) -> Boolean,
    ) {
        val rule = event.recurrence as IfcRecurrence
        val anchor = IfcDate.from(event.startDate)
        val until = (rule.end as? RecurrenceEnd.Until)?.date
        val count = (rule.end as? RecurrenceEnd.Count)?.count
        val last = minOf(walkEnd, MAX_DATE, until ?: MAX_DATE)
        var counted = 0
        var day = event.startDate
        while (day <= last) {
            if (isPosition(rule, anchor, day)) {
                // COUNT counts from the anchor and counts excluded occurrences; rule years that
                // yield nothing never reach this branch, so they do not count (Events.md §2).
                counted++
                if (count != null && counted > count) return
                val occurrence = occurrenceOn(event, day)
                // "Occurrences whose occurrenceDate or lastDate lies after year 9999 do not exist."
                if (occurrence.lastDate.year <= IfcDate.MAX_YEAR && !onOccurrence(occurrence)) return
            }
            day = day.plusDays(1)
        }
    }

    private fun inYearStep(
        anchorYear: Int,
        year: Int,
        interval: Int,
    ): Boolean = year >= anchorYear && (year - anchorYear) % interval == 0

    private fun inMonthStep(
        anchorIndex: Int,
        index: Int,
        interval: Int,
    ): Boolean = index >= anchorIndex && (index - anchorIndex) % interval == 0

    private fun monthIndex(date: IfcDate): Int = date.year * IfcMonth.MONTHS_PER_YEAR + (date.monthNumber - 1)

    private fun IfcDate.isRegular(
        month: IfcMonth,
        dayOfMonth: Int,
    ): Boolean = this is IfcDate.Regular && this.month == month && this.dayOfMonth == dayOfMonth

    private const val SECONDS_PER_MINUTE = 60L
    private const val LAST_DAY_OF_MONTH = 28
}

/** Two closed date ranges intersect when neither ends before the other begins. */
internal infix fun ClosedRange<LocalDate>.intersects(other: ClosedRange<LocalDate>): Boolean =
    start <= other.endInclusive && other.start <= endInclusive

/**
 * One randomly generated comparison: an event, the device zone it is read in, the range to expand and
 * the date to ask [RecurrenceExpander.nextOccurrence] from. Built from a single `Long` seed so that a
 * failure reproduces exactly; [toString] leaks no event text ([Event.toString] is redacted).
 */
internal data class OracleCase(
    val event: Event,
    val deviceZone: ZoneId,
    val range: ClosedRange<LocalDate>,
    val from: LocalDate,
) {
    override fun toString(): String =
        "OracleCase(anchor=${event.startDate}, ifc=${IfcDate.from(event.startDate).toPrefixedString()}, " +
            "rule=${(event.recurrence as? IfcRecurrence)?.toRuleText() ?: event.recurrence}, " +
            "timing=${event.timing}, exdates=${event.exdates.sorted()}, deviceZone=$deviceZone, " +
            "range=${range.start}..${range.endInclusive}, from=$from)"
}

/**
 * Builds [OracleCase]s from a seeded [Random], covering what Events.md and calendar-spec §6 say must
 * work: all three IFC rule shapes, all three [LeapDayPolicy] values, anchors on Year Day, Leap Day,
 * Sol 13, day 28 and the century years, extreme and daylight-saving zones, and every end condition.
 */
internal class OracleCaseFactory(
    private val random: Random,
) {
    fun nextCase(): OracleCase {
        val anchor = nextAnchor()
        val timing = nextTiming(anchor)
        val rule = nextRule(anchor)
        val bare = event(timing, rule)
        val event = bare.copy(exdates = nextExdates(bare))
        return OracleCase(
            event = event,
            deviceZone = ZONES[random.nextInt(ZONES.size)],
            range = nextRange(anchor),
            from = nextFrom(anchor),
        )
    }

    /** A non-recurring or Gregorian-ruled event on the same anchor pool, for the lighter groups. */
    fun nextPlainCase(recurrence: Recurrence): OracleCase {
        val anchor = nextAnchor().withYearIn(GREGORIAN_YEARS)
        return OracleCase(
            event = event(nextTiming(anchor), recurrence),
            deviceZone = ZONES[random.nextInt(ZONES.size)],
            range = nextRange(anchor),
            from = nextFrom(anchor),
        )
    }

    private fun event(
        timing: EventTiming,
        recurrence: Recurrence,
    ): Event =
        Event(
            id = random.nextInt(1, 1000).toLong(),
            uid = "oracle-${random.nextInt(1, Int.MAX_VALUE)}",
            title = "",
            timing = timing,
            recurrence = recurrence,
        )

    private fun nextAnchor(): LocalDate {
        val year = nextYear()
        return when (random.nextInt(ANCHOR_KINDS)) {
            0 -> {
                IfcDate.YearDay(year).toLocalDate()
            }

            1 -> {
                IfcDate.LeapDay(leapYearNear(year)).toLocalDate()
            }

            2 -> {
                IfcDate.Regular(year, IfcMonth.SOL, 13).toLocalDate()
            }

            3 -> {
                IfcDate.Regular(year, IfcMonth.entries[random.nextInt(IfcMonth.entries.size)], 28).toLocalDate()
            }

            4 -> {
                IfcDate.Regular(year, IfcMonth.JUNE, 28).toLocalDate()
            }

            5 -> {
                IfcDate.Regular(year, IfcMonth.SOL, 1).toLocalDate()
            }

            6 -> {
                IfcDate.Regular(year, IfcMonth.JANUARY, 1).toLocalDate()
            }

            7 -> {
                IfcDate.Regular(year, IfcMonth.MARCH, 4).toLocalDate()
            }

            else -> {
                IfcDate
                    .Regular(
                        year,
                        IfcMonth.entries[random.nextInt(IfcMonth.entries.size)],
                        random.nextInt(1, IfcMonth.DAYS_PER_MONTH + 1),
                    ).toLocalDate()
            }
        }
    }

    private fun nextYear(): Int =
        if (random.nextBoolean()) {
            INTERESTING_YEARS[
                random.nextInt(
                    INTERESTING_YEARS.size,
                ),
            ]
        } else {
            random.nextInt(1583, 9900)
        }

    private fun leapYearNear(year: Int): Int {
        var candidate = year.coerceIn(1584, 9996)
        while (!Year.isLeap(candidate.toLong())) candidate++
        return candidate
    }

    private fun LocalDate.withYearIn(years: List<Int>): LocalDate =
        IfcDate
            .from(this)
            .let { ifc ->
                val year = years[random.nextInt(years.size)]
                when (ifc) {
                    is IfcDate.Regular -> IfcDate.Regular(year, ifc.month, ifc.dayOfMonth)
                    is IfcDate.YearDay -> IfcDate.YearDay(year)
                    is IfcDate.LeapDay -> IfcDate.LeapDay(leapYearNear(year))
                }
            }.toLocalDate()

    private fun nextTiming(anchor: LocalDate): EventTiming {
        val nearMax = anchor.year >= NEAR_MAX_YEAR
        return if (random.nextBoolean()) {
            val days =
                when {
                    nearMax -> 1
                    random.nextInt(10) == 0 -> random.nextInt(20, 45)
                    random.nextInt(3) == 0 -> random.nextInt(2, 6)
                    else -> 1
                }
            EventTiming.AllDay(anchor, days)
        } else {
            val minute =
                if (nearMax) {
                    random.nextInt(0, 1000)
                } else {
                    INTERESTING_MINUTES.getOrElse(random.nextInt(INTERESTING_MINUTES.size + 4)) {
                        random.nextInt(0, MINUTES_PER_DAY)
                    }
                }
            val duration =
                when {
                    nearMax -> random.nextInt(0, 120)
                    random.nextInt(8) == 0 -> 0
                    random.nextInt(8) == 0 -> random.nextInt(MINUTES_PER_DAY, 3 * MINUTES_PER_DAY)
                    else -> random.nextInt(0, MINUTES_PER_DAY)
                }
            val zone = if (random.nextInt(3) == 0) null else ZONES[random.nextInt(ZONES.size)]
            EventTiming.Timed(anchor, minute, duration, zone)
        }
    }

    private fun nextRule(anchor: LocalDate): IfcRecurrence {
        val interval = nextInterval()
        val end = nextEnd(anchor, interval)
        return when (val ifc = IfcDate.from(anchor)) {
            is IfcDate.YearDay -> {
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay, interval, end)
            }

            is IfcDate.LeapDay -> {
                IfcRecurrence.YearlyOnIntercalary(
                    IntercalaryDay.LeapDay(LeapDayPolicy.entries[random.nextInt(LeapDayPolicy.entries.size)]),
                    interval,
                    end,
                )
            }

            is IfcDate.Regular -> {
                if (random.nextBoolean()) {
                    IfcRecurrence.YearlyOnDate(ifc.month, ifc.dayOfMonth, interval, end)
                } else {
                    IfcRecurrence.MonthlyOnDay(ifc.dayOfMonth, interval, end)
                }
            }
        }
    }

    private fun nextInterval(): Int =
        if (random.nextInt(10) == 0) LARGE_INTERVALS[random.nextInt(LARGE_INTERVALS.size)] else random.nextInt(1, 8)

    // Large intervals are never combined with COUNT: a "count 25 of every 100th leap year" rule would
    // make the oracle walk millennia for no extra coverage.
    private fun nextEnd(
        anchor: LocalDate,
        interval: Int,
    ): RecurrenceEnd {
        if (anchor.year >= NEAR_MAX_YEAR) return RecurrenceEnd.Never
        val countAllowed = interval <= MAX_COUNTED_INTERVAL
        return when (random.nextInt(3)) {
            0 -> {
                RecurrenceEnd.Never
            }

            1 -> {
                if (countAllowed) RecurrenceEnd.Count(random.nextInt(1, 26)) else RecurrenceEnd.Never
            }

            else -> {
                val days = random.nextInt(0, UNTIL_HORIZON_DAYS).toLong()
                RecurrenceEnd.Until(anchor.plusDays(days).coerceAtMost(RecurrenceOracle.MAX_DATE))
            }
        }
    }

    // Exdates are taken from the rule's own early occurrences so they actually remove something, plus
    // sometimes a date that is not an occurrence at all (Event KDoc: "has no effect").
    private fun nextExdates(event: Event): Set<LocalDate> {
        if (random.nextInt(3) != 0) return emptySet()
        val candidates = RecurrenceOracle.occurrences(event, event.startDate.plusYearsClamped(EXDATE_HORIZON_YEARS))
        val chosen = LinkedHashSet<LocalDate>()
        repeat(random.nextInt(1, 4)) {
            if (candidates.isNotEmpty()) chosen.add(candidates[random.nextInt(candidates.size)].occurrenceDate)
        }
        if (random.nextInt(3) == 0) chosen.add(event.startDate)
        if (random.nextInt(3) == 0) chosen.add(event.startDate.plusDays(random.nextInt(1, 500).toLong()))
        return chosen.filter { it <= RecurrenceOracle.MAX_DATE }.toSet()
    }

    private fun nextRange(anchor: LocalDate): ClosedRange<LocalDate> {
        val start = anchor.plusDays(random.nextInt(-400, 1200).toLong()).clampToSupportedYears()
        val length = RANGE_LENGTHS[random.nextInt(RANGE_LENGTHS.size)]
        return start..start.plusDays(length.toLong()).coerceAtMost(RecurrenceOracle.MAX_DATE)
    }

    private fun nextFrom(anchor: LocalDate): LocalDate =
        anchor.plusDays(random.nextInt(-800, 1200).toLong()).clampToSupportedYears()

    private fun LocalDate.clampToSupportedYears(): LocalDate =
        coerceIn(RecurrenceOracle.MIN_DATE, RecurrenceOracle.MAX_DATE)

    private fun LocalDate.plusYearsClamped(years: Long): LocalDate =
        if (year + years > IfcDate.MAX_YEAR) RecurrenceOracle.MAX_DATE else plusYears(years)

    private companion object {
        /** UTC+14, UTC−11, UTC−12 and three daylight-saving zones (ADR 0005 decision 4). */
        val ZONES: List<ZoneId> =
            listOf(
                ZoneId.of("UTC"),
                ZoneId.of("Pacific/Kiritimati"),
                ZoneId.of("Pacific/Pago_Pago"),
                ZoneId.of("Etc/GMT+12"),
                ZoneId.of("America/New_York"),
                ZoneId.of("Europe/Berlin"),
                ZoneId.of("Australia/Lord_Howe"),
            )

        /** Century years, leap and common neighbours, and the ends of the supported range. */
        val INTERESTING_YEARS: List<Int> =
            listOf(
                1583,
                1899,
                1900,
                1901,
                1928,
                1996,
                1999,
                2000,
                2023,
                2024,
                2025,
                2026,
                2027,
                2028,
                2096,
                2099,
                2100,
                2101,
                2103,
                2199,
                2200,
                2396,
                2400,
                9890,
                9990,
                9995,
                9998,
            )

        /** Gregorian years for the RRULE groups: no proleptic oddities, no year-9999 clipping. */
        val GREGORIAN_YEARS: List<Int> = listOf(2023, 2024, 2025, 2026, 2027, 2028)

        /** Midnight, 02:30 (inside the usual spring-forward gap), 22:00 and 23:00. */
        val INTERESTING_MINUTES: List<Int> = listOf(0, 150, 60, 22 * 60, 23 * 60, 12 * 60)

        val LARGE_INTERVALS: List<Int> = listOf(13, 20, 28, 100)

        val RANGE_LENGTHS: List<Int> = listOf(0, 1, 6, 27, 28, 90, 182, 364)

        const val ANCHOR_KINDS = 9
        const val NEAR_MAX_YEAR = 9950
        const val MAX_COUNTED_INTERVAL = 7
        const val UNTIL_HORIZON_DAYS = 8 * 365
        const val EXDATE_HORIZON_YEARS = 6L
        const val MINUTES_PER_DAY = 1440
    }
}
