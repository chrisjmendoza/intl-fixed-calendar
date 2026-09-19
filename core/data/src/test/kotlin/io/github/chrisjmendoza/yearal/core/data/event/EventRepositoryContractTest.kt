package io.github.chrisjmendoza.yearal.core.data.event

import app.cash.turbine.test
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.domain.event.CalendarSource
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.IntercalaryDay
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.domain.event.Reminder
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The observable behaviour every [EventRepository] implementation must share
 * (`docs/contracts/Events.md`; ROADMAP M4 T2). Run against both
 * `io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository` ([FakeEventRepositoryContractTest])
 * and [RoomEventRepository] ([RoomEventRepositoryContractTest]) so the fake and the real thing cannot
 * silently disagree. Behaviour specific to one implementation (process-restart persistence, the
 * benchmark, FK-cascade at the row level, corrupted-row fail-soft) lives in `RoomEventRepositoryTest`
 * instead.
 */
public abstract class EventRepositoryContractTest {
    protected abstract val repository: EventRepository
    protected abstract val clock: MutableClock

    /**
     * A [RecurrenceExpander][io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander]-backed
     * subclass overrides this to script its fake expander before a recurring [event] is stored (Room's
     * `upsertEvent` asks the expander for `recurrenceEndDate` on every write). The fake repository needs
     * no script, since it never prunes by recurrence end.
     */
    protected open suspend fun prepareForStorage(event: Event) {}

    private suspend fun store(event: Event): Event {
        prepareForStorage(event)
        val id = repository.upsertEvent(event)
        return requireNotNull(repository.getEvent(id)) { "Just-stored event $id was not found" }
    }

    // ---- calendars ----

    @Test
    public fun `the built-in calendar exists from the start`(): Unit =
        runTest {
            repository.getCalendar(EventCalendar.DEFAULT_ID) shouldBe EventCalendar.DEFAULT
        }

    @Test
    public fun `upserting a new calendar assigns an id and stores it`(): Unit =
        runTest {
            val id = repository.upsertCalendar(EventCalendar(name = "Work", colorArgb = 0xFF112233.toInt()))
            id shouldNotBe EventCalendar.NEW_ID
            repository.getCalendar(id)?.name shouldBe "Work"
        }

    @Test
    public fun `upserting an existing calendar replaces it`(): Unit =
        runTest {
            val id = repository.upsertCalendar(EventCalendar(name = "Work"))
            repository.upsertCalendar(EventCalendar(id = id, name = "Renamed"))
            repository.getCalendar(id)?.name shouldBe "Renamed"
        }

    @Test
    public fun `upserting an unknown calendar id throws and changes nothing`(): Unit =
        runTest {
            val before = repository.observeCalendars().first()
            shouldThrow<IllegalArgumentException> {
                repository.upsertCalendar(EventCalendar(id = 999L, name = "x"))
            }
            repository.observeCalendars().first() shouldBe before
        }

    @Test
    public fun `the built-in calendar cannot be deleted`(): Unit =
        runTest {
            shouldThrow<IllegalArgumentException> { repository.deleteCalendar(EventCalendar.DEFAULT_ID) }
        }

    @Test
    public fun `the built-in calendar cannot become ICS`(): Unit =
        runTest {
            shouldThrow<IllegalArgumentException> {
                repository.upsertCalendar(EventCalendar.DEFAULT.copy(source = CalendarSource.ICS))
            }
        }

    @Test
    public fun `deleting a calendar deletes its events`(): Unit =
        runTest {
            val calendarId = repository.upsertCalendar(EventCalendar(name = "Trip"))
            val event = store(EventFixtures.allDay(calendarId = calendarId))
            repository.deleteCalendar(calendarId) shouldBe true
            repository.getEvent(event.id) shouldBe null
            repository.getCalendar(calendarId) shouldBe null
        }

    @Test
    public fun `deleting an unknown calendar returns false`(): Unit =
        runTest {
            repository.deleteCalendar(999L) shouldBe false
        }

    // ---- events: upsert, get, delete ----

    @Test
    public fun `a new event is assigned a positive id and both timestamps from the clock`(): Unit =
        runTest {
            clock.set(FIXED_INSTANT)
            val stored = store(EventFixtures.allDay())
            stored.id shouldNotBe Event.NEW_ID
            stored.createdAt shouldBe FIXED_INSTANT
            stored.updatedAt shouldBe FIXED_INSTANT
        }

    @Test
    public fun `updating an event keeps createdAt and advances updatedAt`(): Unit =
        runTest {
            clock.set(FIXED_INSTANT)
            val stored = store(EventFixtures.allDay(title = "Original"))
            clock.advanceBy(Duration.ofHours(1))
            val updated = store(stored.copy(title = "Renamed"))
            updated.createdAt shouldBe FIXED_INSTANT
            updated.updatedAt shouldBe FIXED_INSTANT.plus(Duration.ofHours(1))
            updated.title shouldBe "Renamed"
        }

