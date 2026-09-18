package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.event.CalendarSource
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.Reminder
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// FakeEventRepository against the EventRepository contract (KDoc of the interface; docs/contracts/Events.md),
// so that UI tests written against it hold against the Room implementation too.
@OptIn(ExperimentalCoroutinesApi::class)
class FakeEventRepositoryTest {
    private val start = Instant.parse("2026-09-18T10:00:00Z")

    // --- calendars --------------------------------------------------------------------------------------

    @Test
    fun `a fresh repository holds exactly the built-in calendar and no events`() =
        runTest {
            val repo = FakeEventRepository()
            repo.observeCalendars().first() shouldContainExactly listOf(EventCalendar.DEFAULT)
            repo.getCalendar(EventCalendar.DEFAULT_ID) shouldBe EventCalendar.DEFAULT
            repo.observeEvents().first().shouldBeEmpty()
            repo.getEvent(1).shouldBeNull()
        }

    @Test
    fun `calendars get ids from 2 and can be updated but only if they exist`() =
        runTest {
            val repo = FakeEventRepository()
            val work = repo.upsertCalendar(EventCalendar(name = "Work", colorArgb = 0xFF0000FF.toInt()))
            val imported = repo.upsertCalendar(EventCalendar(name = "school.ics", source = CalendarSource.ICS))
            work shouldBe 2L
            imported shouldBe 3L

            repo.upsertCalendar(repo.getCalendar(work)!!.copy(visible = false)) shouldBe work
            repo.getCalendar(work)!!.visible shouldBe false
            repo.currentCalendars.map { it.id } shouldContainExactly listOf(1L, 2L, 3L)

            shouldThrow<IllegalArgumentException> { repo.upsertCalendar(EventCalendar(id = 99, name = "Ghost")) }
        }

    @Test
    fun `the built-in calendar can be renamed and hidden but not deleted or turned into an ICS calendar`() =
        runTest {
            val repo = FakeEventRepository()
            repo.upsertCalendar(EventCalendar.DEFAULT.copy(name = "Mine", visible = false)) shouldBe
                EventCalendar.DEFAULT_ID
            repo.getCalendar(EventCalendar.DEFAULT_ID)!!.name shouldBe "Mine"

            shouldThrow<IllegalArgumentException> { repo.deleteCalendar(EventCalendar.DEFAULT_ID) }
            shouldThrow<IllegalArgumentException> {
                repo.upsertCalendar(EventCalendar.DEFAULT.copy(source = CalendarSource.ICS))
            }
            repo.getCalendar(EventCalendar.DEFAULT_ID)!!.source shouldBe CalendarSource.LOCAL
        }

    @Test
    fun `deleting a calendar deletes its events and nothing else`() =
        runTest {
            val repo = FakeEventRepository()
            val work = repo.upsertCalendar(EventCalendar(name = "Work"))
            val kept = repo.upsertEvent(EventFixtures.sol13Yearly())
            val gone = repo.upsertEvent(EventFixtures.allDay(title = "Offsite", calendarId = work))

            repo.deleteCalendar(work) shouldBe true
            repo.deleteCalendar(work) shouldBe false
            repo.getEvent(gone).shouldBeNull()
            repo.getEvent(kept).shouldNotBeNull()
            repo.getCalendar(work).shouldBeNull()
        }

    // --- event writes -----------------------------------------------------------------------------------

    @Test
    fun `inserts assign ids 1, 2, 3 and never reuse one`() =
        runTest {
            val repo = FakeEventRepository()
            val first = repo.upsertEvent(EventFixtures.yearDayYearly())
            val second = repo.upsertEvent(EventFixtures.sol13Yearly())
            repo.deleteEvent(second) shouldBe true
            val third = repo.upsertEvent(EventFixtures.timedZoned())
            listOf(first, second, third) shouldContainExactly listOf(1L, 2L, 3L)

            repo.deleteAllData()
            repo.upsertEvent(EventFixtures.yearDayYearly()) shouldBe 4L
        }

