package io.github.chrisjmendoza.yearal.feature.settings.privacy

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [PrivacyScreen] under Robolectric: every section heading renders and is exposed as a TalkBack
 * heading, every truthful claim's text is present, and the layout survives 200% font scale
 * (docs/FEATURES.md P5; docs/ARCHITECTURE.md §4 "Accessibility"). The claims themselves are checked
 * against `docs/security-and-privacy.md` and the merged manifest by review, not by this test — see the
 * completion report's claim-to-source mapping.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var backPresses = 0

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IfcTheme(dynamicColor = false) {
                    PrivacyScreen(onBack = { backPresses++ })
                }
            }
        }
    }

    private fun heading(text: String) =
        compose.onNode(hasText(text).and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)))

    @Test
    fun `every section heading renders as a TalkBack heading`() {
        show()

        heading("In short").performScrollTo().assertIsDisplayed()
        heading("What is stored, and where").performScrollTo().assertIsDisplayed()
        heading("Permissions, and what they're for").performScrollTo().assertIsDisplayed()
        heading("Backups").performScrollTo().assertIsDisplayed()
        heading("The full policy").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the no-tracking claims and what is stored are shown`() {
        show()

        compose
            .onNodeWithText(
                "Yearal has no account, no ads, no analytics and no crash-reporting service",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "Your settings, your events and your reminders are the only things this app keeps",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the summary does not claim that nothing can leave the device, because a backup can`() {
        show()

        // The app sends nothing itself, but Android's encrypted backup is a copy that leaves the phone; the
        // summary must say both and must not contradict the Backups section (docs/security-and-privacy.md §4.1).
        compose
            .onNodeWithText("the app itself cannot send your data anywhere", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText("The one copy that can leave your phone is Android", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onAllNodesWithText("nothing it stores could leave your device", substring = true).assertCountEquals(0)
    }

    @Test
    fun `every declared permission is explained and the absence of the rest is stated`() {
        show()

        compose.onNodeWithText("receive boot completed", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("wake lock", substring = true).performScrollTo().assertIsDisplayed()
        // One sentence per row of the allow-list table in docs/security-and-privacy.md §5 (the app's own
        // self-scoped signature permission aside). A new permission must add its sentence here.
        compose.onNodeWithText("post notifications", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("use exact alarm", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("schedule exact alarm", substring = true).performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText(
                "Yearal does not ask for your contacts, your location, your photos or files, " +
                    "your device calendars, or the internet.",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the backup explanation and the not-yet-published policy note are shown`() {
        show()

        compose
            .onNodeWithText("encrypted backup to your Google account", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "This screen is the complete privacy policy for the app as it stands today.",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `back arrow calls onBack`() {
        show()

        compose.onNodeWithContentDescription("Back").assertHasClickAction().performClick()

        backPresses shouldBe 1
    }

    // docs/ARCHITECTURE.md §4 "Accessibility": 200% font scale never clips.
    @Test
    fun `at 200 percent font scale the headings and the summary stay displayed`() {
        show(fontScale = 2f)

        heading("The full policy").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("Your calendar stays on your phone.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