    @Test
    public fun `upserting an unknown event id throws and changes nothing`(): Unit =
        runTest {
            val before = repository.observeEvents().first()
            shouldThrow<IllegalArgumentException> {
                repository.upsertEvent(EventFixtures.allDay(id = 999L))
            }
            repository.observeEvents().first() shouldBe before
        }

    @Test
    public fun `upserting an event with an unknown calendar throws`(): Unit =
        runTest {
            shouldThrow<IllegalArgumentException> {
                repository.upsertEvent(EventFixtures.allDay(calendarId = 999L))
            }
        }

    @Test
    public fun `two events cannot share a uid`(): Unit =
        runTest {
            val a = store(EventFixtures.allDay(title = "A"))
            shouldThrow<IllegalArgumentException> {
                repository.upsertEvent(EventFixtures.allDay(title = "B").copy(uid = a.uid))
            }
        }

    @Test
    public fun `a failed write changes nothing`(): Unit =
        runTest {
            val kept = store(EventFixtures.allDay(title = "Keep me"))
            val before = repository.observeEvents().first()
            shouldThrow<IllegalArgumentException> {
                // Reuses an existing uid; must roll back whole, including exdates and reminders.
                store(EventFixtures.timedZoned(id = Event.NEW_ID).copy(uid = kept.uid, reminders = setOf(Reminder(5))))
            }
            repository.observeEvents().first() shouldBe before
        }

    @Test
    public fun `deleting an event removes it`(): Unit =
        runTest {
            val stored = store(EventFixtures.allDay())
            repository.deleteEvent(stored.id) shouldBe true
            repository.getEvent(stored.id) shouldBe null
        }

    @Test
    public fun `deleting an unknown event returns false`(): Unit =
        runTest {
            repository.deleteEvent(999L) shouldBe false
        }

    // ---- fixtures round trip (CLAUDE.md rule 6: Year Day and Leap Day everywhere) ----

    @Test
    public fun `every EventFixtures fixture round-trips losslessly`(): Unit =
        runTest {
            EventFixtures.all().forEach { fixture ->
                // Compares the reload against the ORIGINAL fixture, not against store()'s own read-back
                // (which already went through the same mapper and so cannot expose a read-side bug).
                val original = fixture.copy(id = Event.NEW_ID)
                val stored = store(original)
                val reloaded = requireNotNull(repository.getEvent(stored.id))
                reloaded shouldBe
                    original.copy(id = stored.id, createdAt = stored.createdAt, updatedAt = stored.updatedAt)
            }
        }

