package io.github.chrisjmendoza.yearal.core.scheduling.reminder

import io.github.chrisjmendoza.yearal.core.domain.event.DefaultRecurrenceExpander
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import io.github.chrisjmendoza.yearal.core.domain.event.Reminder
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Oracle tests for [ReminderPlanner], the computation half of the next-alarm pattern
 * (`docs/ARCHITECTURE.md` §3.2 "Reminders"; FEATURES E4).
 *
 * Every expected instant below is worked out by hand from the rules in
 * `docs/contracts/Events.md` §2 and §4 and `docs/holidays-and-import.md` §5.1, never read off the
 * implementation: Sol 13 is Gregorian June 30 (IFC day-of-year 181 in a common year, 182 in a leap
 * year, because Leap Day is inserted after IFC June), Year Day is December 31, Leap Day is June 17 of
 * a leap year, and in a common year the Leap Day policies give June 17 (`JUNE_28`), nothing (`SKIP`)
 * or June 18 (`SOL_1`). All-day reminders are measured from 09:00 in the device zone; timed ones from
 * the occurrence's resolved start.
 *
 * Plain JUnit4 with no Robolectric: the planner is pure.
 */
class ReminderPlannerTest {
    private val expander = DefaultRecurrenceExpander()
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun plan(
        vararg events: Event,
        zone: ZoneId = ZoneOffset.UTC,
        from: LocalDate,
        after: Instant = Instant.MIN,
        now: Instant = Instant.MIN,
    ): List<PendingReminder> = ReminderPlanner.pending(events.toList(), expander, zone, from, after, now)

    private fun List<PendingReminder>.triggers(): List<Instant> = map { it.triggerAt }

    /** A floating timed event with one reminder, for the daylight-saving cases. */
    private fun floatingTimed(
        date: LocalDate,
        minuteOfDay: Int,
        minutesBefore: Int,
    ): Event =
        Event(
            id = 7,
            uid = "floating-timed",
            title = "Standup",
            timing = EventTiming.Timed(date, minuteOfDay, 30),
            reminders = setOf(Reminder(minutesBefore)),
        )

