package io.github.chrisjmendoza.yearal.core.designsystem.picker

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

/**
 * [IfcDatePickerValue] and [IfcDaySelection], written from `docs/calendar-spec.md` §2.4 (intercalary
 * days), §6.4–§6.5 (vectors, leap and common century years), §7.1 (1583..9999), §7.3 (pseudo-fields)
 * and §7.10 (a selected Leap Day clamps to June 28 when the year becomes a common year). Expected
 * dates are spec constants or come from `:core:calendar`, never from arithmetic in this test.
 */
class IfcDatePickerValueTest {
    private val sol13 = IfcDatePickerValue.of(IfcDate.Regular(2026, IfcMonth.SOL, 13))

    @Test
    fun `a value built from a date shows that date`() {
        sol13.yearText shouldBe "2026"
        sol13.year shouldBe 2026
        sol13.selection shouldBe IfcDaySelection.Regular(IfcMonth.SOL, 13)
        sol13.leapDayClamped shouldBe false
        sol13.date shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 13)
        // docs/ARCHITECTURE.md §4 "Accessibility": Sol 13, 2026 = Gregorian Tuesday, June 30, 2026.
        sol13.date?.toLocalDate() shouldBe LocalDate.of(2026, 6, 30)
    }

    @Test
    fun `Year Day and Leap Day round-trip through the value`() {
        IfcDatePickerValue.of(IfcDate.YearDay(2026)).date shouldBe IfcDate.YearDay(2026)
        IfcDatePickerValue.of(IfcDate.LeapDay(2024)).date shouldBe IfcDate.LeapDay(2024)
        // §6.3–§6.4: Leap Day 2024 = 2024-06-17, Year Day 2026 = 2026-12-31.
        IfcDatePickerValue.of(IfcDate.LeapDay(2024)).date?.toLocalDate() shouldBe LocalDate.of(2024, 6, 17)
        IfcDatePickerValue.of(IfcDate.YearDay(2026)).date?.toLocalDate() shouldBe LocalDate.of(2026, 12, 31)
    }

    // §2.1 R2 and the §6.5 negative vectors: 1900 and 2100 are common, 2000 is leap.
    @Test
    fun `Leap Day is offered exactly in leap years`() {
        for (leap in listOf(1584, 1600, 1928, 2000, 2024, 2028, 9996)) {
            sol13.withYearText(leap.toString()).isLeapDayOffered shouldBe true
        }
        for (common in listOf(1583, 1700, 1900, 2025, 2026, 2100, 9999)) {
            sol13.withYearText(common.toString()).isLeapDayOffered shouldBe false
        }
    }

    @Test
    fun `selecting Leap Day in a common year changes nothing`() {
        sol13.withSelection(IfcDaySelection.LeapDay) shouldBe sol13
    }

    // §7.10 with the §7.7 table: Leap Day 2024 + 1 year = June 28, 2025; Leap Day 2096 + 4 years = June 28, 2100.
    @Test
    fun `changing a selected Leap Day to a common year clamps to June 28 and says so`() {
        val clamped = IfcDatePickerValue.of(IfcDate.LeapDay(2024)).withYearText("2025")
        clamped.selection shouldBe IfcDaySelection.Regular(IfcMonth.JUNE, 28)
        clamped.leapDayClamped shouldBe true
        clamped.date shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)
        clamped.date shouldBe IfcDate.LeapDay(2024).plusYears(1)
        clamped.isLeapDayOffered shouldBe false

        val century = IfcDatePickerValue.of(IfcDate.LeapDay(2096)).withYearText("2100")
        century.date shouldBe IfcDate.Regular(2100, IfcMonth.JUNE, 28)
        century.leapDayClamped shouldBe true
    }

    @Test
    fun `the clamp is not undone by returning to a leap year, and the notice ends with the next change`() {
        val clamped = IfcDatePickerValue.of(IfcDate.LeapDay(2024)).withYearText("2025")

        val leapAgain = clamped.withYearText("2028")
        leapAgain.date shouldBe IfcDate.Regular(2028, IfcMonth.JUNE, 28)
        leapAgain.leapDayClamped shouldBe false

        clamped.withDayOfMonth(27).leapDayClamped shouldBe false
        clamped.withSelection(IfcDaySelection.YearDay).leapDayClamped shouldBe false
    }

    @Test
    fun `changing a selected Leap Day to another leap year keeps Leap Day`() {
        val moved = IfcDatePickerValue.of(IfcDate.LeapDay(2024)).withYearText("2028")
        moved.date shouldBe IfcDate.LeapDay(2028)
        moved.leapDayClamped shouldBe false
    }

    @Test
    fun `an incomplete year keeps a selected Leap Day until the year is whole`() {
        val typing = IfcDatePickerValue.of(IfcDate.LeapDay(2024)).withYearText("202")
        typing.year.shouldBeNull()
        typing.date.shouldBeNull()
        typing.selection shouldBe IfcDaySelection.LeapDay
        typing.isLeapDayOffered shouldBe true
        typing.leapDayClamped shouldBe false

        typing.withYearText("2028").date shouldBe IfcDate.LeapDay(2028)
        typing.withYearText("2027").date shouldBe IfcDate.Regular(2027, IfcMonth.JUNE, 28)
    }

    @Test
    fun `an incomplete year does not offer Leap Day unless it is already selected`() {
        sol13.withYearText("202").isLeapDayOffered shouldBe false
        sol13.withYearText("").isLeapDayOffered shouldBe false
    }

    // §7.1 and ARCHITECTURE "Reconciled decisions" 6: the UI accepts 1583..9999.
    @Test
    fun `the year is valid from 1583 to 9999 only`() {
        sol13.withYearText("1583").date shouldBe IfcDate.Regular(1583, IfcMonth.SOL, 13)
        sol13.withYearText("9999").date shouldBe IfcDate.Regular(9999, IfcMonth.SOL, 13)
        sol13.withYearText("9999").withSelection(IfcDaySelection.YearDay).date shouldBe IfcDate.YearDay(9999)

        for (invalid in listOf("", "0", "1", "158", "1582", "0000", "0999")) {
            val value = sol13.withYearText(invalid)
            value.year.shouldBeNull()
            value.date.shouldBeNull()
        }
    }

    @Test
    fun `the year text keeps ASCII digits only, at most four`() {
        sol13.withYearText("20a2 6").yearText shouldBe "2026"
        sol13.withYearText("-2026").yearText shouldBe "2026"
        sol13.withYearText("10000").yearText shouldBe "1000"
        sol13.withYearText("10000").date.shouldBeNull()
        sol13.withYearText("٢٠٢٦").yearText shouldBe ""
    }

    @Test
    fun `a library date before 1583 yields a value without a date`() {
        val early = IfcDatePickerValue.of(IfcDate.Regular(1582, IfcMonth.OCTOBER, 8))
        early.yearText shouldBe "1582"
        early.date.shouldBeNull()
    }

    @Test
    fun `choosing a month keeps the day, and clamps an intercalary day to 28`() {
        sol13.withMonth(IfcMonth.DECEMBER).date shouldBe IfcDate.Regular(2026, IfcMonth.DECEMBER, 13)
        IfcDatePickerValue.of(IfcDate.YearDay(2026)).withMonth(IfcMonth.JANUARY).date shouldBe
            IfcDate.Regular(2026, IfcMonth.JANUARY, 28)
        IfcDatePickerValue.of(IfcDate.LeapDay(2024)).withMonth(IfcMonth.SOL).date shouldBe
            IfcDate.Regular(2024, IfcMonth.SOL, 28)
    }

    // §2.4: Leap Day is attached to June and Year Day to December.
    @Test
    fun `choosing a day keeps the month, and an intercalary day's attached month`() {
        sol13.withDayOfMonth(1).date shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 1)
        sol13.withDayOfMonth(28).date shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 28)
        IfcDatePickerValue.of(IfcDate.LeapDay(2024)).withDayOfMonth(5).date shouldBe
            IfcDate.Regular(2024, IfcMonth.JUNE, 5)
        IfcDatePickerValue.of(IfcDate.YearDay(2026)).withDayOfMonth(5).date shouldBe
            IfcDate.Regular(2026, IfcMonth.DECEMBER, 5)
    }

    @Test
    fun `a day outside 1 to 28 is rejected`() {
        shouldThrow<IllegalArgumentException> { sol13.withDayOfMonth(0) }
        shouldThrow<IllegalArgumentException> { sol13.withDayOfMonth(29) }
        shouldThrow<IllegalArgumentException> { IfcDaySelection.Regular(IfcMonth.JUNE, 29) }
    }

    // §7.3: Leap Day = 06-29, Year Day = 13-29; §6.5: no other day 29, no month 0 or 14, no day 0 or 30.
    @Test
    fun `a selection maps to and from the numeric pseudo-fields`() {
        IfcDaySelection.LeapDay.monthNumber shouldBe 6
        IfcDaySelection.LeapDay.dayOfMonth shouldBe 29
        IfcDaySelection.YearDay.monthNumber shouldBe 13
        IfcDaySelection.YearDay.dayOfMonth shouldBe 29
        IfcDaySelection.Regular(IfcMonth.SOL, 13).monthNumber shouldBe 7

        IfcDaySelection.of(6, 29) shouldBe IfcDaySelection.LeapDay
        IfcDaySelection.of(13, 29) shouldBe IfcDaySelection.YearDay
        IfcDaySelection.of(7, 13) shouldBe IfcDaySelection.Regular(IfcMonth.SOL, 13)
        IfcDaySelection.of(13, 28) shouldBe IfcDaySelection.Regular(IfcMonth.DECEMBER, 28)
        for ((month, day) in listOf(7 to 29, 1 to 29, 13 to 30, 6 to 30, 1 to 0, 0 to 1, 14 to 1)) {
            IfcDaySelection.of(month, day).shouldBeNull()
        }
    }

    @Test
    fun `restoring re-validates the saved parts`() {
        IfcDatePickerValue.restore("2026", IfcDaySelection.Regular(IfcMonth.SOL, 13), leapDayClamped = false) shouldBe
            sol13

        val tampered = IfcDatePickerValue.restore("2025", IfcDaySelection.LeapDay, leapDayClamped = false)
        tampered.date shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)
        tampered.leapDayClamped shouldBe true

        val clamped = IfcDatePickerValue.of(IfcDate.LeapDay(2024)).withYearText("2025")
        IfcDatePickerValue.restore(clamped.yearText, clamped.selection, clamped.leapDayClamped) shouldBe clamped

        IfcDatePickerValue.restore("20x26!", IfcDaySelection.YearDay, leapDayClamped = false).date shouldBe
            IfcDate.YearDay(2026)
    }

    // Every date the picker can produce, for every year of the UI range, against IfcDate.of — the
    // pseudo-field factory of :core:calendar (§3.3 V5).
    @Test
    fun `every reachable selection in every year is the date core-calendar builds from the same fields`() {
        var checked = 0
        for (year in DatePickerRange.years) {
            val base = IfcDatePickerValue.of(IfcDate.YearDay(year))
            base.date shouldBe IfcDate.of(year, 13, 29)
            checked++
            for (month in IfcMonth.entries) {
                for (day in listOf(1, 13, 28)) {
                    base.withMonth(month).withDayOfMonth(day).date shouldBe IfcDate.of(year, month.number, day)
                    checked++
                }
            }
            val leap = base.withSelection(IfcDaySelection.LeapDay)
            if (base.isLeapDayOffered) {
                leap.date shouldBe IfcDate.of(year, 6, 29)
                checked++
            } else {
                leap shouldBe base
            }
        }
        // 8417 years x (Year Day + 13 months x 3 days) + one Leap Day per leap year of 1583..9999.
        checked shouldBe 8417 * 40 + 2041
    }

    @Test
    fun `the picker range is 1583-01-01 to 9999-12-31`() {
        DatePickerRange.years shouldBe 1583..9999
        DatePickerRange.contains(LocalDate.of(1583, 1, 1)) shouldBe true
        DatePickerRange.contains(LocalDate.of(9999, 12, 31)) shouldBe true
        DatePickerRange.contains(LocalDate.of(1582, 12, 31)) shouldBe false
        DatePickerRange.contains(LocalDate.of(10000, 1, 1)) shouldBe false
        DatePickerRange.contains(LocalDate.MIN) shouldBe false
        DatePickerRange.contains(LocalDate.MAX) shouldBe false
    }
}
