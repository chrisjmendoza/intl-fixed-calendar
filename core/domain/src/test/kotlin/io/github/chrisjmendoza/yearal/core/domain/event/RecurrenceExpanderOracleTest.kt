package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Oracle and property suite for [RecurrenceExpander] (ROADMAP M4 T3b).
 *
 * Written from `docs/contracts/Events.md` §2 and §4, `docs/adr/0005-events-contract.md`,
 * `docs/calendar-spec.md` §2, §3, §6 and §7.7–§7.8, `docs/ARCHITECTURE.md` reconciled decision 4, §3.2,
 * §3.4 and §6, and the KDoc of the interfaces and models in this package. Per `docs/WORKFLOW.md` §6 the
 * implementation was written by a different agent and **was not read** while writing this file.
 *
 * Three independent witnesses:
 * 1. [RecurrenceOracle] — a brute-force day-by-day walk that shares no technique with the expander.
 * 2. Invariants stated in ARCHITECTURE §6 and the `RecurrenceExpander` KDoc.
 * 3. Literal dates taken from the `calendar-spec.md` §6 vector tables, so the oracle is not the only
 *    thing the expander is measured against.
 */
class RecurrenceExpanderOracleTest {
    private val expander: RecurrenceExpander = DefaultRecurrenceExpander()

    // region 1. The brute-force oracle

    @Test
    fun `expand, nextOccurrence and recurrenceEndDate agree with the brute-force oracle`() {
        var expandChecked = 0
        var nextChecked = 0
        var endChecked = 0
        var occurrencesCompared = 0
        val shapes = HashSet<String>()
        val policies = HashSet<LeapDayPolicy>()
        runBlocking {
            checkAll(ORACLE_CASES, Arb.long()) { seed ->
                val case = OracleCaseFactory(Random(seed)).nextCase()
                val event = case.event
                val rule = event.recurrence as IfcRecurrence
                shapes.add(rule::class.simpleName.orEmpty())
                ((rule as? IfcRecurrence.YearlyOnIntercalary)?.day as? IntercalaryDay.LeapDay)
                    ?.let { policies.add(it.commonYearPolicy) }

                withClue("seed=$seed\n$case") {
                    val expected = RecurrenceOracle.expand(event, case.range, case.deviceZone)
                    expander.expand(event, case.range, case.deviceZone) shouldBe expected
                    expandChecked++
                    occurrencesCompared += expected.size

                    withClue("nextOccurrence(${case.from})") {
                        expander.nextOccurrence(event, case.from) shouldBe
                            RecurrenceOracle.firstOnOrAfter(event, case.from, RecurrenceOracle.MAX_DATE)
                        nextChecked++
                    }

                    withClue("recurrenceEndDate") {
                        expander.recurrenceEndDate(event) shouldBe RecurrenceOracle.endDate(event)
                        endChecked++
                    }
                }
            }
        }
        // A silently empty property must not pass (docs/WORKFLOW.md §2: an expression-bodied checkAll
        // is skipped by Jupiter without a sound).
        expandChecked shouldBe ORACLE_CASES
        nextChecked shouldBe ORACLE_CASES
        endChecked shouldBe ORACLE_CASES
        occurrencesCompared shouldBeGreaterThanOrEqual ORACLE_CASES / 4
        withClue("all three IFC rule shapes must be generated") { shapes.size shouldBe 3 }
        withClue("all three Leap Day policies must be generated") { policies.size shouldBe 3 }
    }

    // endregion

    // region 2. Invariants from ARCHITECTURE §6 and the RecurrenceExpander KDoc