    // ---------------------------------------------------------------------------------------------
    // Timed events
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a one-off timed event fires its reminder before the resolved start`() {
        // EventFixtures.timedZoned: 09:30 New York on 8 March 2026, already EDT (UTC-4) = 13:30Z,
        // with a 10-minute reminder.
        val plan = plan(EventFixtures.timedZoned(id = 1), zone = newYork, from = LocalDate.of(2026, 3, 6))

        plan.shouldHaveSize(1).single().let {
            it.eventId shouldBe 1L
            it.occurrenceDate shouldBe LocalDate.of(2026, 3, 8)
            it.minutesBefore shouldBe 10
            it.allDay shouldBe false
            it.triggerAt shouldBe Instant.parse("2026-03-08T13:20:00Z")
        }
    }

    @Test
    fun `a zoned event keeps its own wall time when the device is in another zone`() {
        // The same 13:30Z start, read in Tokyo as 22:30 on the same date; the instant does not move.
        val plan = plan(EventFixtures.timedZoned(id = 1), zone = tokyo, from = LocalDate.of(2026, 3, 6))

        val reminder = plan.shouldHaveSize(1).single()
        reminder.triggerAt shouldBe Instant.parse("2026-03-08T13:20:00Z")
        reminder.reference.toLocalDateTime() shouldBe LocalDateTime.of(2026, 3, 8, 22, 30)
        reminder.reference.zone shouldBe tokyo
    }

    @Test
    fun `a wall time inside the spring-forward gap resolves later by the length of the gap`() {
        // New York, 8 March 2026: 02:00 to 03:00 does not exist, so a nominal 02:30 happens at
        // 03:30 EDT = 07:30Z; the 60-minute reminder is an hour before that.
        val plan =
            plan(
                floatingTimed(LocalDate.of(2026, 3, 8), minuteOfDay = 2 * 60 + 30, minutesBefore = 60),
                zone = newYork,
                from = LocalDate.of(2026, 3, 8),
            )

        plan.triggers().shouldContainExactly(Instant.parse("2026-03-08T06:30:00Z"))
    }

    @Test
    fun `a wall time inside the fall-back overlap resolves to the earlier instant`() {
        // New York, 1 November 2026: 01:30 happens twice; the earlier one is 01:30 EDT = 05:30Z, so
        // the 30-minute reminder is at 05:00Z and not an hour later.
        val plan =
            plan(
                floatingTimed(LocalDate.of(2026, 11, 1), minuteOfDay = 60 + 30, minutesBefore = 30),
                zone = newYork,
                from = LocalDate.of(2026, 11, 1),
            )

        plan.triggers().shouldContainExactly(Instant.parse("2026-11-01T05:00:00Z"))
    }

    // ---------------------------------------------------------------------------------------------
    // All-day events: 09:00 in the device zone
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an all-day reminder is measured from 09-00 on the first date in the device zone`() {
        ReminderPlanner.ALL_DAY_REMINDER_TIME.hour shouldBe 9
        // Sol 13 2026 is Gregorian 30 June; 09:00 EDT is 13:00Z, and 1440 minutes before that is
        // 09:00 EDT the day before, not midnight and not 09:00 UTC.
        val event =
            EventFixtures.allDay(
                id = 2,
                date = EventFixtures.SOL_13_2026,
                reminders = setOf(Reminder(0), Reminder(1440)),
            )

        val plan = plan(event, zone = newYork, from = LocalDate.of(2026, 6, 28))

        plan.triggers().shouldContainExactly(
            Instant.parse("2026-06-29T13:00:00Z"),
            Instant.parse("2026-06-30T13:00:00Z"),
        )
        plan.map { it.minutesBefore }.shouldContainExactly(1440, 0)
        plan.all { it.allDay } shouldBe true
        plan.map { it.occurrenceDate }.toSet().shouldContainExactly(LocalDate.of(2026, 6, 30))
    }

