package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// Developer tests for DefaultRecurrenceExpander. The independent oracle/property suite is written by a
// different agent from the contract (docs/WORKFLOW.md §6), so these are hand-worked examples only.
//
// Every expected Gregorian date below is computed by hand from docs/calendar-spec.md §3.1:
// regularDayIndex = (monthNumber − 1) × 28 + dayOfMonth; dayOfYear = regularDayIndex, plus one in a leap
// year when regularDayIndex > 168; Leap Day is day 169 of a leap year, Year Day the last day of any year.
// Worked anchors used repeatedly:
//   Sol 13      index 6×28+13 = 181 → Jun 30 (common) and day 182 → Jun 30 (leap): June 30 every year.
//   April 16    index 3×28+16 = 100 → Apr 10 (common), Apr 9 (leap) — the March 4 – June 28 window.
//   June 28     index 5×28+28 = 168 → Jun 17 (common), Jun 16 (leap).
//   Sol 1       index 6×28+1  = 169 → Jun 18 in every year.
//   Leap Day    day 169 of a leap year → Jun 17.       Year Day → Dec 31.
// US daylight saving 2026 in America/New_York: starts Sunday March 8 at 02:00 (EST −05:00 → EDT −04:00),
// ends Sunday November 1 at 02:00. January 5, 2026 is a Monday.
class DefaultRecurrenceExpanderTest {
    private val expander = DefaultRecurrenceExpander()
    private val utc = ZoneId.of("UTC")
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun allDayEvent(
        date: LocalDate,
        recurrence: Recurrence = Recurrence.None,
        days: Int = 1,
        exdates: Set<LocalDate> = emptySet(),
    ) = Event(
        id = 1,
        uid = "expander-test",
        title = "",
        timing = EventTiming.AllDay(date, days),
        recurrence = recurrence,
        exdates = exdates,
    )

    private fun timedEvent(
        date: LocalDate,
        minuteOfDay: Int,
        durationMinutes: Int,
        zone: ZoneId?,
        recurrence: Recurrence = Recurrence.None,
    ) = Event(
        id = 1,
        uid = "expander-test-timed",
        title = "",
        timing = EventTiming.Timed(date, minuteOfDay, durationMinutes, zone),
        recurrence = recurrence,
    )

    private fun startsIn(
        event: Event,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = utc,
    ): List<LocalDate> = expander.expand(event, from..to, zone).map { it.occurrenceDate }

    // --- no recurrence ----------------------------------------------------------------------------

