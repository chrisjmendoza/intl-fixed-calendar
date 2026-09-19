package io.github.chrisjmendoza.yearal.core.designsystem.calendar

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * [YearMiniMonthTile] under Robolectric, written from `docs/calendar-spec.md` §2.2, §7.2 and
 * docs/ARCHITECTURE.md §4 ("Intercalary days in a 7-column grid", "Accessibility"): the merged
 * spoken description (month, Leap Day note, event count, today), the tile's own test tag, the click
 * callback, and that the whole tile — Leap Day's inline indicator included — stays a single
 * clickable node (FEATURES C6's TalkBack requirement).
 */
@RunWith(AndroidJUnit4::class)
// Native graphics: the tile's Canvas grid draws real shapes, like MonthGridTest's band measurement.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w200dp-h300dp")
class YearOverviewTilesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        month: IfcYearMonth,
        today: LocalDate? = null,
        eventDates: Set<LocalDate> = emptySet(),
        onClick: () -> Unit = {},
    ) {
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                YearMiniMonthTile(month = month, today = today, eventDates = eventDates, onClick = onClick)
            }
        }
    }

    private fun tile() = compose.onNodeWithTag(YearOverviewTestTags.MINI_MONTH_TILE_PREFIX + IfcMonth.OCTOBER.number)

    @Test
    fun `a plain month tile only speaks its title`() {
        show(IfcYearMonth(2026, IfcMonth.OCTOBER))

        tile().assert(hasContentDescription("October 2026"))
    }

    @Test
    fun `June in a common year does not mention Leap Day`() {
        val tag = YearOverviewTestTags.MINI_MONTH_TILE_PREFIX + IfcMonth.JUNE.number
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                YearMiniMonthTile(
                    month = IfcYearMonth(2027, IfcMonth.JUNE),
                    today = null,
                    eventDates = emptySet(),
                    onClick = {},
                )
            }
        }

        compose.onNodeWithTag(tag).assert(hasContentDescription("June 2027"))
    }

    @Test
    fun `June in a leap year mentions Leap Day in the merged description`() {
        val tag = YearOverviewTestTags.MINI_MONTH_TILE_PREFIX + IfcMonth.JUNE.number
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                YearMiniMonthTile(
                    month = IfcYearMonth(2028, IfcMonth.JUNE),
                    today = null,
                    eventDates = emptySet(),
                    onClick = {},
                )
            }
        }

        compose.onNodeWithTag(tag).assert(hasContentDescription("June 2028 Includes Leap Day."))
    }

    @Test
    fun `an event day is counted in the description`() {
        // IFC October 5, 2026 is Gregorian October 12, 2026.
        show(IfcYearMonth(2026, IfcMonth.OCTOBER), eventDates = setOf(LocalDate.of(2026, 10, 12)))

        tile().assert(hasContentDescription("October 2026 1 day with events."))
    }

    @Test
    fun `two event days are counted with the plural form`() {
        show(
            IfcYearMonth(2026, IfcMonth.OCTOBER),
            eventDates = setOf(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13)),
        )

        tile().assert(hasContentDescription("October 2026 2 days with events."))
    }

    @Test
    fun `today inside the tile is named by its IFC day`() {
        // Gregorian October 12, 2026 is IFC October 5, 2026.
        show(IfcYearMonth(2026, IfcMonth.OCTOBER), today = LocalDate.of(2026, 10, 12))

        tile().assert(hasContentDescription("October 2026 Today is October 5."))
    }

    @Test
    fun `today elsewhere marks nothing on this tile`() {
        show(IfcYearMonth(2026, IfcMonth.OCTOBER), today = LocalDate.of(2026, 9, 17))

        tile().assert(hasContentDescription("October 2026"))
    }

    @Test
    fun `Leap Day as today is named and mentioned once`() {
        val tag = YearOverviewTestTags.MINI_MONTH_TILE_PREFIX + IfcMonth.JUNE.number
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                YearMiniMonthTile(
                    month = IfcYearMonth(2028, IfcMonth.JUNE),
                    today = LocalDate.of(2028, 6, 17),
                    eventDates = emptySet(),
                    onClick = {},
                )
            }
        }

        compose.onNodeWithTag(tag).assert(hasContentDescription("June 2028 Includes Leap Day. Today is Leap Day."))
    }

    @Test
    fun `tapping the tile invokes onClick`() {
        var clicks = 0
        show(IfcYearMonth(2026, IfcMonth.OCTOBER), onClick = { clicks++ })

        tile().performClick()

        clicks shouldBe 1
    }

    @Test
    fun `a leap year tile with the Leap Day indicator is still one clickable node`() {
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                YearMiniMonthTile(
                    month = IfcYearMonth(2028, IfcMonth.JUNE),
                    today = null,
                    eventDates = emptySet(),
                    onClick = {},
                )
            }
        }

        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    }
}
