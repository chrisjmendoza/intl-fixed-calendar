package io.github.chrisjmendoza.yearal.feature.events.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [EventListScreen] under Robolectric (`docs/ROADMAP.md` M4 T5): both dates on a row (IFC long form,
 * the canonical numeric form with its mandatory `IFC` prefix — CLAUDE.md rule 5 — and the Gregorian
 * date), the recurrence summary, search, the FAB and row taps report through their callbacks with ids
 * only (CLAUDE.md rule 8), the two empty states, and 200% font scale with 48dp targets
 * (`docs/ARCHITECTURE.md` §4 "Accessibility").
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp")
class EventListScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val queries = mutableListOf<String>()
    private val opened = mutableListOf<Long>()
    private var added = 0

    private var uiState: EventListUiState by mutableStateOf(EventListUiState.Loading)

    /** Sets the initial state and composes the screen once; later state changes assign [uiState] directly. */
    private fun show(
        state: EventListUiState,
        fontScale: Float = 1f,
    ) {
        uiState = state
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IfcTheme(dynamicColor = false) {
                    EventListScreen(
                        state = uiState,
                        onQueryChange = { queries += it },
                        onAddEvent = { added++ },
                        onOpenEvent = { opened += it },
                    )
                }
            }
        }
    }

    private val sol13 =
        EventListItem(
            eventId = 42,
            title = "Sol 13 picnic",
            calendarName = "",
            calendarColorArgb = EventCalendar.DEFAULT_COLOR_ARGB,
            calendarHidden = false,
            ifcLong = "Sol 13, 2026",
            ifcNumeric = "IFC 2026-07-13",
            gregorianLong = "Tuesday, June 30, 2026",
            isAllDay = true,
            timeLabel = null,
            zoneLabel = null,
            recurrenceSummary = RecurrenceSummary.YearlyIfc("Sol 13"),
        )

    private val hiddenTimed =
        EventListItem(
            eventId = 7,
            title = "",
            calendarName = "Work",
            calendarColorArgb = 0xFF5B8DEF.toInt(),
            calendarHidden = true,
            ifcLong = "Year Day, 2026",
            ifcNumeric = "IFC 2026-13-29",
            gregorianLong = "Thursday, December 31, 2026",
            isAllDay = false,
            timeLabel = "9:30 AM",
            zoneLabel = "America/New_York",
            recurrenceSummary = null,
        )

    @Test
    fun `a row shows both dates, the IFC prefix, the time and the recurrence summary`() {
        show(EventListUiState.Loaded(items = listOf(sol13), query = "", hasAnyEvents = true))

        compose.onNodeWithText("Sol 13 picnic").assertIsDisplayed()
        compose.onNodeWithText("Sol 13, 2026").assertIsDisplayed()
        compose.onNodeWithText("IFC 2026-07-13").assertIsDisplayed()
        compose.onNodeWithText("Tuesday, June 30, 2026").assertIsDisplayed()
        compose.onNodeWithText("Every Sol 13").assertIsDisplayed()
        compose.onNodeWithText("All day").assertIsDisplayed()
    }

    @Test
    fun `a blank title shows the placeholder, a hidden calendar is flagged, and the numeric form is prefixed`() {
        show(EventListUiState.Loaded(items = listOf(hiddenTimed), query = "", hasAnyEvents = true))

        compose.onNodeWithText("(No title)").assertIsDisplayed()
        compose.onNodeWithText("Hidden calendar").assertIsDisplayed()
        compose.onNodeWithText("IFC 2026-13-29").assertIsDisplayed()
        compose.onNodeWithText("Thursday, December 31, 2026").assertIsDisplayed()
        compose.onNodeWithText("9:30 AM (America/New_York)").assertIsDisplayed()
        // Rule 5: never a locale-style numeric date for the IFC form.
        compose.onAllNodesWithText("13/29/2026", substring = true).assertCountEquals(0)
    }

    @Test
    fun `every Leap Day policy has its own summary text`() {
        val base = sol13.copy(eventId = 1)
        show(EventListUiState.Loaded(items = listOf(base), query = "", hasAnyEvents = true))
        for (
        (policy, expected) in
        mapOf(
            LeapDayPolicy.JUNE_28 to "Every Leap Day — June 28 in common years",
            LeapDayPolicy.SKIP to "Every Leap Day — leap years only",
            LeapDayPolicy.SOL_1 to "Every Leap Day — Sol 1 in common years",
        )
        ) {
            uiState =
                EventListUiState.Loaded(
                    items = listOf(base.copy(recurrenceSummary = RecurrenceSummary.YearlyLeapDay(policy))),
                    query = "",
                    hasAnyEvents = true,
                )
            compose.onNodeWithText(expected).assertIsDisplayed()
        }
    }

    @Test
    fun `typing in the search field reports through the callback`() {
        show(EventListUiState.Loaded(items = listOf(sol13), query = "", hasAnyEvents = true))

        compose.onNode(hasText("Search events")).performTextInput("picnic")

        queries shouldContainExactly listOf("picnic")
    }

    @Test
    fun `tapping the FAB and a row report through their callbacks, ids only`() {
        show(EventListUiState.Loaded(items = listOf(sol13, hiddenTimed), query = "", hasAnyEvents = true))

        compose.onNodeWithContentDescription("Add event").performClick()
        added shouldBe 1

        compose.onNodeWithText("Sol 13 picnic").performClick()

        opened shouldContainExactly listOf(42L)
    }

    @Test
    fun `the empty state differs for no events at all and for no search matches`() {
        show(EventListUiState.Loaded(items = emptyList(), query = "", hasAnyEvents = false))
        compose.onNodeWithText("No events yet. Tap + to create one.").assertIsDisplayed()

        uiState = EventListUiState.Loaded(items = emptyList(), query = "xyz", hasAnyEvents = true)
        compose.onNodeWithText("No events match your search.").assertIsDisplayed()
    }

    @Test
    fun `the loading state shows no rows and no empty-state text`() {
        show(EventListUiState.Loading)

        compose.onAllNodesWithText("No events", substring = true).assertCountEquals(0)
    }

    // docs/ARCHITECTURE.md §4 "Accessibility": 200% font scale, 48dp targets.
    @Test
    fun `at 200 percent font scale the row and the FAB keep 48dp and no label is cut`() {
        show(EventListUiState.Loaded(items = listOf(sol13), query = "", hasAnyEvents = true), fontScale = 2f)

        compose.onNodeWithContentDescription("Add event").assertHeightIsAtLeast(48.dp)
        val row = compose.onNodeWithText("Sol 13 picnic").assertHasClickAction()
        row.assertHeightIsAtLeast(48.dp)
        for (line in listOf("Sol 13, 2026", "IFC 2026-07-13", "Tuesday, June 30, 2026")) {
            compose.onNodeWithText(line).textLayout().isCut() shouldBe false
        }
    }

    // See feature:converter's ConverterScreenTest: hasVisualOverflow is unusable on the semantics result.
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
}
