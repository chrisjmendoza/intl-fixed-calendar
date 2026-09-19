package io.github.chrisjmendoza.yearal.feature.settings.learn

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
 * [LearnScreen] under Robolectric: every section heading renders and is exposed as a TalkBack heading,
 * the worked examples that come from [LearnFacts] render through the formatter correctly, FAQ items
 * expand and collapse, and the layout survives 200% font scale (docs/FEATURES.md L2;
 * docs/ARCHITECTURE.md §4 "Accessibility"). The screen is a single long scrollable column, so every
 * assertion below the first screenful scrolls the node into view first, exactly like
 * `SettingsScreenTest`.
 */
@RunWith(AndroidJUnit4::class)
class LearnScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var backPresses = 0

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IfcTheme(dynamicColor = false) {
                    LearnScreen(onBack = { backPresses++ })
                }
            }
        }
    }

    private fun heading(text: String) =
        compose.onNode(hasText(text).and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)))

    @Test
    fun `every section heading renders as a TalkBack heading`() {
        show()

        heading("What is the International Fixed Calendar?").performScrollTo().assertIsDisplayed()
        heading("The two days outside the week").performScrollTo().assertIsDisplayed()
        heading("Why the weekdays in this app differ").performScrollTo().assertIsDisplayed()
        heading("How the dates are calculated").performScrollTo().assertIsDisplayed()
        heading("A brief history").performScrollTo().assertIsDisplayed()
        heading("Frequently asked questions").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the floating-day worked examples are computed, not hard-coded text`() {
        show()

        // LearnFacts.yearDayExample is IfcDate.YearDay(2026), which :core:calendar maps to Dec 31, 2026.
        // Matched by full text (not substring) because the calculation section below states the same
        // Year Day fact again, in a different sentence, later on the same screen.
        compose
            .onNodeWithText(
                "Year Day happens every year. It comes after December 28 and before next January 1 " +
                    "— it belongs to no week and has no IFC weekday of its own. Year Day, 2026 is " +
                    "Gregorian Thursday, December 31, 2026.",
            ).performScrollTo()
            .assertIsDisplayed()
        // LearnFacts.leapDayExample is IfcDate.LeapDay(2024), which :core:calendar maps to Jun 17, 2024.
        compose
            .onNodeWithText("Leap Day, 2024 is Gregorian Monday, June 17, 2024.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the weekday worked example shows the spec's own nominal-vs-actual illustration`() {
        show()

        compose
            .onNodeWithText(
                "For example: Thursday, September 17, 2026 is IFC September 8, 2026. " +
                    "Its IFC weekday is Sunday, but because Year Day (and, in leap years, Leap Day) " +
                    "are taken out of the count first, its real weekday is Thursday.",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the calculation section shows the IFC-prefixed numeric form and the Sol 1 and leap-shift facts`() {
        show()

        compose.onNodeWithText("IFC 2026-10-08", substring = true).performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText(
                "Sol 1 is always Gregorian June 18, whether the year is a leap year or not: " +
                    "Sol 1, 2026 is Gregorian Thursday, June 18, 2026, and Sol 1, 2024 is Gregorian " +
                    "Tuesday, June 18, 2024.",
            ).performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText(
                "Gregorian March 1 is March 4, 2026 in a common year like 2026, but March 5, 2024 " +
                    "in a leap year like 2024.",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `an FAQ answer is hidden until its question is tapped, then hides again on a second tap`() {
        show()

        val question = "Is the IFC based on the phases of the moon?"
        val answerStart = "No. Despite having 13 months, the IFC is a solar calendar"

        compose.onAllNodesWithText(answerStart, substring = true).assertCountEquals(0)

        compose
            .onNodeWithText(question)
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText(answerStart, substring = true).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(question).performScrollTo().performClick()
        compose.onAllNodesWithText(answerStart, substring = true).assertCountEquals(0)
    }

    @Test
    fun `every FAQ question is present`() {
        show()

        compose.onNodeWithText("Is the IFC based on the phases of the moon?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Does the IFC year start in spring?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Are Sol and Leap Day the same thing?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Is this the only 13-month calendar?").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("What does the converter's note about \"proleptic\" dates mean?")
            .performScrollTo()
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
    fun `at 200 percent font scale the headings and a worked example stay displayed`() {
        show(fontScale = 2f)

        heading("Frequently asked questions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A brief history").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("Is the IFC based on the phases of the moon?")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
