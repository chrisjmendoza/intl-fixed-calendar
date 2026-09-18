package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

// FakeRecurrenceExpander applies the rule-independent half of the RecurrenceExpander contract to scripted
// occurrences: range test on device-zone dates, exdates, ordering, next occurrence, recurrence end.
class FakeRecurrenceExpanderTest {
    private val utc = ZoneId.of("UTC")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val yearDays = (2026..2030).map { LocalDate.of(it, 12, 31) }

    @Test
    fun `a one-off event needs no script and is its own only occurrence`() {
        val expander = FakeRecurrenceExpander()
        val trip = EventFixtures.floatingMultiDay(id = 1)
        assertSoftly {
            // Touches December 30 – January 1: found from any date it touches, once.
            expander.expand(trip, LocalDate.of(2027, 1, 1)..LocalDate.of(2027, 1, 28), utc) shouldContainExactly
                listOf(trip.firstOccurrence())
            expander.expand(trip, LocalDate.of(2026, 12, 1)..LocalDate.of(2027, 1, 28), utc) shouldContainExactly
                listOf(trip.firstOccurrence())
            expander.expand(trip, LocalDate.of(2027, 1, 2)..LocalDate.of(2027, 1, 28), utc).shouldBeEmpty()
            expander.recurrenceEndDate(trip) shouldBe LocalDate.of(2027, 1, 1)
            expander.nextOccurrence(trip, LocalDate.of(2026, 12, 30)) shouldBe trip.firstOccurrence()
            expander.nextOccurrence(trip, LocalDate.of(2026, 12, 31)).shouldBeNull()
        }
    }

    @Test
    fun `an unscripted recurring event fails loudly`() {
        val expander = FakeRecurrenceExpander()
        val event = EventFixtures.yearDayYearly(id = 1)
        assertSoftly {
            shouldThrow<IllegalStateException> { expander.expand(event, yearDays.first()..yearDays.last(), utc) }
            shouldThrow<IllegalStateException> { expander.nextOccurrence(event, yearDays.first()) }
            shouldThrow<IllegalStateException> { expander.recurrenceEndDate(event) }
        }
    }

    @Test
    fun `scripted occurrences are filtered by range, ordered, and lose their exdates`() {
        val expander = FakeRecurrenceExpander()
        val event = EventFixtures.yearDayYearly(id = 1).copy(exdates = setOf(LocalDate.of(2028, 12, 31)))
        expander.scriptDates(event, yearDays.reversed())

        val all = expander.expand(event, LocalDate.of(2026, 1, 1)..LocalDate.of(2030, 12, 31), utc)
        assertSoftly {
            all.map { it.occurrenceDate } shouldContainExactly
                listOf(
                    LocalDate.of(2026, 12, 31),
                    LocalDate.of(2027, 12, 31),
                    LocalDate.of(2029, 12, 31),
                    LocalDate.of(2030, 12, 31),
                )
            all.all { it.allDay && it.eventId == 1L && it.lastDate == it.occurrenceDate } shouldBe true
            expander.expand(event, LocalDate.of(2027, 1, 1)..LocalDate.of(2027, 12, 30), utc).shouldBeEmpty()
            // An empty range is empty, not an error.
            expander.expand(event, LocalDate.of(2027, 12, 31)..LocalDate.of(2027, 1, 1), utc).shouldBeEmpty()
        }
    }

    @Test
    fun `the range test uses device-zone dates, not the occurrence's own date`() {
        val expander = FakeRecurrenceExpander()
        // 09:30 on March 8 in New York is 22:30 on March 8 in Tokyo; a week later (EDT) likewise.
        val call = EventFixtures.timedZoned(id = 3).copy(recurrence = Recurrence.Gregorian("FREQ=WEEKLY"))
        expander.scriptDates(call, listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 15)))
        // 20:00 on March 8 in New York would be March 9 in Tokyo.
        val late =
            call.copy(
                id = 4,
                uid = "late",
                timing = EventTiming.Timed(LocalDate.of(2026, 3, 8), 20 * 60, 45, EventFixtures.NEW_YORK),
            )
        expander.scriptDates(late, listOf(LocalDate.of(2026, 3, 8)))

        val march8 = LocalDate.of(2026, 3, 8)..LocalDate.of(2026, 3, 8)
        val march9 = LocalDate.of(2026, 3, 9)..LocalDate.of(2026, 3, 9)
        assertSoftly {
            expander.expand(call, march8, tokyo).map { it.occurrenceDate } shouldContainExactly
                listOf(LocalDate.of(2026, 3, 8))
            expander.expand(late, march8, tokyo).shouldBeEmpty()
            expander.expand(late, march9, tokyo).map { it.occurrenceDate } shouldContainExactly
                listOf(LocalDate.of(2026, 3, 8))
            expander.expand(late, march8, EventFixtures.NEW_YORK).size shouldBe 1
        }
    }

    @Test
    fun `nextOccurrence skips exdates and compares the own start date`() {
        val expander = FakeRecurrenceExpander()
        val event = EventFixtures.yearDayYearly(id = 1).copy(exdates = setOf(LocalDate.of(2027, 12, 31)))
        expander.scriptDates(event, yearDays)
        assertSoftly {
            expander.nextOccurrence(event, LocalDate.of(2026, 12, 31))?.occurrenceDate shouldBe
                LocalDate.of(2026, 12, 31)
            expander.nextOccurrence(event, LocalDate.of(2027, 1, 1))?.occurrenceDate shouldBe LocalDate.of(2028, 12, 31)
            expander.nextOccurrence(event, LocalDate.of(2031, 1, 1)).shouldBeNull()
        }
    }

    @Test
    fun `recurrenceEndDate is the last scripted date, ignoring exdates, or null when unbounded`() {
        val expander = FakeRecurrenceExpander()
        val event = EventFixtures.yearDayYearly(id = 1).copy(exdates = setOf(LocalDate.of(2030, 12, 31)))
        expander.scriptDates(event, yearDays)
        expander.recurrenceEndDate(event) shouldBe LocalDate.of(2030, 12, 31)
        expander.scriptDates(event, yearDays, unbounded = true)
        expander.recurrenceEndDate(event).shouldBeNull()
    }

    @Test
    fun `an unsupported rule behaves as a single occurrence and is reported`() {
        val expander = FakeRecurrenceExpander()
        val event = EventFixtures.weeklyGregorian(id = 8)
        expander.supports(event.recurrence) shouldBe true
        expander.markUnsupported(event.recurrence)
        assertSoftly {
            expander.supports(event.recurrence) shouldBe false
            expander.supports(Recurrence.None) shouldBe true
            expander.expand(event, LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31), utc) shouldContainExactly
                listOf(event.firstOccurrence())
            expander.recurrenceEndDate(event).shouldBeNull()
        }
    }

    @Test
    fun `a script must belong to its event`() {
        val expander = FakeRecurrenceExpander()
        val other = EventFixtures.yearDayYearly(id = 2).firstOccurrence()
        shouldThrow<IllegalArgumentException> { expander.script(1, listOf(other)) }
    }
}
