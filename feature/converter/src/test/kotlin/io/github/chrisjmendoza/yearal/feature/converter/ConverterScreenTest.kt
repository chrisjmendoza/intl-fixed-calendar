package io.github.chrisjmendoza.yearal.feature.converter

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange
import io.github.chrisjmendoza.yearal.core.designsystem.picker.IfcDatePickerValue
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.navigation.ConverterKey
import io.github.chrisjmendoza.yearal.core.testing.FakeDateTicker
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

/**
 * [ConverterScreen] under Robolectric (docs/FEATURES.md D1, D2, D4): both dates in full with the
 * numeric `IFC` form, the two weekdays on separately labelled lines and "no IFC weekday" on the
 * intercalary days (spec §4.1), the direction switch and its two inputs, the invalid state, the
 * proleptic note, and copy/share text that always says "IFC". The last tests drive the real
 * [ConverterViewModel] through the screen and round-trip generated dates (docs/ROADMAP.md M3 exit),
 * with `:core:calendar` as the oracle.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp")
class ConverterScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val formatter =
        IfcDateFormatter(ApplicationProvider.getApplicationContext<Context>().resources, Locale.US)
    private val specToday = LocalDate.of(2026, 9, 17)

    private val directions = mutableListOf<ConversionDirection>()
    private val gregorianDates = mutableListOf<LocalDate>()
    private val ifcInputs = mutableListOf<IfcDatePickerValue>()
    private val copied = mutableListOf<String>()
    private val shared = mutableListOf<String>()
    private val openedDays = mutableListOf<LocalDate>()
    private var resets = 0

    private fun show(
        state: ConverterUiState,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IfcTheme(dynamicColor = false) {
                    ConverterScreen(
                        state = state,
                        onDirectionChange = { directions += it },
                        onGregorianDateChange = { gregorianDates += it },
                        onIfcInputChange = { ifcInputs += it },
                        onResetToToday = { resets++ },
                        onCopy = { copied += it },
                        onShare = { shared += it },
                        onOpenDay = { openedDays += it },
                    )
                }
            }
        }
    }

    private fun loaded(
        day: LocalDate,
        direction: ConversionDirection = ConversionDirection.GREGORIAN_TO_IFC,
        chosen: Boolean = true,
        draft: IfcDatePickerValue? = null,
    ) = buildConverterUiState(
        today = if (chosen) specToday else day,
        input = ConverterInput(direction = direction, chosen = day.takeIf { chosen }, ifcDraft = draft),
        formatter = formatter,
    )

    // §4.1 worked example / §6.4 row 1.
    @Test
    fun `Gregorian to IFC shows both dates, the numeric form and both labelled weekdays`() {
        show(loaded(specToday, chosen = false))

        compose.onNodeWithText("IFC: September 8, 2026").assertIsDisplayed()
        compose.onNodeWithText("Gregorian: Thursday, September 17, 2026").assertIsDisplayed()
        compose.onNodeWithText("IFC 2026-10-08").assertIsDisplayed()
        compose.onNodeWithText("IFC weekday: Sunday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Thursday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("IFC Sunday, actual Thursday").assertIsDisplayed()
        compose.onNodeWithText("Day 260 · Week 38 of 52").assertIsDisplayed()
        compose.onAllNodesWithText("proleptic", substring = true).assertCountEquals(0)
        // Rule 5: never a locale-style numeric IFC date.
        compose.onAllNodesWithText("10/08/2026", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("10/8/2026", substring = true).assertCountEquals(0)
        // Following today: nothing to reset.
        compose.onAllNodesWithText("Today").assertCountEquals(0)
    }

    // §6.2 last row; spec §4.1 item 5.
    @Test
    fun `Year Day shows no IFC weekday next to its actual weekday`() {
        show(loaded(LocalDate.of(2026, 12, 31)))

        compose.onNodeWithText("IFC: Year Day, 2026").assertIsDisplayed()
        compose.onNodeWithText("Gregorian: Thursday, December 31, 2026").assertIsDisplayed()
        compose.onNodeWithText("IFC 2026-13-29").assertIsDisplayed()
        compose.onNodeWithText("no IFC weekday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Thursday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("no IFC weekday, actual Thursday").assertIsDisplayed()
        compose.onNodeWithText("Day 365 · outside the weeks").assertIsDisplayed()
        compose.onAllNodesWithText("IFC weekday:", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    // §6.3: Leap Day 2024 = Monday 2024-06-17.
    @Test
    fun `IFC to Gregorian with Leap Day shows the picker and the Gregorian answer first`() {
        show(loaded(LocalDate.of(2024, 6, 17), direction = ConversionDirection.IFC_TO_GREGORIAN))

        compose.onNodeWithText("IFC to Gregorian").assertIsSelected()
        compose.onNodeWithText("Gregorian to IFC").assertIsNotSelected()
        compose.onNodeWithText("Leap Day").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Sol").assertIsNotSelected()
        compose.onAllNodesWithText("Gregorian date").assertCountEquals(0)

        compose.onNodeWithText("Gregorian: Monday, June 17, 2024").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("IFC: Leap Day, 2024").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("IFC 2024-06-29").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("no IFC weekday, actual Monday").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("no IFC weekday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Monday", useUnmergedTree = true).assertIsDisplayed()
        val answerTop =
            compose
                .onNodeWithText("Gregorian: Monday, June 17, 2024")
                .fetchSemanticsNode()
                .boundsInRoot.top
        val questionTop =
            compose
                .onNodeWithText("IFC: Leap Day, 2024")
                .fetchSemanticsNode()
                .boundsInRoot.top
        (answerTop < questionTop) shouldBe true
    }

    @Test
    fun `Gregorian to IFC shows the date button and no IFC picker`() {
        show(loaded(specToday))

        compose.onNodeWithText("Gregorian to IFC").assertIsSelected()
        compose
            .onNodeWithContentDescription(
                "Gregorian date, Thursday, September 17, 2026. Change date",
            ).assertIsDisplayed()
        compose.onAllNodesWithText("IFC year").assertCountEquals(0)
        compose.onAllNodesWithText("Sol").assertCountEquals(0)
    }

    @Test
    fun `the direction switch and the IFC picker report through their callbacks`() {
        show(loaded(specToday, direction = ConversionDirection.IFC_TO_GREGORIAN))

        compose.onNodeWithText("Gregorian to IFC").performClick()
        directions shouldContainExactly listOf(ConversionDirection.GREGORIAN_TO_IFC)

        compose.onNodeWithText("Sol").performScrollTo().performClick()
        compose.onNode(hasText("IFC year")).performScrollTo().performTextReplacement("2028")
        ifcInputs.map { it.date } shouldContainExactly
            listOf(IfcDate.Regular(2026, IfcMonth.SOL, 8), IfcDate.Regular(2028, IfcMonth.SEPTEMBER, 8))
    }

    @Test
    fun `the date button opens the Material picker and OK reports the date`() {
        show(loaded(LocalDate.of(2024, 2, 29)))

        compose.onNodeWithContentDescription("Gregorian date, Thursday, February 29, 2024. Change date").performClick()
        compose.onNodeWithText("OK").performClick()

        gregorianDates shouldContainExactly listOf(LocalDate.of(2024, 2, 29))
        compose.onAllNodesWithText("OK").assertCountEquals(0)
    }

    @Test
    fun `cancelling the Material picker reports nothing`() {
        show(loaded(specToday))

        compose.onNodeWithContentDescription("Gregorian date, Thursday, September 17, 2026. Change date").performClick()
        compose.onNodeWithText("Cancel").performClick()

        gregorianDates shouldHaveSize 0
        compose.onAllNodesWithText("Cancel").assertCountEquals(0)
    }

    @Test
    fun `an invalid IFC year shows a message and no result or actions`() {
        val draft = IfcDatePickerValue.of(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)).withYearText("1582")
        show(loaded(specToday, direction = ConversionDirection.IFC_TO_GREGORIAN, draft = draft))

        compose
            .onNodeWithText("Enter a date between the years 1583 and 9999 to convert it.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("IFC year")).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        for (absent in listOf("Copy", "Share", "Open day")) compose.onAllNodesWithText(absent).assertCountEquals(0)
        compose.onAllNodesWithText("Gregorian: ", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Actual weekday", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a Gregorian date outside the range shows the same message`() {
        show(loaded(LocalDate.of(1582, 10, 15)))

        compose.onNodeWithText("Enter a date between the years 1583 and 9999 to convert it.").assertIsDisplayed()
        compose.onAllNodesWithText("Copy").assertCountEquals(0)
    }

    // FEATURES D2; spec §7.1 gives exactly these three adoptions.
    @Test
    fun `early years carry the proleptic note`() {
        // §6.4: 1900-12-31 = Year Day 1900, a Monday.
        show(loaded(LocalDate.of(1900, 12, 31)))

        compose.onNodeWithText("IFC: Year Day, 1900").assertIsDisplayed()
        val note =
            compose
                .onNodeWithText("proleptic", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .single()
                .text
        for (fact in listOf("Britain in 1752", "Russia in 1918", "Greece in 1923")) note shouldContain fact
    }

    // FEATURES D4; spec §7.3 (the IFC marker) and §7.9 (the Gregorian date goes along).
    @Test
    fun `copy and share hand over text that carries the IFC marker and both dates`() {
        show(loaded(specToday))

        compose.onNodeWithText("Copy").performScrollTo().performClick()
        compose.onNodeWithText("Share").performScrollTo().performClick()

        val expected = "IFC September 8, 2026 (IFC 2026-10-08) = Gregorian Thursday, September 17, 2026"
        copied shouldContainExactly listOf(expected)
        shared shouldContainExactly listOf(expected)
    }

    @Test
    fun `share text for every date shape says IFC and never uses a locale-style numeric date`() {
        val days =
            mapOf(
                LocalDate.of(2026, 12, 31) to
                    "IFC Year Day, 2026 (IFC 2026-13-29) = Gregorian Thursday, December 31, 2026",
                LocalDate.of(2024, 6, 17) to "IFC Leap Day, 2024 (IFC 2024-06-29) = Gregorian Monday, June 17, 2024",
                LocalDate.of(2026, 7, 4) to "IFC Sol 17, 2026 (IFC 2026-07-17) = Gregorian Saturday, July 4, 2026",
            )
        var state: ConverterUiState by mutableStateOf(loaded(specToday))
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                ConverterScreen(
                    state = state,
                    onDirectionChange = {},
                    onGregorianDateChange = {},
                    onIfcInputChange = {},
                    onResetToToday = {},
                    onCopy = {},
                    onShare = { shared += it },
                    onOpenDay = {},
                )
            }
        }
        for (day in days.keys) {
            state = loaded(day)
            compose.onNodeWithText("Share").performScrollTo().performClick()
        }

        shared shouldContainExactly days.values.toList()
        for (text in shared) {
            text shouldContain "IFC"
            Regex("""\d{1,2}[/.]\d{1,2}[/.]\d{2,4}""").containsMatchIn(text) shouldBe false
        }
    }

    @Test
    fun `Open day reports the Gregorian date and Today resets a chosen date`() {
        show(loaded(LocalDate.of(2026, 12, 31)))

        compose.onNodeWithText("Open day").performScrollTo().performClick()
        openedDays shouldContainExactly listOf(LocalDate.of(2026, 12, 31))

        compose.onNodeWithText("Today").performClick()
        resets shouldBe 1
    }

    @Test
    fun `the loading state shows only the spinner`() {
        show(ConverterUiState.Loading)

        compose.onNodeWithContentDescription("Loading the converter").assertIsDisplayed()
        compose.onAllNodesWithText("Result").assertCountEquals(0)
    }

    // docs/ARCHITECTURE.md §4 "Accessibility": 200% font scale, 48dp targets.
    @Test
    fun `at 200 percent font scale the controls keep 48dp and no label is cut`() {
        show(loaded(LocalDate.of(1900, 12, 31)), fontScale = 2f)

        val controls =
            listOf(
                compose.onNodeWithText("Gregorian to IFC"),
                compose.onNodeWithText("IFC to Gregorian"),
                compose.onNodeWithContentDescription("Gregorian date, Monday, December 31, 1900. Change date"),
                compose.onNodeWithText("Copy"),
                compose.onNodeWithText("Share"),
                compose.onNodeWithText("Open day"),
            )
        for (control in controls) {
            control.performScrollTo().assertHeightIsAtLeast(48.dp)
            control.textLayout().isCut() shouldBe false
        }
        for (line in listOf("IFC: Year Day, 1900", "Gregorian: Monday, December 31, 1900", "IFC 1900-13-29")) {
            compose
                .onNodeWithText(line)
                .performScrollTo()
                .textLayout()
                .isCut() shouldBe false
        }
    }

    // ----- The real ViewModel behind the screen -----

    private fun showWithViewModel(): ConverterViewModel {
        val viewModel = ConverterViewModel(ConverterKey(), SavedStateHandle(), FakeDateTicker(specToday), formatter)
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ConverterScreen(
                    state = state,
                    onDirectionChange = viewModel::setDirection,
                    onGregorianDateChange = viewModel::setGregorianDate,
                    onIfcInputChange = viewModel::setIfcInput,
                    onResetToToday = viewModel::resetToToday,
                    onCopy = { copied += it },
                    onShare = { shared += it },
                    onOpenDay = { openedDays += it },
                )
            }
        }
        return viewModel
    }

    /** Enters [date] in the IFC picker the way a user does: the year, then the month and day or the intercalary day. */
    private fun enterInPicker(date: IfcDate) {
        compose.onNode(hasText("IFC year")).performScrollTo().performTextReplacement(date.year.toString())
        when (date) {
            is IfcDate.Regular -> {
                compose.onNodeWithText(formatter.monthName(date.month)).performScrollTo().performClick()
                compose.onNodeWithContentDescription("Day ${date.dayOfMonth}").performScrollTo().performClick()
            }

            is IfcDate.LeapDay -> {
                compose.onNodeWithText("Leap Day").performScrollTo().performClick()
            }

            is IfcDate.YearDay -> {
                compose.onNodeWithText("Year Day").performScrollTo().performClick()
            }
        }
    }

    // Spec §7.10 through the whole stack: Leap Day 2024 → year 2025 → June 28, 2025 = Tuesday 2025-06-17 (§6.4).
    @Test
    fun `through the ViewModel, Leap Day appears with the year and clamps visibly in a common year`() {
        showWithViewModel()
        compose.onNodeWithText("IFC to Gregorian").performClick()
        compose.onAllNodesWithText("Leap Day").assertCountEquals(0)

        enterInPicker(IfcDate.LeapDay(2024))
        compose.onNodeWithText("Gregorian: Monday, June 17, 2024").performScrollTo().assertIsDisplayed()

        compose.onNode(hasText("IFC year")).performScrollTo().performTextReplacement("2025")
        compose.onAllNodesWithText("Leap Day").assertCountEquals(0)
        compose
            .onNodeWithText(
                "2025 has no Leap Day, so the date moved to June 28.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Gregorian: Tuesday, June 17, 2025").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("IFC: June 28, 2025").performScrollTo().assertIsDisplayed()

        compose.onNode(hasText("IFC year")).performScrollTo().performTextReplacement("158")
        compose
            .onNodeWithText("Enter a date between the years 1583 and 9999 to convert it.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    // docs/ROADMAP.md M3 exit: "the converter round-trips in the UI on property-generated dates".
    // Fixed seed, so a failure reproduces; the expected strings come from :core:calendar through the
    // formatter, never from arithmetic here. The fixed days add every date shape and both range ends.
    @Test
    fun `generated dates round-trip through the screen in both directions`() {
        val viewModel = showWithViewModel()
        val random = Random(SEED)
        val first = DatePickerRange.firstDate.toEpochDay()
        val last = DatePickerRange.lastDate.toEpochDay()
        val generated = List(GENERATED_DATES) { LocalDate.ofEpochDay(random.nextLong(first, last + 1)) }
        val fixed =
            listOf(
                DatePickerRange.firstDate,
                DatePickerRange.lastDate,
                LocalDate.of(2024, 6, 17),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 6, 18),
                LocalDate.of(2024, 2, 29),
            )

        for (gregorian in fixed + generated) {
            val expected = IfcDate.from(gregorian)

            // Gregorian → IFC: what the Material picker's OK does, then read the screen.
            compose.onNodeWithText("Gregorian to IFC").performScrollTo().performClick()
            compose.runOnIdle { viewModel.setGregorianDate(gregorian) }
            compose.onNodeWithText("IFC: ${formatter.formatLong(expected)}").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(expected.toPrefixedString()).performScrollTo().assertIsDisplayed()

            // Move the IFC input somewhere else, then type the converted date back in by hand.
            compose.onNodeWithText("IFC to Gregorian").performScrollTo().performClick()
            enterInPicker(IfcDate.YearDay(2000))
            compose.onNodeWithText("Gregorian: Sunday, December 31, 2000").performScrollTo().assertIsDisplayed()
            enterInPicker(expected)

            // IFC → Gregorian lands on the day we started from.
            compose
                .onNodeWithText("Gregorian: ${formatter.formatGregorianLong(gregorian)}")
                .performScrollTo()
                .assertIsDisplayed()
            compose.onNodeWithText(expected.toPrefixedString()).performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                val state = viewModel.uiState.value as ConverterUiState.Loaded
                state.gregorianInput shouldBe gregorian
                state.ifcInput.date shouldBe expected
            }
        }
    }

    // See IfcDatePickerTest: hasVisualOverflow is unusable on the result the semantics action builds.
    private fun TextLayoutResult.isCut(): Boolean =
        didOverflowHeight || (0 until lineCount).any { line -> getLineRight(line) - getLineLeft(line) > size.width }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(results)
        results shouldHaveSize 1
        return results.single()
    }

    private companion object {
        const val SEED = 20260918
        const val GENERATED_DATES = 12
    }
}
