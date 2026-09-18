package io.github.chrisjmendoza.yearal.core.designsystem.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [IfcDatePicker] under Robolectric, written from `docs/calendar-spec.md` §2.2 (13 months, Sol the
 * 7th), §2.4 and §7.10 (Year Day always, Leap Day only in leap years, clamp to June 28), §7.1
 * (1583..9999) and docs/ARCHITECTURE.md §4 "Accessibility" (48dp targets, 200% font scale).
 */
@RunWith(AndroidJUnit4::class)
// Native graphics so text is really measured: the font-scale test depends on it.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h900dp")
class IfcDatePickerTest {
    @get:Rule
    val compose = createComposeRule()

    // §2.2 R4, in calendar order; the twelve shared names come from java.time in the test's en-US locale.
    private val monthNames =
        listOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "Sol",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )

    private lateinit var state: IfcDatePickerState

    private fun show(
        initial: IfcDate,
        fontScale: Float = 1f,
        width: Dp = 360.dp,
    ) {
        state = IfcDatePickerState(IfcDatePickerValue.of(initial))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                IfcTheme(dynamicColor = false) {
                    Column(modifier = Modifier.width(width).verticalScroll(rememberScrollState())) {
                        IfcDatePicker(state = state)
                    }
                }
            }
        }
    }

    private fun yearField() = compose.onNode(hasText("IFC year"))

    private fun day(day: Int) = compose.onNodeWithContentDescription("Day $day")

    private val options = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton).and(isSelectable())

    @Test
    fun `all 13 months are offered, Sol between June and July`() {
        show(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8))

        IfcMonth.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) } shouldBe monthNames
        for (name in monthNames) compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("September").assertIsSelected()
        compose.onNodeWithText("Sol").assertIsNotSelected()
    }

    @Test
    fun `all 28 days are offered and no day 29`() {
        show(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8))

        for (number in 1..28) day(number).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("29").assertCountEquals(0)
        day(8).assertIsSelected()
        day(9).assertIsNotSelected()
    }

    @Test
    fun `there are 13 + 28 + 1 options in a common year and one more in a leap year`() {
        show(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8))
        compose.onAllNodes(options).fetchSemanticsNodes() shouldHaveSize 42

        yearField().performTextReplacement("2028")
        compose.onAllNodes(options).fetchSemanticsNodes() shouldHaveSize 43
    }

    // FEATURES D1; CLAUDE.md rule 6.
    @Test
    fun `Year Day is always offered and Leap Day appears and disappears with the year`() {
        show(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8))
        compose.onNodeWithText("Year Day").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Leap Day").assertCountEquals(0)
        compose.onNodeWithText("2026 is not a leap year, so it has no Leap Day.").assertIsDisplayed()

        yearField().performTextReplacement("2028")
        compose.onNodeWithText("Leap Day").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Year Day").assertIsDisplayed()
        compose.onAllNodesWithText("not a leap year", substring = true).assertCountEquals(0)

        // 2100 is a century common year (§2.1 R2).
        yearField().performTextReplacement("2100")
        compose.onAllNodesWithText("Leap Day").assertCountEquals(0)
        compose.onNodeWithText("Year Day").assertIsDisplayed()

        yearField().performTextReplacement("2000")
        compose.onNodeWithText("Leap Day").assertIsDisplayed()
    }

    @Test
    fun `choosing a month, a day and the intercalary days reports the date`() {
        show(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8))

        compose.onNodeWithText("Sol").performScrollTo().performClick()
        state.date shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 8)
        day(13).performScrollTo().performClick()
        state.date shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 13)
        compose.onNodeWithText("Sol").assertIsSelected()
        day(13).assertIsSelected()

        compose.onNodeWithText("Year Day").performScrollTo().performClick()
        state.date shouldBe IfcDate.YearDay(2026)
        compose.onNodeWithText("Year Day").assertIsSelected()
        // An intercalary day belongs to no month: no month and no day stays selected.
        compose.onNodeWithText("Sol").assertIsNotSelected()
        day(13).assertIsNotSelected()

        yearField().performTextReplacement("2028")
        compose.onNodeWithText("Leap Day").performScrollTo().performClick()
        state.date shouldBe IfcDate.LeapDay(2028)
        compose.onNodeWithText("Leap Day").assertIsSelected()
        compose.onNodeWithText("Year Day").assertIsNotSelected()
    }

    // Spec §7.10: "Switching the picker's year while Leap Day is selected clamps to June 28."
    @Test
    fun `changing a selected Leap Day to a common year moves to June 28 and says so`() {
        show(IfcDate.LeapDay(2028))
        compose.onNodeWithText("Leap Day").performScrollTo().assertIsSelected()

        yearField().performTextReplacement("2027")

        state.date shouldBe IfcDate.Regular(2027, IfcMonth.JUNE, 28)
        compose.onNodeWithText("2027 has no Leap Day, so the date moved to June 28.").assertIsDisplayed()
        compose.onNodeWithText("June").assertIsSelected()
        day(28).assertIsSelected()
        compose.onAllNodesWithText("Leap Day").assertCountEquals(0)

        // The notice ends with the next edit.
        day(27).performScrollTo().performClick()
        compose.onAllNodesWithText("has no Leap Day, so", substring = true).assertCountEquals(0)
    }

    @Test
    fun `retyping the year between two leap years keeps Leap Day`() {
        show(IfcDate.LeapDay(2024))

        yearField().performTextReplacement("202")
        state.date.shouldBeNull()
        compose.onNodeWithText("Leap Day").performScrollTo().assertIsSelected()

        yearField().performTextReplacement("2028")
        state.date shouldBe IfcDate.LeapDay(2028)
    }

    // §7.1: 1583..9999; anything else is an error state, not an exception.
    @Test
    fun `a year outside 1583 to 9999 is an error state with no date`() {
        show(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8))
        yearField().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        compose.onNodeWithText("Enter a year from 1583 to 9999").assertIsDisplayed()

        for (invalid in listOf("", "12", "0999")) {
            yearField().performTextReplacement(invalid)
            state.date.shouldBeNull()
        }
        // Only four ASCII digits are kept, so an over-long entry cannot leave the range upwards.
        yearField().performTextReplacement("99999x")
        state.value.yearText shouldBe "9999"
        state.date shouldBe IfcDate.Regular(9999, IfcMonth.SEPTEMBER, 8)

        yearField().performTextReplacement("1582")
        yearField().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        state.date.shouldBeNull()

        yearField().performTextReplacement("1583")
        yearField().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        state.date shouldBe IfcDate.Regular(1583, IfcMonth.SEPTEMBER, 8)
    }

    @Test
    fun `every option is at least 48dp in both directions`() {
        show(IfcDate.LeapDay(2028))
        assertEveryOptionIsAtLeast48dp(expected = 43)
    }

    @Test
    fun `in a narrow container the days fall back to four columns and stay 48dp`() {
        show(IfcDate.LeapDay(2028), width = 280.dp)
        assertEveryOptionIsAtLeast48dp(expected = 43)
        // Seven rows of four: day 5 starts the second row, directly under day 1.
        val first = day(1).fetchSemanticsNode().boundsInRoot
        val fifth = day(5).fetchSemanticsNode().boundsInRoot
        fifth.left shouldBe first.left
        (fifth.top > first.top) shouldBe true
    }

    @Test
    fun `at 200 percent font scale nothing is clipped and day numbers stay on one line`() {
        show(IfcDate.LeapDay(2028), fontScale = 2f)
        assertEveryOptionIsAtLeast48dp(expected = 43)

        val nodes = compose.onAllNodes(options).fetchSemanticsNodes()
        for (index in nodes.indices) {
            compose.onAllNodes(options)[index].textLayout().isClipped() shouldBe false
        }
        for (number in 1..28) day(number).textLayout().lineCount shouldBe 1
        compose.onNodeWithText("Days outside the months").textLayout().isClipped() shouldBe false
        compose
            .onNodeWithText(
                "Enter a year from 1583 to 9999",
                useUnmergedTree = true,
            ).textLayout()
            .isClipped() shouldBe
            false
    }

    @Test
    fun `remembered state survives recreation`() {
        val restoration = StateRestorationTester(compose)
        lateinit var remembered: IfcDatePickerState
        restoration.setContent {
            IfcTheme(dynamicColor = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    remembered = rememberIfcDatePickerState(IfcDate.LeapDay(2028))
                    IfcDatePicker(state = remembered)
                }
            }
        }
        compose.onNode(hasText("IFC year")).performTextReplacement("2027")
        compose.onNodeWithContentDescription("Day 27").performScrollTo().performClick()
        compose.onNodeWithText("Sol").performScrollTo().performClick()
        remembered.date shouldBe IfcDate.Regular(2027, IfcMonth.SOL, 27)

        restoration.emulateSavedInstanceStateRestore()

        remembered.date shouldBe IfcDate.Regular(2027, IfcMonth.SOL, 27)
        compose.onNodeWithText("Sol").assertIsSelected()
    }

    private fun assertEveryOptionIsAtLeast48dp(expected: Int) {
        val all = compose.onAllNodes(options)
        all.fetchSemanticsNodes() shouldHaveSize expected
        for (index in 0 until expected) {
            all[index].assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    // Not TextLayoutResult.hasVisualOverflow: the result the semantics action builds is laid out at the
    // maximum width, so its didOverflowWidth is true for any text narrower than its constraints. What
    // matters is that no line was cut: none dropped (height, max lines) and none wider than the node.
    private fun TextLayoutResult.isClipped(): Boolean =
        didOverflowHeight || (0 until lineCount).any { line -> getLineRight(line) - getLineLeft(line) > size.width }

    // The merged option node carries its label's GetTextLayoutResult action.
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