    @Test
    fun `every occurrence converts back to its rule position, keeps the anchor's shape and is unique`() {
        var occurrencesChecked = 0
        var leapDayOccurrences = 0
        var intercalaryFallbacks = 0
        runBlocking {
            checkAll(INVARIANT_CASES, Arb.long()) { seed ->
                val case = OracleCaseFactory(Random(seed)).nextCase()
                val event = case.event
                val rule = event.recurrence as IfcRecurrence
                val first = event.firstOccurrence()
                val nominalLength = Duration.between(first.startLocal, first.endLocal)

                // A window anchored on the event, so that every case really has occurrences to check;
                // three IFC years is at most 39 monthly ones, well inside the documented work cap.
                val window =
                    event.startDate..minOf(event.startDate.plusYears(3), RecurrenceOracle.MAX_DATE)
                withClue("seed=$seed\n$case\nwindow=$window") {
                    val occurrences = expander.expand(event, window, case.deviceZone)

                    withClue("ordered by startLocal with no duplicates") {
                        occurrences.map { it.startLocal }.sorted() shouldBe occurrences.map { it.startLocal }
                        occurrences.distinct().size shouldBe occurrences.size
                    }

                    for (occurrence in occurrences) {
                        withClue("occurrence ${occurrence.startLocal}") {
                            // Shape: same event, zone, all-day flag, time of day and nominal length.
                            occurrence.eventId shouldBe event.id
                            occurrence.zone shouldBe first.zone
                            occurrence.allDay shouldBe first.allDay
                            occurrence.startLocal.toLocalTime() shouldBe first.startLocal.toLocalTime()
                            Duration.between(occurrence.startLocal, occurrence.endLocal) shouldBe nominalLength

                            // Year range: "every date returned converts with IfcDate.from", so both
                            // ends lie in 1..9999 and neither conversion throws.
                            occurrence.occurrenceDate.year shouldBeGreaterThanOrEqual IfcDate.MIN_YEAR
                            occurrence.lastDate.year shouldBeLessThanOrEqual IfcDate.MAX_YEAR
                            IfcDate.from(occurrence.lastDate).toLocalDate() shouldBe occurrence.lastDate

                            // Exdates are never returned.
                            (occurrence.occurrenceDate in event.exdates) shouldBe false

                            when (assertRulePosition(rule, occurrence.occurrenceDate)) {
                                PositionKind.LEAP_DAY -> leapDayOccurrences++
                                PositionKind.COMMON_YEAR_FALLBACK -> intercalaryFallbacks++
                                PositionKind.OTHER -> Unit
                            }
                            occurrencesChecked++
                        }
                    }
                }
            }
        }
        occurrencesChecked shouldBeGreaterThanOrEqual INVARIANT_CASES * 2
        withClue("Leap Day rules must actually have fired") { leapDayOccurrences shouldBeGreaterThanOrEqual 1 }
        withClue("a common-year fallback must have been produced") {
            intercalaryFallbacks shouldBeGreaterThanOrEqual 1
        }
    }

    @Test
    fun `expand over a split range equals the union of the halves`() {
        var checked = 0
        var splitsThatOverlapped = 0

        // A deterministic spanning case first, so the dedup is exercised on every run: a five-day
        // all-day occurrence, split down its middle, must appear in both halves and once in the union.
        val spanning = allDayMultiDayEvent(SOL_13_2026, days = 5, IfcRecurrence.MonthlyOnDay(13))
        val spanningRange = LocalDate.of(2026, 6, 28)..LocalDate.of(2026, 7, 10)
        val cut = LocalDate.of(2026, 7, 1)
        val spanningHead = expander.expand(spanning, spanningRange.start..cut, UTC)
        val spanningTail = expander.expand(spanning, cut.plusDays(1)..spanningRange.endInclusive, UTC)
        withClue("the five-day occurrence must be in both halves of the split at $cut") {
            spanningHead.map { it.occurrenceDate } shouldBe listOf(SOL_13_2026)
            spanningTail.map { it.occurrenceDate } shouldBe listOf(SOL_13_2026)
        }
        (spanningHead + spanningTail).distinct().sortedBy { it.startLocal } shouldBe
            expander.expand(spanning, spanningRange, UTC)
        splitsThatOverlapped++

        runBlocking {
            checkAll(INVARIANT_CASES, Arb.long()) { seed ->
                val case = OracleCaseFactory(Random(seed)).nextCase()
                val length = ChronoUnit.DAYS.between(case.range.start, case.range.endInclusive)
                val middle = case.range.start.plusDays(length / 2)
                withClue("seed=$seed\n$case\nsplit at $middle") {
                    val whole = expander.expand(case.event, case.range, case.deviceZone)
                    val head = expander.expand(case.event, case.range.start..middle, case.deviceZone)
                    val tail = expander.expand(case.event, middle.plusDays(1)..case.range.endInclusive, case.deviceZone)
                    if (head.intersect(tail.toSet()).isNotEmpty()) splitsThatOverlapped++
                    (head + tail).distinct().sortedBy { it.startLocal } shouldBe whole
                    checked++
                }
            }
        }
        checked shouldBe INVARIANT_CASES
        withClue("at least one occurrence must have spanned a split, or the dedup is untested") {
            splitsThatOverlapped shouldBeGreaterThanOrEqual 1
        }
    }