    @Test
    fun `a non-recurring event yields its single occurrence`() {
        val event = allDayEvent(LocalDate.of(2026, 6, 30), days = 3)
        assertSoftly {
            startsIn(event, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31)) shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30))
            // The occurrence spans June 30 – July 2; a range holding only its last date still finds it once.
            startsIn(event, LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 2)) shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30))
            startsIn(event, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 12, 31)).shouldHaveSize(0)
            expander.nextOccurrence(event, LocalDate.of(2026, 1, 1))?.occurrenceDate shouldBe LocalDate.of(2026, 6, 30)
            expander.nextOccurrence(event, LocalDate.of(2026, 7, 1)) shouldBe null
            expander.recurrenceEndDate(event) shouldBe LocalDate.of(2026, 7, 2)
            expander.supports(Recurrence.None) shouldBe true
        }
    }

    @Test
    fun `an empty range yields nothing`() {
        val event = allDayEvent(LocalDate.of(2026, 6, 30))
        expander.expand(event, LocalDate.of(2026, 7, 1)..LocalDate.of(2026, 6, 1), utc).shouldHaveSize(0)
    }

    // --- IFC yearly on a regular date -------------------------------------------------------------

    @Test
    fun `a yearly IFC date keeps its IFC day and moves one Gregorian day in leap years`() {
        // IFC April 16 = Gregorian April 10 in a common year, April 9 in a leap year (2028).
        val event = allDayEvent(LocalDate.of(2026, 4, 10), IfcRecurrence.YearlyOnDate(IfcMonth.APRIL, 16))
        val starts = startsIn(event, LocalDate.of(2026, 1, 1), LocalDate.of(2029, 12, 31))
        assertSoftly {
            starts shouldContainExactly
                listOf(
                    LocalDate.of(2026, 4, 10),
                    LocalDate.of(2027, 4, 10),
                    LocalDate.of(2028, 4, 9),
                    LocalDate.of(2029, 4, 10),
                )
            starts.map { IfcDate.from(it) }.forEach { ifc ->
                ifc shouldBe IfcDate.Regular(ifc.year, IfcMonth.APRIL, 16)
            }
            expander.supports(event.recurrence) shouldBe true
        }
    }

    @Test
    fun `interval steps rule years from the anchor and a huge interval never overflows`() {
        val everyThird =
            allDayEvent(LocalDate.of(2026, 6, 30), IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, interval = 3))
        val once =
            allDayEvent(
                LocalDate.of(2026, 6, 30),
                IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, interval = Int.MAX_VALUE),
            )
        assertSoftly {
            startsIn(everyThird, LocalDate.of(2026, 1, 1), LocalDate.of(2033, 12, 31)) shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30), LocalDate.of(2029, 6, 30), LocalDate.of(2032, 6, 30))
            // The second rule year is 2026 + 2147483647, far past year 9999: only the anchor exists.
            startsIn(once, LocalDate.of(2026, 1, 1), LocalDate.of(9999, 12, 31)) shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30))
            expander.recurrenceEndDate(once) shouldBe null
        }
    }

    // --- IFC yearly on the intercalary days --------------------------------------------------------

    @Test
    fun `a yearly Year Day rule is December 31 of every year`() {
        val event = allDayEvent(LocalDate.of(2026, 12, 31), IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay))
        assertSoftly {
            startsIn(event, LocalDate.of(2026, 1, 1), LocalDate.of(2029, 12, 31)) shouldContainExactly
                listOf(
                    LocalDate.of(2026, 12, 31),
                    LocalDate.of(2027, 12, 31),
                    LocalDate.of(2028, 12, 31),
                    LocalDate.of(2029, 12, 31),
                )
            // 2028 is a leap year and Year Day is still the last day of it.
            IfcDate.from(LocalDate.of(2028, 12, 31)) shouldBe IfcDate.YearDay(2028)
        }
    }

    @Test
    fun `each Leap Day common-year policy produces what the contract says`() {
        fun starts(policy: LeapDayPolicy) =
            startsIn(
                allDayEvent(
                    LocalDate.of(2024, 6, 17),
                    IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy)),
                ),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2029, 12, 31),
            )
        assertSoftly {
            // JUNE_28: IFC June 28 is Gregorian June 17 in a common year, so the date never moves.
            starts(LeapDayPolicy.JUNE_28) shouldContainExactly
                listOf(
                    LocalDate.of(2024, 6, 17),
                    LocalDate.of(2025, 6, 17),
                    LocalDate.of(2026, 6, 17),
                    LocalDate.of(2027, 6, 17),
                    LocalDate.of(2028, 6, 17),
                    LocalDate.of(2029, 6, 17),
                )
            starts(LeapDayPolicy.SKIP) shouldContainExactly
                listOf(LocalDate.of(2024, 6, 17), LocalDate.of(2028, 6, 17))
            // SOL_1: Sol 1 is Gregorian June 18 in every year; leap years still give Leap Day itself.
            starts(LeapDayPolicy.SOL_1) shouldContainExactly
                listOf(
                    LocalDate.of(2024, 6, 17),
                    LocalDate.of(2025, 6, 18),
                    LocalDate.of(2026, 6, 18),
                    LocalDate.of(2027, 6, 18),
                    LocalDate.of(2028, 6, 17),
                    LocalDate.of(2029, 6, 18),
                )
        }
    }

    @Test
    fun `the century year 2100 has no Leap Day, whatever the interval`() {
        fun starts(policy: LeapDayPolicy) =
            startsIn(
                allDayEvent(
                    LocalDate.of(2096, 6, 17),
                    IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy), interval = 4),
                ),
                LocalDate.of(2096, 1, 1),
                LocalDate.of(2104, 12, 31),
            )
        assertSoftly {
            // Rule years 2096, 2100, 2104; 2100 is a common year (divisible by 100, not by 400).
            starts(LeapDayPolicy.JUNE_28) shouldContainExactly
                listOf(LocalDate.of(2096, 6, 17), LocalDate.of(2100, 6, 17), LocalDate.of(2104, 6, 17))
            starts(LeapDayPolicy.SKIP) shouldContainExactly
                listOf(LocalDate.of(2096, 6, 17), LocalDate.of(2104, 6, 17))
            starts(LeapDayPolicy.SOL_1) shouldContainExactly
                listOf(LocalDate.of(2096, 6, 17), LocalDate.of(2100, 6, 18), LocalDate.of(2104, 6, 17))
        }
    }

    // --- IFC monthly -------------------------------------------------------------------------------

    @Test
    fun `a monthly IFC rule gives 13 occurrences a year and never an intercalary day`() {
        val event = allDayEvent(LocalDate.of(2026, 6, 30), IfcRecurrence.MonthlyOnDay(13))
        val starts = startsIn(event, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        assertSoftly {
            // The 13th of each IFC month of 2026 (a common year) from the Sol anchor on: dayOfYear =
            // (m − 1) × 28 + 13. IFC June 13, 2026 (Gregorian June 2) is before the anchor and absent.
            starts shouldContainExactly
                listOf(
                    LocalDate.of(2026, 6, 30),
                    LocalDate.of(2026, 7, 28),
                    LocalDate.of(2026, 8, 25),
                    LocalDate.of(2026, 9, 22),
                    LocalDate.of(2026, 10, 20),
                    LocalDate.of(2026, 11, 17),
                    LocalDate.of(2026, 12, 15),
                )
            // A full IFC year of the rule, starting at the anchor's month, is 13 occurrences.
            startsIn(event, LocalDate.of(2026, 6, 30), LocalDate.of(2027, 6, 29)).shouldHaveSize(13)
            startsIn(event, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)).shouldHaveSize(13)
            startsIn(event, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31)).forEach {
                IfcDate.from(it).isIntercalary shouldBe false
            }
        }
    }

    @Test
    fun `a monthly IFC interval steps IFC months, not Gregorian ones`() {
        val event = allDayEvent(LocalDate.of(2026, 6, 30), IfcRecurrence.MonthlyOnDay(13, interval = 2))
        // Anchor Sol (month 7); every second IFC month is August, October, December, then February 2027.
        startsIn(event, LocalDate.of(2026, 6, 30), LocalDate.of(2027, 2, 28)) shouldContainExactly
            listOf(
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2027, 2, 10),
            )
    }

    // --- UNTIL, COUNT and exdates -------------------------------------------------------------------

    @Test
    fun `UNTIL is inclusive on the occurrence's own start date`() {
        fun until(date: LocalDate) =
            allDayEvent(
                LocalDate.of(2026, 6, 30),
                IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, end = RecurrenceEnd.Until(date)),
            )
        assertSoftly {
            startsIn(until(LocalDate.of(2028, 6, 30)), LocalDate.of(2026, 1, 1), LocalDate.of(2032, 12, 31))
                .shouldContainExactly(
                    listOf(LocalDate.of(2026, 6, 30), LocalDate.of(2027, 6, 30), LocalDate.of(2028, 6, 30)),
                )
            startsIn(until(LocalDate.of(2028, 6, 29)), LocalDate.of(2026, 1, 1), LocalDate.of(2032, 12, 31))
                .shouldContainExactly(
                    listOf(LocalDate.of(2026, 6, 30), LocalDate.of(2027, 6, 30)),
                )
            expander.recurrenceEndDate(until(LocalDate.of(2028, 6, 29))) shouldBe LocalDate.of(2027, 6, 30)
            expander.recurrenceEndDate(until(LocalDate.of(2028, 6, 30))) shouldBe LocalDate.of(2028, 6, 30)
        }
    }

    @Test
    fun `COUNT counts from the anchor - excluded occurrences count, skipped rule years do not`() {
        val skipping =
            allDayEvent(
                LocalDate.of(2024, 6, 17),
                IfcRecurrence.YearlyOnIntercalary(
                    IntercalaryDay.LeapDay(LeapDayPolicy.SKIP),
                    end = RecurrenceEnd.Count(3),
                ),
                exdates = setOf(LocalDate.of(2028, 6, 17)),
            )
        assertSoftly {
            // Rule years 2025–2027 yield nothing and do not spend the count: 2024, 2028, 2032 are the
            // three occurrences, and the excluded 2028 one still counts.
            startsIn(skipping, LocalDate.of(2024, 1, 1), LocalDate.of(2040, 12, 31)) shouldContainExactly
                listOf(LocalDate.of(2024, 6, 17), LocalDate.of(2032, 6, 17))
            expander.recurrenceEndDate(skipping) shouldBe LocalDate.of(2032, 6, 17)
            expander.nextOccurrence(skipping, LocalDate.of(2025, 1, 1))?.occurrenceDate shouldBe
                LocalDate.of(2032, 6, 17)
        }
    }

    @Test
    fun `COUNT one means the event happens once and a huge COUNT stops at year 9999`() {
        val once =
            allDayEvent(
                LocalDate.of(2026, 6, 30),
                IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, end = RecurrenceEnd.Count(1)),
            )
        val endless =
            allDayEvent(
                LocalDate.of(2026, 6, 30),
                IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, end = RecurrenceEnd.Count(Int.MAX_VALUE)),
            )
        assertSoftly {
            startsIn(once, LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31)) shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30))
            expander.recurrenceEndDate(once) shouldBe LocalDate.of(2026, 6, 30)
            // Rule years run out at 9999, where Sol 13 is June 30 again.
            expander.recurrenceEndDate(endless) shouldBe LocalDate.of(9999, 6, 30)
        }
    }

    @Test
    fun `exdates are matched on the occurrence's own start date and ignored by recurrenceEndDate`() {
        val event =
            allDayEvent(
                LocalDate.of(2026, 6, 30),
                IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, end = RecurrenceEnd.Count(4)),
                exdates = setOf(LocalDate.of(2027, 6, 30), LocalDate.of(2028, 6, 30)),
            )
        assertSoftly {
            startsIn(event, LocalDate.of(2026, 1, 1), LocalDate.of(2032, 12, 31)) shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30), LocalDate.of(2029, 6, 30))
            expander.nextOccurrence(event, LocalDate.of(2027, 1, 1))?.occurrenceDate shouldBe LocalDate.of(2029, 6, 30)
            expander.recurrenceEndDate(event) shouldBe LocalDate.of(2029, 6, 30)
        }
    }

    // --- shape, zones and daylight saving -----------------------------------------------------------

    @Test
    fun `every occurrence keeps the anchor's time, length, zone and all-day flag`() {
        val timed =
            timedEvent(
                LocalDate.of(2026, 6, 30),
                minuteOfDay = 9 * 60 + 30,
                durationMinutes = 45,
                zone = newYork,
                recurrence = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13),
            )
        val second = expander.expand(timed, LocalDate.of(2027, 1, 1)..LocalDate.of(2027, 12, 31), utc).single()
        val multiDay =
            allDayEvent(
                LocalDate.of(2026, 12, 31),
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay),
                days = 3,
            )
        val nextYearDay =
            expander.expand(multiDay, LocalDate.of(2027, 12, 1)..LocalDate.of(2027, 12, 31), utc).single()
        assertSoftly {
            second.startLocal.toLocalDate() shouldBe LocalDate.of(2027, 6, 30)
            second.startLocal.toLocalTime() shouldBe LocalTime.of(9, 30)
            second.endLocal.toLocalTime() shouldBe LocalTime.of(10, 15)
            second.zone shouldBe newYork
            second.allDay shouldBe false
            second.eventId shouldBe 1L
            nextYearDay.allDay shouldBe true
            nextYearDay.zone shouldBe null
            nextYearDay.lastDate shouldBe LocalDate.of(2028, 1, 2)
        }
    }

    @Test
    fun `a zoned occurrence is found on the device-zone dates it touches, not on its own date`() {
        // 23:00–24:00 on June 30 in New York (EDT, −04:00) is 12:00–13:00 on July 1 in Tokyo (+09:00).
        val event =
            timedEvent(
                LocalDate.of(2026, 6, 30),
                minuteOfDay = 23 * 60,
                durationMinutes = 60,
                zone = newYork,
                recurrence = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13),
            )
        assertSoftly {
            startsIn(event, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1), tokyo) shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30))
            startsIn(event, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 30), tokyo).shouldHaveSize(0)
            startsIn(event, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 30), newYork) shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30))
        }
    }

    @Test
    fun `a multi-day occurrence that began before the range is returned once`() {
        val event =
            allDayEvent(
                LocalDate.of(2026, 12, 31),
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay),
                days = 3,
            )
        // The 2026 occurrence covers December 31 – January 2; the range holds only its last two dates.
        val found = expander.expand(event, LocalDate.of(2027, 1, 1)..LocalDate.of(2027, 1, 2), utc)
        found.map { it.occurrenceDate } shouldContainExactly listOf(LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `nominal wall times survive a daylight-saving gap and an overlap`() {
        val gap =
            timedEvent(
                LocalDate.of(2026, 3, 7),
                minuteOfDay = 2 * 60 + 30,
                durationMinutes = 60,
                zone = newYork,
                recurrence = Recurrence.Gregorian("FREQ=DAILY"),
            )
        val overlap =
            timedEvent(
                LocalDate.of(2026, 10, 31),
                minuteOfDay = 60 + 30,
                durationMinutes = 60,
                zone = newYork,
                recurrence = Recurrence.Gregorian("FREQ=DAILY"),
            )
        val gapDay =
            expander.expand(gap, LocalDate.of(2026, 3, 8)..LocalDate.of(2026, 3, 8), newYork).single()
        val overlapDay =
            expander.expand(overlap, LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 1), newYork).single()
        assertSoftly {
            // Listed at the nominal 02:30 it never had; it happens at 03:30 EDT (docs/contracts/Events.md §4).
            gapDay.startLocal.toLocalTime() shouldBe LocalTime.of(2, 30)
            gapDay.start(newYork).toLocalTime() shouldBe LocalTime.of(3, 30)
            gapDay.start(newYork).offset shouldBe ZoneOffset.ofHours(-4)
            // 01:30 exists twice on November 1; the earlier instant, still EDT, is the one.
            overlapDay.startLocal.toLocalTime() shouldBe LocalTime.of(1, 30)
            overlapDay.start(newYork).offset shouldBe ZoneOffset.ofHours(-4)
        }
    }

    // --- Gregorian RRULEs ---------------------------------------------------------------------------

    @Test
    fun `a weekly RRULE follows the real seven-day week`() {
        val event =
            timedEvent(
                LocalDate.of(2026, 1, 5),
                minuteOfDay = 18 * 60,
                durationMinutes = 60,
                zone = null,
                recurrence = Recurrence.Gregorian("FREQ=WEEKLY;BYDAY=MO"),
            )
        assertSoftly {
            expander.supports(event.recurrence) shouldBe true
            startsIn(event, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 2)) shouldContainExactly
                listOf(
                    LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 1, 12),
                    LocalDate.of(2026, 1, 19),
                    LocalDate.of(2026, 1, 26),
                    LocalDate.of(2026, 2, 2),
                )
            expander.recurrenceEndDate(event) shouldBe null
        }
    }

    @Test
    fun `a monthly RRULE is Gregorian - twelve a year, not thirteen - and honours UNTIL`() {
        val monthly =
            allDayEvent(LocalDate.of(2026, 1, 15), Recurrence.Gregorian("FREQ=MONTHLY"))
        val bounded =
            timedEvent(
                LocalDate.of(2026, 1, 5),
                minuteOfDay = 18 * 60,
                durationMinutes = 60,
                zone = null,
                recurrence = Recurrence.Gregorian("FREQ=WEEKLY;BYDAY=MO;UNTIL=20260202"),
            )
        assertSoftly {
            startsIn(monthly, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).shouldHaveSize(12)
            startsIn(monthly, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)) shouldContainExactly
                listOf(LocalDate.of(2026, 2, 15))
            startsIn(bounded, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)) shouldContainExactly
                listOf(
                    LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 1, 12),
                    LocalDate.of(2026, 1, 19),
                    LocalDate.of(2026, 1, 26),
                    LocalDate.of(2026, 2, 2),
                )
            expander.recurrenceEndDate(bounded) shouldBe LocalDate.of(2026, 2, 2)
        }
    }

    @Test
    fun `an unsupported or unparseable RRULE becomes a single occurrence and never throws`() {
        val unsupported =
            listOf(
                "FREQ=SECONDLY",
                "FREQ=MINUTELY;INTERVAL=5",
                "FREQ=HOURLY",
                "FREQ=DAILY;BYHOUR=9,17",
                "not an rrule at all",
                "FREQ=NEVERLY",
            )
        assertSoftly {
            unsupported.forEach { text ->
                val recurrence = Recurrence.Gregorian(text)
                val event = allDayEvent(LocalDate.of(2026, 6, 30), recurrence)
                expander.supports(recurrence) shouldBe false
                startsIn(event, LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31)) shouldContainExactly
                    listOf(LocalDate.of(2026, 6, 30))
                expander.nextOccurrence(event, LocalDate.of(2026, 1, 1))?.occurrenceDate shouldBe
                    LocalDate.of(2026, 6, 30)
                // Never pruned by mistake: an unsupported rule has no known end.
                expander.recurrenceEndDate(event) shouldBe null
            }
        }
    }

    @Test
    @Timeout(30)
    fun `a hostile RRULE is bounded rather than hanging`() {
        val event =
            allDayEvent(LocalDate.of(2026, 6, 30), Recurrence.Gregorian("FREQ=DAILY;COUNT=2000000000"))
        assertSoftly {
            expander.supports(event.recurrence) shouldBe true
            // A ten-day range costs ten occurrences, whatever the rule claims.
            startsIn(event, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10)).shouldHaveSize(10)
            // The end cannot be found inside MAX_OCCURRENCES_PER_CALL steps, so it stays unbounded.
            expander.recurrenceEndDate(event) shouldBe null
            DefaultRecurrenceExpander.MAX_OCCURRENCES_PER_CALL shouldBe 10_000
        }
    }

    // --- the end of the supported range --------------------------------------------------------------

    @Test
    fun `nothing exists after year 9999`() {
        val event =
            allDayEvent(LocalDate.of(9998, 12, 31), IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay))
        val counted =
            allDayEvent(
                LocalDate.of(9998, 12, 31),
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay, end = RecurrenceEnd.Count(5)),
            )
        assertSoftly {
            startsIn(event, LocalDate.of(9998, 1, 1), LocalDate.of(9999, 12, 31)) shouldContainExactly
                listOf(LocalDate.of(9998, 12, 31), LocalDate.of(9999, 12, 31))
            expander.nextOccurrence(event, LocalDate.of(9999, 1, 1))?.occurrenceDate shouldBe
                LocalDate.of(9999, 12, 31)
            expander.nextOccurrence(event, LocalDate.of(9999, 12, 31))?.occurrenceDate shouldBe
                LocalDate.of(9999, 12, 31)
            // A COUNT that would run past year 9999 ends at the last occurrence that exists.
            expander.recurrenceEndDate(counted) shouldBe LocalDate.of(9999, 12, 31)
        }
    }
}
