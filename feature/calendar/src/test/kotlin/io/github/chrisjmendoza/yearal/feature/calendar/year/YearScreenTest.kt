package io.github.chrisjmendoza.yearal.feature.calendar.year

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.calendar.YearOverviewTestTags
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * [YearScreen] under Robolectric, written from `docs/calendar-spec.md` §2.2, §7.2 and docs/FEATURES.md
 * C6, C7: the 13 mini-months in calendar order with Sol between June and July, the Year Day tile as
 * the grid's 14th item, tile taps reporting the right [IfcYearMonth] (or, for Year Day, opening
 * December), the previous/next/Today year actions, and exactly one TalkBack node per tile — never one
 * per day cell.
 */
@RunWith(AndroidJUnit4::class)
// Native graphics: YearMiniMonthTile's Canvas grid needs real measurement like MonthGridTest's band.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h3600dp")
class YearScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        state: YearUiState,
        onPreviousYear: () -> Unit = {},
        onNextYear: () -> Unit = {},
        onGoToYear: (Int) -> Unit = {},
        onMonthClick: (IfcYearMonth) -> Unit = {},
        onYearDayClick: () -> Unit = {},
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IfcTheme(dynamicColor = false) {
                    YearScreen(
                        state = state,
                        onPreviousYear = onPreviousYear,
                        onNextYear = onNextYear,
                        onGoToYear = onGoToYear,
                        onMonthClick = onMonthClick,
                        onYearDayClick = onYearDayClick,
                    )
                }
            }
        }
    }

    private fun tileTag(monthNumber: Int) = YearOverviewTestTags.MINI_MONTH_TILE_PREFIX + monthNumber

    // spec §2.2: 13 months, Sol between June (6) and July (8).

    @Test
    fun `all 13 month tiles are present, Sol between June and July`() {
        show(YearUiState(year = 2026, today = null))

        for (number in 1..13) {
            compose.onNodeWithTag(tileTag(number)).assertIsDisplayed()
        }
        compose.onNode(hasTestTag(tileTag(IfcMonth.JUNE.number)).and(hasText("June"))).assertIsDisplayed()
        compose.onNode(hasTestTag(tileTag(IfcMonth.SOL.number)).and(hasText("Sol"))).assertIsDisplayed()
        compose.onNode(hasTestTag(tileTag(IfcMonth.JULY.number)).and(hasText("July"))).assertIsDisplayed()
    }

    // FEATURES C6: tapping a tile reports the right month; Year Day belongs to December.

    @Test
    fun `tapping a mini-month reports its IfcYearMonth`() {
        val clicked = mutableListOf<IfcYearMonth>()
        show(YearUiState(year = 2026, today = null), onMonthClick = { clicked += it })

        compose.onNodeWithTag(tileTag(IfcMonth.SEPTEMBER.number)).performClick()

        clicked shouldBe listOf(IfcYearMonth(2026, IfcMonth.SEPTEMBER))
    }

    @Test
    fun `tapping the Year Day tile invokes onYearDayClick`() {
        var clicks = 0
        show(YearUiState(year = 2026, today = null), onYearDayClick = { clicks++ })

        compose.onNodeWithTag(YEAR_DAY_TILE_TEST_TAG).performClick()

        clicks shouldBe 1
    }

    // TalkBack: one node per tile (14 total), never one per day cell (would be 364).

    @Test
    fun `the grid exposes exactly 14 clickable nodes, not one per day cell`() {
        show(YearUiState(year = 2026, today = null))

        compose
            .onAllNodes(hasClickAction().and(hasAnyAncestor(hasTestTag(YEAR_GRID_TEST_TAG))))
            .assertCountEquals(14)
    }

    @Test
    fun `a leap year still exposes exactly 14 clickable nodes despite June's Leap Day indicator`() {
        show(YearUiState(year = 2028, today = null))

        compose
            .onAllNodes(hasClickAction().and(hasAnyAncestor(hasTestTag(YEAR_GRID_TEST_TAG))))
            .assertCountEquals(14)
    }

    // Today, events and Leap Day reach the merged description (docs/ARCHITECTURE.md §4 "Accessibility").

    @Test
    fun `June's tile mentions Leap Day only in a leap year`() {
        show(YearUiState(year = 2028, today = null))

        compose
            .onNode(
                hasTestTag(
                    tileTag(IfcMonth.JUNE.number),
                ).and(hasContentDescription("Includes Leap Day.", substring = true)),
            ).assertIsDisplayed()
    }

    @Test
    fun `a common year's June tile does not mention Leap Day`() {
        show(YearUiState(year = 2026, today = null))

        compose.onAllNodesWithText("Leap Day", substring = true).assertCountEquals(0)
    }

    @Test
    fun `an event day and today are both spoken in a tile's description`() {
        // Gregorian September 17, 2026 is IFC September 8, 2026 (spec §4.1 worked example): the
        // spoken "today" name uses the IFC day of month, not the Gregorian one.
        val today = LocalDate.of(2026, 9, 17)
        show(YearUiState(year = 2026, today = today, eventDates = setOf(today)))

        val septemberTile = hasTestTag(tileTag(IfcMonth.SEPTEMBER.number))
        compose
            .onNode(septemberTile.and(hasContentDescription("1 day with events.", substring = true)))
            .assertIsDisplayed()
        compose
            .onNode(septemberTile.and(hasContentDescription("Today is September 8.", substring = true)))
            .assertIsDisplayed()
    }

    // FEATURES C7: previous/next year and the Today action.

    @Test
    fun `previous and next year invoke their callbacks`() {
        var previous = 0
        var next = 0
        show(YearUiState(year = 2026, today = null), onPreviousYear = { previous++ }, onNextYear = { next++ })

        compose.onNodeWithContentDescription("Previous year").performClick()
        compose.onNodeWithContentDescription("Next year").performClick()

        previous shouldBe 1
        next shouldBe 1
    }

    @Test
    fun `previous year is disabled at the minimum year`() {
        show(YearUiState(year = 1583, today = null))
        compose.onNodeWithContentDescription("Previous year").assertIsNotEnabled()
    }

    @Test
    fun `next year is disabled at the maximum year`() {
        show(YearUiState(year = 9999, today = null))
        compose.onNodeWithContentDescription("Next year").assertIsNotEnabled()
    }

    @Test
    fun `the Today action is hidden while the shown year already is today's year`() {
        show(YearUiState(year = 2026, today = LocalDate.of(2026, 9, 17)))

        compose.onAllNodesWithText("Today").assertCountEquals(0)
    }

    @Test
    fun `the Today action jumps to today's year`() {
        var jumpedTo: Int? = null
        show(YearUiState(year = 2020, today = LocalDate.of(2026, 9, 17)), onGoToYear = { jumpedTo = it })

        compose.onNodeWithText("Today").performClick()

        jumpedTo shouldBe 2026
    }

    // docs/ARCHITECTURE.md §4 "Accessibility": no clipping at 200% font scale.

    @Test
    fun `at 200 percent font scale every tile still renders`() {
        show(YearUiState(year = 2026, today = null), fontScale = 2f)

        for (number in 1..13) {
            compose.onNodeWithTag(tileTag(number)).assertIsDisplayed()
        }
        compose.onNodeWithTag(YEAR_DAY_TILE_TEST_TAG).assertIsDisplayed()
    }
}
