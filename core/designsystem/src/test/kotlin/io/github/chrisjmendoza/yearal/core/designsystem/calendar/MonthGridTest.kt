package io.github.chrisjmendoza.yearal.core.designsystem.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * [MonthGrid] under Robolectric, written from `docs/calendar-spec.md` §2.2–§2.3, §4.1, §7.2 and
 * `docs/ARCHITECTURE.md` §4 ("Intercalary days in a 7-column grid", "Accessibility"): the 4 × 7
 * cells with both numbers, the three header modes, the band and its same-height placeholder,
 * clicks, the today ring, the exact spoken description, the dot cap and RTL.
 */
@RunWith(AndroidJUnit4::class)
// Native graphics: legacy mode fakes every text line at one height, which would hide any layout
// mismatch between the band and its placeholder (and is what Roborazzi captures with).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp")
class MonthGridTest {
    @get:Rule
    val compose = createComposeRule()

    private val sol2026 = IfcYearMonth(2026, IfcMonth.SOL)
    private val june2027 = IfcYearMonth(2027, IfcMonth.JUNE)
    private val june2028 = IfcYearMonth(2028, IfcMonth.JUNE)
    private val december2026 = IfcYearMonth(2026, IfcMonth.DECEMBER)

    // The docs/ARCHITECTURE.md §4 example cell: Sol 13, 2026 = Gregorian Tuesday, June 30, 2026.
    private val sol13 = IfcDate.Regular(2026, IfcMonth.SOL, 13)
    private val june30 = LocalDate.of(2026, 6, 30)

