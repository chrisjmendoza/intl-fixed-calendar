package io.github.chrisjmendoza.yearal.feature.converter

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange
import io.github.chrisjmendoza.yearal.core.designsystem.picker.IfcDatePickerValue
import io.github.chrisjmendoza.yearal.core.designsystem.picker.IfcDaySelection
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.RealDateTicker
import io.github.chrisjmendoza.yearal.core.navigation.ConverterKey
import io.github.chrisjmendoza.yearal.core.testing.FakeDateTicker
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * [ConverterViewModel] against [FakeDateTicker] (and once against the real ticker on a [MutableClock]),
 * written from `docs/calendar-spec.md`: every expected date and weekday below is a row of the §6
 * vector tables or a rule of §2 (Year Day = December 31, Leap Day = June 17), and the property tests
 * use `:core:calendar` as the oracle — nothing is computed here. Covers every date shape in both
 * directions (CLAUDE.md rule 6), the 1583..9999 bounds (§7.1), invalid input as a state, the prefill,
 * the proleptic note, "today" crossing midnight (docs/WORKFLOW.md §3) and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ConverterViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var formatter: IfcDateFormatter

    // §6.4 "today (spec date)".
    private val specToday = LocalDate.of(2026, 9, 17)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        formatter = IfcDateFormatter(ApplicationProvider.getApplicationContext<Context>().resources, Locale.US)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        key: ConverterKey = ConverterKey(),
        ticker: DateTicker = FakeDateTicker(specToday),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = ConverterViewModel(key, handle, ticker, formatter)

    /** Keeps [viewModel] subscribed and returns a reader of its settled, loaded state. */
    private fun TestScope.observe(viewModel: ConverterViewModel): () -> ConverterUiState.Loaded {
        backgroundScope.launch { viewModel.uiState.collect {} }
        return {
            runCurrent()
            viewModel.uiState.value.shouldBeInstanceOf<ConverterUiState.Loaded>()
        }
    }

    private fun ConverterUiState.Loaded.converted() = result.shouldBeInstanceOf<ConversionResult.Converted>()

    /** The value a user reaches by typing [date]'s year and tapping its month and day, from an unrelated start. */
    private fun entered(date: IfcDate): IfcDatePickerValue {
        val typed = IfcDatePickerValue.of(IfcDate.YearDay(2000)).withYearText(date.year.toString())
        return when (date) {
            is IfcDate.Regular -> typed.withMonth(date.month).withDayOfMonth(date.dayOfMonth)
            is IfcDate.LeapDay -> typed.withSelection(IfcDaySelection.LeapDay)
            is IfcDate.YearDay -> typed.withSelection(IfcDaySelection.YearDay)
        }
    }

    // §4.1 worked example and §6.4 row 1: nominal Sunday, actual Thursday — never one from the other.
    @Test
    fun `starts loading, then converts today as the default input`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.uiState.value shouldBe ConverterUiState.Loading
            viewModel.uiState.test {
                awaitItem() shouldBe ConverterUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<ConverterUiState.Loaded>()
                loaded.direction shouldBe ConversionDirection.GREGORIAN_TO_IFC
                loaded.followsToday shouldBe true
                loaded.gregorianInput shouldBe specToday
                loaded.gregorianInputLabel shouldBe "Thursday, September 17, 2026"
                loaded.ifcInput.date shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
                val result = loaded.converted()
                result.date shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
                result.gregorianDate shouldBe specToday
                result.ifcLong shouldBe "September 8, 2026"
                result.numeric shouldBe "IFC 2026-10-08"
                result.gregorianLong shouldBe "Thursday, September 17, 2026"
                result.nominalWeekday shouldBe "IFC weekday: Sunday"
                result.actualWeekday shouldBe "Actual weekday: Thursday"
                result.weekdaysDescription shouldBe "IFC Sunday, actual Thursday"
                result.dayAndWeek shouldBe "Day 260 · Week 38 of 52"
                result.showProlepticNote shouldBe false
            }
        }

    // §6.2 last row.
    @Test
    fun `Gregorian December 31 is Year Day, with no IFC weekday`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            viewModel.setGregorianDate(LocalDate.of(2026, 12, 31))

            val result = state().converted()
            result.date shouldBe IfcDate.YearDay(2026)
            result.ifcLong shouldBe "Year Day, 2026"
            result.numeric shouldBe "IFC 2026-13-29"
            result.nominalWeekday shouldBe "no IFC weekday"
            result.actualWeekday shouldBe "Actual weekday: Thursday"
            result.weekdaysDescription shouldBe "no IFC weekday, actual Thursday"
            result.dayAndWeek shouldBe "Day 365 · outside the weeks"
            state().followsToday shouldBe false
        }

    // §6.4: 2024-06-17 is Leap Day (Monday); 2025-06-17 is the ordinary June 28 (nominal Saturday, real Tuesday).
    @Test
    fun `Gregorian June 17 is Leap Day in a leap year and June 28 in a common year`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)

            viewModel.setGregorianDate(LocalDate.of(2024, 6, 17))
            val leap = state().converted()
            leap.date shouldBe IfcDate.LeapDay(2024)
            leap.ifcLong shouldBe "Leap Day, 2024"
            leap.numeric shouldBe "IFC 2024-06-29"
            leap.nominalWeekday shouldBe "no IFC weekday"
            leap.actualWeekday shouldBe "Actual weekday: Monday"
            leap.dayAndWeek shouldBe "Day 169 · outside the weeks"

            viewModel.setGregorianDate(LocalDate.of(2025, 6, 17))
            val common = state().converted()
            common.date shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)
            common.numeric shouldBe "IFC 2025-06-28"
            common.nominalWeekday shouldBe "IFC weekday: Saturday"
            common.actualWeekday shouldBe "Actual weekday: Tuesday"
            common.dayAndWeek shouldBe "Day 168 · Week 24 of 52"
        }

    // §6.2: 2026-06-18 = Sol 1 (month 07); §6.4: 2026-07-04 = Sol 17, nominal Tuesday, real Saturday.
    @Test
    fun `Sol converts in both directions`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)

            viewModel.setGregorianDate(LocalDate.of(2026, 6, 18))
            val forward = state().converted()
            forward.date shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 1)
            forward.ifcLong shouldBe "Sol 1, 2026"
            forward.numeric shouldBe "IFC 2026-07-01"

            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            viewModel.setIfcInput(entered(IfcDate.Regular(2026, IfcMonth.SOL, 17)))
            val back = state().converted()
            back.gregorianDate shouldBe LocalDate.of(2026, 7, 4)
            back.gregorianLong shouldBe "Saturday, July 4, 2026"
            back.numeric shouldBe "IFC 2026-07-17"
            back.nominalWeekday shouldBe "IFC weekday: Tuesday"
            back.actualWeekday shouldBe "Actual weekday: Saturday"
            state().gregorianInput shouldBe LocalDate.of(2026, 7, 4)
        }

    // §6.2 / §6.4: Year Day 2026 = Thu 2026-12-31; Leap Day 2000 = Sat 2000-06-17; Christmas = December 23.
    @Test
    fun `IFC to Gregorian converts Year Day, Leap Day and a month after Sol`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)

            viewModel.setIfcInput(entered(IfcDate.YearDay(2026)))
            state().converted().gregorianLong shouldBe "Thursday, December 31, 2026"
            state().converted().nominalWeekday shouldBe "no IFC weekday"

            viewModel.setIfcInput(entered(IfcDate.LeapDay(2000)))
            state().converted().gregorianLong shouldBe "Saturday, June 17, 2000"
            state().converted().numeric shouldBe "IFC 2000-06-29"
            state().converted().nominalWeekday shouldBe "no IFC weekday"

            // IFC December is month 13, not 12 (rule 5): December 23 is Gregorian December 25.
            viewModel.setIfcInput(entered(IfcDate.Regular(2026, IfcMonth.DECEMBER, 23)))
            state().converted().gregorianLong shouldBe "Friday, December 25, 2026"
            state().converted().numeric shouldBe "IFC 2026-13-23"
        }

    // Spec §7.10 with §6.4: Leap Day 2024, year changed to 2025 → June 28, 2025 = Tuesday 2025-06-17.
    @Test
    fun `a selected Leap Day moved to a common year converts as June 28`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            viewModel.setIfcInput(entered(IfcDate.LeapDay(2024)))
            state().converted().gregorianDate shouldBe LocalDate.of(2024, 6, 17)

            viewModel.setIfcInput(state().ifcInput.withYearText("2025"))

            state().ifcInput.leapDayClamped shouldBe true
            state().ifcInput.isLeapDayOffered shouldBe false
            state().converted().date shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)
            state().converted().gregorianLong shouldBe "Tuesday, June 17, 2025"
        }

    // §7.1: the UI range is 1583..9999; §6.4 last vectors: 9999-12-31 is Year Day 9999, a Friday.
    @Test
    fun `the first and the last supported day convert in both directions`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)

            viewModel.setGregorianDate(LocalDate.of(1583, 1, 1))
            state().converted().date shouldBe IfcDate.Regular(1583, IfcMonth.JANUARY, 1)
            state().converted().numeric shouldBe "IFC 1583-01-01"

            viewModel.setGregorianDate(LocalDate.of(9999, 12, 31))
            state().converted().date shouldBe IfcDate.YearDay(9999)
            state().converted().numeric shouldBe "IFC 9999-13-29"
            state().converted().actualWeekday shouldBe "Actual weekday: Friday"

            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            viewModel.setIfcInput(entered(IfcDate.Regular(1583, IfcMonth.JANUARY, 1)))
            state().converted().gregorianDate shouldBe LocalDate.of(1583, 1, 1)
            viewModel.setIfcInput(entered(IfcDate.YearDay(9999)))
            state().converted().gregorianDate shouldBe LocalDate.of(9999, 12, 31)
        }

    @Test
    fun `a Gregorian date outside 1583 to 9999 is an invalid state, not an exception`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)

            // 1582-10-15 is the first Gregorian day (§6.4) but before the first full year (§7.1).
            for (outside in listOf(LocalDate.of(1582, 10, 15), LocalDate.of(1582, 12, 31), LocalDate.of(10000, 1, 1))) {
                viewModel.setGregorianDate(outside)
                state().result shouldBe ConversionResult.Invalid
                state().gregorianInput shouldBe outside
            }
            for (extreme in listOf(LocalDate.MIN, LocalDate.MAX, LocalDate.of(0, 1, 1), LocalDate.of(-1, 1, 1))) {
                viewModel.setGregorianDate(extreme)
                state().result shouldBe ConversionResult.Invalid
                // The IFC picker still has something valid to show: today.
                state().ifcInput.date shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
            }

            viewModel.setGregorianDate(LocalDate.of(1583, 1, 1))
            state().converted().date shouldBe IfcDate.Regular(1583, IfcMonth.JANUARY, 1)
        }

    @Test
    fun `an IFC year that is empty, partial or out of range is an invalid state and keeps the last day`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            viewModel.setIfcInput(entered(IfcDate.Regular(2026, IfcMonth.SOL, 17)))

            for (text in listOf("", "2", "202", "1582", "0000", "999")) {
                viewModel.setIfcInput(state().ifcInput.withYearText(text))
                state().result shouldBe ConversionResult.Invalid
                state().ifcInput.yearText shouldBe text
                state().ifcInput.date.shouldBeNull()
                state().gregorianInput shouldBe LocalDate.of(2026, 7, 4)
            }

            // The other direction is unaffected by the half-typed year…
            viewModel.setDirection(ConversionDirection.GREGORIAN_TO_IFC)
            state().converted().date shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 17)
            // …and the draft is still there on the way back.
            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            state().result shouldBe ConversionResult.Invalid

            viewModel.setIfcInput(state().ifcInput.withYearText("9999"))
            state().converted().date shouldBe IfcDate.Regular(9999, IfcMonth.SOL, 17)
        }

    @Test
    fun `the two inputs stay the same physical day`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)

            viewModel.setGregorianDate(LocalDate.of(2026, 12, 25))
            state().ifcInput shouldBe IfcDatePickerValue.of(IfcDate.Regular(2026, IfcMonth.DECEMBER, 23))

            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            state().converted().gregorianDate shouldBe LocalDate.of(2026, 12, 25)

            viewModel.setIfcInput(state().ifcInput.withSelection(IfcDaySelection.YearDay))
            viewModel.setDirection(ConversionDirection.GREGORIAN_TO_IFC)
            state().gregorianInput shouldBe LocalDate.of(2026, 12, 31)
            state().converted().date shouldBe IfcDate.YearDay(2026)
        }

    // ConverterKey.prefillEpochDay overrides the today default.
    @Test
    fun `a prefill is the initial date and does not follow the clock`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(specToday)
            val viewModel = viewModel(ConverterKey(LocalDate.of(2024, 6, 17).toEpochDay()), ticker)
            val state = observe(viewModel)

            state().followsToday shouldBe false
            state().gregorianInput shouldBe LocalDate.of(2024, 6, 17)
            state().converted().date shouldBe IfcDate.LeapDay(2024)
            state().ifcInput.date shouldBe IfcDate.LeapDay(2024)

            ticker.set(specToday.plusDays(1))
            state().gregorianInput shouldBe LocalDate.of(2024, 6, 17)
        }

    // A key can be synthesized from an intent, so its Long is untrusted (security-and-privacy §6.3).
    @Test
    fun `a prefill outside the supported range is ignored`() =
        runTest(dispatcher) {
            val outside =
                listOf(
                    LocalDate.of(1582, 12, 31).toEpochDay(),
                    LocalDate.of(10000, 1, 1).toEpochDay(),
                    Long.MIN_VALUE,
                    Long.MAX_VALUE,
                )
            for (epochDay in outside) {
                val state = observe(viewModel(ConverterKey(epochDay)))
                state().followsToday shouldBe true
                state().gregorianInput shouldBe specToday
                state().converted().date shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
            }
            val first = observe(viewModel(ConverterKey(DatePickerRange.firstDate.toEpochDay())))
            first().converted().date shouldBe IfcDate.Regular(1583, IfcMonth.JANUARY, 1)
            val last = observe(viewModel(ConverterKey(DatePickerRange.lastDate.toEpochDay())))
            last().converted().date shouldBe IfcDate.YearDay(9999)
        }

    // FEATURES D2 / spec §7.1: the latest adoption the spec names is Greece, 1923.
    @Test
    fun `the proleptic note is shown up to 1923 and not after`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            val expectations =
                mapOf(
                    LocalDate.of(1583, 1, 1) to true,
                    LocalDate.of(1752, 9, 14) to true,
                    LocalDate.of(1923, 12, 31) to true,
                    LocalDate.of(1924, 1, 1) to false,
                    LocalDate.of(2026, 9, 17) to false,
                    LocalDate.of(9999, 12, 31) to false,
                )
            for ((date, expected) in expectations) {
                viewModel.setGregorianDate(date)
                state().converted().showProlepticNote shouldBe expected
            }
            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            viewModel.setIfcInput(entered(IfcDate.YearDay(1900)))
            state().converted().showProlepticNote shouldBe true
        }

    // docs/WORKFLOW.md §3: anything that shows "today" crosses midnight. §6.2 / §6.4: Year Day → January 1.
    @Test
    fun `the today default rolls over at midnight, from Year Day into January 1`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(LocalDate.of(2026, 12, 31))
            val viewModel = viewModel(ticker = ticker)
            val state = observe(viewModel)
            state().converted().date shouldBe IfcDate.YearDay(2026)
            state().ifcInput.date shouldBe IfcDate.YearDay(2026)

            ticker.set(LocalDate.of(2027, 1, 1))

            state().followsToday shouldBe true
            state().gregorianInput shouldBe LocalDate.of(2027, 1, 1)
            state().converted().date shouldBe IfcDate.Regular(2027, IfcMonth.JANUARY, 1)
            state().converted().nominalWeekday shouldBe "IFC weekday: Sunday"
            state().converted().actualWeekday shouldBe "Actual weekday: Friday"
            state().ifcInput.date shouldBe IfcDate.Regular(2027, IfcMonth.JANUARY, 1)
        }

    // The same crossing driven by a fake Clock through the real ticker: Leap Day 2024 → Sol 1 (§6.4).
    @Test
    fun `with a fake clock the default moves from Leap Day to Sol 1 at local midnight`() =
        runTest(dispatcher) {
            val clock = MutableClock(Instant.parse("2024-06-17T23:59:59Z"), ZoneOffset.UTC)
            val viewModel = viewModel(ticker = RealDateTicker(clock, FakeZoneProvider(ZoneOffset.UTC)))
            val state = observe(viewModel)
            state().converted().date shouldBe IfcDate.LeapDay(2024)

            advanceTimeBy(500)
            state().converted().date shouldBe IfcDate.LeapDay(2024)

            clock.set(Instant.parse("2024-06-18T00:00:00Z"))
            advanceTimeBy(600)
            state().converted().date shouldBe IfcDate.Regular(2024, IfcMonth.SOL, 1)
            state().converted().actualWeekday shouldBe "Actual weekday: Tuesday"
            // Leap Day was only the default, never a selection, so nothing is "clamped".
            state().ifcInput shouldBe IfcDatePickerValue.of(IfcDate.Regular(2024, IfcMonth.SOL, 1))
        }

    @Test
    fun `a chosen date ignores midnight until Today is pressed`() =
        runTest(dispatcher) {
            val ticker = FakeDateTicker(specToday)
            val viewModel = viewModel(ticker = ticker)
            val state = observe(viewModel)
            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            viewModel.setIfcInput(entered(IfcDate.Regular(2026, IfcMonth.SOL, 17)))

            ticker.set(specToday.plusDays(1))
            state().followsToday shouldBe false
            state().converted().gregorianDate shouldBe LocalDate.of(2026, 7, 4)

            viewModel.resetToToday()
            state().followsToday shouldBe true
            state().direction shouldBe ConversionDirection.IFC_TO_GREGORIAN
            // 2026-09-18 = September 9 (§6.4 has September 8 for the 17th).
            state().converted().gregorianDate shouldBe LocalDate.of(2026, 9, 18)
            state().ifcInput.date shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 9)
        }

    @Test
    fun `input survives process death and wins over the prefill`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle = handle)
            val before = observe(first)
            first.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            first.setIfcInput(entered(IfcDate.LeapDay(2024)))
            first.setIfcInput(before().ifcInput.withYearText("2025"))
            val expected = before()
            expected.ifcInput.leapDayClamped shouldBe true

            val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val after = observe(viewModel(ConverterKey(LocalDate.of(2000, 1, 1).toEpochDay()), handle = restored))

            after() shouldBe expected
            after().converted().gregorianDate shouldBe LocalDate.of(2025, 6, 17)
        }

    @Test
    fun `a half-typed IFC year survives process death next to the last valid day`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(handle = handle)
            val before = observe(first)
            first.setGregorianDate(LocalDate.of(2026, 12, 31))
            first.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            first.setIfcInput(before().ifcInput.withYearText("20"))

            val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            val after = observe(viewModel(handle = restored))

            after().result shouldBe ConversionResult.Invalid
            after().ifcInput.yearText shouldBe "20"
            after().ifcInput.selection shouldBe IfcDaySelection.YearDay
            after().gregorianInput shouldBe LocalDate.of(2026, 12, 31)
            after() shouldBe before()
        }

    @Test
    fun `saved state that makes no sense is dropped, not trusted`() =
        runTest(dispatcher) {
            val garbage =
                SavedStateHandle(
                    mapOf(
                        "converter.direction" to "IFC_TO_GREGORIAN",
                        "converter.epochDay" to Long.MAX_VALUE,
                        "converter.ifc.yearText" to "2025",
                        "converter.ifc.month" to 7,
                        "converter.ifc.day" to 29,
                        "converter.ifc.clamped" to false,
                    ),
                )
            val state = observe(viewModel(handle = garbage))
            // Sol 29 names no day and the epoch day is absurd: only the direction survives.
            state().direction shouldBe ConversionDirection.IFC_TO_GREGORIAN
            state().followsToday shouldBe true
            state().converted().gregorianDate shouldBe specToday

            val unknownDirection = SavedStateHandle(mapOf("converter.direction" to "SIDEWAYS"))
            observe(viewModel(handle = unknownDirection))().direction shouldBe ConversionDirection.GREGORIAN_TO_IFC

            // A saved Leap Day in a common year is clamped on the way in, as §7.10 requires.
            val leapInCommonYear =
                SavedStateHandle(
                    mapOf(
                        "converter.direction" to "IFC_TO_GREGORIAN",
                        "converter.ifc.yearText" to "2025",
                        "converter.ifc.month" to 6,
                        "converter.ifc.day" to 29,
                    ),
                )
            observe(viewModel(handle = leapInCommonYear))().converted().date shouldBe
                IfcDate.Regular(2025, IfcMonth.JUNE, 28)
        }

    // docs/ROADMAP.md M3 exit: the converter round-trips on property-generated dates. The oracle is
    // :core:calendar; the generator's edge cases include both ends of the range.
    @Test
    fun `generated dates round-trip Gregorian to IFC and back through the picker`() {
        var checked = 0
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            val epochDays = Arb.long(DatePickerRange.firstDate.toEpochDay()..DatePickerRange.lastDate.toEpochDay())
            checkAll(ROUND_TRIPS, epochDays) { epochDay ->
                val gregorian = LocalDate.ofEpochDay(epochDay)
                val expected = IfcDate.from(gregorian)

                viewModel.setDirection(ConversionDirection.GREGORIAN_TO_IFC)
                viewModel.setGregorianDate(gregorian)
                val forward = state().converted()
                forward.date shouldBe expected
                forward.gregorianDate shouldBe gregorian
                forward.numeric shouldBe expected.toPrefixedString()
                forward.numeric shouldStartWith "IFC "
                forward.ifcLong shouldBe formatter.formatLong(expected)

                viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
                state().ifcInput.date shouldBe expected
                viewModel.setIfcInput(entered(expected))
                val back = state().converted()
                back.date shouldBe expected
                back.gregorianDate shouldBe gregorian
                back.gregorianLong shouldBe formatter.formatGregorianLong(gregorian)
                state().gregorianInput shouldBe gregorian
                checked++
            }
        }
        checked shouldBe ROUND_TRIPS
    }

    // Random days almost never hit an intercalary day, so they get their own property. §2.4 R8–R9:
    // Year Day is always December 31 and Leap Day always June 17 — constants, not arithmetic.
    @Test
    fun `Year Day and Leap Day round-trip in generated years`() {
        var checked = 0
        var leapYears = 0
        runTest(dispatcher) {
            val viewModel = viewModel()
            val state = observe(viewModel)
            viewModel.setDirection(ConversionDirection.IFC_TO_GREGORIAN)
            checkAll(ROUND_TRIPS, Arb.int(DatePickerRange.years)) { year ->
                viewModel.setIfcInput(entered(IfcDate.YearDay(year)))
                state().converted().gregorianDate shouldBe LocalDate.of(year, 12, 31)
                state().converted().nominalWeekday shouldBe "no IFC weekday"

                val offered = state().ifcInput.isLeapDayOffered
                offered shouldBe (IfcYearMonth(year, IfcMonth.JUNE).trailingIntercalary != null)
                viewModel.setIfcInput(state().ifcInput.withSelection(IfcDaySelection.LeapDay))
                if (offered) {
                    leapYears++
                    state().converted().date shouldBe IfcDate.LeapDay(year)
                    state().converted().gregorianDate shouldBe LocalDate.of(year, 6, 17)
                    state().converted().numeric shouldBe "IFC $year-06-29"
                } else {
                    // Not offered: the selection stays Year Day.
                    state().converted().date shouldBe IfcDate.YearDay(year)
                }
                checked++
            }
        }
        checked shouldBe ROUND_TRIPS
        (leapYears > 0) shouldBe true
    }

    private companion object {
        const val ROUND_TRIPS = 500
    }
}
