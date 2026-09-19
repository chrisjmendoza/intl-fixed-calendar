package io.github.chrisjmendoza.yearal.feature.calendar.month

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.calendar.MonthGridTestTags
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * [MonthScreen] under Robolectric, written from `docs/calendar-spec.md` §7.1–7.2 and
 * docs/FEATURES.md C1, C3, C5, C7: the pager opens on the state's page with that month's heading,
 * taps report the tapped [IfcDate] — the Leap Day band included — the "Today" action appears only
 * away from today's month and scrolls back to it, and the today ring follows the state across
 * midnight.
 */
@RunWith(AndroidJUnit4::class)
// Native graphics: the grid's band slot is measured from real text metrics (see MonthGridTest).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp")
class MonthScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 17)
    private val october2026 = IfcYearMonth(2026, IfcMonth.OCTOBER)
    private val june2028 = IfcYearMonth(2028, IfcMonth.JUNE)

    /** Gregorian September 17, 2026 is IFC September 8, 2026, so today's page is IFC September. */
    private val todayPage = MonthPages.pageOf(IfcYearMonth(2026, IfcMonth.SEPTEMBER))

    private fun state(
        month: IfcYearMonth,
        today: LocalDate? = this.today,
    ) = MonthUiState(
        currentPage = MonthPages.pageOf(month),
        today = today,
        todayPage = today?.let { MonthPages.pageOf(IfcYearMonth.from(IfcDate.from(it))) },
        selected = null,
    )

    private fun show(
        state: MonthUiState,
        onPageChanged: (Int) -> Unit = {},
        onDayClick: (IfcDate) -> Unit = {},
        onTitleClick: (Int) -> Unit = {},
        onJumpToDate: (LocalDate) -> Unit = {},
    ) {
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                MonthScreen(
                    state = state,
                    onPageChanged = onPageChanged,
                    onDayClick = onDayClick,
                    onTitleClick = onTitleClick,
                    onJumpToDate = onJumpToDate,
                )
            }
        }
    }

    private fun heading(text: String) =
        compose.onNode(hasText(text).and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)))

    @Test
    fun `opens on the state's page with that month's heading and reports the page`() {
        val pages = mutableListOf<Int>()
        show(state(october2026), onPageChanged = { pages += it })

        heading("October 2026").assertIsDisplayed()
        // The neighbours are composed (kept warm) but off screen.
        heading("September 2026").assertIsNotDisplayed()
        heading("November 2026").assertIsNotDisplayed()
        pages shouldContainExactly listOf(MonthPages.pageOf(october2026))
    }

    @Test
    fun `tapping a cell reports its IFC date`() {
        val clicks = mutableListOf<IfcDate>()
        show(state(october2026), onDayClick = { clicks += it })

        compose.onNode(hasContentDescription("October 5, IFC", substring = true)).performClick()

        clicks shouldContainExactly listOf(IfcDate.Regular(2026, IfcMonth.OCTOBER, 5))
    }

    @Test
    fun `tapping the Leap Day band on June 2028 reports Leap Day`() {
        val clicks = mutableListOf<IfcDate>()
        show(state(june2028), onDayClick = { clicks += it })

        compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND).assertIsDisplayed().performClick()

        clicks shouldContainExactly listOf(IfcDate.LeapDay(2028))
    }

    @Test
    fun `tapping the Year Day band on December 2026 reports Year Day`() {
        val clicks = mutableListOf<IfcDate>()
        show(state(IfcYearMonth(2026, IfcMonth.DECEMBER)), onDayClick = { clicks += it })

        compose.onNodeWithTag(MonthGridTestTags.INTERCALARY_BAND).performClick()

        clicks shouldContainExactly listOf(IfcDate.YearDay(2026))
    }

    @Test
    fun `the Today action is absent on today's month`() {
        show(state(IfcYearMonth(2026, IfcMonth.SEPTEMBER)))

        compose.onAllNodesWithText("Today").assertCountEquals(0)
    }

    @Test
    fun `the Today action is absent before the first date tick`() {
        show(state(october2026, today = null))

        compose.onAllNodesWithText("Today").assertCountEquals(0)
    }

    @Test
    fun `the Today action on another month scrolls back to today's month`() {
        val pages = mutableListOf<Int>()
        show(state(june2028), onPageChanged = { pages += it })

        compose.onNodeWithText("Today").assertIsDisplayed().performClick()
        compose.waitForIdle()

        heading("September 2026").assertIsDisplayed()
        pages.last() shouldBe todayPage
        compose.onAllNodesWithText("Today").assertCountEquals(0)
    }

    @Test
    fun `the today ring marks today's cell and moves with the state across midnight`() {
        var state by mutableStateOf(state(IfcYearMonth(2026, IfcMonth.SEPTEMBER)))
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                MonthScreen(state = state, onPageChanged = {}, onDayClick = {})
            }
        }
        val ring = hasTestTag(MonthGridTestTags.TODAY_RING)

        fun cell(description: String) =
            compose.onNode(hasContentDescription(description, substring = true), useUnmergedTree = true)

        compose.onAllNodesWithTag(MonthGridTestTags.TODAY_RING, useUnmergedTree = true).assertCountEquals(1)
        cell("September 8, IFC").assert(hasAnyDescendant(ring))
        cell("September 8, IFC").assert(hasContentDescription("Today.", substring = true))

        state = state.copy(today = today.plusDays(1))

        compose.onAllNodesWithTag(MonthGridTestTags.TODAY_RING, useUnmergedTree = true).assertCountEquals(1)
        cell("September 8, IFC").assert(!hasContentDescription("Today.", substring = true))
        cell("September 9, IFC").assert(hasAnyDescendant(ring))
        cell("September 9, IFC").assert(hasContentDescription("Today.", substring = true))
    }

    @Test
    fun `holidays of the shown month reach the grid`() {
        val december = IfcYearMonth(2026, IfcMonth.DECEMBER)
        val christmas = LocalDate.of(2026, 12, 25)
        show(state(december).copy(holidaysByMonth = mapOf(december to mapOf(christmas to "Christmas Day"))))

        compose
            .onNode(hasContentDescription("December 23, IFC", substring = true))
            .assertIsDisplayed()
            .assert(hasContentDescription("Holiday: Christmas Day.", substring = true))
    }

    // FEATURES C4: event dots, never colour alone, the spoken count included.

    @Test
    fun `event counts of the shown month reach the grid as dots and a spoken count`() {
        // IFC October 5, 2026 is Gregorian October 12, 2026.
        val onScreen = LocalDate.of(2026, 10, 12)
        show(state(october2026).copy(eventCountsByMonth = mapOf(october2026 to mapOf(onScreen to 2))))

        compose
            .onNode(hasContentDescription("October 5, IFC", substring = true))
            .assertIsDisplayed()
            .assert(hasContentDescription("2 events.", substring = true))
        compose
            .onAllNodesWithTag(MonthGridTestTags.EVENT_DOT, useUnmergedTree = true)
            .assertCountEquals(2)
    }

    // docs/ROADMAP.md M3 T2: tapping the month title zooms out to the Year view.

    @Test
    fun `tapping the month title reports the visible page's year`() {
        var clickedYear: Int? = null
        show(state(october2026), onTitleClick = { clickedYear = it })

        compose.onNode(hasText("October 2026").and(hasClickAction())).performClick()

        clickedYear shouldBe 2026
    }

    // FEATURES C7: jump to date, either calendar — the picker opens pre-selected on state.today, so
    // confirming immediately round-trips that exact date, the same technique ConverterScreenTest uses.

    @Test
    fun `jump to date via Gregorian reports the pre-selected date`() {
        val jumped = mutableListOf<LocalDate>()
        show(state(october2026, today = LocalDate.of(2026, 9, 17)), onJumpToDate = { jumped += it })

        compose.onNodeWithContentDescription("Jump to date").performClick()
        compose.onNodeWithText("Pick a Gregorian date").performClick()
        compose.onNodeWithText("OK").performClick()

        jumped shouldBe listOf(LocalDate.of(2026, 9, 17))
    }

    @Test
    fun `jump to date via IFC reports the pre-selected date for a regular day`() {
        val jumped = mutableListOf<LocalDate>()
        // Gregorian September 17, 2026 is IFC September 8, 2026 (spec §4.1 worked example).
        show(state(october2026, today = LocalDate.of(2026, 9, 17)), onJumpToDate = { jumped += it })

        compose.onNodeWithContentDescription("Jump to date").performClick()
        compose.onNodeWithText("Pick an IFC date").performClick()
        compose.onNodeWithText("Jump").performClick()

        jumped shouldBe listOf(LocalDate.of(2026, 9, 17))
    }

    @Test
    fun `jump to date via IFC reports Year Day`() {
        val jumped = mutableListOf<LocalDate>()
        show(state(october2026, today = LocalDate.of(2026, 12, 31)), onJumpToDate = { jumped += it })

        compose.onNodeWithContentDescription("Jump to date").performClick()
        compose.onNodeWithText("Pick an IFC date").performClick()
        compose.onNodeWithText("Jump").performClick()

        jumped shouldBe listOf(LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `jump to date via IFC reports Leap Day`() {
        val jumped = mutableListOf<LocalDate>()
        show(state(october2026, today = LocalDate.of(2028, 6, 17)), onJumpToDate = { jumped += it })

        compose.onNodeWithContentDescription("Jump to date").performClick()
        compose.onNodeWithText("Pick an IFC date").performClick()
        compose.onNodeWithText("Jump").performClick()

        jumped shouldBe listOf(LocalDate.of(2028, 6, 17))
    }

    @Test
    fun `the jump-to-date chooser can be cancelled without invoking the callback`() {
        var called = false
        show(state(october2026), onJumpToDate = { called = true })

        compose.onNodeWithContentDescription("Jump to date").performClick()
        compose.onNodeWithText("Cancel").performClick()

        compose.onAllNodesWithText("Choose a calendar").assertCountEquals(0)
        called shouldBe false
    }
}
