package io.github.chrisjmendoza.yearal.feature.calendar.day

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.event.AgendaEntry
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.Occurrence
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.holidays.HolidayPackLoader
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.FakeDateTicker
import io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository
import io.github.chrisjmendoza.yearal.core.testing.FakeObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.testing.FakeSettingsRepository
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.AgendaItemUi
import io.github.chrisjmendoza.yearal.feature.calendar.holiday.HolidayCatalog
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * [DayViewModel] against [FakeDateTicker] and [FakeSettingsRepository] with the real [HolidayEngine]
 * and bundled packs: every date shape (spec §4.1, §7.3–7.5; CLAUDE.md rule 6), `isToday` flipping
 * across midnight (docs/WORKFLOW.md §3) and the holiday labels, observed entries included
 * (docs/holidays-and-import.md §2.2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var formatter: IfcDateFormatter
    private lateinit var catalog: HolidayCatalog

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        formatter = IfcDateFormatter(context.resources, Locale.US)
        catalog = HolidayCatalog(HolidayEngine(), HolidayPackLoader(), context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        day: LocalDate,
        ticker: FakeDateTicker = FakeDateTicker(LocalDate.of(2026, 9, 17)),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        observeAgenda: FakeObserveAgendaUseCase = FakeObserveAgendaUseCase(),
        eventRepository: FakeEventRepository = FakeEventRepository(),
    ) = viewModelForEpochDay(day.toEpochDay(), ticker, settings, observeAgenda, eventRepository)

    private fun viewModelForEpochDay(
        epochDay: Long,
        ticker: FakeDateTicker = FakeDateTicker(LocalDate.of(2026, 9, 17)),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        observeAgenda: FakeObserveAgendaUseCase = FakeObserveAgendaUseCase(),
        eventRepository: FakeEventRepository = FakeEventRepository(),
    ) = DayViewModel(epochDay, ticker, settings, catalog, formatter, observeAgenda, eventRepository)

    @Test
    fun `starts loading, then shows the regular day of the spec's worked example`() =
        runTest(dispatcher) {
            val viewModel = viewModel(LocalDate.of(2026, 9, 17))
            viewModel.uiState.value shouldBe DayUiState.Loading
            viewModel.uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                loaded.date shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
                loaded.gregorianDate shouldBe LocalDate.of(2026, 9, 17)
                loaded.ifcLong shouldBe "September 8, 2026"
                loaded.numeric shouldBe "IFC 2026-10-08"
                loaded.gregorianLong shouldBe "Thursday, September 17, 2026"
                loaded.nominalWeekday shouldBe "IFC weekday: Sunday"
                loaded.actualWeekday shouldBe "Actual weekday: Thursday"
                loaded.weekdaysDescription shouldBe "IFC Sunday, actual Thursday"
                loaded.dayAndWeek shouldBe "Day 260 · Week 38 of 52"
                loaded.quarter shouldBe "Q3"
                loaded.isToday shouldBe true
                loaded.holidays shouldBe emptyList()
            }
        }

    @Test
    fun `Leap Day 2028 has no IFC weekday and is a holiday of the ifc pack`() =
        runTest(dispatcher) {
            viewModel(LocalDate.of(2028, 6, 17)).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                loaded.date shouldBe IfcDate.LeapDay(2028)
                loaded.ifcLong shouldBe "Leap Day, 2028"
                loaded.numeric shouldBe "IFC 2028-06-29"
                loaded.gregorianLong shouldBe "Saturday, June 17, 2028"
                loaded.nominalWeekday shouldBe "no IFC weekday"
                loaded.actualWeekday shouldBe "Actual weekday: Saturday"
                loaded.weekdaysDescription shouldBe "no IFC weekday, actual Saturday"
                loaded.dayAndWeek shouldBe "Day 169 · outside the weeks"
                loaded.quarter shouldBe "Q2"
                loaded.isToday shouldBe false
                loaded.holidays shouldContainExactly listOf("Leap Day")
            }
        }

    @Test
    fun `Year Day 2026 has no IFC weekday and shares the day with the US pack`() =
        runTest(dispatcher) {
            viewModel(LocalDate.of(2026, 12, 31)).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                loaded.date shouldBe IfcDate.YearDay(2026)
                loaded.ifcLong shouldBe "Year Day, 2026"
                loaded.numeric shouldBe "IFC 2026-13-29"
                loaded.gregorianLong shouldBe "Thursday, December 31, 2026"
                loaded.nominalWeekday shouldBe "no IFC weekday"
                loaded.actualWeekday shouldBe "Actual weekday: Thursday"
                loaded.dayAndWeek shouldBe "Day 365 · outside the weeks"
                loaded.quarter shouldBe "Q4"
                loaded.isToday shouldBe false
                // Engine order: by set id (ifc before us), then holiday id (kwanzaa before new_years_eve).
                loaded.holidays shouldContainExactly listOf("Year Day", "Kwanzaa", "New Year's Eve")
            }
        }

    @Test
    fun `isToday flips across midnight in both directions`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2026, 9, 17))
            viewModel(LocalDate.of(2026, 9, 18), ticker = ticker).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().isToday shouldBe false

                ticker.set(LocalDate.of(2026, 9, 18))
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().isToday shouldBe true

                ticker.set(LocalDate.of(2026, 9, 19))
                val after = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                after.isToday shouldBe false
                // The day itself never changes with the clock.
                after.ifcLong shouldBe "September 9, 2026"
            }
        }

    @Test
    fun `Christmas Day 2026 is listed from the US pack`() =
        runTest(dispatcher) {
            viewModel(LocalDate.of(2026, 12, 25)).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                loaded.ifcLong shouldBe "December 23, 2026"
                loaded.holidays shouldContainExactly listOf("Christmas Day")
            }
        }

    @Test
    fun `an observed federal holiday is labelled as observed`() =
        runTest(dispatcher) {
            // July 4, 2026 is a Saturday, so the federal rule observes it on Friday, July 3.
            viewModel(LocalDate.of(2026, 7, 3)).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                loaded.ifcLong shouldBe "Sol 16, 2026"
                loaded.holidays shouldContainExactly listOf("Independence Day (observed)")
            }
        }

    @Test
    fun `disabling every pack empties the holidays live`() =
        runTest(dispatcher) {
            val settings = FakeSettingsRepository(UserSettings(enabledHolidaySets = setOf("us")))
            viewModel(LocalDate.of(2026, 12, 25), settings = settings).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().holidays shouldContainExactly
                    listOf("Christmas Day")

                settings.update { it.copy(enabledHolidaySets = emptySet()) }

                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().holidays shouldBe emptyList()
            }
        }

    // FEATURES C5: the day's own agenda, sourced from ObserveAgendaUseCase (docs/ARCHITECTURE.md §3.4).

    @Test
    fun `the day's agenda entries appear, all-day first then by start time`() =
        runTest(dispatcher) {
            val day = LocalDate.of(2026, 9, 17)
            val allDayEvent = EventFixtures.allDay(id = 1, date = day, title = "Conference")
            val timedEvent =
                Event(id = 2, uid = "timed", title = "Standup", timing = EventTiming.Timed(day, 9 * 60, 30))
            val agenda = FakeObserveAgendaUseCase()
            agenda.putEntry(EventFixtures.entry(allDayEvent))
            agenda.putEntry(EventFixtures.entry(timedEvent))

            viewModel(day, observeAgenda = agenda).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                loaded.agenda.map { it.eventId } shouldContainExactly listOf(1L, 2L)
                loaded.agenda[0].isAllDay shouldBe true
                loaded.agenda[1].isAllDay shouldBe false
                agenda.requestedRanges shouldContainExactly listOf(day..day)
            }
        }

    @Test
    fun `a day with no occurrences has an empty agenda`() =
        runTest(dispatcher) {
            viewModel(LocalDate.of(2026, 9, 17)).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().agenda shouldBe emptyList()
            }
        }

    // FEATURES E1: "delete this occurrence" — docs/contracts/Events.md §4, §7 "T6–T8": the exdate key
    // is the occurrence's own start date, never the day the sheet is showing.

    /** A three-day recurring all-day event, Dec 30 – Year Day – Jan 1, whose own start is Dec 30. */
    private suspend fun seedMultiDayRecurringEvent(repository: FakeEventRepository): Event {
        val draft =
            EventFixtures.allDay(
                date = LocalDate.of(2026, 12, 30),
                days = 3,
                title = "New Year trip",
                recurrence = IfcRecurrence.yearlyOn(LocalDate.of(2026, 12, 30)),
            )
        val id = repository.upsertEvent(draft)
        return checkNotNull(repository.getEvent(id))
    }

    private fun multiDayOccurrence(event: Event): Occurrence =
        Occurrence(
            eventId = event.id,
            startLocal = LocalDateTime.of(2026, 12, 30, 0, 0),
            endLocal = LocalDateTime.of(2027, 1, 2, 0, 0),
            zone = null,
            allDay = true,
        )

    @Test
    fun `deleting a shown occurrence exdates its own start date, not the day being viewed`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            val event = seedMultiDayRecurringEvent(repository)
            val occurrence = multiDayOccurrence(event)
            val shownDay = LocalDate.of(2026, 12, 31) // Year Day: the middle of the span, not its own start.
            val agenda = FakeObserveAgendaUseCase()
            agenda.putEntry(AgendaEntry(event, occurrence, EventCalendar.DEFAULT_COLOR_ARGB, ZoneId.of("UTC")))

            val viewModel = viewModel(shownDay, observeAgenda = agenda, eventRepository = repository)
            viewModel.uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                val item = loaded.agenda.single()
                item.isRecurring shouldBe true
                item.occurrenceDate shouldBe LocalDate.of(2026, 12, 30)

                viewModel.requestDelete(item)
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().pendingDelete shouldBe item

                viewModel.confirmDelete()
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().pendingDelete.shouldBeNull()
            }
            repository.getEvent(event.id)?.exdates shouldBe setOf(LocalDate.of(2026, 12, 30))
        }

    @Test
    fun `confirming a recurring delete offers undo, and undo clears the exdate`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            val event = seedMultiDayRecurringEvent(repository)
            val occurrence = multiDayOccurrence(event)
            val item =
                AgendaItemUi(
                    eventId = event.id,
                    title = event.title,
                    isAllDay = true,
                    startTime = null,
                    endTime = null,
                    colorArgb = EventCalendar.DEFAULT_COLOR_ARGB,
                    isRecurring = true,
                    occurrenceDate = occurrence.occurrenceDate,
                )
            val viewModel =
                viewModel(
                    LocalDate.of(2026, 12, 30),
                    observeAgenda = FakeObserveAgendaUseCase(),
                    eventRepository = repository,
                )
            viewModel.events.test {
                viewModel.requestDelete(item)
                viewModel.confirmDelete()

                val deleted = awaitItem().shouldBeInstanceOf<DayEvent.OccurrenceDeleted>()
                deleted.eventId shouldBe event.id
                deleted.occurrenceDate shouldBe LocalDate.of(2026, 12, 30)

                repository.getEvent(event.id)?.exdates shouldBe setOf(LocalDate.of(2026, 12, 30))

                viewModel.undoDeleteOccurrence(deleted.eventId, deleted.occurrenceDate)
                runCurrent()
                repository
                    .getEvent(event.id)
                    ?.exdates
                    .orEmpty()
                    .shouldBeEmpty()
            }
        }

    @Test
    fun `cancelling a delete confirmation changes nothing`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            val event = seedMultiDayRecurringEvent(repository)
            val occurrence = multiDayOccurrence(event)
            val agenda = FakeObserveAgendaUseCase()
            agenda.putEntry(AgendaEntry(event, occurrence, EventCalendar.DEFAULT_COLOR_ARGB, ZoneId.of("UTC")))

            val viewModel = viewModel(LocalDate.of(2026, 12, 30), observeAgenda = agenda, eventRepository = repository)
            viewModel.uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()

                viewModel.requestDelete(loaded.agenda.single())
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().pendingDelete.shouldNotBeNull()

                viewModel.cancelDelete()
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().pendingDelete.shouldBeNull()
            }
            repository
                .getEvent(event.id)
                ?.exdates
                .orEmpty()
                .shouldBeEmpty()
        }

    @Test
    fun `a non-recurring row deletes the whole event, not an exdate`() =
        runTest(dispatcher) {
            val repository = FakeEventRepository()
            val day = LocalDate.of(2026, 9, 17)
            val storedId = repository.upsertEvent(EventFixtures.allDay(date = day, title = "Once"))
            val event = checkNotNull(repository.getEvent(storedId))
            val agenda = FakeObserveAgendaUseCase()
            agenda.putEntry(EventFixtures.entry(event))

            val viewModel = viewModel(day, observeAgenda = agenda, eventRepository = repository)
            viewModel.uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<DayUiState.Loaded>()
                val item = loaded.agenda.single()
                item.isRecurring shouldBe false

                viewModel.requestDelete(item)
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().pendingDelete.shouldNotBeNull()
                viewModel.confirmDelete()
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().pendingDelete.shouldBeNull()
            }
            repository.getEvent(event.id).shouldBeNull()
        }

    // A DayKey can be synthesized from a widget or notification intent (docs/security-and-privacy.md
    // §6.3): an out-of-range epoch day must fail soft to DayUiState.Unavailable, never crash.

    @Test
    fun `Long MIN_VALUE and MAX_VALUE epoch days are unavailable, not a crash`() =
        runTest(dispatcher) {
            viewModelForEpochDay(Long.MIN_VALUE).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                awaitItem() shouldBe DayUiState.Unavailable
            }
            viewModelForEpochDay(Long.MAX_VALUE).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                awaitItem() shouldBe DayUiState.Unavailable
            }
        }

    @Test
    fun `an epoch day in year 10000 or year 0 is unavailable`() =
        runTest(dispatcher) {
            val year10000 = LocalDate.of(9999, 12, 31).toEpochDay() + 366
            val year0 = LocalDate.of(1, 1, 1).toEpochDay() - 1

            viewModelForEpochDay(year10000).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                awaitItem() shouldBe DayUiState.Unavailable
            }
            viewModelForEpochDay(year0).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                awaitItem() shouldBe DayUiState.Unavailable
            }
        }

    @Test
    fun `an in-range epoch day still loads normally`() =
        runTest(dispatcher) {
            viewModelForEpochDay(LocalDate.of(2026, 9, 17).toEpochDay()).uiState.test {
                awaitItem() shouldBe DayUiState.Loading
                awaitItem().shouldBeInstanceOf<DayUiState.Loaded>().gregorianDate shouldBe LocalDate.of(2026, 9, 17)
            }
        }
}