    @Test
    public fun `Year Day yearly round-trips its IFC rule`(): Unit =
        runTest {
            val stored = store(EventFixtures.yearDayYearly())
            requireNotNull(repository.getEvent(stored.id)).recurrence shouldBe
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay)
        }

    @Test
    public fun `each Leap Day policy round-trips its IFC rule`(): Unit =
        runTest {
            LeapDayPolicy.entries.forEach { policy ->
                val stored = store(EventFixtures.leapDayYearly(policy = policy))
                requireNotNull(repository.getEvent(stored.id)).recurrence shouldBe
                    IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy))
            }
        }

    @Test
    public fun `Sol 13 yearly round-trips its IFC rule`(): Unit =
        runTest {
            val stored = store(EventFixtures.sol13Yearly())
            requireNotNull(repository.getEvent(stored.id)).recurrence shouldBe
                IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13)
        }

    @Test
    public fun `a zoned timed event and its reminder round-trip`(): Unit =
        runTest {
            val original = EventFixtures.timedZoned()
            val stored = store(original)
            val reloaded = requireNotNull(repository.getEvent(stored.id))
            reloaded.timing shouldBe original.timing
            reloaded.reminders shouldBe original.reminders
        }

    @Test
    public fun `a floating multi-day all-day event round-trips`(): Unit =
        runTest {
            val original = EventFixtures.floatingMultiDay()
            val stored = store(original)
            val reloaded = requireNotNull(repository.getEvent(stored.id))
            reloaded.timing shouldBe original.timing
            reloaded.endDate shouldBe original.endDate
        }

    // ---- exdates ----

    @Test
    public fun `adding an exdate to a non-recurring event throws`(): Unit =
        runTest {
            val stored = store(EventFixtures.allDay())
            shouldThrow<IllegalArgumentException> { repository.addExdate(stored.id, stored.startDate) }
        }

    @Test
    public fun `adding an exdate before the start throws`(): Unit =
        runTest {
            val stored = store(EventFixtures.sol13Yearly())
            shouldThrow<IllegalArgumentException> {
                repository.addExdate(stored.id, stored.startDate.minusDays(1))
            }
        }

    @Test
    public fun `an exdate can be added and removed`(): Unit =
        runTest {
            val stored = store(EventFixtures.sol13Yearly())
            val nextYear = stored.startDate.plusYears(1)
            repository.addExdate(stored.id, nextYear) shouldBe true
            requireNotNull(repository.getEvent(stored.id)).exdates shouldBe setOf(nextYear)
            repository.addExdate(stored.id, nextYear) shouldBe false
            repository.removeExdate(stored.id, nextYear) shouldBe true
            requireNotNull(repository.getEvent(stored.id)).exdates shouldBe emptySet()
            repository.removeExdate(stored.id, nextYear) shouldBe false
        }

    @Test
    public fun `adding an exdate bumps updatedAt`(): Unit =
        runTest {
            clock.set(FIXED_INSTANT)
            val stored = store(EventFixtures.sol13Yearly())
            clock.advanceBy(Duration.ofMinutes(5))
            repository.addExdate(stored.id, stored.startDate.plusYears(1))
            requireNotNull(repository.getEvent(stored.id)).updatedAt shouldBe FIXED_INSTANT.plus(Duration.ofMinutes(5))
        }

    @Test
    public fun `addExdate and removeExdate on an unknown event return false`(): Unit =
        runTest {
            repository.addExdate(999L, LocalDate.of(2026, 1, 1)) shouldBe false
            repository.removeExdate(999L, LocalDate.of(2026, 1, 1)) shouldBe false
        }

    // ---- range queries: the zone-skew padding boundary ----

    @Test
    public fun `observeAgendaCandidates is empty for an empty range`(): Unit =
        runTest {
            store(EventFixtures.allDay())
            val empty = LocalDate.of(2026, 6, 1)..LocalDate.of(2026, 5, 1)
            repository.observeAgendaCandidates(empty).first() shouldBe emptyList()
        }

    @Test
    public fun `observeAgendaCandidates includes an event exactly at the zone-skew padding`(): Unit =
        runTest {
            val date = LocalDate.of(2026, 6, 30)
            val stored = store(EventFixtures.allDay(date = date))
            val range = date.plusDays(EventRepository.ZONE_SKEW_DAYS)..date.plusDays(EventRepository.ZONE_SKEW_DAYS)
            repository.observeAgendaCandidates(range).first().map { it.id } shouldContainExactly listOf(stored.id)
        }

    @Test
    public fun `observeAgendaCandidates excludes an event just past the zone-skew padding`(): Unit =
        runTest {
            val date = LocalDate.of(2026, 6, 30)
            store(EventFixtures.allDay(date = date))
            val beyond = EventRepository.ZONE_SKEW_DAYS + 1
            val range = date.plusDays(beyond)..date.plusDays(beyond)
            repository.observeAgendaCandidates(range).first() shouldBe emptyList()
        }

    @Test
    public fun `observeAgendaCandidates excludes hidden calendars`(): Unit =
        runTest {
            val calendarId = repository.upsertCalendar(EventCalendar(name = "Hidden", visible = false))
            val date = LocalDate.of(2026, 6, 30)
            store(EventFixtures.allDay(date = date, calendarId = calendarId))
            repository.observeAgendaCandidates(date..date).first() shouldBe emptyList()
        }

    @Test
    public fun `getReminderCandidates only returns events with a reminder, from visible calendars`(): Unit =
        runTest {
            store(EventFixtures.allDay())
            val hiddenCalendarId = repository.upsertCalendar(EventCalendar(name = "Hidden", visible = false))
            // Distinct fixture ids only to give the two `timedZoned` events distinct uids; both are
            // still stored as new (Event.NEW_ID) rows.
            store(EventFixtures.timedZoned(id = 2L).copy(id = Event.NEW_ID, calendarId = hiddenCalendarId))
            val withReminder = store(EventFixtures.timedZoned(id = 3L).copy(id = Event.NEW_ID))
            repository.getReminderCandidates(LocalDate.of(2020, 1, 1)).map { it.id } shouldContainExactly
                listOf(withReminder.id)
        }

    // ---- Flow re-emission ----

    @Test
    public fun `observeEvents re-emits after a write`(): Unit =
        runTest {
            repository.observeEvents().test {
                awaitItem() shouldBe emptyList()
                store(EventFixtures.allDay())
                awaitItem().size shouldBe 1
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    public fun `observeCalendars re-emits after a write`(): Unit =
        runTest {
            repository.observeCalendars().test {
                awaitItem().size shouldBe 1
                repository.upsertCalendar(EventCalendar(name = "New"))
                awaitItem().size shouldBe 2
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    public fun `observeEvent re-emits when its own exdates change`(): Unit =
        runTest {
            val stored = store(EventFixtures.sol13Yearly())
            repository.observeEvent(stored.id).test {
                awaitItem()?.exdates shouldBe emptySet()
                repository.addExdate(stored.id, stored.startDate.plusYears(1))
                awaitItem()?.exdates shouldBe setOf(stored.startDate.plusYears(1))
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---- deleteAllData ----

    @Test
    public fun `deleteAllData removes every event and calendar, and re-seeds the built-in one`(): Unit =
        runTest {
            store(EventFixtures.allDay())
            repository.upsertCalendar(EventCalendar(name = "Extra"))
            repository.deleteAllData()
            repository.observeEvents().first() shouldBe emptyList()
            repository.observeCalendars().first() shouldBe listOf(EventCalendar.DEFAULT)
        }

    internal companion object {
        /** A stable instant tests set the clock to before asserting timestamps. */
        val FIXED_INSTANT: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
