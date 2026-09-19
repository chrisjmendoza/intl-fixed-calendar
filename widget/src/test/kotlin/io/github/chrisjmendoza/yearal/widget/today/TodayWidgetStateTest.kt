package io.github.chrisjmendoza.yearal.widget.today

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * [buildTodayWidgetState] renders every [IfcDate] shape correctly (CLAUDE.md rule 6): a regular day, and
 * both intercalary days, which have no month/day number and so need the year in the primary label.
 * Robolectric only for [android.content.res.Resources]; the dates themselves come from [todayDate],
 * proven date-agnostic in `TodayDateTest`.
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetStateTest {
    private val formatter =
        IfcDateFormatter(ApplicationProvider.getApplicationContext<android.content.Context>().resources, Locale.US)
    private val tapHint = "Double-tap to open Yearal."

    private fun stateFor(gregorian: LocalDate) =
        buildTodayWidgetState(TodayDate(IfcDate.from(gregorian), gregorian), formatter, tapHint)

    @Test
    fun `a regular day shows the month and day, no year`() {
        val state = stateFor(LocalDate.of(2026, 9, 17))

        state.primaryLabel shouldBe "September 8"
        state.gregorianLabel shouldContain "Sep"
        state.gregorianLabel shouldContain "2026"
        state.actualWeekdayLabel shouldBe "Actual weekday: Thursday"
    }

    @Test
    fun `Sol shows its own name, not a Gregorian namesake`() {
        val state = stateFor(LocalDate.of(2026, 6, 20))

        state.primaryLabel shouldBe "Sol 3"
    }

    @Test
    fun `Year Day shows its name and year, since it has no month or day`() {
        val state = stateFor(LocalDate.of(2026, 12, 31))

        state.primaryLabel shouldBe "Year Day, 2026"
    }

    @Test
    fun `Leap Day shows its name and year, only in a leap year`() {
        val state = stateFor(LocalDate.of(2028, 6, 17))

        state.primaryLabel shouldBe "Leap Day, 2028"
    }

    @Test
    fun `the content description merges both dates, both weekdays, today, and the tap hint`() {
        val state = stateFor(LocalDate.of(2026, 9, 17))

        state.contentDescription shouldContain "Today."
        state.contentDescription shouldEndWith tapHint
    }

    @Test
    fun `an intercalary content description says there is no IFC weekday, never a derived one`() {
        val state = stateFor(LocalDate.of(2026, 12, 31))

        state.contentDescription shouldContain "no IFC weekday"
    }
}