    @Test
    fun `nextOccurrence is the first element of a long enough expand from the same date`() {
        var checked = 0
        runBlocking {
            checkAll(INVARIANT_CASES, Arb.long()) { seed ->
                val case = OracleCaseFactory(Random(seed)).nextCase()
                val from = case.from
                val horizon = minOf(from.plusDays(LOOKAHEAD_DAYS), RecurrenceOracle.MAX_DATE)
                // Padded by ZONE_SKEW_DAYS on both sides: expand buckets by device-zone date while
                // nextOccurrence works on the occurrence's own date (Events.md §4).
                val window =
                    from.minusDays(
                        EventRepository.ZONE_SKEW_DAYS,
                    )..minOf(horizon.plusDays(EventRepository.ZONE_SKEW_DAYS), RecurrenceOracle.MAX_DATE)
                withClue("seed=$seed\n$case\nwindow=$window") {
                    val next = expander.nextOccurrence(case.event, from)
                    val inWindow =
                        expander
                            .expand(case.event, window, UTC)
                            .filter { !it.occurrenceDate.isBefore(from) }
                            .minByOrNull { it.startLocal }
                    if (next != null && !next.occurrenceDate.isAfter(horizon)) {
                        inWindow shouldBe next
                        checked++
                    }
                }
            }
        }
        checked shouldBeGreaterThanOrEqual INVARIANT_CASES / 2
    }

    @Test
    fun `occurrence one is always the event's own start`() {
        var checked = 0
        runBlocking {
            checkAll(INVARIANT_CASES, Arb.long()) { seed ->
                val case = OracleCaseFactory(Random(seed)).nextCase()
                val event = case.event.copy(exdates = emptySet())
                withClue("seed=$seed\n$case") {
                    // The zone-free statement of "occurrence 1 is the event's own start".
                    expander.nextOccurrence(event, event.startDate) shouldBe event.firstOccurrence()
                    RecurrenceOracle.occurrences(event, event.startDate).first() shouldBe event.firstOccurrence()
                    // Read in the event's own zone, an occurrence is shown on its own dates, so the
                    // first occurrence is the first thing a range around the anchor returns.
                    val ownZone = event.timing.zone ?: UTC
                    expander
                        .expand(event, event.startDate..event.endDate, ownZone)
                        .firstOrNull() shouldBe event.firstOccurrence()
                    checked++
                }
            }
        }
        checked shouldBe INVARIANT_CASES
    }

    @Test
    fun `monthly rules yield thirteen occurrences in every IFC year and never an intercalary day`() {
        var checked = 0
        for (year in MONTHLY_YEARS) {
            for (day in listOf(1, 13, 28)) {
                val anchor = IfcDate.Regular(year, IfcMonth.JANUARY, day).toLocalDate()
                val event = allDayEvent(anchor, IfcRecurrence.MonthlyOnDay(day))
                val wholeIfcYear = LocalDate.of(year, 1, 1)..LocalDate.of(year, 12, 31)
                val occurrences = expander.expand(event, wholeIfcYear, UTC)
                withClue("IFC $year, monthly on day $day") {
                    occurrences.size shouldBe IfcMonth.MONTHS_PER_YEAR
                    val ifcDates = occurrences.map { IfcDate.from(it.occurrenceDate) }
                    ifcDates.map { it.monthNumber } shouldBe (1..IfcMonth.MONTHS_PER_YEAR).toList()
                    ifcDates.forEach {
                        it.isIntercalary shouldBe false
                        it.dayOfMonth shouldBe day
                        it.year shouldBe year
                    }
                }
                checked++
            }
        }
        checked shouldBe MONTHLY_YEARS.size * 3
    }

    @Test
    fun `non-recurring events yield exactly one occurrence and their own end date`() {
        var checked = 0
        runBlocking {
            checkAll(SMALL_CASES, Arb.long()) { seed ->
                val case = OracleCaseFactory(Random(seed)).nextPlainCase(Recurrence.None)
                val event = case.event
                withClue("seed=$seed\n$case") {
                    expander.supports(Recurrence.None) shouldBe true
                    // Read in its own zone an occurrence is shown on its own dates; seen from any other
                    // zone it moves by at most ZONE_SKEW_DAYS (ADR 0005 decision 4), never further.
                    val ownZone = event.timing.zone ?: UTC
                    expander.expand(event, event.startDate..event.endDate, ownZone) shouldBe
                        listOf(event.firstOccurrence())
                    val padded =
                        event.startDate.minusDays(
                            EventRepository.ZONE_SKEW_DAYS,
                        )..event.endDate.plusDays(EventRepository.ZONE_SKEW_DAYS)
                    expander.expand(event, padded, case.deviceZone) shouldBe listOf(event.firstOccurrence())
                    expander.nextOccurrence(event, event.startDate) shouldBe event.firstOccurrence()
                    expander.nextOccurrence(event, event.startDate.plusDays(1)) shouldBe null
                    expander.recurrenceEndDate(event) shouldBe event.endDate
                    checked++
                }
            }
        }
        checked shouldBe SMALL_CASES
    }