    @Test
    fun `the repository owns the timestamps`() =
        runTest {
            val clock = MutableClock(start)
            val repo = FakeEventRepository(clock)
            val claimed = Instant.parse("1999-01-01T00:00:00Z")
            val id = repo.upsertEvent(EventFixtures.sol13Yearly().copy(createdAt = claimed, updatedAt = claimed))
            val inserted = repo.getEvent(id)!!
            inserted.createdAt shouldBe start
            inserted.updatedAt shouldBe start

            clock.advanceBy(Duration.ofHours(2))
            repo.upsertEvent(inserted.copy(title = "Renamed", createdAt = claimed, updatedAt = claimed))
            val updated = repo.getEvent(id)!!
            assertSoftly {
                updated.title shouldBe "Renamed"
                updated.createdAt shouldBe start
                updated.updatedAt shouldBe start.plus(Duration.ofHours(2))
            }

            // A clock moved back before createdAt never produces updatedAt < createdAt.
            clock.set(start.minusSeconds(60))
            repo.upsertEvent(updated.copy(title = "Again"))
            repo.getEvent(id)!!.updatedAt shouldBe start
        }

    @Test
    fun `an update replaces the whole aggregate, exdates and reminders included`() =
        runTest {
            val repo = FakeEventRepository()
            val id =
                repo.upsertEvent(
                    EventFixtures.sol13Yearly().copy(
                        exdates = setOf(LocalDate.of(2027, 6, 30)),
                        reminders = setOf(Reminder(10), Reminder(1440)),
                    ),
                )
            repo.upsertEvent(repo.getEvent(id)!!.copy(exdates = emptySet(), reminders = setOf(Reminder(0))))
            val stored = repo.getEvent(id)!!
            stored.exdates.shouldBeEmpty()
            stored.reminders shouldContainExactly setOf(Reminder(0))
        }

    @Test
    fun `upsert rejects an unknown id, an unknown calendar and a duplicate uid, and changes nothing`() =
        runTest {
            val repo = FakeEventRepository()
            val id = repo.upsertEvent(EventFixtures.sol13Yearly())
            val stored = repo.getEvent(id)!!

            shouldThrow<IllegalArgumentException> { repo.upsertEvent(EventFixtures.yearDayYearly(id = 42)) }
            shouldThrow<IllegalArgumentException> {
                repo.upsertEvent(
                    EventFixtures.yearDayYearly().copy(calendarId = 77),
                )
            }
            shouldThrow<IllegalArgumentException> {
                repo.upsertEvent(
                    EventFixtures.yearDayYearly().copy(uid = stored.uid),
                )
            }
            // Re-saving an event under its own uid is fine.
            repo.upsertEvent(stored) shouldBe id

            repo.currentEvents.map { it.id } shouldContainExactly listOf(id)
            // A rejected insert does not burn an id.
            repo.upsertEvent(EventFixtures.yearDayYearly()) shouldBe 2L
        }

    @Test
    fun `deleting an event returns whether it existed`() =
        runTest {
            val repo = FakeEventRepository()
            val id = repo.upsertEvent(EventFixtures.timedZoned())
            repo.deleteEvent(id) shouldBe true
            repo.deleteEvent(id) shouldBe false
            repo.getEvent(id).shouldBeNull()
        }

    // --- exdates ----------------------------------------------------------------------------------------

    @Test
    fun `addExdate deletes one occurrence and removeExdate undoes it`() =
        runTest {
            val clock = MutableClock(start)
            val repo = FakeEventRepository(clock)
            val id = repo.upsertEvent(EventFixtures.yearDayYearly())
            val yearDay2027 = LocalDate.of(2027, 12, 31)

            clock.advanceBy(Duration.ofMinutes(5))
            repo.addExdate(id, yearDay2027) shouldBe true
            repo.addExdate(id, yearDay2027) shouldBe false
            val excluded = repo.getEvent(id)!!
            excluded.exdates shouldContainExactly setOf(yearDay2027)
            excluded.updatedAt shouldBe start.plus(Duration.ofMinutes(5))

            repo.removeExdate(id, yearDay2027) shouldBe true
            repo.removeExdate(id, yearDay2027) shouldBe false
            repo.getEvent(id)!!.exdates.shouldBeEmpty()
        }