    private fun show(
        month: IfcYearMonth,
        today: LocalDate? = null,
        selected: LocalDate? = null,
        weekdayDisplay: WeekdayDisplay = WeekdayDisplay.BOTH,
        eventCounts: Map<LocalDate, Int> = emptyMap(),
        holidays: Map<LocalDate, String> = emptyMap(),
        onDayClick: (IfcDate) -> Unit = {},
    ) {
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                MonthGrid(
                    month = month,
                    today = today,
                    selected = selected,
                    weekdayDisplay = weekdayDisplay,
                    onDayClick = onDayClick,
                    eventCounts = eventCounts,
                    holidays = holidays,
                )
            }
        }
    }

    private fun cell(date: IfcDate.Regular) =
        compose.onNode(hasContentDescription("Sol ${date.dayOfMonth}, ", substring = true))

    private val dayButtons = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button).and(isSelectable())

    // §2.2–§2.3, FEATURES C1: 28 cells, IFC number large and Gregorian day in the corner.

    @Test
    fun `Sol 2026 shows 28 day cells with the IFC and Gregorian day numbers`() {
        show(sol2026)

        compose.onAllNodes(dayButtons).assertCountEquals(IfcMonth.DAYS_PER_MONTH)
        for (day in 1..IfcMonth.DAYS_PER_MONTH) {
            val date = IfcDate.Regular(2026, IfcMonth.SOL, day)
            val gregorianDay = date.toLocalDate().dayOfMonth
            compose
                .onNode(hasContentDescription("Sol $day, ", substring = true), useUnmergedTree = true)
                .assert(hasAnyDescendant(hasText(day.toString())))
                .assert(hasAnyDescendant(hasText(gregorianDay.toString())))
        }
        // Sol 1 is always Gregorian June 18 (spec §2.2), so the corner reads "18".
        compose
            .onNode(hasContentDescription("Sol 1, ", substring = true), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText("18")))
    }

    @Test
    fun `the month title is a heading`() {
        show(sol2026)

        compose.onNodeWithText("Sol 2026").assertIsDisplayed()
        compose
            .onNode(
                hasText("Sol 2026").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)),
            ).assertIsDisplayed()
    }

    // §4.1 item 3, FEATURES C2: the header modes.

    @Test
    fun `NOMINAL shows only the perpetual IFC weekday row starting on Sunday`() {
        show(sol2026, weekdayDisplay = WeekdayDisplay.NOMINAL)

        val row = compose.onNodeWithContentDescription("IFC weekdays")
        row.assertIsDisplayed()
        row.onChildren().assertCountEquals(GRID_COLUMNS)
        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEachIndexed { column, name ->
            row.onChildAt(column).assertTextEquals(name)
        }
        compose.onAllNodesWithContentDescription("Actual weekdays").assertCountEquals(0)
    }

    @Test
    fun `ACTUAL shows only the real weekdays of the month's columns`() {
        show(sol2026, weekdayDisplay = WeekdayDisplay.ACTUAL)

        val row = compose.onNodeWithContentDescription("Actual weekdays")
        row.assertIsDisplayed()
        // Sol 1, 2026 is Gregorian June 18, 2026; the spec's worked example (§4.1) puts the real
        // weekday of a nominal Sunday in 2026 on a Thursday.
        row.onChildAt(0).assertTextEquals("Thu")
        for (column in 0 until GRID_COLUMNS) {
            val expected = sol2026.actualDayOfWeek(column).getDisplayName(TextStyle.SHORT_STANDALONE, Locale.US)
            row.onChildAt(column).assertTextEquals(expected)
        }
        compose.onAllNodesWithContentDescription("IFC weekdays").assertCountEquals(0)
    }

    @Test
    fun `BOTH shows the nominal row and the actual row`() {
        show(june2028, weekdayDisplay = WeekdayDisplay.BOTH)

        val nominal = compose.onNodeWithContentDescription("IFC weekdays")
        val actual = compose.onNodeWithContentDescription("Actual weekdays")
        nominal.assertIsDisplayed()
        actual.assertIsDisplayed()
        nominal.onChildAt(0).assertTextEquals("Sun")
        val expected = june2028.actualDayOfWeek(0).getDisplayName(TextStyle.SHORT_STANDALONE, Locale.US)
        actual.onChildAt(0).assertTextEquals(expected)
        // A leap year: the offset shifts after Leap Day (§4.1), so the two rows must differ here.
        expected shouldBe "Sat"
    }

    // §7.2, FEATURES C3: the band and its reserved slot.

    @Test
    fun `June 2028 shows the Leap Day band with the Gregorian date and no IFC weekday`() {
        show(june2028)

        compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND).assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Leap Day, no IFC weekday. Gregorian Saturday, June 17, 2028.")
            .assertIsDisplayed()
        compose.onNodeWithText("Leap Day", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Sat, Jun 17, 2028 · no IFC weekday", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(dayButtons).assertCountEquals(IfcMonth.DAYS_PER_MONTH + 1)
    }

    @Test
    fun `December 2026 shows the Year Day band`() {
        show(december2026)

        compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND).assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Year Day, no IFC weekday. Gregorian Thursday, December 31, 2026.")
            .assertIsDisplayed()
        compose.onNodeWithText("Thu, Dec 31, 2026 · no IFC weekday", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `June 2027 shows the placeholder with the Gregorian span and no band`() {
        show(june2027)

        compose.onAllNodesWithTag(MonthGridTestTags.INTERCALARY_BAND).assertCountEquals(0)
        compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_PLACEHOLDER).assertIsDisplayed()
        compose.onNodeWithText("May 21 – Jun 17").assertIsDisplayed()
        compose.onAllNodes(dayButtons).assertCountEquals(IfcMonth.DAYS_PER_MONTH)
    }

    @Test
    fun `the placeholder reserves exactly the band's height`() {
        compose.setContent { BandAndPlaceholder() }

        assertSlotHeightsEqual()
    }

    @Test
    fun `the placeholder reserves exactly the band's height at font scale 2`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                BandAndPlaceholder()
            }
        }

        assertSlotHeightsEqual()
    }

    // Two full grids do not fit one window, so the column scrolls: children are then measured with
    // unbounded height, and the unclipped layout size is what is compared.
    @Composable
    private fun BandAndPlaceholder() {
        IfcTheme(dynamicColor = false) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MonthGrid(june2028, null, null, WeekdayDisplay.BOTH, onDayClick = {})
                MonthGrid(june2027, null, null, WeekdayDisplay.BOTH, onDayClick = {})
            }
        }
    }

    private fun assertSlotHeightsEqual() {
        val band = compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND).fetchSemanticsNode().size
        val placeholder = compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_PLACEHOLDER).fetchSemanticsNode().size
        band.height shouldBeGreaterThan 0
        placeholder.height shouldBe band.height
        placeholder.width shouldBe band.width
    }

    // Clicks deliver the day, including the intercalary ones.

    @Test
    fun `tapping a cell reports its IfcDate`() {
        val clicks = mutableListOf<IfcDate>()
        show(sol2026, onDayClick = clicks::add)

        cell(sol13).performClick()

        clicks shouldBe listOf(sol13)
    }

    @Test
    fun `tapping the Leap Day band reports LeapDay`() {
        val clicks = mutableListOf<IfcDate>()
        show(june2028, onDayClick = clicks::add)

        compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND).performClick()

        clicks shouldBe listOf(IfcDate.LeapDay(2028))
    }

    @Test
    fun `tapping the Year Day band reports YearDay`() {
        val clicks = mutableListOf<IfcDate>()
        show(december2026, onDayClick = clicks::add)

        compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND).performClick()

        clicks shouldBe listOf(IfcDate.YearDay(2026))
    }

    // Today: matched on the Gregorian date, shown by a ring, spoken as "Today." (§4.1 item 2).

    @Test
    fun `the today ring is on the cell with the matching Gregorian date only`() {
        show(sol2026, today = june30)

        compose.onAllNodesWithTag(MonthGridTestTags.TODAY_RING, useUnmergedTree = true).assertCountEquals(1)
        compose
            .onNode(hasContentDescription("Sol 13, ", substring = true), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag(MonthGridTestTags.TODAY_RING)))
        compose
            .onNodeWithContentDescription(
                "Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026. Today.",
            ).assertIsDisplayed()
    }

    @Test
    fun `a today in another month marks nothing`() {
        // September 17, 2026 is IFC September 8 (spec §4.1), not in Sol.
        show(sol2026, today = LocalDate.of(2026, 9, 17))

        compose.onAllNodesWithTag(MonthGridTestTags.TODAY_RING, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Today.", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the band can be today`() {
        show(june2028, today = LocalDate.of(2028, 6, 17))

        compose.onAllNodesWithTag(MonthGridTestTags.TODAY_RING, useUnmergedTree = true).assertCountEquals(1)
        compose
            .onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND, useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag(MonthGridTestTags.TODAY_RING)))
        compose
            .onNodeWithContentDescription("Leap Day, no IFC weekday. Gregorian Saturday, June 17, 2028. Today.")
            .assertIsDisplayed()
    }

    @Test
    fun `the ring follows today across midnight`() {
        var today by mutableStateOf(june30)
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                MonthGrid(sol2026, today, null, WeekdayDisplay.BOTH, onDayClick = {})
            }
        }
        cell(sol13).assert(hasContentDescription("Today.", substring = true))

        today = june30.plusDays(1)

        cell(sol13).assert(!hasContentDescription("Today.", substring = true))
        cell(IfcDate.Regular(2026, IfcMonth.SOL, 14)).assert(hasContentDescription("Today.", substring = true))
        compose.onAllNodesWithTag(MonthGridTestTags.TODAY_RING, useUnmergedTree = true).assertCountEquals(1)
    }

    // Accessibility (docs/ARCHITECTURE.md §4): the merged description, selection, the dot cap.

    @Test
    fun `a cell's merged description has the ARCHITECTURE form`() {
        show(
            sol2026,
            selected = june30,
            eventCounts = mapOf(june30 to 2),
            holidays = mapOf(june30 to "Canada Day"),
        )

        val cell =
            compose.onNodeWithContentDescription(
                "Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026. 2 events. Holiday: Canada Day.",
            )
        cell.assertIsDisplayed()
        cell.assertIsSelected()
        cell.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        cell(IfcDate.Regular(2026, IfcMonth.SOL, 12)).assertIsNotSelected()
    }

    @Test
    fun `event dots are capped at three while the description keeps the count`() {
        show(sol2026, eventCounts = mapOf(june30 to 5, LocalDate.of(2026, 6, 19) to 1))

        // Three dots on Sol 13 (capped from five) plus one on Sol 2.
        compose
            .onAllNodesWithTag(
                MonthGridTestTags.EVENT_DOT,
                useUnmergedTree = true,
            ).assertCountEquals(MAX_EVENT_DOTS + 1)
        compose
            .onNodeWithContentDescription(
                "Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026. 5 events.",
            ).assertIsDisplayed()
        compose
            .onNodeWithContentDescription(
                "Sol 2, IFC Monday. Gregorian Friday, June 19, 2026. 1 event.",
            ).assertIsDisplayed()
    }

    @Test
    fun `a holiday shows its marker`() {
        show(sol2026, holidays = mapOf(june30 to "Canada Day"))

        compose.onAllNodesWithTag(MonthGridTestTags.HOLIDAY_MARKER, useUnmergedTree = true).assertCountEquals(1)
        compose
            .onNode(hasContentDescription("Sol 13, ", substring = true), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasTestTag(MonthGridTestTags.HOLIDAY_MARKER)))
    }

    @Test
    fun `a right-to-left layout renders every cell and the band`() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                IfcTheme(dynamicColor = false) {
                    MonthGrid(december2026, LocalDate.of(2026, 12, 31), null, WeekdayDisplay.BOTH, onDayClick = {})
                }
            }
        }

        compose.onAllNodes(dayButtons).assertCountEquals(IfcMonth.DAYS_PER_MONTH + 1)
        compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND).assertIsDisplayed()
        compose.onNodeWithContentDescription("IFC weekdays").onChildAt(0).assertTextEquals("Sun")
    }
}
