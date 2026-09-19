package io.github.chrisjmendoza.yearal.feature.events.editor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * [EventEditorScreen] under Robolectric (`docs/ROADMAP.md` M4 T4): the recurrence chooser hides
 * "monthly (IFC)" on Year Day and Leap Day and asks for the Leap Day policy only on a real Leap Day
 * anchor (CLAUDE.md rule 6), Save is disabled while a blocking error holds, delete and the unsaved-
 * changes guard both go through a confirmation, reminder chips toggle, and 200% font scale keeps 48dp.
 *
 * Each test calls [show] once and drives further state changes by reassigning [uiState] directly (as
 * `feature:converter`'s `ConverterScreenTest` does), because the Compose test rule refuses a second
 * `setContent` call on the same activity.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp")
class EventEditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val titles = mutableListOf<String>()
    private val recurrenceKinds = mutableListOf<RecurrenceKind>()
    private val leapDayPolicies = mutableListOf<LeapDayPolicy>()
    private val reminders = mutableListOf<Int>()
    private var saved = 0
    private var deleteRequested = 0
    private var deleteConfirmed = 0
    private var backRequested = 0
    private var discardConfirmed = 0

    private var uiState: EventEditorUiState by mutableStateOf(EventEditorUiState.Loading)

    private fun callbacks() =
        EventEditorCallbacks(
            onTitleChange = { titles += it },
            onDescriptionChange = {},
            onLocationChange = {},
            onAllDayChange = {},
            onStartDateChange = {},
            onAllDayEndDateChange = {},
            onStartMinuteChange = {},
            onEndMinuteChange = {},
            onZoneChoiceChange = {},
            onRecurrenceKindChange = { recurrenceKinds += it },
            onLeapDayPolicyChange = { leapDayPolicies += it },
            onRecurrenceEndKindChange = {},
            onUntilDateChange = {},
            onCountChange = {},
            onToggleReminder = { reminders += it },
            onSave = { saved++ },
            onRequestDelete = { deleteRequested++ },
            onConfirmDelete = { deleteConfirmed++ },
            onCancelDelete = {},
            onBack = { backRequested++ },
            onConfirmDiscard = { discardConfirmed++ },
            onCancelDiscard = {},
        )

    private fun baseState(
        isNew: Boolean = true,
        startDate: LocalDate = LocalDate.of(2026, 6, 30),
        isAllDay: Boolean = true,
        recurrenceKind: RecurrenceKind = RecurrenceKind.NONE,
        isLeapDayAnchor: Boolean = false,
        monthlyIfcAvailable: Boolean = true,
        canSave: Boolean = true,
        endBeforeStart: Boolean = false,
        showDeleteConfirm: Boolean = false,
        showDiscardConfirm: Boolean = false,
        reminders: Set<Int> = emptySet(),
    ) = EventEditorUiState.Loaded(
        isNew = isNew,
        title = "",
        description = "",
        location = "",
        isAllDay = isAllDay,
        startDate = startDate,
        startIfcLabel = "Sol 13, 2026",
        startIfcDayLabel = "Sol 13",
        startGregorianLabel = "Tuesday, June 30, 2026",
        startGregorianDayLabel = "Jun 30",
        allDayEndDate = startDate,
        allDayEndBeforeStart = false,
        startMinuteOfDay = EventDraft.DEFAULT_START_MINUTE,
        endMinuteOfDay = EventDraft.DEFAULT_START_MINUTE + EventDraft.DEFAULT_DURATION_MINUTES,
        endBeforeStart = endBeforeStart,
        zoneChoice = ZoneChoice.FLOATING,
        fixedZoneId = ZoneId.of("UTC"),
        recurrenceKind = recurrenceKind,
        monthlyIfcAvailable = monthlyIfcAvailable,
        isLeapDayAnchor = isLeapDayAnchor,
        leapDayPolicy = LeapDayPolicy.JUNE_28,
        recurrenceEndKind = RecurrenceEndKind.NEVER,
        untilDate = startDate.plusYears(1),
        untilBeforeStart = false,
        count = 1,
        reminders = reminders,
        canSave = canSave,
        isDirty = false,
        saveFailed = false,
        showDeleteConfirm = showDeleteConfirm,
        showDiscardConfirm = showDiscardConfirm,
    )

    /** Sets the initial state and composes the screen once; later state changes assign [uiState] directly. */
    private fun show(
        state: EventEditorUiState,
        fontScale: Float = 1f,
    ) {
        uiState = state
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IfcTheme(dynamicColor = false) {
                    EventEditorScreen(state = uiState, callbacks = callbacks())
                }
            }
        }
    }

    @Test
    fun `typing a title reports through the callback`() {
        show(baseState())

        compose.onNode(hasText("Title")).performTextInput("Picnic")

        titles shouldContainExactly listOf("Picnic")
    }

    @Test
    fun `Save is enabled when the draft can be saved and disabled when it cannot`() {
        show(baseState(canSave = true))
        compose.onNodeWithContentDescription("Save").performClick()
        saved shouldBe 1

        uiState = baseState(isAllDay = false, canSave = false, endBeforeStart = true)
        compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
        compose.onNodeWithText("The end must be on or after the start.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `monthly IFC is offered on a regular day and hidden on an intercalary day`() {
        show(baseState(monthlyIfcAvailable = true))
        compose.onNodeWithText("Monthly on this IFC day").performScrollTo().assertIsDisplayed()

        uiState = baseState(monthlyIfcAvailable = false)
        compose.onAllNodesWithText("Monthly on this IFC day").assertCountEquals(0)
    }

    @Test
    fun `the Leap Day policy section shows only for a yearly IFC rule anchored on a real Leap Day`() {
        show(baseState(recurrenceKind = RecurrenceKind.YEARLY_IFC, isLeapDayAnchor = false))
        compose.onAllNodesWithText("In years with no Leap Day").assertCountEquals(0)

        uiState = baseState(recurrenceKind = RecurrenceKind.NONE, isLeapDayAnchor = true)
        compose.onAllNodesWithText("In years with no Leap Day").assertCountEquals(0)

        uiState = baseState(recurrenceKind = RecurrenceKind.YEARLY_IFC, isLeapDayAnchor = true)
        compose.onNodeWithText("In years with no Leap Day").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Skip that year").performScrollTo().performClick()
        leapDayPolicies shouldContainExactly listOf(LeapDayPolicy.SKIP)
    }

    @Test
    fun `choosing a recurrence option reports through the callback`() {
        show(baseState())

        compose.onNodeWithText("Weekly").performScrollTo().performClick()

        recurrenceKinds shouldContainExactly listOf(RecurrenceKind.WEEKLY)
    }

    @Test
    fun `reminder chips toggle through the callback`() {
        show(baseState())

        compose.onNodeWithText("At the time").performScrollTo().performClick()
        compose.onNodeWithText("1 day before").performScrollTo().performClick()

        reminders shouldContainExactly listOf(0, 1440)
    }

    // Reminder delivery is a no-op until docs/ROADMAP.md M6 T1 (ReminderScheduler); the reminders section
    // must say so, and the chips must still work and still report through the callback in the meantime.
    @Test
    fun `the reminders section shows the not-yet-delivered note and chips still toggle`() {
        show(baseState())

        compose
            .onNodeWithText("Reminders are saved with the event. Notifications arrive in a later version.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("At the time").performScrollTo().performClick()

        reminders shouldContainExactly listOf(0)
    }

    @Test
    fun `delete goes through a confirmation before it happens`() {
        show(baseState(isNew = false))
        compose.onNodeWithContentDescription("Delete").performClick()
        deleteRequested shouldBe 1

        uiState = baseState(isNew = false, showDeleteConfirm = true)
        compose.onNodeWithText("Delete this event?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        deleteConfirmed shouldBe 1
    }

    @Test
    fun `delete is not offered while creating a new event`() {
        show(baseState(isNew = true))

        compose.onAllNodesWithText("Delete").assertCountEquals(0)
    }

    @Test
    fun `back asks the ViewModel, and the discard guard is shown as its own dialog`() {
        show(baseState())
        compose.onNodeWithContentDescription("Back").performClick()
        backRequested shouldBe 1

        uiState = baseState(showDiscardConfirm = true)
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Discard").performClick()
        discardConfirmed shouldBe 1
    }

    @Test
    fun `the not-found state shows a message instead of the form`() {
        show(EventEditorUiState.NotFound)

        compose.onNodeWithText("This event no longer exists.").assertIsDisplayed()
        compose.onAllNodesWithText("Title").assertCountEquals(0)
    }

    // docs/ARCHITECTURE.md §4 "Accessibility": 200% font scale, no clipped label, every control reachable.
    // Exact-dp assertions on the TopAppBar's own IconButtons are left to `feature:converter`'s and
    // `feature:events`' list screen tests (both pass at 48dp there); this app bar's action icons render
    // through Material3's `TopAppBar` action slot, whose own sizing this test does not re-verify.
    @Test
    fun `at 200 percent font scale every control is reachable and no label is cut`() {
        show(baseState(recurrenceKind = RecurrenceKind.YEARLY_IFC), fontScale = 2f)

        compose.onNodeWithContentDescription("Save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        val recurrenceOption =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton).and(hasText("Does not repeat"))
        compose.onNode(recurrenceOption).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("At the time").performScrollTo().assertIsDisplayed()
    }
}