    @Test
    fun `exdates on a missing event are false and on a one-off event or before the start are errors`() =
        runTest {
            val repo = FakeEventRepository()
            val oneOff = repo.upsertEvent(EventFixtures.floatingMultiDay())
            val yearly = repo.upsertEvent(EventFixtures.yearDayYearly())

            repo.addExdate(99, LocalDate.of(2027, 12, 31)) shouldBe false
            repo.removeExdate(99, LocalDate.of(2027, 12, 31)) shouldBe false
            shouldThrow<IllegalArgumentException> { repo.addExdate(oneOff, LocalDate.of(2026, 12, 30)) }
            shouldThrow<IllegalArgumentException> { repo.addExdate(yearly, LocalDate.of(2025, 12, 31)) }
            repo.getEvent(yearly)!!.exdates.shouldBeEmpty()
            // The first occurrence itself can be excluded.
            repo.addExdate(yearly, EventFixtures.YEAR_DAY_2026) shouldBe true
        }

    // --- flows ------------------------------------------------------------------------------------------

    @Test
    fun `observeEvents emits the list in LIST_ORDER after every write`() =
        runTest {
            val repo = FakeEventRepository()
            val seen = mutableListOf<List<Long>>()
            val job = launch { repo.observeEvents().collect { events -> seen += events.map { it.id } } }
            runCurrent()
            repo.upsertEvent(EventFixtures.yearDayYearly())
            runCurrent()
            repo.upsertEvent(EventFixtures.sol13Yearly())
            runCurrent()
            repo.deleteEvent(1)
            runCurrent()
            job.cancel()
            // Sol 13 (June 30) sorts before Year Day (December 31), whatever the insertion order.
            seen shouldContainExactly listOf(emptyList(), listOf(1L), listOf(2L, 1L), listOf(2L))
        }

    @Test
    fun `observeEvent follows one event from absent to present to changed to deleted`() =
        runTest {
            val repo = FakeEventRepository()
            val seen = mutableListOf<String?>()
            val job = launch { repo.observeEvent(1).collect { seen += it?.title } }
            runCurrent()
            repo.upsertEvent(EventFixtures.sol13Yearly())
            runCurrent()
            repo.upsertEvent(repo.getEvent(1)!!.copy(title = "Renamed"))
            runCurrent()
            // A write to another event does not re-emit this one.
            repo.upsertEvent(EventFixtures.yearDayYearly())
            runCurrent()
            repo.deleteEvent(1)
            runCurrent()
            job.cancel()
            seen shouldContainExactly listOf(null, "Sol 13 picnic", "Renamed", null)
        }

    @Test
    fun `seed stores the fixtures as ids 1 to 9 and observeEvents shows hidden calendars too`() =
        runTest {
            val repo = FakeEventRepository()
            val stored = repo.seed(EventFixtures.all())
            stored.map { it.id } shouldContainExactly (1L..9L).toList()
            repo.upsertCalendar(EventCalendar.DEFAULT.copy(visible = false))
            repo.observeEvents().first().size shouldBe 9
        }

    // --- range queries ----------------------------------------------------------------------------------

