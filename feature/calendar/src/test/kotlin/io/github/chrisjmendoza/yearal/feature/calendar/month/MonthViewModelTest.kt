package io.github.chrisjmendoza.yearal.feature.calendar.month

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.github.chrisjmendoza.yearal.core.holidays.HolidayPackLoader
import io.github.chrisjmendoza.yearal.core.navigation.MonthKey
import io.github.chrisjmendoza.yearal.core.testing.FakeDateTicker
import io.github.chrisjmendoza.yearal.core.testing.FakeSettingsRepository
import io.github.chrisjmendoza.yearal.feature.calendar.holiday.HolidayCatalog
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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
 * [MonthViewModel] and [MonthPages] against [FakeDateTicker] and [FakeSettingsRepository] with the
 * real [HolidayEngine] and bundled packs: the page ↔ month arithmetic at both ends of the
 * `docs/calendar-spec.md` §7.1 UI range, holidays per visible month from the enabled sets
 * (docs/ARCHITECTURE.md §3.3–3.4), the today page moving across midnight (docs/WORKFLOW.md §3) and
 * the live settings.
 *
 * A [StandardTestDispatcher] is used on purpose, as in `TodayViewModelTest`: the initial state (no
 * today yet) is observable before the first combined emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MonthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var catalog: HolidayCatalog

    /** IFC month 10 is September, not October (CLAUDE.md rule 5): the month of the spec §4.1 example. */
    private val september2026 = IfcYearMonth(2026, IfcMonth.SEPTEMBER)
    private val october2026 = IfcYearMonth(2026, IfcMonth.OCTOBER)
    private val december2026 = IfcYearMonth(2026, IfcMonth.DECEMBER)
    private val christmas2026 = LocalDate.of(2026, 12, 25)
    private val yearDay2026 = LocalDate.of(2026, 12, 31)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        catalog =
            HolidayCatalog(
                HolidayEngine(),
                HolidayPackLoader(),
                ApplicationProvider.getApplicationContext<Context>(),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        initialMonth: IfcYearMonth,
        ticker: FakeDateTicker = FakeDateTicker(LocalDate.of(2026, 9, 17)),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = MonthViewModel(initialMonth, ticker, settings, catalog)

    // Spec §7.1: the pager covers 1583..9999, 13 pages a year.

    @Test
    fun `page 0 is January 1583 and the last page is December 9999`() {
        MonthPages.COUNT shouldBe (9999 - 1583 + 1) * 13
        MonthPages.pageOf(IfcYearMonth(1583, IfcMonth.JANUARY)) shouldBe 0
        MonthPages.monthAt(0) shouldBe IfcYearMonth(1583, IfcMonth.JANUARY)
        MonthPages.pageOf(IfcYearMonth(9999, IfcMonth.DECEMBER)) shouldBe MonthPages.LAST_PAGE
        MonthPages.monthAt(MonthPages.LAST_PAGE) shouldBe IfcYearMonth(9999, IfcMonth.DECEMBER)
    }

    @Test
    fun `MonthKey(2026, 10) is IFC September 2026 and round-trips through its page`() {
        val month = MonthPages.monthOf(MonthKey(2026, 10))
        month shouldBe september2026
        month.month.number shouldBe 10
        val page = MonthPages.pageOf(month)
        page shouldBe (2026 - 1583) * 13 + 9
        MonthPages.monthAt(page) shouldBe september2026
        MonthPages.monthAt(page + 1) shouldBe october2026
        MonthPages.monthAt(page - 9) shouldBe IfcYearMonth(2026, IfcMonth.JANUARY)
        MonthPages.monthAt(page - 10) shouldBe IfcYearMonth(2025, IfcMonth.DECEMBER)
    }

    @Test
    fun `keys outside the UI range are clamped, never thrown`() {
        MonthPages.monthOf(MonthKey(1200, 7)) shouldBe IfcYearMonth(1583, IfcMonth.SOL)
        MonthPages.monthOf(MonthKey(12000, 0)) shouldBe IfcYearMonth(9999, IfcMonth.JANUARY)
        MonthPages.monthOf(MonthKey(2026, 14)) shouldBe IfcYearMonth(2026, IfcMonth.DECEMBER)
        MonthPages.pageOf(IfcYearMonth(1, IfcMonth.MARCH)) shouldBe 2
    }

    @Test
    fun `starts on the key's page with no today, then fills in from the ticker and settings`() =
        runTest(dispatcher) {
            val viewModel = viewModel(september2026)
            val page = MonthPages.pageOf(september2026)
            viewModel.uiState.value shouldBe
                MonthUiState(currentPage = page, today = null, todayPage = null, selected = null)
            viewModel.uiState.test {
                awaitItem().today shouldBe null
                val loaded = awaitItem()
                loaded.currentPage shouldBe page
                loaded.today shouldBe LocalDate.of(2026, 9, 17)
                // Gregorian September 17, 2026 is IFC September 8, 2026 (spec §4.1): the same page.
                loaded.todayPage shouldBe page
                loaded.selected shouldBe null
                loaded.weekdayDisplay shouldBe WeekdayDisplay.BOTH
                loaded.holidaysByMonth.keys shouldContainExactly
                    setOf(IfcYearMonth(2026, IfcMonth.AUGUST), september2026, october2026)
                // Labor Day, Monday September 7, 2026, is day 250 = IFC August 26 (spec §7.4), so it
                // belongs to the August page; the September page (Gregorian Sep 10 – Oct 7) has Patriot Day.
                loaded.holidaysByMonth[IfcYearMonth(2026, IfcMonth.AUGUST)] shouldBe
                    mapOf(LocalDate.of(2026, 9, 7) to "Labor Day")
                loaded.holidaysByMonth[september2026] shouldBe mapOf(LocalDate.of(2026, 9, 11) to "Patriot Day")
            }
        }

    // ARCHITECTURE §3.3–3.4: holidays of the enabled sets, per visible month, keyed by Gregorian date.

    @Test
    fun `December 2026 lists Christmas Day and Year Day from the default packs`() =
        runTest(dispatcher) {
            viewModel(december2026).uiState.test {
                awaitItem()
                val december = awaitItem().holidaysByMonth[december2026].shouldNotBeNull()
                december[christmas2026] shouldBe "Christmas Day"
                // Year Day (ifc pack) shares Dec 31 with the US pack's New Year's Eve and Kwanzaa.
                december[yearDay2026].shouldNotBeNull() shouldContain "Year Day"
                december shouldContainKey LocalDate.of(2026, 12, 24)
            }
        }

    @Test
    fun `disabling the us pack removes Christmas but keeps Year Day`() =
        runTest(dispatcher) {
            val settings = FakeSettingsRepository(UserSettings(enabledHolidaySets = setOf("ifc")))
            viewModel(december2026, settings = settings).uiState.test {
                awaitItem()
                val december = awaitItem().holidaysByMonth[december2026].shouldNotBeNull()
                december shouldNotContainKey christmas2026
                december[yearDay2026] shouldBe "Year Day"
            }
        }

    @Test
    fun `toggling a pack in settings updates the holidays live`() =
        runTest(dispatcher) {
            val settings = FakeSettingsRepository()
            viewModel(december2026, settings = settings).uiState.test {
                awaitItem()
                awaitItem().holidaysByMonth[december2026].shouldNotBeNull() shouldContainKey christmas2026

                settings.update { it.copy(enabledHolidaySets = emptySet()) }

                awaitItem().holidaysByMonth.values.all { it.isEmpty() } shouldBe true
            }
        }

    @Test
    fun `paging evaluates holidays for the new page and its neighbours`() =
        runTest(dispatcher) {
            val viewModel = viewModel(october2026)
            viewModel.uiState.test {
                awaitItem()
                awaitItem().holidaysByMonth shouldNotContainKey december2026

                viewModel.showPage(MonthPages.pageOf(IfcYearMonth(2026, IfcMonth.NOVEMBER)))

                val paged = awaitItem()
                paged.currentPage shouldBe MonthPages.pageOf(IfcYearMonth(2026, IfcMonth.NOVEMBER))
                paged.holidaysByMonth[december2026].shouldNotBeNull()[christmas2026] shouldBe "Christmas Day"
            }
        }

    @Test
    fun `an out-of-range page is clamped`() =
        runTest(dispatcher) {
            val viewModel = viewModel(october2026)
            viewModel.uiState.test {
                awaitItem()
                awaitItem()
                viewModel.showPage(-5)
                awaitItem().currentPage shouldBe 0
                viewModel.showPage(Int.MAX_VALUE)
                awaitItem().currentPage shouldBe MonthPages.LAST_PAGE
            }
        }

    // WORKFLOW §3: crossing midnight with a fake clock; the today ring and the Today target both move.

    @Test
    fun `crossing midnight moves today and, at a month boundary, the today page`() =
        runTest(dispatcher) {
            // IFC September 28, 2026 is Gregorian October 7; the next day is IFC October 1.
            val ticker = FakeDateTicker(LocalDate.of(2026, 10, 7))
            viewModel(october2026, ticker = ticker).uiState.test {
                awaitItem()
                val before = awaitItem()
                before.today shouldBe LocalDate.of(2026, 10, 7)
                before.todayPage shouldBe MonthPages.pageOf(IfcYearMonth(2026, IfcMonth.SEPTEMBER))

                ticker.set(LocalDate.of(2026, 10, 8))

                val after = awaitItem()
                after.today shouldBe LocalDate.of(2026, 10, 8)
                after.todayPage shouldBe MonthPages.pageOf(october2026)
            }
        }

    @Test
    fun `Year Day as today targets the December page`() =
        runTest(dispatcher) {
            viewModel(october2026, ticker = FakeDateTicker(yearDay2026)).uiState.test {
                awaitItem()
                awaitItem().todayPage shouldBe MonthPages.pageOf(december2026)
            }
        }

    @Test
    fun `Leap Day as today targets the June page`() =
        runTest(dispatcher) {
            viewModel(october2026, ticker = FakeDateTicker(LocalDate.of(2028, 6, 17))).uiState.test {
                awaitItem()
                awaitItem().todayPage shouldBe MonthPages.pageOf(IfcYearMonth(2028, IfcMonth.JUNE))
            }
        }

    @Test
    fun `changing the weekday display setting updates the state`() =
        runTest(dispatcher) {
            val settings = FakeSettingsRepository()
            viewModel(october2026, settings = settings).uiState.test {
                awaitItem()
                awaitItem().weekdayDisplay shouldBe WeekdayDisplay.BOTH

                settings.update { it.copy(weekdayDisplay = WeekdayDisplay.ACTUAL) }

                awaitItem().weekdayDisplay shouldBe WeekdayDisplay.ACTUAL
            }
        }

    @Test
    fun `selecting a day is reflected in the state`() =
        runTest(dispatcher) {
            val viewModel = viewModel(december2026)
            viewModel.uiState.test {
                awaitItem()
                awaitItem().selected shouldBe null

                viewModel.select(yearDay2026)

                awaitItem().selected shouldBe yearDay2026
            }
        }
}
