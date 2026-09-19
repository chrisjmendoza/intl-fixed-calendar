package io.github.chrisjmendoza.yearal.feature.calendar.year

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.FakeDateTicker
import io.github.chrisjmendoza.yearal.core.testing.FakeObserveAgendaUseCase
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * [YearViewModel] against [FakeDateTicker] and [FakeObserveAgendaUseCase]: the 13 months in order
 * (docs/calendar-spec.md §2.2), Leap Day present only in leap years (CLAUDE.md rule 6), Year Day
 * always valid, the year's event presence from **one** range query (docs/ARCHITECTURE.md §3.4 last
 * paragraph), the today marker crossing midnight and a year boundary (docs/WORKFLOW.md §3), and year
 * clamping to [DatePickerRange] (ARCHITECTURE "Reconciled decisions" 6).
 *
 * A [StandardTestDispatcher] is used on purpose, as in `MonthViewModelTest`: the initial state (no
 * today yet) is observable before the first combined emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class YearViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        initialYear: Int,
        ticker: FakeDateTicker = FakeDateTicker(LocalDate.of(2026, 9, 17)),
        observeAgenda: FakeObserveAgendaUseCase = FakeObserveAgendaUseCase(),
    ) = YearViewModel(initialYear, ticker, observeAgenda)

    // spec §2.2: 13 months in calendar order, Sol between June and July.

    @Test
    fun `the 13 months are in calendar order with Sol between June and July`() =
        runTest(dispatcher) {
            viewModel(2026).uiState.test {
                awaitItem()
                val months = awaitItem().months
                months.map { it.month } shouldContainExactly IfcMonth.entries.toList()
                months.size shouldBe 13
                months.all { it.year == 2026 } shouldBe true
                months[IfcMonth.JUNE.ordinal].month shouldBe IfcMonth.JUNE
                months[IfcMonth.SOL.ordinal].month shouldBe IfcMonth.SOL
                months[IfcMonth.JULY.ordinal].month shouldBe IfcMonth.JULY
            }
        }

    // CLAUDE.md rule 6: Leap Day exists only in leap years; Year Day exists in every year.

    @Test
    fun `June's trailing intercalary is Leap Day only in a leap year like 2028`() =
        runTest(dispatcher) {
            viewModel(2028).uiState.test {
                awaitItem()
                val june = awaitItem().months[IfcMonth.JUNE.ordinal]
                june.trailingIntercalary shouldBe IfcDate.LeapDay(2028)
            }
        }

    @Test
    fun `1900 is a common year, so June has no trailing intercalary`() =
        runTest(dispatcher) {
            viewModel(1900).uiState.test {
                awaitItem()
                awaitItem().months[IfcMonth.JUNE.ordinal].trailingIntercalary shouldBe null
            }
        }

    @Test
    fun `2100 is a common year, so June has no trailing intercalary`() =
        runTest(dispatcher) {
            viewModel(2100).uiState.test {
                awaitItem()
                awaitItem().months[IfcMonth.JUNE.ordinal].trailingIntercalary shouldBe null
            }
        }

    @Test
    fun `Year Day exists in a common year`() =
        runTest(dispatcher) {
            viewModel(2026).uiState.test {
                awaitItem()
                awaitItem().months[IfcMonth.DECEMBER.ordinal].trailingIntercalary shouldBe IfcDate.YearDay(2026)
            }
        }

    @Test
    fun `Year Day also exists in a leap year`() =
        runTest(dispatcher) {
            viewModel(2028).uiState.test {
                awaitItem()
                awaitItem().months[IfcMonth.DECEMBER.ordinal].trailingIntercalary shouldBe IfcDate.YearDay(2028)
            }
        }

    // ARCHITECTURE §3.4 last paragraph: one range query for the whole year; the presence set includes
    // an event on Year Day and one on Leap Day.

    @Test
    fun `the year's presence comes from one range query covering January 1 to Year Day`() =
        runTest(dispatcher) {
            val agenda = FakeObserveAgendaUseCase()
            viewModel(2026, observeAgenda = agenda).uiState.test {
                awaitItem()
                awaitItem()
                agenda.requestedRanges shouldContainExactly
                    listOf(LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31))
            }
        }

    @Test
    fun `an event on Year Day reaches the presence set`() =
        runTest(dispatcher) {
            val agenda = FakeObserveAgendaUseCase()
            agenda.putEntry(
                EventFixtures.entry(EventFixtures.allDay(id = 1, date = EventFixtures.YEAR_DAY_2026, title = "Party")),
            )
            viewModel(2026, observeAgenda = agenda).uiState.test {
                awaitItem()
                awaitItem().eventDates shouldContainExactly setOf(EventFixtures.YEAR_DAY_2026)
            }
        }

    @Test
    fun `an event on Leap Day reaches the presence set`() =
        runTest(dispatcher) {
            val agenda = FakeObserveAgendaUseCase()
            agenda.putEntry(
                EventFixtures.entry(
                    EventFixtures.allDay(id = 2, date = EventFixtures.LEAP_DAY_2028, title = "Extra day"),
                ),
            )
            viewModel(2028, observeAgenda = agenda).uiState.test {
                awaitItem()
                awaitItem().eventDates shouldContainExactly setOf(EventFixtures.LEAP_DAY_2028)
            }
        }

    @Test
    fun `an event outside the shown year is not in the presence set`() =
        runTest(dispatcher) {
            val agenda = FakeObserveAgendaUseCase()
            agenda.putEntry(
                EventFixtures.entry(EventFixtures.allDay(id = 1, date = LocalDate.of(2027, 1, 1), title = "Next year")),
            )
            viewModel(2026, observeAgenda = agenda).uiState.test {
                awaitItem()
                awaitItem().eventDates shouldBe emptySet()
            }
        }

    // WORKFLOW §3: crossing midnight, including the Dec 31 -> Jan 1 year rollover.

    @Test
    fun `today follows the ticker regardless of the year shown`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2026, 9, 17))
            viewModel(2026, ticker = ticker).uiState.test {
                awaitItem()
                awaitItem().today shouldBe LocalDate.of(2026, 9, 17)

                ticker.set(LocalDate.of(2026, 9, 18))

                awaitItem().today shouldBe LocalDate.of(2026, 9, 18)
            }
        }

    @Test
    fun `crossing from Year Day into January 1 rolls today into the next year`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2026, 12, 31))
            viewModel(2026, ticker = ticker).uiState.test {
                awaitItem()
                val before = awaitItem()
                before.today shouldBe LocalDate.of(2026, 12, 31)
                before.year shouldBe 2026

                ticker.set(LocalDate.of(2027, 1, 1))

                val after = awaitItem()
                after.today shouldBe LocalDate.of(2027, 1, 1)
                // The shown year does not follow the clock by itself; only an explicit goToYear does.
                after.year shouldBe 2026
            }
        }

    // ARCHITECTURE "Reconciled decisions" 6: years clamp to DatePickerRange (1583..9999).

    @Test
    fun `a year below the range clamps to 1583`() =
        runTest(dispatcher) {
            viewModel(1200).uiState.test {
                awaitItem()
                val state = awaitItem()
                state.year shouldBe DatePickerRange.MIN_YEAR
                state.canGoPrevious shouldBe false
                state.canGoNext shouldBe true
            }
        }

    @Test
    fun `a year above the range clamps to 9999`() =
        runTest(dispatcher) {
            viewModel(20000).uiState.test {
                awaitItem()
                val state = awaitItem()
                state.year shouldBe DatePickerRange.MAX_YEAR
                state.canGoNext shouldBe false
                state.canGoPrevious shouldBe true
            }
        }

    @Test
    fun `previousYear at the minimum year stays clamped`() =
        runTest(dispatcher) {
            val viewModel = viewModel(DatePickerRange.MIN_YEAR)
            viewModel.uiState.test {
                awaitItem()
                awaitItem().year shouldBe DatePickerRange.MIN_YEAR
            }

            // MutableStateFlow conflates a value equal to the current one, so a no-op clamp emits
            // nothing new; the state simply never leaves DatePickerRange.MIN_YEAR.
            viewModel.previousYear()
            viewModel.uiState.value.year shouldBe DatePickerRange.MIN_YEAR
        }

    @Test
    fun `nextYear at the maximum year stays clamped`() =
        runTest(dispatcher) {
            val viewModel = viewModel(DatePickerRange.MAX_YEAR)
            viewModel.uiState.test {
                awaitItem()
                awaitItem().year shouldBe DatePickerRange.MAX_YEAR
            }

            viewModel.nextYear()
            viewModel.uiState.value.year shouldBe DatePickerRange.MAX_YEAR
        }

    @Test
    fun `previousYear and nextYear move by one year away from the ends of the range`() =
        runTest(dispatcher) {
            val viewModel = viewModel(2026)
            viewModel.uiState.test {
                awaitItem()
                awaitItem().year shouldBe 2026

                viewModel.previousYear()
                awaitItem().year shouldBe 2025

                viewModel.nextYear()
                awaitItem().year shouldBe 2026
            }
        }

    @Test
    fun `goToYear jumps directly and re-issues the presence query for the new year`() =
        runTest(dispatcher) {
            val agenda = FakeObserveAgendaUseCase()
            val viewModel = viewModel(2026, observeAgenda = agenda)
            viewModel.uiState.test {
                awaitItem()
                awaitItem().year shouldBe 2026

                viewModel.goToYear(2028)

                awaitItem().year shouldBe 2028
                agenda.requestedRanges shouldContainExactly
                    listOf(
                        LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31),
                        LocalDate.of(2028, 1, 1)..LocalDate.of(2028, 12, 31),
                    )
            }
        }

    @Test
    fun `goToYear also clamps into the range`() =
        runTest(dispatcher) {
            val viewModel = viewModel(2026)
            viewModel.uiState.test {
                awaitItem()
                awaitItem()

                viewModel.goToYear(0)

                awaitItem().year shouldBe DatePickerRange.MIN_YEAR
            }
        }

    @Test
    fun `IfcYearMonth from is consistent with the mini-month tile's own month mapping`() {
        // Sanity check that Leap Day and Year Day map back to June and December respectively, the
        // invariant the Year overview's tiles rely on (core/designsystem YearMiniMonthTile).
        IfcYearMonth.from(IfcDate.LeapDay(2028)) shouldBe IfcYearMonth(2028, IfcMonth.JUNE)
        IfcYearMonth.from(IfcDate.YearDay(2026)) shouldBe IfcYearMonth(2026, IfcMonth.DECEMBER)
    }
}
