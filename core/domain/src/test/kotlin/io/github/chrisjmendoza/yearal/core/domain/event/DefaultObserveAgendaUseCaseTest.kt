package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySet
import io.github.chrisjmendoza.yearal.core.domain.holiday.ifcSet
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository
import io.github.chrisjmendoza.yearal.core.testing.FakeHolidaySetProvider
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [DefaultObserveAgendaUseCase] against [FakeEventRepository], the real [DefaultRecurrenceExpander]
 * and [FakeHolidaySetProvider]: the pipeline of `docs/ARCHITECTURE.md` §3.4 (candidates, expansion,
 * colour resolution, holidays, bucketing) and the re-emission and empty-range guarantees of
 * `docs/contracts/Events.md` §5. Expected dates are hand-computed from the IFC recurrence semantics
 * documented on [IfcRecurrence] and worked in `docs/adr/0005-events-contract.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultObserveAgendaUseCaseTest {
    private val utc = ZoneId.of("UTC")
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun useCase(
        repository: FakeEventRepository,
        holidaySets: List<HolidaySet> = emptyList(),
        zoneProvider: FakeZoneProvider = FakeZoneProvider(utc),
    ) = DefaultObserveAgendaUseCase(
        eventRepository = repository,
        recurrenceExpander = DefaultRecurrenceExpander(),
        holidayEngine = HolidayEngine(),
        holidaySetProvider = FakeHolidaySetProvider(holidaySets),
        zoneProvider = zoneProvider,
    )

    // --- Year Day and Leap Day (CLAUDE.md rule 6) ---------------------------------------------------

    @Test
    fun `an every-Year-Day event appears on Year Day and nowhere else in the range`() =
        runTest {
            val repository = FakeEventRepository()
            repository.seed(listOf(EventFixtures.yearDayYearly()))
            val agendas =
                useCase(repository)
                    .invoke(LocalDate.of(2026, 12, 24)..LocalDate.of(2027, 1, 2))
                    .first()

            assertSoftly {
                agendas.keys shouldContainExactly setOf(EventFixtures.YEAR_DAY_2026)
                agendas
                    .getValue(EventFixtures.YEAR_DAY_2026)
                    .entries
                    .single()
                    .event.title shouldBe "Year Day party"
            }
        }

    @Test
    fun `each Leap Day common-year policy yields its own documented date in 2025`() =
        runTest {
            val repository = FakeEventRepository()
            // 2025 is a common year: JUNE_28 falls on Gregorian June 17, SOL_1 on June 18, SKIP yields nothing.
            val june28 = repository.seed(listOf(EventFixtures.leapDayYearly(policy = LeapDayPolicy.JUNE_28)))[0]
            val skip = repository.seed(listOf(EventFixtures.leapDayYearly(policy = LeapDayPolicy.SKIP)))[0]
            val sol1 = repository.seed(listOf(EventFixtures.leapDayYearly(policy = LeapDayPolicy.SOL_1)))[0]
            val agendas =
                useCase(repository)
                    .invoke(LocalDate.of(2025, 6, 1)..LocalDate.of(2025, 6, 30))
                    .first()

            assertSoftly {
                agendas.getValue(LocalDate.of(2025, 6, 17)).entries.map { it.event.id } shouldContainExactly
                    listOf(june28.id)
                agendas.getValue(LocalDate.of(2025, 6, 18)).entries.map { it.event.id } shouldContainExactly
                    listOf(sol1.id)
                agendas.values.flatMap { it.entries }.map { it.event.id } shouldNotContain skip.id
            }
        }

    @Test
    fun `Sol 13 lands on Gregorian June 30 in both a common and a leap year`() =
        runTest {
            val repository = FakeEventRepository()
            repository.seed(listOf(EventFixtures.sol13Yearly()))
            val agendas =
                useCase(repository)
                    .invoke(LocalDate.of(2026, 1, 1)..LocalDate.of(2028, 12, 31))
                    .first()

            agendas.values.flatMap { it.entries }.map { it.firstDate } shouldContainExactly
                listOf(LocalDate.of(2026, 6, 30), LocalDate.of(2027, 6, 30), LocalDate.of(2028, 6, 30))
        }

    // --- Time zones and multi-day occurrences (docs/adr/0005-events-contract.md decision 4) ----------

    @Test
    fun `a zoned occurrence is bucketed on its device-zone date, which can differ from its own date`() =
        runTest {
            val repository = FakeEventRepository()
            // 22:00-23:00 America/New_York on Jan 5, 2026 (EST, UTC-5) is 12:00-13:00 the next day in Tokyo.
            val event =
                Event(
                    uid = "zoned",
                    title = "Late call",
                    timing = EventTiming.Timed(LocalDate.of(2026, 1, 5), 22 * 60, 60, newYork),
                )
            repository.seed(listOf(event))

            val ownZoneAgendas =
                useCase(repository, zoneProvider = FakeZoneProvider(newYork))
                    .invoke(LocalDate.of(2026, 1, 5)..LocalDate.of(2026, 1, 6))
                    .first()
            val tokyoAgendas =
                useCase(repository, zoneProvider = FakeZoneProvider(tokyo))
                    .invoke(LocalDate.of(2026, 1, 5)..LocalDate.of(2026, 1, 6))
                    .first()

            assertSoftly {
                ownZoneAgendas.keys shouldContainExactly setOf(LocalDate.of(2026, 1, 5))
                tokyoAgendas.keys shouldContainExactly setOf(LocalDate.of(2026, 1, 6))
            }
        }

    @Test
    fun `a multi-day occurrence is clipped to the requested range but still found from either edge`() =
        runTest {
            val repository = FakeEventRepository()
            repository.seed(listOf(EventFixtures.floatingMultiDay()))
            val useCase = useCase(repository)

            val onlyYearDay = useCase.invoke(EventFixtures.YEAR_DAY_2026..EventFixtures.YEAR_DAY_2026).first()
            val pastTheEnd = useCase.invoke(LocalDate.of(2027, 1, 2)..LocalDate.of(2027, 1, 5)).first()

            assertSoftly {
                onlyYearDay.keys shouldContainExactly setOf(EventFixtures.YEAR_DAY_2026)
                onlyYearDay.getValue(EventFixtures.YEAR_DAY_2026).entries shouldHaveSize 1
                pastTheEnd.shouldBeEmpty()
            }
        }

    // --- Calendars, exdates, holidays ------------------------------------------------------------

    @Test
    fun `events of a hidden calendar never appear`() =
        runTest {
            val repository = FakeEventRepository()
            val hiddenCalendarId = repository.upsertCalendar(EventCalendar(name = "Hidden", visible = false))
            repository.seed(listOf(EventFixtures.allDay(calendarId = hiddenCalendarId)))

            val agendas = useCase(repository).invoke(EventFixtures.SOL_13_2026..EventFixtures.SOL_13_2026).first()

            agendas.shouldBeEmpty()
        }

    @Test
    fun `an exdate removes only that occurrence, other years remain`() =
        runTest {
            val repository = FakeEventRepository()
            val stored = repository.seed(listOf(EventFixtures.sol13Yearly()))[0]
            repository.addExdate(stored.id, EventFixtures.SOL_13_2026)

            val agendas =
                useCase(repository)
                    .invoke(LocalDate.of(2026, 1, 1)..LocalDate.of(2027, 12, 31))
                    .first()

            assertSoftly {
                agendas shouldNotContainKey EventFixtures.SOL_13_2026
                agendas shouldContainKey LocalDate.of(2027, 6, 30)
            }
        }

    @Test
    fun `a holiday and an event on the same date both appear, holidays separate from entries`() =
        runTest {
            val repository = FakeEventRepository()
            repository.seed(listOf(EventFixtures.yearDayYearly()))

            val agenda =
                useCase(repository, holidaySets = listOf(ifcSet))
                    .invoke(EventFixtures.YEAR_DAY_2026..EventFixtures.YEAR_DAY_2026)
                    .first()
                    .getValue(EventFixtures.YEAR_DAY_2026)

            assertSoftly {
                agenda.entries
                    .single()
                    .event.title shouldBe "Year Day party"
                agenda.holidays
                    .single()
                    .holiday.id shouldBe "ifc.year_day"
            }
        }

    // --- Re-emission and the zone (docs/contracts/Events.md §5) ---------------------------------

    @Test
    fun `the zone is re-read at every recomputation, not cached from construction`() =
        runTest {
            val repository = FakeEventRepository()
            // 22:00-23:00 America/New_York (EST) on Jan 5 is 12:00-13:00 in Tokyo the next day.
            val event =
                Event(
                    uid = "zoned-reemit",
                    title = "Late call",
                    timing = EventTiming.Timed(LocalDate.of(2026, 1, 5), 22 * 60, 60, newYork),
                )
            repository.seed(listOf(event))
            val zoneProvider = FakeZoneProvider(newYork)
            val useCase = useCase(repository, zoneProvider = zoneProvider)
            val range = LocalDate.of(2026, 1, 5)..LocalDate.of(2026, 1, 6)

            useCase.invoke(range).first().keys shouldContainExactly setOf(LocalDate.of(2026, 1, 5))

            // A fresh recomputation (a new subscription, exactly what a screen does on resume) must
            // read the zone again rather than reuse whatever was current when the use case was built.
            zoneProvider.set(tokyo)
            useCase.invoke(range).first().keys shouldContainExactly setOf(LocalDate.of(2026, 1, 6))
        }

    @Test
    fun `an empty range gives an empty result for both invoke and presence`() =
        runTest {
            val repository = FakeEventRepository()
            repository.seed(listOf(EventFixtures.yearDayYearly()))
            val useCase = useCase(repository, holidaySets = listOf(ifcSet))
            val emptyRange = LocalDate.of(2026, 1, 2)..LocalDate.of(2026, 1, 1)

            assertSoftly {
                useCase.invoke(emptyRange).first().shouldBeEmpty()
                useCase.presence(emptyRange).first() shouldBe emptySet()
            }
        }

    // --- presence: events only, holidays never counted (docs/contracts/Events.md §5) ----------------

    @Test
    fun `presence reports only the dates with an event occurrence, never a holiday-only date`() =
        runTest {
            val repository = FakeEventRepository()
            repository.seed(listOf(EventFixtures.sol13Yearly()))
            val range = LocalDate.of(2026, 6, 1)..LocalDate.of(2026, 12, 31)

            val presence = useCase(repository, holidaySets = listOf(ifcSet)).presence(range).first()

            // The ifc set puts Year Day on Dec 31, but presence never counts it; only Sol 13 shows.
            presence shouldContainExactly setOf(EventFixtures.SOL_13_2026)
        }
}
