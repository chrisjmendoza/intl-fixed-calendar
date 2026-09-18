package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.DateTimeException
import java.time.LocalDate

// IfcRecurrence construction and anchoring. Gregorian dates of IFC positions come from
// docs/holidays-and-import.md §5.1 (Sol 1 = June 18, Year Day = December 31, Leap Day = June 17 of leap
// years, IFC June 28 = June 17 in common years and June 16 in leap years) and docs/ARCHITECTURE.md §4
// (Sol 13, 2026 = June 30, 2026).
class IfcRecurrenceTest {
    private val sol13In2026 = LocalDate.of(2026, 6, 30)
    private val yearDay2026 = LocalDate.of(2026, 12, 31)
    private val leapDay2024 = LocalDate.of(2024, 6, 17)

    @Test
    fun `rejects days outside 1 to 28 and intervals below 1`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { IfcRecurrence.YearlyOnDate(IfcMonth.JUNE, 29) }
            shouldThrow<IllegalArgumentException> { IfcRecurrence.YearlyOnDate(IfcMonth.JUNE, 0) }
            shouldThrow<IllegalArgumentException> { IfcRecurrence.MonthlyOnDay(29) }
            shouldThrow<IllegalArgumentException> { IfcRecurrence.MonthlyOnDay(0) }
            shouldThrow<IllegalArgumentException> { IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13, interval = 0) }
            shouldThrow<IllegalArgumentException> { IfcRecurrence.MonthlyOnDay(13, interval = -1) }
            shouldThrow<IllegalArgumentException> {
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay, interval = 0)
            }
            shouldThrow<IllegalArgumentException> { RecurrenceEnd.Count(0) }
            shouldThrow<IllegalArgumentException> { RecurrenceEnd.Until(LocalDate.of(10_000, 1, 1)) }
            shouldThrow<IllegalArgumentException> { RecurrenceEnd.Until(LocalDate.of(0, 12, 31)) }
        }
    }

    @Test
    fun `defaults are interval 1, never ending, and JUNE_28 for Leap Day`() {
        val rule = IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay())
        assertSoftly {
            rule.interval shouldBe 1
            rule.end shouldBe RecurrenceEnd.Never
            (rule.day as IntercalaryDay.LeapDay).commonYearPolicy shouldBe LeapDayPolicy.JUNE_28
        }
    }

    @Test
    fun `a yearly date rule is anchored only on its own IFC month and day`() {
        val sol13 = IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13)
        assertSoftly {
            sol13.isAnchoredOn(sol13In2026) shouldBe true
            // Sol 13 is on the same Gregorian date in a leap year: it is after Leap Day.
            sol13.isAnchoredOn(LocalDate.of(2028, 6, 30)) shouldBe true
            sol13.isAnchoredOn(LocalDate.of(2026, 6, 29)) shouldBe false
            // July 13 (IFC month 8) is not Sol 13.
            sol13.isAnchoredOn(LocalDate.of(2026, 7, 28)) shouldBe false
            sol13.isAnchoredOn(yearDay2026) shouldBe false
            sol13.isAnchoredOn(leapDay2024) shouldBe false
        }
    }

    @Test
    fun `IFC June 28 is June 17 in a common year but June 16 in a leap year`() {
        val june28 = IfcRecurrence.YearlyOnDate(IfcMonth.JUNE, 28)
        assertSoftly {
            june28.isAnchoredOn(LocalDate.of(2026, 6, 17)) shouldBe true
            june28.isAnchoredOn(LocalDate.of(2024, 6, 16)) shouldBe true
            // June 17 of a leap year is Leap Day, which no regular rule is anchored on.
            june28.isAnchoredOn(leapDay2024) shouldBe false
        }
    }

    @Test
    fun `intercalary rules are anchored only on the real intercalary day`() {
        val yearDay = IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay)
        assertSoftly {
            yearDay.isAnchoredOn(yearDay2026) shouldBe true
            yearDay.isAnchoredOn(LocalDate.of(2024, 12, 31)) shouldBe true
            yearDay.isAnchoredOn(LocalDate.of(2026, 12, 30)) shouldBe false
            yearDay.isAnchoredOn(leapDay2024) shouldBe false

            for (policy in LeapDayPolicy.entries) {
                val leapDay = IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(policy))
                withClue(policy) {
                    leapDay.isAnchoredOn(leapDay2024) shouldBe true
                    leapDay.isAnchoredOn(LocalDate.of(2028, 6, 17)) shouldBe true
                    // The fallback dates of a common year are produced later, never chosen as the anchor.
                    leapDay.isAnchoredOn(LocalDate.of(2026, 6, 17)) shouldBe false
                    leapDay.isAnchoredOn(LocalDate.of(2026, 6, 18)) shouldBe false
                    // 2100 is a common year: June 17, 2100 is IFC June 28.
                    leapDay.isAnchoredOn(LocalDate.of(2100, 6, 17)) shouldBe false
                    leapDay.isAnchoredOn(yearDay2026) shouldBe false
                }
            }
        }
    }

    @Test
    fun `a monthly rule is anchored on its day in any month and never on an intercalary day`() {
        val thirteenth = IfcRecurrence.MonthlyOnDay(13)
        assertSoftly {
            thirteenth.isAnchoredOn(sol13In2026) shouldBe true
            // January 13 is the same in both calendars.
            thirteenth.isAnchoredOn(LocalDate.of(2026, 1, 13)) shouldBe true
            thirteenth.isAnchoredOn(LocalDate.of(2026, 1, 14)) shouldBe false
            IfcRecurrence.MonthlyOnDay(28).isAnchoredOn(yearDay2026) shouldBe false
            IfcRecurrence.MonthlyOnDay(28).isAnchoredOn(leapDay2024) shouldBe false
        }
    }

    @Test
    fun `anchoring answers false instead of throwing outside years 1 to 9999`() {
        val beyond = LocalDate.of(10_000, 1, 1)
        assertSoftly {
            IfcRecurrence.YearlyOnDate(IfcMonth.JANUARY, 1).isAnchoredOn(beyond) shouldBe false
            IfcRecurrence.MonthlyOnDay(1).isAnchoredOn(beyond) shouldBe false
            IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay).isAnchoredOn(LocalDate.of(0, 12, 31)) shouldBe
                false
        }
    }

    @Test
    fun `yearlyOn builds the rule for a regular day, Year Day and Leap Day`() {
        assertSoftly {
            IfcRecurrence.yearlyOn(sol13In2026) shouldBe IfcRecurrence.YearlyOnDate(IfcMonth.SOL, 13)
            IfcRecurrence.yearlyOn(yearDay2026) shouldBe IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay)
            IfcRecurrence.yearlyOn(leapDay2024) shouldBe
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(LeapDayPolicy.JUNE_28))
            IfcRecurrence.yearlyOn(leapDay2024, leapDayPolicy = LeapDayPolicy.SOL_1, interval = 4) shouldBe
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.LeapDay(LeapDayPolicy.SOL_1), interval = 4)
            // The policy is ignored where it means nothing.
            IfcRecurrence.yearlyOn(yearDay2026, leapDayPolicy = LeapDayPolicy.SKIP) shouldBe
                IfcRecurrence.YearlyOnIntercalary(IntercalaryDay.YearDay)
            // A Gregorian Feb 29 birthday is IFC March 4 (holidays-and-import §5.1).
            IfcRecurrence.yearlyOn(LocalDate.of(2024, 2, 29)) shouldBe IfcRecurrence.YearlyOnDate(IfcMonth.MARCH, 4)
            shouldThrow<DateTimeException> { IfcRecurrence.yearlyOn(LocalDate.of(10_000, 1, 1)) }
        }
    }

    @Test
    fun `monthlyOn is null for the intercalary days`() {
        assertSoftly {
            IfcRecurrence.monthlyOn(sol13In2026, interval = 2) shouldBe IfcRecurrence.MonthlyOnDay(13, interval = 2)
            IfcRecurrence.monthlyOn(yearDay2026).shouldBeNull()
            IfcRecurrence.monthlyOn(leapDay2024).shouldBeNull()
        }
    }

    @Test
    fun `the rule built from any date is anchored on it and names its IFC position`() {
        runBlocking {
            checkAll(2_000, Arb.int(1583..9999), Arb.int(1..366)) { year, dayOfYear ->
                val length = if (java.time.Year.isLeap(year.toLong())) 366 else 365
                val date = LocalDate.ofYearDay(year, minOf(dayOfYear, length))
                val ifc = IfcDate.from(date)
                val yearly = IfcRecurrence.yearlyOn(date)
                withClue("$date = ${ifc.toPrefixedString()}") {
                    yearly.isAnchoredOn(date) shouldBe true
                    when (ifc) {
                        is IfcDate.Regular -> {
                            yearly shouldBe IfcRecurrence.YearlyOnDate(ifc.month, ifc.dayOfMonth)
                            IfcRecurrence.monthlyOn(date) shouldBe IfcRecurrence.MonthlyOnDay(ifc.dayOfMonth)
                            IfcRecurrence.MonthlyOnDay(ifc.dayOfMonth).isAnchoredOn(date) shouldBe true
                        }

                        is IfcDate.LeapDay -> {
                            date shouldBe LocalDate.of(year, 6, 17)
                            IfcRecurrence.monthlyOn(date).shouldBeNull()
                        }

                        is IfcDate.YearDay -> {
                            date shouldBe LocalDate.of(year, 12, 31)
                            IfcRecurrence.monthlyOn(date).shouldBeNull()
                        }
                    }
                }
            }
        }
    }
}
