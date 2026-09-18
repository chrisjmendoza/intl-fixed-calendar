package io.github.chrisjmendoza.yearal.core.calendar

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit
import java.util.Random

// Verifies spec §2.2–§2.4 and the §5.1/§5.2 month tables as exposed by IfcYearMonth, plus §7.7 month stepping.
class IfcYearMonthTest {
    @Test
    fun `gregorianRange is 28 days long or 29 for June in leap years and every December`() {
        assertSoftly {
            for (year in SPREAD_OF_YEARS) {
                for (month in IfcMonth.entries) {
                    val range = IfcYearMonth(year, month).gregorianRange
                    val length = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
                    val hasIntercalary =
                        month == IfcMonth.DECEMBER || (month == IfcMonth.JUNE && Year.isLeap(year.toLong()))
                    withClue("$year $month: $range") { length shouldBe if (hasIntercalary) 29L else 28L }
                }
            }
        }
    }

    @Test
    fun `gregorianRange matches the month tables of sections 5_1 and 5_2`() {
        assertSoftly {
            expectRange(2026, IfcMonth.JANUARY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 28))
            expectRange(2026, IfcMonth.MARCH, LocalDate.of(2026, 2, 26), LocalDate.of(2026, 3, 25))
            expectRange(2026, IfcMonth.JUNE, LocalDate.of(2026, 5, 21), LocalDate.of(2026, 6, 17))
            expectRange(2026, IfcMonth.SOL, LocalDate.of(2026, 6, 18), LocalDate.of(2026, 7, 15))
            expectRange(2026, IfcMonth.SEPTEMBER, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 7))
            expectRange(2026, IfcMonth.DECEMBER, LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 31))
            expectRange(2024, IfcMonth.MARCH, LocalDate.of(2024, 2, 26), LocalDate.of(2024, 3, 24))
            expectRange(2024, IfcMonth.APRIL, LocalDate.of(2024, 3, 25), LocalDate.of(2024, 4, 21))
            expectRange(2024, IfcMonth.JUNE, LocalDate.of(2024, 5, 20), LocalDate.of(2024, 6, 17))
            expectRange(2024, IfcMonth.SOL, LocalDate.of(2024, 6, 18), LocalDate.of(2024, 7, 15))
            expectRange(2024, IfcMonth.DECEMBER, LocalDate.of(2024, 12, 3), LocalDate.of(2024, 12, 31))
        }
    }

    @Test
    fun `the thirteen ranges tile every year from January 1 to December 31 without gaps or overlaps`() {
        for (year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) {
            var expectedStart = LocalDate.of(year, 1, 1)
            for (month in MONTHS_IN_SPEC_ORDER) {
                val range = IfcYearMonth(year, month).gregorianRange
                if (range.start != expectedStart) throw AssertionError("$year $month starts ${range.start}")
                if (range.endInclusive < range.start) throw AssertionError("$year $month is empty: $range")
                expectedStart = range.endInclusive.plusDays(1)
            }
            if (expectedStart != LocalDate.of(year + 1, 1, 1)) throw AssertionError("$year ends before $expectedStart")
        }
    }

    @Test
    fun `every Gregorian day of a range belongs to that month and to no other`() {
        for (year in SPREAD_OF_YEARS) {
            var day = LocalDate.of(year, 1, 1)
            while (day.year == year) {
                val owner = IfcYearMonth.from(IfcDate.from(day))
                for (month in IfcMonth.entries) {
                    val candidate = IfcYearMonth(year, month)
                    withClue("$day in $candidate") { (day in candidate.gregorianRange) shouldBe (candidate == owner) }
                }
                day = day.plusDays(1)
            }
        }
    }

    @Test
    fun `days lists the 28 regular days in order followed by the trailing intercalary day`() {
        for (year in SPREAD_OF_YEARS) {
            for (month in IfcMonth.entries) {
                val yearMonth = IfcYearMonth(year, month)
                val days = yearMonth.days
                withClue(yearMonth.toString()) {
                    val expected: List<IfcDate> =
                        (1..28).map { IfcDate.Regular(year, month, it) } + listOfNotNull(yearMonth.trailingIntercalary)
                    days shouldBe expected
                    days.sorted() shouldBe days
                    days.first() shouldBe yearMonth.firstDay
                    days[27] shouldBe yearMonth.lastRegularDay
                    days.map { it.toLocalDate() } shouldBe
                        days.indices.map { yearMonth.gregorianRange.start.plusDays(it.toLong()) }
                    days.last().toLocalDate() shouldBe yearMonth.gregorianRange.endInclusive
                    days.forEach { IfcYearMonth.from(it) shouldBe yearMonth }
                }
            }
        }
    }

    @Test
    fun `firstDay is a nominal Sunday and lastRegularDay a nominal Saturday`() {
        assertSoftly {
            for (month in IfcMonth.entries) {
                val yearMonth = IfcYearMonth(2024, month)
                yearMonth.firstDay shouldBe IfcDate.Regular(2024, month, 1)
                yearMonth.firstDay.nominalDayOfWeek shouldBe DayOfWeek.SUNDAY
                yearMonth.lastRegularDay shouldBe IfcDate.Regular(2024, month, 28)
                yearMonth.lastRegularDay.nominalDayOfWeek shouldBe DayOfWeek.SATURDAY
            }
        }
    }

    @Test
    fun `trailingIntercalary is Leap Day after June in leap years and Year Day after every December`() {
        assertSoftly {
            for (year in SPREAD_OF_YEARS) {
                for (month in IfcMonth.entries) {
                    val expected: IfcDate? =
                        when {
                            month == IfcMonth.DECEMBER -> IfcDate.YearDay(year)
                            month == IfcMonth.JUNE && Year.isLeap(year.toLong()) -> IfcDate.LeapDay(year)
                            else -> null
                        }
                    withClue("$year $month") { IfcYearMonth(year, month).trailingIntercalary shouldBe expected }
                }
            }
            IfcYearMonth(2024, IfcMonth.JUNE).trailingIntercalary shouldBe IfcDate.LeapDay(2024)
            IfcYearMonth(2000, IfcMonth.JUNE).trailingIntercalary shouldBe IfcDate.LeapDay(2000)
            IfcYearMonth(2025, IfcMonth.JUNE).trailingIntercalary shouldBe null
            IfcYearMonth(1900, IfcMonth.JUNE).trailingIntercalary shouldBe null
            IfcYearMonth(2100, IfcMonth.JUNE).trailingIntercalary shouldBe null
            IfcYearMonth(2024, IfcMonth.SOL).trailingIntercalary shouldBe null
            IfcYearMonth(2026, IfcMonth.DECEMBER).trailingIntercalary shouldBe IfcDate.YearDay(2026)
        }
    }

    @Test
    fun `actualDayOfWeek of a column is the real weekday of every day in that column`() {
        for (year in SPREAD_OF_YEARS) {
            for (month in IfcMonth.entries) {
                val yearMonth = IfcYearMonth(year, month)
                for (day in 1..28) {
                    val column = (day - 1) % 7
                    val gregorian = IfcDate.Regular(year, month, day).toLocalDate()
                    withClue("$yearMonth day $day column $column") {
                        yearMonth.actualDayOfWeek(column) shouldBe gregorian.dayOfWeek
                    }
                }
            }
        }
    }

    @Test
    fun `actualDayOfWeek matches the worked example of section 4_1`() {
        // September 2026: nominal Sun Mon Tue ... sits above actual Thu Fri Sat ...
        val september = IfcYearMonth(2026, IfcMonth.SEPTEMBER)
        (0..6).map { september.actualDayOfWeek(it) } shouldBe
            listOf(
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
            )
        // In a leap year the offset shifts by one from Sol 1 onward: June 1, 2024 is a Monday, Sol 1 a Tuesday.
        IfcYearMonth(2024, IfcMonth.JUNE).actualDayOfWeek(0) shouldBe DayOfWeek.MONDAY
        IfcYearMonth(2024, IfcMonth.SOL).actualDayOfWeek(0) shouldBe DayOfWeek.TUESDAY
    }

    @Test
    fun `actualDayOfWeek rejects columns outside 0 to 6`() {
        val yearMonth = IfcYearMonth(2026, IfcMonth.SOL)
        assertSoftly {
            for (column in listOf(-1, 7, 8, 28, Int.MIN_VALUE, Int.MAX_VALUE)) {
                withClue("column $column") {
                    shouldThrow<IllegalArgumentException> { yearMonth.actualDayOfWeek(column) }
                }
            }
        }
    }

    @Test
    fun `plusMonths steps through the months of rule R4 and across year boundaries`() {
        assertSoftly {
            IfcYearMonth(2026, IfcMonth.JUNE).plusMonths(1) shouldBe IfcYearMonth(2026, IfcMonth.SOL)
            IfcYearMonth(2026, IfcMonth.SOL).plusMonths(1) shouldBe IfcYearMonth(2026, IfcMonth.JULY)
            IfcYearMonth(2026, IfcMonth.DECEMBER).plusMonths(1) shouldBe IfcYearMonth(2027, IfcMonth.JANUARY)
            IfcYearMonth(2027, IfcMonth.JANUARY).plusMonths(-1) shouldBe IfcYearMonth(2026, IfcMonth.DECEMBER)
            IfcYearMonth(2026, IfcMonth.JANUARY).plusMonths(12) shouldBe IfcYearMonth(2026, IfcMonth.DECEMBER)
            IfcYearMonth(2026, IfcMonth.JANUARY).plusMonths(-12) shouldBe IfcYearMonth(2025, IfcMonth.FEBRUARY)
            IfcYearMonth(2026, IfcMonth.SEPTEMBER).plusMonths(0) shouldBe IfcYearMonth(2026, IfcMonth.SEPTEMBER)
            IfcYearMonth(2026, IfcMonth.SEPTEMBER).plusMonths(7) shouldBe IfcYearMonth(2027, IfcMonth.APRIL)
            IfcYearMonth(2026, IfcMonth.MARCH).plusMonths(-7) shouldBe IfcYearMonth(2025, IfcMonth.AUGUST)
            for (month in IfcMonth.entries) {
                withClue(month.name) {
                    IfcYearMonth(2026, month).plusMonths(13) shouldBe IfcYearMonth(2027, month)
                    IfcYearMonth(2026, month).plusMonths(-13) shouldBe IfcYearMonth(2025, month)
                    IfcYearMonth(2026, month).plusMonths(1300) shouldBe IfcYearMonth(2126, month)
                }
            }
        }
    }

    @Test
    fun `plusMonths agrees with stepping one month at a time`() {
        val start = IfcYearMonth(2020, IfcMonth.JANUARY)
        var forward = start
        var backward = start
        for (steps in 1..400L) {
            forward = successor(forward)
            backward = predecessor(backward)
            withClue("$steps steps") {
                start.plusMonths(steps) shouldBe forward
                start.plusMonths(-steps) shouldBe backward
                forward.plusMonths(1) shouldBe successor(forward)
                backward.plusMonths(-1) shouldBe predecessor(backward)
            }
        }
    }

    @Test
    fun `plusMonths is inverted by the negated amount and lands on the expected month`() {
        runBlocking {
            checkAll(
                PROPERTY_ITERATIONS,
                Arb.int(IfcDate.MIN_YEAR..IfcDate.MAX_YEAR),
                Arb.enum<IfcMonth>(),
                Arb.long(-150_000L..150_000L),
            ) { year, month, months ->
                val start = IfcYearMonth(year, month)
                val index = year * 13L + MONTHS_IN_SPEC_ORDER.indexOf(month) + months
                if (index in FIRST_MONTH_INDEX..LAST_MONTH_INDEX) {
                    val moved = start.plusMonths(months)
                    moved.year shouldBe Math.floorDiv(index, 13L).toInt()
                    moved.month shouldBe MONTHS_IN_SPEC_ORDER[Math.floorMod(index, 13L).toInt()]
                    moved.plusMonths(-months) shouldBe start
                    Integer.signum(moved.compareTo(start)) shouldBe java.lang.Long.signum(months)
                } else {
                    shouldThrow<DateTimeException> { start.plusMonths(months) }
                }
            }
        }
    }

    @Test
    fun `plusMonths composes additively`() {
        runBlocking {
            checkAll(
                PROPERTY_ITERATIONS,
                Arb.int(1000..9000),
                Arb.enum<IfcMonth>(),
                Arb.long(-5_000L..5_000L),
                Arb.long(-5_000L..5_000L),
            ) { year, month, first, second ->
                val start = IfcYearMonth(year, month)
                start.plusMonths(first).plusMonths(second) shouldBe start.plusMonths(first + second)
            }
        }
    }

    @Test
    fun `plusMonths outside the supported years throws DateTimeException`() {
        val first = IfcYearMonth(IfcDate.MIN_YEAR, IfcMonth.JANUARY)
        val last = IfcYearMonth(IfcDate.MAX_YEAR, IfcMonth.DECEMBER)
        assertSoftly {
            first.plusMonths(LAST_MONTH_INDEX - FIRST_MONTH_INDEX) shouldBe last
            last.plusMonths(FIRST_MONTH_INDEX - LAST_MONTH_INDEX) shouldBe first
            IfcYearMonth(IfcDate.MAX_YEAR, IfcMonth.NOVEMBER).plusMonths(1) shouldBe last
            IfcYearMonth(IfcDate.MIN_YEAR, IfcMonth.FEBRUARY).plusMonths(-1) shouldBe first
            val overflowing =
                listOf(
                    last to 1L,
                    last to 13L,
                    last to Long.MAX_VALUE,
                    last to Long.MIN_VALUE,
                    first to -1L,
                    first to -13L,
                    first to Long.MIN_VALUE,
                    first to Long.MAX_VALUE,
                    first to LAST_MONTH_INDEX - FIRST_MONTH_INDEX + 1,
                    IfcYearMonth(2026, IfcMonth.SOL) to Long.MAX_VALUE,
                    IfcYearMonth(2026, IfcMonth.SOL) to Long.MIN_VALUE,
                    IfcYearMonth(2026, IfcMonth.SOL) to Int.MAX_VALUE.toLong(),
                    IfcYearMonth(2026, IfcMonth.SOL) to Int.MIN_VALUE.toLong(),
                )
            for ((start, months) in overflowing) {
                withClue("$start.plusMonths($months)") { shouldThrow<DateTimeException> { start.plusMonths(months) } }
            }
        }
    }

    @Test
    fun `the constructor rejects years outside 1 to 9999`() {
        assertSoftly {
            for (year in listOf(0, -1, 10000, Int.MIN_VALUE, Int.MAX_VALUE)) {
                withClue("year $year") { shouldThrow<DateTimeException> { IfcYearMonth(year, IfcMonth.JANUARY) } }
            }
            IfcYearMonth(1, IfcMonth.JANUARY).year shouldBe 1
            IfcYearMonth(9999, IfcMonth.DECEMBER).year shouldBe 9999
        }
    }

    @Test
    fun `from maps intercalary days to the month they follow`() {
        assertSoftly {
            IfcYearMonth.from(IfcDate.LeapDay(2024)) shouldBe IfcYearMonth(2024, IfcMonth.JUNE)
            IfcYearMonth.from(IfcDate.YearDay(2026)) shouldBe IfcYearMonth(2026, IfcMonth.DECEMBER)
            IfcYearMonth.from(IfcDate.YearDay(2024)) shouldBe IfcYearMonth(2024, IfcMonth.DECEMBER)
            IfcYearMonth.from(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)) shouldBe
                IfcYearMonth(2026, IfcMonth.SEPTEMBER)
            IfcYearMonth.from(IfcDate.from(LocalDate.of(2026, 9, 17))) shouldBe IfcYearMonth(2026, IfcMonth.SEPTEMBER)
            IfcYearMonth.from(IfcDate.from(LocalDate.of(2024, 6, 17))) shouldBe IfcYearMonth(2024, IfcMonth.JUNE)
            IfcYearMonth.from(IfcDate.from(LocalDate.of(2024, 6, 18))) shouldBe IfcYearMonth(2024, IfcMonth.SOL)
            IfcYearMonth.from(IfcDate.from(LocalDate.of(2026, 12, 31))) shouldBe IfcYearMonth(2026, IfcMonth.DECEMBER)
            IfcYearMonth.from(IfcDate.from(LocalDate.of(2027, 1, 1))) shouldBe IfcYearMonth(2027, IfcMonth.JANUARY)
        }
    }

    @Test
    fun `months order chronologically`() {
        val ordered =
            listOf(2025, 2026, 2027).flatMap { year -> MONTHS_IN_SPEC_ORDER.map { IfcYearMonth(year, it) } }
        assertSoftly {
            ordered.reversed().sorted() shouldBe ordered
            ordered.shuffled(Random(SHUFFLE_SEED)).sorted() shouldBe ordered
            IfcYearMonth(2026, IfcMonth.DECEMBER) shouldBeLessThan IfcYearMonth(2027, IfcMonth.JANUARY)
            IfcYearMonth(2026, IfcMonth.JUNE) shouldBeLessThan IfcYearMonth(2026, IfcMonth.SOL)
            IfcYearMonth(2026, IfcMonth.SOL) shouldBeLessThan IfcYearMonth(2026, IfcMonth.JULY)
            IfcYearMonth(2026, IfcMonth.JULY) shouldBeGreaterThan IfcYearMonth(2026, IfcMonth.JUNE)
            IfcYearMonth(2026, IfcMonth.SOL).compareTo(IfcYearMonth(2026, IfcMonth.SOL)) shouldBe 0
            ordered.zipWithNext().forEach { (earlier, later) ->
                withClue("$earlier before $later") {
                    (earlier.gregorianRange.endInclusive < later.gregorianRange.start) shouldBe true
                }
            }
        }
    }

    private fun expectRange(
        year: Int,
        month: IfcMonth,
        start: LocalDate,
        endInclusive: LocalDate,
    ) {
        val range = IfcYearMonth(year, month).gregorianRange
        withClue("$year $month start") { range.start shouldBe start }
        withClue("$year $month end") { range.endInclusive shouldBe endInclusive }
        withClue("$year $month isEmpty") { range.isEmpty() shouldBe false }
    }

    /** The next month by rule R4, written without any index arithmetic on the enum. */
    private fun successor(yearMonth: IfcYearMonth): IfcYearMonth {
        val position = MONTHS_IN_SPEC_ORDER.indexOf(yearMonth.month)
        return if (position == MONTHS_IN_SPEC_ORDER.lastIndex) {
            IfcYearMonth(yearMonth.year + 1, MONTHS_IN_SPEC_ORDER.first())
        } else {
            IfcYearMonth(yearMonth.year, MONTHS_IN_SPEC_ORDER[position + 1])
        }
    }

    private fun predecessor(yearMonth: IfcYearMonth): IfcYearMonth {
        val position = MONTHS_IN_SPEC_ORDER.indexOf(yearMonth.month)
        return if (position == 0) {
            IfcYearMonth(yearMonth.year - 1, MONTHS_IN_SPEC_ORDER.last())
        } else {
            IfcYearMonth(yearMonth.year, MONTHS_IN_SPEC_ORDER[position - 1])
        }
    }

    private companion object {
        const val PROPERTY_ITERATIONS = 2_000
        const val SHUFFLE_SEED = 20_260_917L
        const val FIRST_MONTH_INDEX = 1 * 13L
        const val LAST_MONTH_INDEX = 9999 * 13L + 12

        val SPREAD_OF_YEARS = listOf(1, 4, 1582, 1899, 1900, 1928, 2000, 2024, 2025, 2026, 2028, 2100, 2101, 9996, 9999)

        // Spec §2.2 R4, written out so the tests do not depend on the enum's declaration order.
        val MONTHS_IN_SPEC_ORDER =
            listOf(
                IfcMonth.JANUARY,
                IfcMonth.FEBRUARY,
                IfcMonth.MARCH,
                IfcMonth.APRIL,
                IfcMonth.MAY,
                IfcMonth.JUNE,
                IfcMonth.SOL,
                IfcMonth.JULY,
                IfcMonth.AUGUST,
                IfcMonth.SEPTEMBER,
                IfcMonth.OCTOBER,
                IfcMonth.NOVEMBER,
                IfcMonth.DECEMBER,
            )
    }
}
