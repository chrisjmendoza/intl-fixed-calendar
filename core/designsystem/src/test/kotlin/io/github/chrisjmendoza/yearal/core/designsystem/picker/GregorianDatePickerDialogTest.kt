package io.github.chrisjmendoza.yearal.core.designsystem.picker

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * [GregorianDatePickerDialog] under Robolectric: it exchanges [LocalDate]s (never milliseconds) with
 * its caller, accepts the whole 1583..9999 range (docs/ARCHITECTURE.md "Reconciled decisions" 6) and
 * refuses to confirm a date outside it.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h900dp")
class GregorianDatePickerDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private var confirmed: LocalDate? = null
    private var dismissed = 0

    private fun show(initial: LocalDate) {
        compose.setContent {
            IfcTheme(dynamicColor = false) {
                GregorianDatePickerDialog(
                    initialDate = initial,
                    onConfirm = { confirmed = it },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun `confirming returns the initial date unchanged`() {
        show(LocalDate.of(2026, 9, 17))

        compose.onNodeWithText("OK").assertIsEnabled().performClick()

        confirmed shouldBe LocalDate.of(2026, 9, 17)
        dismissed shouldBe 0
    }

    @Test
    fun `the first day of 1583 can be confirmed`() {
        show(LocalDate.of(1583, 1, 1))
        compose.onNodeWithText("OK").performClick()
        confirmed shouldBe LocalDate.of(1583, 1, 1)
    }

    @Test
    fun `the last day of 9999 can be confirmed`() {
        show(LocalDate.of(9999, 12, 31))
        compose.onNodeWithText("OK").performClick()
        confirmed shouldBe LocalDate.of(9999, 12, 31)
    }

    @Test
    fun `a Gregorian leap day survives the millisecond adapter`() {
        show(LocalDate.of(2024, 2, 29))
        compose.onNodeWithText("OK").performClick()
        confirmed shouldBe LocalDate.of(2024, 2, 29)
    }

    @Test
    fun `a date outside the range opens with nothing selected and cannot be confirmed`() {
        show(LocalDate.of(1582, 10, 15))

        compose.onNodeWithText("OK").assertIsNotEnabled()
        confirmed shouldBe null
    }

    @Test
    fun `cancel dismisses without a date`() {
        show(LocalDate.of(2026, 9, 17))

        compose.onNodeWithText("Cancel").performClick()

        dismissed shouldBe 1
        confirmed shouldBe null
    }

    @Test
    fun `the adapter maps a date to the start of its UTC day and back`() {
        // 1970-01-01 is the epoch; 2026-09-17T00:00:00Z is 1 789 603 200 seconds after it.
        LocalDate.of(1970, 1, 1).toUtcStartOfDayMillis() shouldBe 0L
        LocalDate.of(2026, 9, 17).toUtcStartOfDayMillis() shouldBe 1_789_603_200_000L
        utcMillisToLocalDate(1_789_603_200_000L) shouldBe LocalDate.of(2026, 9, 17)
        // Any instant of the UTC day maps to that day; a millisecond earlier is the day before.
        utcMillisToLocalDate(1_789_603_200_000L + 86_399_999L) shouldBe LocalDate.of(2026, 9, 17)
        utcMillisToLocalDate(1_789_603_200_000L - 1L) shouldBe LocalDate.of(2026, 9, 16)
        utcMillisToLocalDate(-1L) shouldBe LocalDate.of(1969, 12, 31)
    }
}
