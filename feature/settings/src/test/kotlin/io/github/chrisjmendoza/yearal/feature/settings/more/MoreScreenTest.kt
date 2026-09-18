package io.github.chrisjmendoza.yearal.feature.settings.more

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MoreScreen] under Robolectric: the Settings row is a button that fires its callback, and the About
 * row shows the app name and version without being clickable.
 */
@RunWith(AndroidJUnit4::class)
class MoreScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var settingsClicks = 0

    private fun show() {
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                MoreScreen(appName = "Yearal", versionName = "0.1.0", onSettingsClick = { settingsClicks++ })
            }
        }
    }

    @Test
    fun `Settings row is clickable and invokes its callback`() {
        show()

        compose
            .onNodeWithText("Settings")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        settingsClicks shouldBe 1
        compose.onNodeWithText("Weekday headers, theme, holidays").assertIsDisplayed()
    }

    @Test
    fun `About row shows the app name and version and is not clickable`() {
        show()

        compose.onNodeWithText("Yearal").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("Version 0.1.0").assertIsDisplayed()
        compose.onNodeWithText("More").assertIsDisplayed()
    }
}
