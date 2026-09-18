package io.github.chrisjmendoza.yearal.feature.calendar.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.testing.FakeDateTicker
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import java.util.Locale

/**
 * [TodayViewModel] against [FakeDateTicker]: the state is built from whatever the ticker says "today"
 * is, and only from that (docs/WORKFLOW.md §3: a midnight-crossing test for anything that shows
 * "today"). Expected strings come from `docs/calendar-spec.md` §4.1, §7.3–7.5 and FEATURES T1–T4.
 *
 * A [StandardTestDispatcher] is used on purpose: with an unconfined one the first tick is mapped
 * synchronously on subscription and `stateIn` conflates the initial [TodayUiState.Loading] away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var formatter: IfcDateFormatter

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        formatter = IfcDateFormatter(ApplicationProvider.getApplicationContext<Context>().resources, Locale.US)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(ticker: FakeDateTicker) = TodayViewModel(ticker, formatter)

    @Test
    fun `starts loading, then shows the ticker's date`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2026, 9, 17))
            val viewModel = viewModel(ticker)
            viewModel.uiState.value shouldBe TodayUiState.Loading
            viewModel.uiState.test {
                awaitItem() shouldBe TodayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>()
                loaded.date shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
                loaded.gregorianDate shouldBe LocalDate.of(2026, 9, 17)
                loaded.heroDate shouldBe "September 8, 2026"
                loaded.mediumDate shouldBe "Sep 8, 2026"
                loaded.numericDate shouldBe "IFC 2026-10-08"
                loaded.gregorianLongDate shouldBe "Thursday, September 17, 2026"
                loaded.nominalWeekday shouldBe "IFC weekday: Sunday"
                loaded.actualWeekday shouldBe "Actual weekday: Thursday"
                loaded.weekdaysDescription shouldBe "IFC Sunday, actual Thursday"
                loaded.dayAndWeek shouldBe "Day 260 · Week 38 of 52"
                loaded.quarter shouldBe "Q3"
                loaded.yearProgress shouldBe (260f / 365 plusOrMinus 0.0001f)
                loaded.yearProgressLabel shouldBe "71% of the year"
                loaded.countdown shouldBe "105 days until Year Day"
            }
        }

    @Test
    fun `crossing midnight replaces every field with the next day`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2026, 9, 17))
            viewModel(ticker).uiState.test {
                awaitItem() shouldBe TodayUiState.Loading
                awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>().heroDate shouldBe "September 8, 2026"

                ticker.set(LocalDate.of(2026, 9, 18))

                val next = awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>()
                next.date shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 9)
                next.heroDate shouldBe "September 9, 2026"
                next.numericDate shouldBe "IFC 2026-10-09"
                next.gregorianLongDate shouldBe "Friday, September 18, 2026"
                next.nominalWeekday shouldBe "IFC weekday: Monday"
                next.actualWeekday shouldBe "Actual weekday: Friday"
                next.dayAndWeek shouldBe "Day 261 · Week 38 of 52"
                next.countdown shouldBe "104 days until Year Day"
            }
        }

    @Test
    fun `crossing into a new year rolls from Year Day to January 1`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2026, 12, 31))
            viewModel(ticker).uiState.test {
                awaitItem() shouldBe TodayUiState.Loading
                awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>().heroDate shouldBe "Year Day, 2026"

                ticker.set(LocalDate.of(2027, 1, 1))

                val next = awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>()
                next.heroDate shouldBe "January 1, 2027"
                next.numericDate shouldBe "IFC 2027-01-01"
                next.dayAndWeek shouldBe "Day 1 · Week 1 of 52"
                next.yearProgress shouldBe (1f / 365 plusOrMinus 0.0001f)
            }
        }

    @Test
    fun `Year Day as today renders correctly`() =
        runTest(dispatcher) {
            viewModel(FakeDateTicker(LocalDate.of(2026, 12, 31))).uiState.test {
                awaitItem() shouldBe TodayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>()
                loaded.date shouldBe IfcDate.YearDay(2026)
                loaded.heroDate shouldBe "Year Day, 2026"
                loaded.mediumDate shouldBe "Year Day 2026"
                loaded.numericDate shouldBe "IFC 2026-13-29"
                loaded.gregorianLongDate shouldBe "Thursday, December 31, 2026"
                loaded.nominalWeekday shouldBe "no IFC weekday"
                loaded.actualWeekday shouldBe "Actual weekday: Thursday"
                loaded.weekdaysDescription shouldBe "no IFC weekday, actual Thursday"
                loaded.dayAndWeek shouldBe "Day 365 · outside the weeks"
                loaded.quarter shouldBe "Q4"
                loaded.yearProgress shouldBe 1f
                loaded.yearProgressLabel shouldBe "100% of the year"
                // 2027 is a common year, so the next intercalary day is its Year Day, a full year away.
                loaded.countdown shouldBe "365 days until Year Day"
            }
        }

    @Test
    fun `Leap Day as today renders correctly`() =
        runTest(dispatcher) {
            viewModel(FakeDateTicker(LocalDate.of(2028, 6, 17))).uiState.test {
                awaitItem() shouldBe TodayUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>()
                loaded.date shouldBe IfcDate.LeapDay(2028)
                loaded.heroDate shouldBe "Leap Day, 2028"
                loaded.mediumDate shouldBe "Leap Day 2028"
                loaded.numericDate shouldBe "IFC 2028-06-29"
                loaded.gregorianLongDate shouldBe "Saturday, June 17, 2028"
                loaded.nominalWeekday shouldBe "no IFC weekday"
                loaded.actualWeekday shouldBe "Actual weekday: Saturday"
                loaded.dayAndWeek shouldBe "Day 169 · outside the weeks"
                loaded.quarter shouldBe "Q2"
                loaded.yearProgress shouldBe (169f / 366 plusOrMinus 0.0001f)
                loaded.countdown shouldBe "197 days until Year Day"
            }
        }

    @Test
    fun `countdown targets Leap Day before it and Year Day after it in a leap year`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2028, 6, 16))
            viewModel(ticker).uiState.test {
                awaitItem() shouldBe TodayUiState.Loading
                val before = awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>()
                before.date shouldBe IfcDate.Regular(2028, IfcMonth.JUNE, 28)
                before.countdown shouldBe "1 day until Leap Day"

                ticker.set(LocalDate.of(2028, 6, 18))

                val after = awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>()
                after.date shouldBe IfcDate.Regular(2028, IfcMonth.SOL, 1)
                after.dayAndWeek shouldBe "Day 170 · Week 25 of 52"
                after.countdown shouldBe "196 days until Year Day"
            }
        }

    @Test
    fun `countdown in a common year always targets Year Day`() =
        runTest(dispatcher) {
            viewModel(FakeDateTicker(LocalDate.of(2026, 1, 1))).uiState.test {
                awaitItem() shouldBe TodayUiState.Loading
                awaitItem().shouldBeInstanceOf<TodayUiState.Loaded>().countdown shouldBe "364 days until Year Day"
            }
        }

    @Test
    fun `next intercalary day handles every date shape`() {
        nextIntercalaryDay(IfcDate.Regular(2028, IfcMonth.JANUARY, 1)) shouldBe IfcDate.LeapDay(2028)
        nextIntercalaryDay(IfcDate.Regular(2028, IfcMonth.JUNE, 28)) shouldBe IfcDate.LeapDay(2028)
        nextIntercalaryDay(IfcDate.Regular(2028, IfcMonth.SOL, 1)) shouldBe IfcDate.YearDay(2028)
        nextIntercalaryDay(IfcDate.Regular(2026, IfcMonth.JANUARY, 1)) shouldBe IfcDate.YearDay(2026)
        nextIntercalaryDay(IfcDate.LeapDay(2028)) shouldBe IfcDate.YearDay(2028)
        nextIntercalaryDay(IfcDate.YearDay(2027)) shouldBe IfcDate.LeapDay(2028)
        nextIntercalaryDay(IfcDate.YearDay(2028)) shouldBe IfcDate.YearDay(2029)
        nextIntercalaryDay(IfcDate.YearDay(IfcDate.MAX_YEAR)) shouldBe null
    }
}
