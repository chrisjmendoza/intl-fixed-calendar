package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// The Event aggregate and its parts: the invariants of docs/ARCHITECTURE.md §3.2 (start_minute_of_day NULL
// iff all-day, all-day always floating, duration N*1440, end_epoch_day) and docs/contracts/Events.md.
class EventTest {
    private val newYork = ZoneId.of("America/New_York")
    private val sol13In2026 = LocalDate.of(2026, 6, 30)
    private val yearDay2026 = LocalDate.of(2026, 12, 31)
    private val leapDay2024 = LocalDate.of(2024, 6, 17)

    private fun allDay(
        date: LocalDate,
        days: Int = 1,
        recurrence: Recurrence = Recurrence.None,
        exdates: Set<LocalDate> = emptySet(),
    ) = Event(
        uid = "uid",
        title = "Title",
        timing = EventTiming.AllDay(date, days),
        recurrence = recurrence,
        exdates = exdates,
    )

    // --- timing -----------------------------------------------------------------------------------------

    @Test
    fun `an all-day timing has no start minute, no zone, and N times 1440 minutes`() {
        val timing: EventTiming = EventTiming.AllDay(yearDay2026, days = 3)
        assertSoftly {
            timing.startMinuteOfDay.shouldBeNull()
            timing.zone.shouldBeNull()
            timing.durationMinutes shouldBe 4320
            EventTiming.AllDay(yearDay2026).durationMinutes shouldBe 1440
        }
    }

    @Test
    fun `a timed timing always has a start minute and may be zoned or floating`() {
        val zoned =
            EventTiming.Timed(
                LocalDate.of(2026, 3, 8),
                startMinuteOfDay = 9 * 60 + 30,
                durationMinutes = 45,
                newYork,
            )
        val floating: EventTiming = EventTiming.Timed(LocalDate.of(2026, 3, 8), 0, 0)
        assertSoftly {
            zoned.start shouldBe LocalDateTime.of(2026, 3, 8, 9, 30)
            zoned.zone shouldBe newYork
            floating.zone.shouldBeNull()
            floating.startMinuteOfDay shouldBe 0
        }
    }

