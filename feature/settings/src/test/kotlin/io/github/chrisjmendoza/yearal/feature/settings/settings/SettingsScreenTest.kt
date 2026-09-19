package io.github.chrisjmendoza.yearal.feature.settings.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.domain.settings.ThemeMode
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SettingsScreen] under Robolectric: each control reflects the state it is given, has the semantics
 * TalkBack needs (`selected`, on/off, disabled), and reports a change through its callback with the
 * right value (FEATURES W1, W2, H5; docs/ARCHITECTURE.md §4 "Accessibility").
 *
 * Tests of the "Delete all data" dialogs drive the two-step flow by reassigning [loadedState]
 * directly (as `feature:events`'s `EventEditorScreenTest` does), since the Compose test rule refuses a
 * second `setContent` call.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val packs =
        listOf(
            HolidayPackItem(id = "ifc", name = "International Fixed Calendar", region = null),
            HolidayPackItem(id = "us", name = "United States", region = "United States"),
            HolidayPackItem(id = "religious-christian", name = "Christian (Easter family)", region = null),
        )

    private val weekdaySelections = mutableListOf<WeekdayDisplay>()
    private val themeSelections = mutableListOf<ThemeMode>()
    private val dynamicColorChanges = mutableListOf<Boolean>()
    private val holidayChanges = mutableListOf<Pair<String, Boolean>>()
    private var backPresses = 0
    private var deleteAllDataRequested = 0
    private var deleteAllDataContinued = 0
    private var deleteAllDataCancelled = 0
    private var deleteAllDataConfirmed = 0
    private var deleteAllDataDoneDismissed = 0

    private var loadedState: SettingsUiState.Loaded by
        mutableStateOf(SettingsUiState.Loaded(UserSettings.DEFAULT, packs, dynamicColorSupported = true))

    private fun show(
        settings: UserSettings = UserSettings.DEFAULT,
        dynamicColorSupported: Boolean = true,
        deleteAllDataStep: DeleteAllDataStep = DeleteAllDataStep.NONE,
    ) {
        loadedState = SettingsUiState.Loaded(settings, packs, dynamicColorSupported, deleteAllDataStep)
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                SettingsScreen(
                    state = loadedState,
                    onBack = { backPresses++ },
                    onWeekdayDisplaySelected = { weekdaySelections += it },
                    onThemeModeSelected = { themeSelections += it },
                    onDynamicColorChanged = { dynamicColorChanges += it },
                    onHolidaySetEnabledChanged = { id, enabled -> holidayChanges += id to enabled },
                    onRequestDeleteAllData = { deleteAllDataRequested++ },
                    onContinueDeleteAllData = { deleteAllDataContinued++ },
                    onCancelDeleteAllData = { deleteAllDataCancelled++ },
                    onConfirmDeleteAllData = { deleteAllDataConfirmed++ },
                    onDismissDeleteAllDataDone = { deleteAllDataDoneDismissed++ },
                )
            }
        }
    }

    @Test
    fun `weekday options render with their explanations and the stored one is selected`() {
        show(UserSettings(weekdayDisplay = WeekdayDisplay.BOTH))

        compose.onNodeWithText("IFC weekdays are not the real weekdays", substring = true).assertIsDisplayed()
        compose
            .onNodeWithText("Both")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsSelected()
        compose.onNodeWithText("IFC weekdays with the real weekdays underneath").assertIsDisplayed()
        compose
            .onNodeWithText("Actual only")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotSelected()
        compose.onNodeWithText("Only the real weekdays of each month").assertIsDisplayed()
        compose
            .onNodeWithText("IFC only")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotSelected()
        compose.onNodeWithText("Only the perpetual IFC weekday names, identical every month").assertIsDisplayed()
    }

    @Test
    fun `the selected weekday option follows the state`() {
        show(UserSettings(weekdayDisplay = WeekdayDisplay.NOMINAL))

        compose.onNodeWithText("IFC only").assertIsSelected()
        compose.onNodeWithText("Both").assertIsNotSelected()
        compose.onNodeWithText("Actual only").assertIsNotSelected()
    }

    @Test
    fun `clicking a weekday option reports it and does not change the screen by itself`() {
        show()

        compose.onNodeWithText("Actual only").performScrollTo().performClick()

        weekdaySelections shouldContainExactly listOf(WeekdayDisplay.ACTUAL)
        // Stateless: the selection only moves once the caller passes a new state.
        compose.onNodeWithText("Both").assertIsSelected()
    }

    @Test
    fun `theme options form a radio group that reports the chosen mode`() {
        show(UserSettings(themeMode = ThemeMode.SYSTEM))

        compose.onNodeWithText("System default").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Light").performScrollTo().assertIsNotSelected()
        compose.onNodeWithText("Dark").performScrollTo().assertIsNotSelected()

        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.onNodeWithText("Light").performScrollTo().performClick()

        themeSelections shouldContainExactly listOf(ThemeMode.DARK, ThemeMode.LIGHT)
    }

    @Test
    fun `dynamic colour switch reflects the state and reports the opposite value on click`() {
        show(UserSettings(dynamicColor = true), dynamicColorSupported = true)

        val row = compose.onNodeWithText("Dynamic colour").performScrollTo()
        row.assertIsEnabled().assertIsOn()
        compose.onNodeWithText("Use wallpaper colours (Android 12+)").assertIsDisplayed()

        row.performClick()

        dynamicColorChanges shouldContainExactly listOf(false)
    }

    @Test
    fun `dynamic colour switch is disabled with its reason when the device cannot support it`() {
        show(UserSettings(dynamicColor = true), dynamicColorSupported = false)

        compose.onNodeWithText("Dynamic colour").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Not available on this device").assertIsDisplayed()

        compose.onNodeWithText("Dynamic colour").performClick()

        dynamicColorChanges shouldBe emptyList()
    }

    @Test
    fun `holiday switches show every pack, its region, and the stored on-off state`() {
        show(UserSettings(enabledHolidaySets = setOf("ifc", "us")))

        compose.onNodeWithText("International Fixed Calendar").performScrollTo().assertIsOn()
        compose.onNodeWithText("United States").performScrollTo().assertIsOn()
        compose.onNodeWithText("Region: United States").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Christian (Easter family)").performScrollTo().assertIsOff()
    }

    @Test
    fun `clicking a holiday switch reports the pack id with the new state`() {
        show(UserSettings(enabledHolidaySets = setOf("ifc", "us")))

        compose.onNodeWithText("United States").performScrollTo().performClick()
        compose.onNodeWithText("Christian (Easter family)").performScrollTo().performClick()
        compose.onNodeWithText("International Fixed Calendar").performScrollTo().performClick()

        holidayChanges shouldContainExactly
            listOf("us" to false, "religious-christian" to true, "ifc" to false)
    }

    @Test
    fun `back arrow calls onBack`() {
        show()

        compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()

        backPresses shouldBe 1
    }

    // ----- "Delete all data" (FEATURES W6): reachable by TalkBack, unmistakably destructive, a
    // two-step confirmation with cancel at each step. -----

    @Test
    fun `the delete-all-data row is reachable and opens the first confirmation`() {
        show()

        compose
            .onNodeWithText("Delete all data")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        deleteAllDataRequested shouldBe 1
    }

    @Test
    fun `the first confirmation explains what will be erased and can be cancelled`() {
        show(deleteAllDataStep = DeleteAllDataStep.CONFIRM_FIRST)

        compose.onNodeWithText("Delete all data?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        deleteAllDataCancelled shouldBe 1
        deleteAllDataContinued shouldBe 0
    }

    @Test
    fun `continuing the first confirmation opens the final, unmistakably destructive one`() {
        show(deleteAllDataStep = DeleteAllDataStep.CONFIRM_FIRST)

        compose.onNodeWithText("Continue").performClick()

        deleteAllDataContinued shouldBe 1
    }

    @Test
    fun `the final confirmation can be cancelled without erasing anything`() {
        show(deleteAllDataStep = DeleteAllDataStep.CONFIRM_SECOND)

        compose.onNodeWithText("This can't be undone").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        deleteAllDataCancelled shouldBe 1
        deleteAllDataConfirmed shouldBe 0
    }

    @Test
    fun `confirming the final dialog erases everything`() {
        show(deleteAllDataStep = DeleteAllDataStep.CONFIRM_SECOND)

        // The row behind the dialog shares its text with the dialog's own confirm button.
        compose.onAllNodesWithText("Delete all data").onLast().performClick()

        deleteAllDataConfirmed shouldBe 1
    }

    @Test
    fun `the completion notice can be dismissed`() {
        show(deleteAllDataStep = DeleteAllDataStep.DONE)

        compose.onNodeWithText("All data deleted").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()

        deleteAllDataDoneDismissed shouldBe 1
    }

    @Test
    fun `no confirmation dialog shows while the step is NONE`() {
        show(deleteAllDataStep = DeleteAllDataStep.NONE)

        compose.onAllNodesWithText("Delete all data?").assertCountEquals(0)
        compose.onAllNodesWithText("This can't be undone").assertCountEquals(0)
        compose.onAllNodesWithText("All data deleted").assertCountEquals(0)
    }

    @Test
    fun `loading state shows the spinner and the app bar only`() {
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                SettingsScreen(
                    state = SettingsUiState.Loading,
                    onBack = {},
                    onWeekdayDisplaySelected = {},
                    onThemeModeSelected = {},
                    onDynamicColorChanged = {},
                    onHolidaySetEnabledChanged = { _, _ -> },
                )
            }
        }

        compose.onNodeWithContentDescription("Loading settings").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }
}
