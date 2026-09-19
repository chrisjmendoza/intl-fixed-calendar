package io.github.chrisjmendoza.yearal.feature.calendar.day

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
 * [DayScreen] under Robolectric: the sheet shows the IFC date as a heading, the numeric form with
 * its `IFC` marker, the Gregorian date, both labelled weekday lines (spec §4.1) with "no IFC
 * weekday" on intercalary days, day/week/quarter, the holiday names, the "Today" badge only on
 * today, and the close button invokes the dismiss callback.
 */
@RunWith(AndroidJUnit4::class)
// A tall window so the whole sheet, holidays included, is on screen; the content scrolls otherwise.
@Config(qualifiers = "w360dp-h900dp")
class DayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val formatter =
        IfcDateFormatter(ApplicationProvider.getApplicationContext<Context>().resources, Locale.US)

    private fun show(
        day: LocalDate,
        today: LocalDate = LocalDate.of(2026, 9, 17),
        holidays: List<String> = emptyList(),
        agenda: List<AgendaItemUi> = emptyList(),
        pendingDelete: AgendaItemUi? = null,
        onDismiss: () -> Unit = {},
        onEventClick: (Long) -> Unit = {},
        onAddEvent: () -> Unit = {},
        onOpenInConverter: () -> Unit = {},
        onRequestDelete: (AgendaItemUi) -> Unit = {},
        onConfirmDelete: () -> Unit = {},
        onCancelDelete: () -> Unit = {},
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IfcTheme(dynamicColor = false) {
                    DayScreen(
                        state = buildDayUiState(day, today, formatter, holidays, agenda, pendingDelete),
                        onDismiss = onDismiss,
                        onEventClick = onEventClick,
                        onAddEvent = onAddEvent,
                        onOpenInConverter = onOpenInConverter,
                        onRequestDelete = onRequestDelete,
                        onConfirmDelete = onConfirmDelete,
                        onCancelDelete = onCancelDelete,
                    )
                }
            }
        }
    }

    @Test
    fun `a regular day shows both dates, both weekday lines and day-week-quarter`() {
        show(LocalDate.of(2026, 9, 17))

        compose
            .onNode(hasText("September 8, 2026").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)))
            .assertIsDisplayed()
        compose.onNodeWithText("IFC 2026-10-08").assertIsDisplayed()
        compose.onNodeWithText("Gregorian: Thursday, September 17, 2026").assertIsDisplayed()
        compose.onNodeWithText("IFC weekday: Sunday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Thursday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("IFC Sunday, actual Thursday").assertIsDisplayed()
        compose.onNodeWithText("Day 260 · Week 38 of 52 · Q3").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onAllNodesWithText("Holidays").assertCountEquals(0)
    }

    @Test
    fun `Year Day shows no IFC weekday and its actual weekday`() {
        show(LocalDate.of(2026, 12, 31), holidays = listOf("Year Day", "New Year's Eve"))

        compose.onNodeWithText("Year Day, 2026").assertIsDisplayed()
        compose.onNodeWithText("IFC 2026-13-29").assertIsDisplayed()
        compose.onNodeWithText("no IFC weekday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Thursday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("no IFC weekday, actual Thursday").assertIsDisplayed()
        compose.onNodeWithText("Day 365 · outside the weeks · Q4").assertIsDisplayed()
        compose.onNodeWithText("Holidays").assertIsDisplayed()
        compose.onNodeWithText("Year Day").assertIsDisplayed()
        compose.onNodeWithText("New Year's Eve").assertIsDisplayed()
        compose.onAllNodesWithText("Today").assertCountEquals(0)
    }

    @Test
    fun `Leap Day shows no IFC weekday and can be today`() {
        show(LocalDate.of(2028, 6, 17), today = LocalDate.of(2028, 6, 17), holidays = listOf("Leap Day"))

        compose.onNodeWithText("Leap Day, 2028").assertIsDisplayed()
        compose.onNodeWithText("IFC 2028-06-29").assertIsDisplayed()
        compose.onNodeWithText("no IFC weekday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Actual weekday: Saturday", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Day 169 · outside the weeks · Q2").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithText("Leap Day").assertIsDisplayed()
    }

    @Test
    fun `an observed holiday is rendered with its label`() {
        show(LocalDate.of(2026, 7, 3), holidays = listOf("Independence Day (observed)"))

        compose.onNodeWithText("Sol 16, 2026").assertIsDisplayed()
        compose.onNodeWithText("Independence Day (observed)").assertIsDisplayed()
    }

    @Test
    fun `the close button invokes the dismiss callback`() {
        var dismissed = 0
        show(LocalDate.of(2026, 9, 17), onDismiss = { dismissed++ })

        compose.onNodeWithContentDescription("Close").performClick()

        dismissed shouldBe 1
    }

    // FEATURES C5: the day's agenda, all-day first then by start time, tap navigates by id only.

    @Test
    fun `the day's agenda shows all-day and timed entries, and tapping one reports its id`() {
        val agenda =
            listOf(
                AgendaItemUi(
                    eventId = 1,
                    title = "Conference",
                    isAllDay = true,
                    startTime = null,
                    endTime = null,
                    colorArgb = 0xFF123F3D.toInt(),
                ),
                AgendaItemUi(
                    eventId = 2,
                    title = "Standup",
                    isAllDay = false,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(9, 30),
                    colorArgb = 0xFF123F3D.toInt(),
                ),
            )
        val clicked = mutableListOf<Long>()
        show(LocalDate.of(2026, 9, 17), agenda = agenda, onEventClick = { clicked += it })

        compose.onNodeWithText("Events").assertIsDisplayed()
        compose.onNodeWithText("Conference").assertIsDisplayed()
        compose.onNodeWithText("All day").assertIsDisplayed()
        compose.onNodeWithText("Standup").performClick()

        clicked shouldBe listOf(2L)
    }

    @Test
    fun `a blank title shows the untitled placeholder`() {
        val agenda =
            listOf(
                AgendaItemUi(
                    eventId = 1,
                    title = "",
                    isAllDay = true,
                    startTime = null,
                    endTime = null,
                    colorArgb = 0xFF123F3D.toInt(),
                ),
            )
        show(LocalDate.of(2026, 9, 17), agenda = agenda)

        compose.onNodeWithText("(No title)").assertIsDisplayed()
    }

    @Test
    fun `the Add event action invokes its callback`() {
        var added = 0
        show(LocalDate.of(2026, 9, 17), onAddEvent = { added++ })

        compose.onNodeWithText("Add event").performClick()

        added shouldBe 1
    }

    // FEATURES D1: "Open in converter" (docs/ROADMAP.md M3 T5).

    @Test
    fun `the Open in converter action invokes its callback`() {
        var opened = 0
        show(LocalDate.of(2026, 9, 17), onOpenInConverter = { opened++ })

        compose.onNodeWithText("Open in converter").performClick()

        opened shouldBe 1
    }

    // docs/ARCHITECTURE.md §4 "Accessibility": 200% font scale, 48dp touch targets.
    @Test
    fun `at 200 percent font scale the agenda row and Add event keep their touch target`() {
        val agenda =
            listOf(
                AgendaItemUi(
                    eventId = 1,
                    title = "Conference",
                    isAllDay = true,
                    startTime = null,
                    endTime = null,
                    colorArgb = 0xFF123F3D.toInt(),
                ),
            )
        show(LocalDate.of(2026, 9, 17), agenda = agenda, fontScale = 2f)

        compose.onNodeWithText("Conference").assertIsDisplayed()
        compose.onNodeWithText("Add event").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `the loading state shows only the spinner`() {
        compose.setContent {
            IfcTheme(dynamicColor = false) { DayScreen(state = DayUiState.Loading, onDismiss = {}) }
        }

        compose.onNodeWithContentDescription("Loading the day").assertIsDisplayed()
        compose.onAllNodesWithText("Gregorian: ", substring = true).assertCountEquals(0)
    }

    // A DayKey can be synthesized from a widget or notification intent with an out-of-range epoch day
    // (docs/security-and-privacy.md §6.3); DayViewModel fails soft to DayUiState.Unavailable.
    @Test
    fun `the unavailable state shows a message and an explicit close action`() {
        var dismissed = 0
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                DayScreen(state = DayUiState.Unavailable, onDismiss = { dismissed++ })
            }
        }

        compose.onNodeWithText("Date not available").assertIsDisplayed()
        compose.onNodeWithText("This date can't be shown.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        dismissed shouldBe 1
    }

    // FEATURES E1: "delete this occurrence" and plain delete, with a TalkBack-reachable action.

    private val recurringItem =
        AgendaItemUi(
            eventId = 5,
            title = "Sol 13 picnic",
            isAllDay = true,
            startTime = null,
            endTime = null,
            colorArgb = 0xFF123F3D.toInt(),
            isRecurring = true,
            occurrenceDate = LocalDate.of(2026, 6, 30),
        )

    private val oneOffItem =
        AgendaItemUi(
            eventId = 6,
            title = "Once",
            isAllDay = true,
            startTime = null,
            endTime = null,
            colorArgb = 0xFF123F3D.toInt(),
            isRecurring = false,
            occurrenceDate = LocalDate.of(2026, 9, 17),
        )

    @Test
    fun `long-pressing a recurring row requests its delete`() {
        val requested = mutableListOf<AgendaItemUi>()
        show(LocalDate.of(2026, 9, 17), agenda = listOf(recurringItem), onRequestDelete = { requested += it })

        compose.onNodeWithText("Sol 13 picnic").performTouchInput { longClick() }

        requested shouldBe listOf(recurringItem)
    }

    @Test
    fun `a row's TalkBack custom action requests the same delete as long-press`() {
        val requested = mutableListOf<AgendaItemUi>()
        show(LocalDate.of(2026, 9, 17), agenda = listOf(oneOffItem), onRequestDelete = { requested += it })

        val actionLabel = "Delete event"
        compose
            .onNodeWithText("Once")
            .performSemanticsAction(actionLabel)

        requested shouldBe listOf(oneOffItem)
    }

    @Test
    fun `a recurring row's confirmation offers delete-this-occurrence wording`() {
        var confirmed = 0
        var cancelled = 0
        show(
            LocalDate.of(2026, 9, 17),
            agenda = listOf(recurringItem),
            pendingDelete = recurringItem,
            onConfirmDelete = { confirmed++ },
            onCancelDelete = { cancelled++ },
        )

        compose.onNodeWithText("Delete this occurrence?").assertIsDisplayed()
        compose.onNodeWithText("Delete this occurrence").performClick()
        confirmed shouldBe 1
        cancelled shouldBe 0
    }

    @Test
    fun `a non-recurring row's confirmation offers a plain, permanent delete`() {
        var confirmed = 0
        show(
            LocalDate.of(2026, 9, 17),
            agenda = listOf(oneOffItem),
            pendingDelete = oneOffItem,
            onConfirmDelete = { confirmed++ },
        )

        compose.onNodeWithText("Delete event?").assertIsDisplayed()
        compose.onNodeWithText("This deletes the whole event. This can't be undone.").assertIsDisplayed()
        compose.onNodeWithText("Delete event").performClick()
        confirmed shouldBe 1
    }

    @Test
    fun `cancelling the delete confirmation invokes onCancelDelete, not onConfirmDelete`() {
        var confirmed = 0
        var cancelled = 0
        show(
            LocalDate.of(2026, 9, 17),
            agenda = listOf(recurringItem),
            pendingDelete = recurringItem,
            onConfirmDelete = { confirmed++ },
            onCancelDelete = { cancelled++ },
        )

        compose.onNodeWithText("Cancel").performClick()
        confirmed shouldBe 0
        cancelled shouldBe 1
    }

    /** Invokes a semantics [CustomAccessibilityAction] by its label — the TalkBack local-context-menu path. */
    private fun SemanticsNodeInteraction.performSemanticsAction(label: String) {
        val node = fetchSemanticsNode()
        val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        val action = actions.first { it.label == label }
        action.action.invoke()
    }
}
