package io.github.chrisjmendoza.yearal.feature.calendar.today

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.AgendaItemUi
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * [TodayScreen] under Robolectric: the hero date, the numeric form with its `IFC` marker, the two
 * labelled weekday lines (spec §4.1) and the intercalary "no IFC weekday" text are on screen, and the
 * weekday block speaks both weekdays in one description (§4.1 item 7).
 */
@RunWith(AndroidJUnit4::class)
// A tall window so the whole screen, agenda and holidays included, is on screen without scrolling.
@Config(qualifiers = "w360dp-h1200dp")
class TodayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val formatter =
        IfcDateFormatter(ApplicationProvider.getApplicationContext<Context>().resources, Locale.US)

    private fun show(
        today: LocalDate,
        holidays: List<String> = emptyList(),
        nextHoliday: Pair<LocalDate, String>? = null,
        agenda: List<AgendaItemUi> = emptyList(),
        onAgendaItemClick: (Long) -> Unit = {},
    ) {
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                TodayScreen(
                    state = buildTodayUiState(today, formatter, holidays, nextHoliday, agenda),
                    onAgendaItemClick = onAgendaItemClick,
                )
            }
        }
    }

    @Test
    fun `regular day shows the hero, numeric, Gregorian and both weekday lines`() {
        show(LocalDate.of(2026, 9, 17))

        compose.onNodeWithText("September 8, 2026").assertIsDisplayed()
        compose.onNodeWithText("IFC 2026-10-08").assertIsDisplayed()
        compose.onNodeWithText("Gregorian: Thursday, September 17, 2026").assertIsDisplayed()
        compose.onNodeWithText("IFC weekday: Sunday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Thursday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("IFC Sunday, actual Thursday").assertIsDisplayed()
        compose.onNodeWithText("Day 260 · Week 38 of 52 · Q3").assertIsDisplayed()
        compose.onNodeWithContentDescription("71% of the year").assertIsDisplayed()
        compose.onNodeWithText("105 days until Year Day").assertIsDisplayed()
    }

    @Test
    fun `Year Day shows no IFC weekday and its actual weekday`() {
        show(LocalDate.of(2026, 12, 31))

        compose.onNodeWithText("Year Day, 2026").assertIsDisplayed()
        compose.onNodeWithText("IFC 2026-13-29").assertIsDisplayed()
        compose.onNodeWithText("no IFC weekday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Thursday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("no IFC weekday, actual Thursday").assertIsDisplayed()
        compose.onNodeWithText("Day 365 · outside the weeks · Q4").assertIsDisplayed()
    }

    @Test
    fun `Leap Day shows no IFC weekday and counts down to Year Day`() {
        show(LocalDate.of(2028, 6, 17))

        compose.onNodeWithText("Leap Day, 2028").assertIsDisplayed()
        compose.onNodeWithText("IFC 2028-06-29").assertIsDisplayed()
        compose.onNodeWithText("no IFC weekday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Saturday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("197 days until Year Day").assertIsDisplayed()
    }

    @Test
    fun `loading state shows only the spinner`() {
        compose.setContent { IfcTheme(dynamicColor = false) { TodayScreen(state = TodayUiState.Loading) } }

        compose.onNodeWithContentDescription("Loading today’s date").assertIsDisplayed()
    }

    // FEATURES T5: today's agenda summary and the next holiday.

    @Test
    fun `today's holidays, the next holiday and the agenda summary are shown`() {
        show(
            LocalDate.of(2026, 7, 3),
            holidays = listOf("Independence Day (observed)"),
            nextHoliday = LocalDate.of(2026, 7, 4) to "Independence Day",
            agenda =
                listOf(
                    AgendaItemUi(
                        eventId = 1,
                        title = "Picnic",
                        isAllDay = true,
                        startTime = null,
                        endTime = null,
                        colorArgb = 0xFF123F3D.toInt(),
                    ),
                ),
        )

        compose.onNodeWithText("Holidays").assertIsDisplayed()
        compose.onNodeWithText("Independence Day (observed)").assertIsDisplayed()
        compose.onNodeWithText("1 day until Independence Day").assertIsDisplayed()
        compose.onNodeWithText("Today’s events").assertIsDisplayed()
        compose.onNodeWithText("Picnic").assertIsDisplayed()
        compose.onNodeWithText("All day").assertIsDisplayed()
    }

    @Test
    fun `no holidays or agenda means neither section is shown`() {
        show(LocalDate.of(2026, 9, 17))

        compose.onAllNodesWithText("Holidays").assertCountEquals(0)
        compose.onAllNodesWithText("Today’s events").assertCountEquals(0)
    }

    // Left over from M4 T7 (docs/ROADMAP.md): today's agenda rows are tappable, id only (CLAUDE.md rule 8).

    @Test
    fun `tapping an agenda row reports its event id`() {
        val clicked = mutableListOf<Long>()
        show(
            LocalDate.of(2026, 9, 17),
            agenda =
                listOf(
                    AgendaItemUi(
                        eventId = 7,
                        title = "Standup",
                        isAllDay = false,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(9, 30),
                        colorArgb = 0xFF123F3D.toInt(),
                    ),
                ),
            onAgendaItemClick = { clicked += it },
        )

        compose.onNodeWithText("Standup").performClick()

        clicked shouldBe listOf(7L)
    }
}