    @Test
    fun `an empty range gives an empty list`() {
        val event = allDayEvent(SOL_13_2026, IfcRecurrence.MonthlyOnDay(13))
        expander.expand(event, LocalDate.of(2026, 5, 1)..LocalDate.of(2026, 4, 1), UTC) shouldBe emptyList()
    }

    // endregion

    // region 3. Hand-checked vectors from docs/calendar-spec.md §6

    @Test
    fun `yearly on Year Day is Gregorian December 31 of every year`() {
        // calendar-spec §6.2/§6.3: 2026-12-31 and 2024-12-31 are both Year Day.
        val event = allDayEvent(YEAR_DAY_2026, IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay))
        occurrenceDates(event, 2026, 2032) shouldBe
            (2026..2032).map { LocalDate.of(it, 12, 31) }
    }

    @Test
    fun `yearly on Leap Day keeps Gregorian June 17 under JUNE_28 and skips common years under SKIP`() {
        // calendar-spec §6.4: 2024-06-17 is Leap Day; 2025-06-17 is IFC June 28; 2025-06-18 is Sol 1.
        val june28 = leapDayEvent(LeapDayPolicy.JUNE_28)
        occurrenceDates(june28, 2024, 2028) shouldBe (2024..2028).map { LocalDate.of(it, 6, 17) }
        IfcDate.from(LocalDate.of(2024, 6, 17)) shouldBe IfcDate.LeapDay(2024)
        IfcDate.from(LocalDate.of(2025, 6, 17)) shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)

        val skip = leapDayEvent(LeapDayPolicy.SKIP)
        occurrenceDates(skip, 2024, 2032) shouldBe
            listOf(LocalDate.of(2024, 6, 17), LocalDate.of(2028, 6, 17), LocalDate.of(2032, 6, 17))

        val sol1 = leapDayEvent(LeapDayPolicy.SOL_1)
        occurrenceDates(sol1, 2024, 2028) shouldBe
            listOf(
                LocalDate.of(2024, 6, 17),
                LocalDate.of(2025, 6, 18),
                LocalDate.of(2026, 6, 18),
                LocalDate.of(2027, 6, 18),
                LocalDate.of(2028, 6, 17),
            )
        IfcDate.from(LocalDate.of(2025, 6, 18)) shouldBe IfcDate.Regular(2025, IfcMonth.SOL, 1)
    }

    @Test
    fun `a Leap Day rule over a century common year follows its policy in 2100`() {
        // calendar-spec §6.4: 2100 is a century common year; 2100-06-17 is IFC June 28, 2100.
        Year.isLeap(2100) shouldBe false
        val anchor = LocalDate.of(2096, 6, 17)
        IfcDate.from(anchor) shouldBe IfcDate.LeapDay(2096)

        val june28 =
            allDayEvent(
                anchor,
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(LeapDayPolicy.JUNE_28), interval = 4),
            )
        occurrenceDates(june28, 2096, 2104) shouldBe
            listOf(LocalDate.of(2096, 6, 17), LocalDate.of(2100, 6, 17), LocalDate.of(2104, 6, 17))
        IfcDate.from(LocalDate.of(2100, 6, 17)) shouldBe IfcDate.Regular(2100, IfcMonth.JUNE, 28)

        val skip =
            allDayEvent(
                anchor,
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(LeapDayPolicy.SKIP), interval = 4),
            )
        occurrenceDates(skip, 2096, 2104) shouldBe
            listOf(LocalDate.of(2096, 6, 17), LocalDate.of(2104, 6, 17))
    }

    @Test
    fun `yearly on Sol 13 is Gregorian June 30 and yearly on IFC June 28 shifts a day in leap years`() {
        // calendar-spec §3.4 invariant 5 and §6.2/§6.3: Sol 1 is June 18 in every year, so Sol 13 is
        // June 30; IFC June 28 is June 17 in common years and June 16 in leap years.
        val sol13 = allDayEvent(SOL_13_2026, IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13))
        occurrenceDates(sol13, 2026, 2029) shouldBe (2026..2029).map { LocalDate.of(it, 6, 30) }

        val june28 = allDayEvent(LocalDate.of(2025, 6, 17), IfcRecurrence.YearlyOnDate(IfcMonth.JUNE, 28))
        occurrenceDates(june28, 2025, 2029) shouldBe
            listOf(
                LocalDate.of(2025, 6, 17),
                LocalDate.of(2026, 6, 17),
                LocalDate.of(2027, 6, 17),
                LocalDate.of(2028, 6, 16),
                LocalDate.of(2029, 6, 17),
            )
        IfcDate.from(LocalDate.of(2028, 6, 16)) shouldBe IfcDate.Regular(2028, IfcMonth.JUNE, 28)
    }

    @Test
    fun `the thirteenth of every IFC month walks the month boundaries of the spec vector table`() {
        // Every date below is 12 days after a "first day of <month>" row of calendar-spec §6.2.
        val event = allDayEvent(SOL_13_2026, IfcRecurrence.MonthlyOnDay(13))
        expander
            .expand(event, LocalDate.of(2026, 6, 30)..LocalDate.of(2027, 1, 31), UTC)
            .map { it.occurrenceDate } shouldBe
            listOf(
                LocalDate.of(2026, 6, 30), // Sol 13, 2026
                LocalDate.of(2026, 7, 28), // July 13, 2026
                LocalDate.of(2026, 8, 25), // August 13, 2026
                LocalDate.of(2026, 9, 22), // September 13, 2026
                LocalDate.of(2026, 10, 20), // October 13, 2026
                LocalDate.of(2026, 11, 17), // November 13, 2026
                LocalDate.of(2026, 12, 15), // December 13, 2026
                LocalDate.of(2027, 1, 13), // January 13, 2027 — Year Day is skipped, it has no month
            )
    }

    @Test
    fun `a monthly rule steps 29 real days over Leap Day and 28 days in a common year`() {
        // calendar-spec §6.3: June 28, 2024 is 2024-06-16 and Sol 28, 2024 is 2024-07-15.
        val leap = allDayEvent(LocalDate.of(2024, 6, 16), IfcRecurrence.MonthlyOnDay(28))
        expander
            .expand(leap, LocalDate.of(2024, 6, 16)..LocalDate.of(2024, 7, 31), UTC)
            .map { it.occurrenceDate } shouldBe
            listOf(LocalDate.of(2024, 6, 16), LocalDate.of(2024, 7, 15))

        val common = allDayEvent(LocalDate.of(2025, 6, 17), IfcRecurrence.MonthlyOnDay(28))
        expander
            .expand(common, LocalDate.of(2025, 6, 17)..LocalDate.of(2025, 7, 31), UTC)
            .map { it.occurrenceDate } shouldBe
            listOf(LocalDate.of(2025, 6, 17), LocalDate.of(2025, 7, 15))
    }

    @Test
    fun `UNTIL is inclusive on the occurrence's own start date and COUNT counts exdates but not skipped years`() {
        val until =
            allDayEvent(
                YEAR_DAY_2026,
                IfcRecurrence.YearlyOnIntercalary(
                    IntercalaryDay.YearDay,
                    end = RecurrenceEnd.Until(LocalDate.of(2028, 12, 31)),
                ),
            )
        occurrenceDates(until, 2026, 2032) shouldBe
            listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 12, 31), LocalDate.of(2028, 12, 31))
        expander.recurrenceEndDate(until) shouldBe LocalDate.of(2028, 12, 31)

        // Events.md §2: "Excluded occurrences count"; recurrenceEndDate ignores exdates.
        val counted =
            allDayEvent(
                SOL_13_2026,
                IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, end = RecurrenceEnd.Count(3)),
                exdates = setOf(LocalDate.of(2027, 6, 30)),
            )
        occurrenceDates(counted, 2026, 2032) shouldBe
            listOf(LocalDate.of(2026, 6, 30), LocalDate.of(2028, 6, 30))
        expander.recurrenceEndDate(counted) shouldBe LocalDate.of(2028, 6, 30)

        // Events.md §2: "rule years that yield nothing do not" count — SKIP in a common year.
        val skipped =
            allDayEvent(
                LEAP_DAY_2024,
                IfcRecurrence.YearlyOnIntercalary(
                    IntercalaryDay.LeapDay(LeapDayPolicy.SKIP),
                    end = RecurrenceEnd.Count(3),
                ),
            )
        occurrenceDates(skipped, 2024, 2040) shouldBe
            listOf(LocalDate.of(2024, 6, 17), LocalDate.of(2028, 6, 17), LocalDate.of(2032, 6, 17))
        expander.recurrenceEndDate(skipped) shouldBe LocalDate.of(2032, 6, 17)
    }

    @Test
    fun `a never-ending rule has no end date and stops at year 9999`() {
        val event = allDayEvent(LocalDate.of(9995, 12, 31), IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay))
        IfcDate.from(LocalDate.of(9995, 12, 31)) shouldBe IfcDate.YearDay(9995)
        expander.recurrenceEndDate(event) shouldBe null
        expander
            .expand(event, LocalDate.of(9995, 1, 1)..LocalDate.of(9999, 12, 31), UTC)
            .map { it.occurrenceDate } shouldBe (9995..9999).map { LocalDate.of(it, 12, 31) }
        expander.nextOccurrence(event, LocalDate.of(9999, 12, 31))?.occurrenceDate shouldBe
            LocalDate.of(9999, 12, 31)
    }

    @Test
    fun `occurrences carry nominal wall-clock times across a daylight-saving gap`() {
        // 2026-03-08 is IFC March 11, 2026 and the US spring-forward day: 02:30 does not exist in
        // America/New_York. Events.md §4 and Occurrence: the occurrence is listed at 02:30 and
        // resolves to 03:30.
        val anchor = LocalDate.of(2026, 3, 8)
        IfcDate.from(anchor) shouldBe IfcDate.Regular(2026, IfcMonth.MARCH, 11)
        val event =
            Event(
                id = 7,
                uid = "dst-gap",
                title = "",
                timing = EventTiming.Timed(anchor, startMinuteOfDay = 150, durationMinutes = 60),
                recurrence = IfcRecurrence.MonthlyOnDay(11),
            )
        val occurrence = expander.expand(event, anchor..anchor, NEW_YORK).single()
        occurrence.startLocal shouldBe anchor.atTime(LocalTime.of(2, 30))
        occurrence.start(NEW_YORK).toLocalTime() shouldBe LocalTime.of(3, 30)
        occurrence.zone shouldBe null
    }

    @Test
    fun `a zoned occurrence is shown on a range date up to two days from its own date`() {
        // ADR 0005 decision 4: 00:30 in Pacific/Kiritimati is 23:30 two dates earlier in Pago_Pago.
        val anchor = SOL_13_2026
        val event =
            Event(
                id = 8,
                uid = "extreme-zone",
                title = "",
                timing = EventTiming.Timed(anchor, startMinuteOfDay = 30, durationMinutes = 30, zone = KIRITIMATI),
                recurrence = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13),
            )
        val shownOn = anchor.minusDays(2)
        val occurrences = expander.expand(event, shownOn..shownOn, PAGO_PAGO)
        occurrences.map { it.occurrenceDate } shouldBe listOf(anchor)
        occurrences.single().dates(PAGO_PAGO) shouldBe shownOn..shownOn
    }

    // endregion

    // region 4. Gregorian RRULEs (light, per docs/holidays-and-import.md §4.2)

    @Test
    fun `supported Gregorian rules match a plain java-time enumeration`() {
        val start = LocalDate.of(2026, 1, 5) // a Monday
        val cases =
            listOf(
                Triple("FREQ=DAILY;COUNT=12", "DAILY", 1L),
                Triple("FREQ=DAILY;INTERVAL=3;COUNT=8", "DAILY", 3L),
                Triple("FREQ=WEEKLY;COUNT=10", "WEEKLY", 1L),
                Triple("FREQ=WEEKLY;INTERVAL=2;COUNT=6", "WEEKLY", 2L),
                Triple("FREQ=WEEKLY;BYDAY=MO;COUNT=9", "WEEKLY", 1L),
                Triple("FREQ=MONTHLY;COUNT=7", "MONTHLY", 1L),
                Triple("FREQ=MONTHLY;INTERVAL=2;COUNT=5", "MONTHLY", 2L),
                Triple("FREQ=YEARLY;COUNT=4", "YEARLY", 1L),
            )
        var checked = 0
        for ((text, freq, interval) in cases) {
            val count = COUNT_IN_TEXT.find(text)!!.groupValues[1].toInt()
            val recurrence = Recurrence.Gregorian(text)
            val event = allDayEvent(start, recurrence)
            withClue(text) {
                // holidays-and-import §4.2 names FREQ=DAILY|WEEKLY|MONTHLY|YEARLY, INTERVAL, COUNT and
                // BYDAY as the supported subset, so these must not degrade to a single occurrence.
                expander.supports(recurrence) shouldBe true
                val expected = gregorianEnumeration(start, freq, interval, count)
                expander
                    .expand(event, start..start.plusYears(6), UTC)
                    .map { it.occurrenceDate } shouldBe expected
                expander.recurrenceEndDate(event) shouldBe expected.last()
                expander.nextOccurrence(event, start) shouldBe event.firstOccurrence()
            }
            checked++
        }
        checked shouldBe cases.size

        // An unbounded rule is bounded by the range, and has no end date.
        val unbounded = allDayEvent(start, Recurrence.Gregorian("FREQ=DAILY"))
        expander
            .expand(unbounded, start..start.plusDays(9), UTC)
            .map { it.occurrenceDate } shouldBe (0L..9L).map { start.plusDays(it) }
        expander.recurrenceEndDate(unbounded) shouldBe null
    }

    @Test
    fun `an unsupported rule never throws and expands to the event's own start only`() {
        var unsupportedSeen = 0
        var totalChecked = 0
        runBlocking {
            checkAll(SMALL_CASES, Arb.long()) { seed ->
                val random = Random(seed)
                val odd = UNPARSEABLE_RRULES + LENIENTLY_PARSEABLE_RRULES
                val text =
                    if (random.nextInt(2) == 0) {
                        odd[random.nextInt(odd.size)]
                    } else {
                        List(random.nextInt(1, 5)) { RRULE_FRAGMENTS[random.nextInt(RRULE_FRAGMENTS.size)] }
                            .joinToString(";")
                    }
                val recurrence = Recurrence.Gregorian(text)
                val event = allDayEvent(LocalDate.of(2026, 1, 5), recurrence)
                withClue("seed=$seed rrule=$text") {
                    val range = event.startDate..event.startDate.plusYears(2)
                    val occurrences = expander.expand(event, range, UTC)
                    // "Occurrence 1 is the event's own start", for every recurrence shape.
                    occurrences.first() shouldBe event.firstOccurrence()
                    expander.nextOccurrence(event, event.startDate) shouldBe event.firstOccurrence()
                    if (!expander.supports(recurrence)) {
                        occurrences shouldBe listOf(event.firstOccurrence())
                        expander.recurrenceEndDate(event) shouldBe null
                        unsupportedSeen++
                    }
                    totalChecked++
                }
            }
        }
        totalChecked shouldBe SMALL_CASES
        withClue("the generated texts must contain rules no implementation can evaluate") {
            unsupportedSeen shouldBeGreaterThanOrEqual 1
        }
        // These are not RFC 5545 at all, so no implementation can claim to evaluate them.
        for (text in UNPARSEABLE_RRULES) {
            withClue(text) { expander.supports(Recurrence.Gregorian(text)) shouldBe false }
        }
    }

    // endregion

    // region helpers

    private enum class PositionKind {
        LEAP_DAY,
        COMMON_YEAR_FALLBACK,
        OTHER,
    }

    /**
     * Asserts that [date] really is a position of [rule] — ARCHITECTURE §6: "each occurrence converts
     * back to a matching `IfcDate`, and Leap Day rules fire only in leap years".
     */
    private fun assertRulePosition(
        rule: IfcRecurrence,
        date: LocalDate,
    ): PositionKind {
        val ifc = IfcDate.from(date)
        return when (rule) {
            is IfcRecurrence.YearlyOnDate -> {
                withClue("${ifc.toPrefixedString()} must be IFC ${rule.month} ${rule.day}") {
                    (ifc is IfcDate.Regular && ifc.month == rule.month && ifc.dayOfMonth == rule.day) shouldBe true
                }
                PositionKind.OTHER
            }

            is IfcRecurrence.MonthlyOnDay -> {
                withClue("${ifc.toPrefixedString()} must be day ${rule.day} of an IFC month") {
                    ifc.isIntercalary shouldBe false
                    ifc.dayOfMonth shouldBe rule.day
                }
                PositionKind.OTHER
            }

            is IfcRecurrence.YearlyOnIntercalary -> {
                when (val intercalary = rule.day) {
                    IntercalaryDay.YearDay -> {
                        withClue("${ifc.toPrefixedString()} must be Year Day") {
                            (ifc is IfcDate.YearDay) shouldBe true
                            date.monthValue shouldBe 12
                            date.dayOfMonth shouldBe 31
                        }
                        PositionKind.OTHER
                    }

                    is IntercalaryDay.LeapDay -> {
                        if (ifc is IfcDate.LeapDay) {
                            withClue("Leap Day only exists in leap years") {
                                Year.isLeap(ifc.year.toLong()) shouldBe true
                                date.monthValue shouldBe 6
                                date.dayOfMonth shouldBe 17
                            }
                            PositionKind.LEAP_DAY
                        } else {
                            withClue("${ifc.toPrefixedString()} must be the ${intercalary.commonYearPolicy} fallback") {
                                Year.isLeap(date.year.toLong()) shouldBe false
                                when (intercalary.commonYearPolicy) {
                                    LeapDayPolicy.JUNE_28 -> ifc shouldBe IfcDate.Regular(date.year, IfcMonth.JUNE, 28)
                                    LeapDayPolicy.SOL_1 -> ifc shouldBe IfcDate.Regular(date.year, IfcMonth.SOL, 1)
                                    LeapDayPolicy.SKIP -> error("SKIP must produce nothing in a common year")
                                }
                            }
                            PositionKind.COMMON_YEAR_FALLBACK
                        }
                    }
                }
            }
        }
    }

    private fun gregorianEnumeration(
        start: LocalDate,
        freq: String,
        interval: Long,
        count: Int,
    ): List<LocalDate> =
        (0 until count).map { step ->
            val n = step.toLong() * interval
            when (freq) {
                "DAILY" -> start.plusDays(n)
                "WEEKLY" -> start.plusWeeks(n)
                "MONTHLY" -> start.plusMonths(n)
                "YEARLY" -> start.plusYears(n)
                else -> error("Unknown FREQ $freq")
            }
        }

    private fun allDayEvent(
        date: LocalDate,
        recurrence: Recurrence,
        exdates: Set<LocalDate> = emptySet(),
    ): Event =
        Event(
            id = 1,
            uid = "hand-checked-${date.toEpochDay()}",
            title = "",
            timing = EventTiming.AllDay(date),
            recurrence = recurrence,
            exdates = exdates,
        )

    private fun allDayMultiDayEvent(
        date: LocalDate,
        days: Int,
        recurrence: Recurrence,
    ): Event =
        Event(
            id = 1,
            uid = "hand-checked-multi-${date.toEpochDay()}",
            title = "",
            timing = EventTiming.AllDay(date, days),
            recurrence = recurrence,
        )

    private fun leapDayEvent(policy: LeapDayPolicy): Event =
        allDayEvent(LEAP_DAY_2024, IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy)))

    private fun occurrenceDates(
        event: Event,
        fromYear: Int,
        toYear: Int,
    ): List<LocalDate> =
        expander
            .expand(event, LocalDate.of(fromYear, 1, 1)..LocalDate.of(toYear, 12, 31), UTC)
            .map { it.occurrenceDate }

    private companion object {
        const val ORACLE_CASES = 3000
        const val INVARIANT_CASES = 1000
        const val SMALL_CASES = 300
        const val LOOKAHEAD_DAYS = 800L

        val UTC: ZoneId = ZoneId.of("UTC")
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val KIRITIMATI: ZoneId = ZoneId.of("Pacific/Kiritimati")
        val PAGO_PAGO: ZoneId = ZoneId.of("Pacific/Pago_Pago")

        /** Sol 13, 2026 — calendar-spec §6.2: Sol 1, 2026 is 2026-06-18, so Sol 13 is 2026-06-30. */
        val SOL_13_2026: LocalDate = LocalDate.of(2026, 6, 30)

        /** Year Day 2026 — calendar-spec §6.2. */
        val YEAR_DAY_2026: LocalDate = LocalDate.of(2026, 12, 31)

        /** Leap Day 2024 — calendar-spec §6.3. */
        val LEAP_DAY_2024: LocalDate = LocalDate.of(2024, 6, 17)

        /** Leap, common and century years, per ARCHITECTURE reconciled decision 5. */
        val MONTHLY_YEARS: List<Int> = listOf(1900, 1999, 2000, 2024, 2025, 2026, 2027, 2028, 2100, 2400)

        val COUNT_IN_TEXT = Regex("COUNT=(\\d+)")

        /** Texts that are not RFC 5545 under any reading, so [RecurrenceExpander.supports] must say no. */
        val UNPARSEABLE_RRULES: List<String> =
            listOf(
                "NOT AN RRULE",
                "GIBBERISH;;;",
                "FREQ=FORTNIGHTLY",
            )

        /**
         * Texts with one malformed or exotic part. RFC 5545 parsers legitimately differ on whether to
         * reject them or read them leniently, so these only have to **not throw** and to keep
         * occurrence 1; the suite does not assert what [RecurrenceExpander.supports] answers.
         */
        val LENIENTLY_PARSEABLE_RRULES: List<String> =
            listOf(
                "FREQ=DAILY;INTERVAL=zero",
                "FREQ=DAILY;UNTIL=notadate",
                "RSCALE=X-IFC;FREQ=YEARLY;SKIP=OMIT",
                "COUNT=3",
            )

        val RRULE_FRAGMENTS: List<String> =
            listOf(
                "FREQ=DAILY",
                "FREQ=WEEKLY",
                "FREQ=NOPE",
                "INTERVAL=0",
                "INTERVAL=2",
                "COUNT=abc",
                "COUNT=4",
                "BYDAY=XX",
                "BYDAY=MO",
                "X-JUNK=1",
                "UNTIL=notadate",
                "BYSETPOS=99",
                "WKST=QQ",
                "RSCALE=X-IFC",
                "SKIP=OMIT",
            )
    }

    // endregion
}