    @Test
    fun `agenda candidates cover the range widened by the zone skew, for one-off events exactly`() =
        runTest {
            val repo = FakeEventRepository()
            // December 30 – January 1.
            val trip = repo.upsertEvent(EventFixtures.floatingMultiDay())

            suspend fun candidates(
                from: LocalDate,
                to: LocalDate,
            ) = repo.observeAgendaCandidates(from..to).first().map { it.id }

            assertSoftly {
                candidates(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)) shouldContainExactly listOf(trip)
                // Two days of skew on both sides: ends January 1, so still a candidate for January 3 …
                candidates(LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 31)) shouldContainExactly listOf(trip)
                // … but not for January 4; starts December 30, so a candidate up to December 28 …
                candidates(LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 31)).shouldBeEmpty()
                candidates(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 28)) shouldContainExactly listOf(trip)
                // … but not December 27.
                candidates(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 27)).shouldBeEmpty()
                // An empty range has no candidates.
                candidates(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 30)).shouldBeEmpty()
            }
        }

    @Test
    fun `recurring events are candidates from their start on and never before it`() =
        runTest {
            val repo = FakeEventRepository()
            val yearDay = repo.upsertEvent(EventFixtures.yearDayYearly())
            assertSoftly {
                repo.observeAgendaCandidates(LocalDate.of(2030, 12, 1)..LocalDate.of(2030, 12, 31)).first().map {
                    it.id
                } shouldContainExactly
                    listOf(yearDay)
                repo
                    .observeAgendaCandidates(
                        LocalDate.of(2026, 12, 1)..LocalDate.of(2026, 12, 28),
                    ).first()
                    .shouldBeEmpty()
                repo.observeAgendaCandidates(LocalDate.of(2026, 12, 1)..LocalDate.of(2026, 12, 29)).first().map {
                    it.id
                } shouldContainExactly
                    listOf(yearDay)
            }
        }

    @Test
    fun `hidden calendars drop out of the agenda and reminder candidates and come back when shown`() =
        runTest {
            val repo = FakeEventRepository()
            val work = repo.upsertCalendar(EventCalendar(name = "Work"))
            val call = repo.upsertEvent(EventFixtures.timedZoned().copy(calendarId = work))
            val march = LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 28)

            val seen = mutableListOf<List<Long>>()
            val job = launch { repo.observeAgendaCandidates(march).collect { events -> seen += events.map { it.id } } }
            runCurrent()
            repo.upsertCalendar(repo.getCalendar(work)!!.copy(visible = false))
            runCurrent()
            repo.getReminderCandidates(LocalDate.of(2026, 3, 1)).shouldBeEmpty()
            repo.upsertCalendar(repo.getCalendar(work)!!.copy(visible = true))
            runCurrent()
            job.cancel()

            seen shouldContainExactly listOf(listOf(call), emptyList(), listOf(call))
            repo.getReminderCandidates(LocalDate.of(2026, 3, 1)).map { it.id } shouldContainExactly listOf(call)
        }

    @Test
    fun `reminder candidates have a reminder and may still happen`() =
        runTest {
            val repo = FakeEventRepository()
            // March 8, 2026, with a reminder.
            val call = repo.upsertEvent(EventFixtures.timedZoned())
            val yearly = repo.upsertEvent(EventFixtures.yearDayYearly().copy(reminders = setOf(Reminder(1440))))
            repo.upsertEvent(EventFixtures.sol13Yearly())

            assertSoftly {
                repo.getReminderCandidates(LocalDate.of(2026, 3, 8)).map { it.id } shouldContainExactly
                    listOf(call, yearly)
                // Still a candidate two days later (zone skew), gone after that; the yearly one never ends.
                repo.getReminderCandidates(LocalDate.of(2026, 3, 10)).map { it.id } shouldContainExactly
                    listOf(call, yearly)
                repo.getReminderCandidates(LocalDate.of(2026, 3, 11)).map { it.id } shouldContainExactly listOf(yearly)
            }
        }

    // --- delete all and concurrency ---------------------------------------------------------------------

    @Test
    fun `deleteAllData removes everything and restores the built-in calendar`() =
        runTest {
            val repo = FakeEventRepository()
            repo.upsertCalendar(EventCalendar.DEFAULT.copy(name = "Mine", visible = false))
            repo.upsertCalendar(EventCalendar(name = "Work"))
            repo.seed(EventFixtures.all())

            repo.deleteAllData()

            repo.observeEvents().first().shouldBeEmpty()
            repo.observeCalendars().first() shouldContainExactly listOf(EventCalendar.DEFAULT)
            repo.getReminderCandidates(LocalDate.of(2020, 1, 1)).shouldBeEmpty()
        }

    @Test
    fun `concurrent inserts all land with distinct ids`() =
        runTest {
            val repo = FakeEventRepository()
            val ids =
                (1..50)
                    .map { n -> async { repo.upsertEvent(EventFixtures.allDay(title = "Event $n")) } }
                    .awaitAll()
            ids.toSet().size shouldBe 50
            repo.observeEvents().first().size shouldBe 50
        }

    @Test
    fun `a flow collected late starts from the current state`() =
        runTest {
            val repo = FakeEventRepository()
            repo.seed(listOf(EventFixtures.sol13Yearly(), EventFixtures.yearDayYearly()))
            val emissions = mutableListOf<List<Event>>()
            val job = launch { repo.observeEvents().toList(emissions) }
            runCurrent()
            job.cancel()
            emissions.single().map { it.id } shouldContainExactly listOf(1L, 2L)
        }
}