    @Test
    fun `timing rejects impossible values`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { EventTiming.AllDay(yearDay2026, days = 0) }
            shouldThrow<IllegalArgumentException> { EventTiming.AllDay(yearDay2026, days = EventTiming.MAX_DAYS + 1) }
            shouldThrow<IllegalArgumentException> { EventTiming.Timed(yearDay2026, -1, 60) }
            shouldThrow<IllegalArgumentException> { EventTiming.Timed(yearDay2026, 1440, 60) }
            shouldThrow<IllegalArgumentException> { EventTiming.Timed(yearDay2026, 0, -1) }
            shouldNotThrowAny { EventTiming.Timed(yearDay2026, 1439, 0) }
            // The longest all-day length still fits an Int of minutes.
            EventTiming.AllDay(LocalDate.of(1, 1, 1), EventTiming.MAX_DAYS).durationMinutes shouldBe 2_147_483_520
        }
    }

    @Test
    fun `timed from two wall-clock pickers measures nominal minutes and truncates seconds`() {
        assertSoftly {
            EventTiming.timed(
                LocalDateTime.of(2026, 12, 31, 22, 0, 59),
                LocalDateTime.of(2027, 1, 1, 1, 30),
                newYork,
            ) shouldBe EventTiming.Timed(yearDay2026, 22 * 60, 210, newYork)
            // Nominal, not elapsed: 01:30 to 03:30 across the New York spring-forward gap is 120 minutes.
            EventTiming
                .timed(LocalDateTime.of(2026, 3, 8, 1, 30), LocalDateTime.of(2026, 3, 8, 3, 30), newYork)
                .durationMinutes shouldBe 120
            shouldThrow<IllegalArgumentException> {
                EventTiming.timed(LocalDateTime.of(2026, 3, 8, 9, 0), LocalDateTime.of(2026, 3, 8, 8, 59))
            }
        }
    }

    // --- first occurrence and end date ------------------------------------------------------------------

    @Test
    fun `the first occurrence of an all-day event spans whole dates and ends exclusively`() {
        val event = allDay(LocalDate.of(2026, 12, 30), days = 3).copy(id = 7)
        val first = event.firstOccurrence()
        assertSoftly {
            first shouldBe
                Occurrence(
                    eventId = 7,
                    startLocal = LocalDateTime.of(2026, 12, 30, 0, 0),
                    endLocal = LocalDateTime.of(2027, 1, 2, 0, 0),
                    zone = null,
                    allDay = true,
                )
            event.startDate shouldBe LocalDate.of(2026, 12, 30)
            // Dec 30, Year Day (Dec 31), Jan 1.
            event.endDate shouldBe LocalDate.of(2027, 1, 1)
            event.isAllDay shouldBe true
        }
    }

    @Test
    fun `the end date of a timed event is the last date it touches, not the date of an exclusive midnight end`() {
        fun timed(
            startMinute: Int,
            duration: Int,
        ) = Event(uid = "u", title = "", timing = EventTiming.Timed(yearDay2026, startMinute, duration, newYork))
        assertSoftly {
            timed(22 * 60, 60).endDate shouldBe yearDay2026
            // 22:00–00:00 ends exactly at midnight and does not touch January 1.
            timed(22 * 60, 120).endDate shouldBe yearDay2026
            timed(22 * 60, 121).endDate shouldBe LocalDate.of(2027, 1, 1)
            timed(0, 0).endDate shouldBe yearDay2026
            timed(22 * 60, 120).firstOccurrence().endLocal shouldBe LocalDateTime.of(2027, 1, 1, 0, 0)
            timed(22 * 60, 120).isAllDay shouldBe false
        }
    }

    // --- invariants -------------------------------------------------------------------------------------

    @Test
    fun `rejects bad ids, a blank uid and over-long text`() {
        val valid = allDay(sol13In2026)
        assertSoftly {
            shouldThrow<IllegalArgumentException> { valid.copy(id = -1) }
            shouldThrow<IllegalArgumentException> { valid.copy(calendarId = 0) }
            shouldThrow<IllegalArgumentException> { valid.copy(uid = " ") }
            shouldThrow<IllegalArgumentException> { valid.copy(title = "x".repeat(Event.MAX_TITLE_LENGTH + 1)) }
            shouldThrow<IllegalArgumentException> {
                valid.copy(description = "x".repeat(Event.MAX_DESCRIPTION_LENGTH + 1))
            }
            shouldThrow<IllegalArgumentException> { valid.copy(location = "x".repeat(Event.MAX_LOCATION_LENGTH + 1)) }
            shouldNotThrowAny {
                valid.copy(
                    title = "x".repeat(500),
                    description = "x".repeat(10_000),
                    location = "x".repeat(500),
                )
            }
            // A blank title is allowed; the UI shows "(No title)".
            shouldNotThrowAny { valid.copy(title = "") }
            shouldThrow<IllegalArgumentException> {
                valid.copy(createdAt = Instant.ofEpochSecond(10), updatedAt = Instant.ofEpochSecond(9))
            }
        }
    }

    @Test
    fun `defaults describe a new plain event in the built-in calendar`() {
        val event = allDay(sol13In2026)
        assertSoftly {
            event.id shouldBe Event.NEW_ID
            event.calendarId shouldBe EventCalendar.DEFAULT_ID
            event.category shouldBe EventCategory.EVENT
            event.colorArgb.shouldBeNull()
            event.recurrence shouldBe Recurrence.None
            event.isRecurring shouldBe false
            event.createdAt shouldBe Event.NOT_STORED
            event.updatedAt shouldBe Event.NOT_STORED
        }
    }

    @Test
    fun `every date of the first occurrence must lie in years 1 to 9999`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { allDay(LocalDate.of(10_000, 1, 1)) }
            shouldThrow<IllegalArgumentException> { allDay(LocalDate.of(0, 12, 31)) }
            // Year Day 9999 is the last day there is; a second day would be in year 10000.
            shouldNotThrowAny { allDay(LocalDate.of(9999, 12, 31)) }
            shouldThrow<IllegalArgumentException> { allDay(LocalDate.of(9999, 12, 31), days = 2) }
            shouldThrow<IllegalArgumentException> {
                Event(uid = "u", title = "", timing = EventTiming.Timed(LocalDate.of(9999, 12, 31), 1439, 2))
            }
        }
    }

    @Test
    fun `a non-recurring event has no exdates and no exdate precedes the start`() {
        val yearly = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13)
        assertSoftly {
            shouldThrow<IllegalArgumentException> { allDay(sol13In2026, exdates = setOf(sol13In2026)) }
            shouldThrow<IllegalArgumentException> {
                allDay(sol13In2026, recurrence = yearly, exdates = setOf(LocalDate.of(2025, 6, 30)))
            }
            // The first date itself may be excluded; firstOccurrence still reports it.
            val event = allDay(sol13In2026, recurrence = yearly, exdates = setOf(sol13In2026))
            event.firstOccurrence().occurrenceDate shouldBe sol13In2026
            event.isRecurring shouldBe true
        }
    }

    @Test
    fun `an IFC rule must be anchored on the event start, floating days included`() {
        assertSoftly {
            shouldNotThrowAny { allDay(sol13In2026, recurrence = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13)) }
            shouldNotThrowAny { allDay(sol13In2026, recurrence = IfcRecurrence.MonthlyOnDay(13)) }
            shouldNotThrowAny {
                allDay(yearDay2026, recurrence = IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay))
            }
            for (policy in LeapDayPolicy.entries) {
                shouldNotThrowAny {
                    allDay(leapDay2024, recurrence = IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy)))
                }
            }
            // Gregorian July 13 is not Sol 13; June 17 of a common year is not Leap Day.
            shouldThrow<IllegalArgumentException> {
                allDay(LocalDate.of(2026, 7, 13), recurrence = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13))
            }
            shouldThrow<IllegalArgumentException> {
                allDay(
                    LocalDate.of(2026, 6, 17),
                    recurrence = IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay()),
                )
            }
            shouldThrow<IllegalArgumentException> {
                allDay(yearDay2026, recurrence = IfcRecurrence.MonthlyOnDay(28))
            }
            // A Gregorian rule is carried as text and not checked against the start.
            shouldNotThrowAny { allDay(yearDay2026, recurrence = Recurrence.Gregorian("FREQ=WEEKLY;BYDAY=MO")) }
        }
    }

    @Test
    fun `the anchor of a zoned timed event is its own wall-clock date`() {
        // 23:30 on Sol 13 in New York is already July 1 in UTC; the rule is evaluated on New York's date.
        val timing = EventTiming.Timed(sol13In2026, 23 * 60 + 30, 60, newYork)
        shouldNotThrowAny {
            Event(uid = "u", title = "", timing = timing, recurrence = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13))
        }
    }

    @Test
    fun `UNTIL may not precede the start, so occurrence 1 always exists`() {
        fun until(date: LocalDate) = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, end = RecurrenceEnd.Until(date))
        assertSoftly {
            shouldNotThrowAny { allDay(sol13In2026, recurrence = until(sol13In2026)) }
            shouldThrow<IllegalArgumentException> { allDay(sol13In2026, recurrence = until(LocalDate.of(2026, 6, 29))) }
        }
    }

    // --- privacy and ordering ---------------------------------------------------------------------------

    @Test
    fun `toString never contains the title, description or location`() {
        val event =
            allDay(sol13In2026).copy(
                id = 12,
                title = "Oncology appointment",
                description = "Bring the referral letter",
                location = "Mercy Hospital",
            )
        val text = event.toString()
        assertSoftly {
            text shouldNotContain "Oncology"
            text shouldNotContain "referral"
            text shouldNotContain "Mercy"
            AgendaEntry(event, event.firstOccurrence(), 0, ZoneId.of("UTC")).toString() shouldNotContain "Oncology"
            DayAgenda
                .of(sol13In2026, listOf(AgendaEntry(event, event.firstOccurrence(), 0, ZoneId.of("UTC"))))
                .toString() shouldNotContain "Mercy"
        }
    }

    @Test
    fun `list order is start date, all-day first, start minute, then id`() {
        fun timed(
            id: Long,
            date: LocalDate,
            minute: Int,
        ) = Event(id = id, uid = "u$id", title = "", timing = EventTiming.Timed(date, minute, 30))
        val a = timed(1, yearDay2026, 600)
        val b = timed(2, yearDay2026, 540)
        val c = allDay(yearDay2026).copy(id = 3)
        val d = timed(4, sol13In2026, 1380)
        val e = timed(5, yearDay2026, 540)
        listOf(a, b, c, d, e).sortedWith(Event.LIST_ORDER).map { it.id } shouldContainExactly listOf(4L, 3L, 2L, 5L, 1L)
    }

    // --- small value types ------------------------------------------------------------------------------

    @Test
    fun `reminders are non-negative and ordered closest first`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { Reminder(-1) }
            setOf(Reminder(1440), Reminder(0), Reminder(10)).sorted() shouldContainExactly
                listOf(Reminder(0), Reminder(10), Reminder(1440))
        }
    }

    @Test
    fun `a Gregorian rule is the RRULE value, without the property name or control characters`() {
        assertSoftly {
            shouldNotThrowAny { Recurrence.Gregorian("FREQ=YEARLY;BYMONTH=7;BYMONTHDAY=1") }
            // Not validated as an RRULE here: that is RecurrenceExpander.supports.
            shouldNotThrowAny { Recurrence.Gregorian("FREQ=SOMETIMES") }
            shouldThrow<IllegalArgumentException> { Recurrence.Gregorian("") }
            shouldThrow<IllegalArgumentException> { Recurrence.Gregorian("  ") }
            shouldThrow<IllegalArgumentException> { Recurrence.Gregorian("RRULE:FREQ=DAILY") }
            shouldThrow<IllegalArgumentException> { Recurrence.Gregorian("rrule:FREQ=DAILY") }
            shouldThrow<IllegalArgumentException> {
                Recurrence.Gregorian(
                    "FREQ=DAILY\r\nATTENDEE:mailto:x@example.com",
                )
            }
        }
    }

    @Test
    fun `the built-in calendar is local, visible, unnamed and brand teal`() {
        assertSoftly {
            EventCalendar.DEFAULT shouldBe
                EventCalendar(
                    id = 1,
                    name = "",
                    colorArgb = 0xFF123F3D.toInt(),
                    source = CalendarSource.LOCAL,
                    visible = true,
                )
            shouldThrow<IllegalArgumentException> { EventCalendar(id = -1, name = "x") }
            shouldThrow<IllegalArgumentException> {
                EventCalendar(
                    name = "x".repeat(EventCalendar.MAX_NAME_LENGTH + 1),
                )
            }
        }
    }

    @Test
    fun `random uids are distinct canonical UUIDs`() {
        val uids = List(50) { RandomEventUidGenerator.newUid() }
        assertSoftly {
            uids.toSet().size shouldBe 50
            uids.forEach {
                java.util.UUID
                    .fromString(it)
                    .toString() shouldBe it
            }
        }
    }
}