    @Test
    fun `an all-day reminder follows the device zone, not the date`() {
        val event = EventFixtures.allDay(id = 2, date = EventFixtures.SOL_13_2026, reminders = setOf(Reminder(0)))

        // 09:00 Tokyo (UTC+9) on the same 30 June; the date itself is never shifted between zones.
        plan(event, zone = tokyo, from = LocalDate.of(2026, 6, 28)).triggers().shouldContainExactly(
            Instant.parse("2026-06-30T00:00:00Z"),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // IFC recurrence
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `every Sol 13 finds the next IFC Sol 13, which is Gregorian 30 June`() {
        val event = EventFixtures.sol13Yearly(id = 3).copy(reminders = setOf(Reminder(30)))

        val plan = plan(event, from = LocalDate.of(2027, 1, 1))

        plan.shouldHaveSize(1).single().let {
            it.occurrenceDate shouldBe LocalDate.of(2027, 6, 30)
            it.triggerAt shouldBe Instant.parse("2027-06-30T08:30:00Z")
        }
    }

    @Test
    fun `every Year Day finds 31 December of the next rule year`() {
        val event = EventFixtures.yearDayYearly(id = 4).copy(reminders = setOf(Reminder(0)))

        val plan = plan(event, from = LocalDate.of(2027, 1, 5))

        plan.shouldHaveSize(1).single().let {
            it.occurrenceDate shouldBe LocalDate.of(2027, 12, 31)
            it.triggerAt shouldBe Instant.parse("2027-12-31T09:00:00Z")
        }
    }

    @Test
    fun `every Leap Day follows its common-year policy in a common year`() {
        val expected =
            mapOf(
                // IFC June 28 in a common year is Gregorian 17 June.
                LeapDayPolicy.JUNE_28 to (LocalDate.of(2026, 6, 17) to Instant.parse("2026-06-17T09:00:00Z")),
                // Sol 1 is Gregorian 18 June.
                LeapDayPolicy.SOL_1 to (LocalDate.of(2026, 6, 18) to Instant.parse("2026-06-18T09:00:00Z")),
                // Nothing in 2026 or 2027; the next occurrence is Leap Day 2028 itself, 17 June.
                LeapDayPolicy.SKIP to (LocalDate.of(2028, 6, 17) to Instant.parse("2028-06-17T09:00:00Z")),
            )

        expected.forEach { (policy, dateAndInstant) ->
            val (date, triggerAt) = dateAndInstant
            val event = EventFixtures.leapDayYearly(id = 5, policy = policy).copy(reminders = setOf(Reminder(0)))

            val plan = plan(event, from = LocalDate.of(2026, 1, 1))

            withClue(policy) {
                plan.shouldHaveSize(1).single().occurrenceDate shouldBe date
                plan.single().triggerAt shouldBe triggerAt
            }
        }
    }

    @Test
    fun `an exdated occurrence is skipped and the following one is used`() {
        val event =
            EventFixtures
                .sol13Yearly(id = 3)
                .copy(reminders = setOf(Reminder(0)), exdates = setOf(LocalDate.of(2027, 6, 30)))

        val plan = plan(event, from = LocalDate.of(2027, 1, 1))

        // Sol 13 2028 is still 30 June: Leap Day is inserted before Sol, so the IFC day-of-year
        // shifts from 181 to 182 and the Gregorian date does not move.
        plan.shouldHaveSize(1).single().let {
            it.occurrenceDate shouldBe LocalDate.of(2028, 6, 30)
            it.triggerAt shouldBe Instant.parse("2028-06-30T09:00:00Z")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bounds and the "after" high-water mark
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `reminders at or before the high-water mark are not returned again`() {
        val event = EventFixtures.allDay(id = 2, date = EventFixtures.SOL_13_2026, reminders = setOf(Reminder(0)))
        val triggerAt = Instant.parse("2026-06-30T09:00:00Z")

        plan(event, from = LocalDate.of(2026, 6, 28), after = triggerAt).shouldBeEmpty()
        plan(event, from = LocalDate.of(2026, 6, 28), after = triggerAt.minusMillis(1))
            .triggers()
            .shouldContainExactly(triggerAt)
    }

    @Test
    fun `an event whose rule has ended yields nothing`() {
        val event = EventFixtures.timedZoned(id = 1)

        plan(event, zone = newYork, from = LocalDate.of(2026, 3, 9)).shouldBeEmpty()
    }

    @Test
    fun `an event without reminders is ignored`() {
        plan(EventFixtures.sol13Yearly(id = 3), from = LocalDate.of(2026, 1, 1)).shouldBeEmpty()
    }

    @Test
    fun `a monthly rule with a short lead time stops after the first occurrence`() {
        val event = EventFixtures.thirteenthMonthly(id = 6).copy(reminders = setOf(Reminder(0)))

        // Sol 13 2026 = 30 June is the anchor and therefore occurrence 1.
        val plan = plan(event, from = LocalDate.of(2026, 6, 1))

        plan.shouldHaveSize(1).single().triggerAt shouldBe Instant.parse("2026-06-30T09:00:00Z")
    }

    @Test
    fun `a long lead time on a later occurrence is found, so two reminders can be due at once`() {
        // The 13th of every IFC month is 30 June, then 28 July 2026 — 28 days apart. With reminders
        // at the time and 28 days before, occurrence 2's early reminder falls on exactly occurrence
        // 1's own instant, so the walk has to look past the first occurrence to find it.
        val event = EventFixtures.thirteenthMonthly(id = 6).copy(reminders = setOf(Reminder(0), Reminder(28 * 1440)))
        val bothDue = Instant.parse("2026-06-30T09:00:00Z")

        val plan =
            plan(
                event,
                from = LocalDate.of(2026, 6, 28),
                after = Instant.parse("2026-06-02T09:00:00Z"), // the early one for 30 June is delivered
                now = bothDue,
            )

        plan
            .filter {
                !it.triggerAt.isAfter(
                    bothDue,
                )
            }.map { it.occurrenceDate to it.minutesBefore } shouldContainExactly
            listOf(
                LocalDate.of(2026, 6, 30) to 0,
                LocalDate.of(2026, 7, 28) to 28 * 1440,
            )
        // The walk stops one occurrence past the point where nothing earlier can appear, so the tail
        // beyond the next alarm is allowed to hold a little more than the caller needs.
        plan.first { it.triggerAt.isAfter(bothDue) }.triggerAt shouldBe Instant.parse("2026-07-28T09:00:00Z")
        plan.map { it.occurrenceDate }.distinct().size.shouldBeLessThanOrEqual(
            ReminderPlanner.MAX_OCCURRENCES_PER_EVENT,
        )
    }

    @Test
    fun `a backlog of missed occurrences is collected, not just the oldest one`() {
        // A daily all-day event with a 09:00 reminder, seen at 09:03 on the third day after a reboot:
        // the two older ones and today's must all be found, or the catch-up would deliver the wrong
        // one and mark the rest as handled.
        val event =
            EventFixtures
                .allDay(id = 11, date = LocalDate.of(2026, 6, 28), reminders = setOf(Reminder(0)))
                .copy(recurrence = Recurrence.Gregorian("FREQ=DAILY"))
        val now = Instant.parse("2026-06-30T09:03:00Z")

        val plan = plan(event, from = LocalDate.of(2026, 6, 28), now = now)

        plan.filter { !it.triggerAt.isAfter(now) }.triggers().shouldContainExactly(
            Instant.parse("2026-06-28T09:00:00Z"),
            Instant.parse("2026-06-29T09:00:00Z"),
            Instant.parse("2026-06-30T09:00:00Z"),
        )
        plan.first { it.triggerAt.isAfter(now) }.triggerAt shouldBe Instant.parse("2026-07-01T09:00:00Z")
    }

    @Test
    fun `several events are merged into one list ordered by trigger instant`() {
        val morning = EventFixtures.allDay(id = 8, date = LocalDate.of(2026, 7, 1), reminders = setOf(Reminder(0)))
        val earlier =
            EventFixtures.allDay(
                id = 9,
                date = LocalDate.of(2026, 7, 1),
                title = "Second all-day event",
                reminders = setOf(Reminder(60)),
            )

        val plan = plan(morning, earlier, from = LocalDate.of(2026, 6, 30))

        plan.map { it.eventId to it.triggerAt } shouldContainExactly
            listOf(
                9L to Instant.parse("2026-07-01T08:00:00Z"),
                8L to Instant.parse("2026-07-01T09:00:00Z"),
            )
    }

    @Test
    fun `two events due at the same instant are both returned, ordered by id`() {
        val first = EventFixtures.allDay(id = 8, date = LocalDate.of(2026, 7, 1), reminders = setOf(Reminder(0)))
        val second =
            EventFixtures.allDay(
                id = 9,
                date = LocalDate.of(2026, 7, 1),
                title = "Second all-day event",
                reminders = setOf(Reminder(0)),
            )

        val plan = plan(second, first, from = LocalDate.of(2026, 6, 30))

        plan.map { it.eventId }.shouldContainExactly(8L, 9L)
        plan.triggers().toSet().shouldContainExactly(Instant.parse("2026-07-01T09:00:00Z"))
    }

    @Test
    fun `a Gregorian weekly rule is followed like any other`() {
        // EventFixtures.weeklyGregorian: floating Mondays 18:00-19:00 from 5 January 2026.
        val event = EventFixtures.weeklyGregorian(id = 10).copy(reminders = setOf(Reminder(15)))

        val plan = plan(event, from = LocalDate.of(2026, 1, 6))

        plan.shouldHaveSize(1).single().let {
            it.occurrenceDate shouldBe LocalDate.of(2026, 1, 12)
            it.triggerAt shouldBe Instant.parse("2026-01-12T17:45:00Z")
        }
        event.recurrence shouldBe Recurrence.Gregorian("FREQ=WEEKLY;BYDAY=MO")
    }
}
