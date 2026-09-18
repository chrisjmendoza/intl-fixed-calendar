package io.github.chrisjmendoza.yearal.core.calendar

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year

// Verifies spec §3.1, §3.2 and the §3.4 invariants against the definitional construction of §6.1, plus §5.3.
class IfcDateOracleTest {
    @Test
    fun `conversion matches the definitional enumeration for every day of years 1 to 9999`() {
        val started = System.nanoTime()
        val sweep = Sweep()
        for (year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) sweep.visitYear(year)
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        println("IfcDateOracleTest: swept ${sweep.daysVisited} days in $millis ms")
        sweep.daysVisited shouldBe DAYS_IN_SUPPORTED_RANGE
        sweep.gregorian shouldBe LocalDate.of(IfcDate.MAX_YEAR + 1, 1, 1)
    }

    @Test
    fun `fixed anchors of invariants 5 and 6 hold in every year`() {
        for (year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) {
            val leap = Year.isLeap(year.toLong())
            expectEquals(IfcDate.from(LocalDate.of(year, 1, 1)), IfcDate.Regular(year, IfcMonth.JANUARY, 1))
            expectEquals(IfcDate.from(LocalDate.of(year, 12, 31)), IfcDate.YearDay(year))
            expectEquals(IfcDate.from(LocalDate.of(year, 12, 30)), IfcDate.Regular(year, IfcMonth.DECEMBER, 28))
            expectEquals(IfcDate.from(LocalDate.of(year, 6, 18)), IfcDate.Regular(year, IfcMonth.SOL, 1))
            val june17 = IfcDate.from(LocalDate.of(year, 6, 17))
            if (leap) {
                expectEquals(june17, IfcDate.LeapDay(year))
            } else {
                expectEquals(june17, IfcDate.Regular(year, IfcMonth.JUNE, 28))
            }
            expectEquals(IfcDate.Regular(year, IfcMonth.JANUARY, 1).toLocalDate(), LocalDate.of(year, 1, 1))
            expectEquals(IfcDate.YearDay(year).toLocalDate(), LocalDate.of(year, 12, 31))
            expectEquals(IfcDate.Regular(year, IfcMonth.DECEMBER, 28).toLocalDate(), LocalDate.of(year, 12, 30))
            expectEquals(IfcDate.Regular(year, IfcMonth.SOL, 1).toLocalDate(), LocalDate.of(year, 6, 18))
            val june28 = IfcDate.Regular(year, IfcMonth.JUNE, 28).toLocalDate()
            expectEquals(june28, LocalDate.of(year, 6, if (leap) 16 else 17))
        }
    }

    @Test
    fun `Leap Day exists exactly in the Gregorian leap years`() {
        for (year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) {
            if (Year.isLeap(year.toLong())) {
                expectEquals(IfcDate.LeapDay(year).toLocalDate(), LocalDate.of(year, 6, 17))
            } else {
                withClue("LeapDay($year)") { shouldThrow<DateTimeException> { IfcDate.LeapDay(year) } }
                withClue("of($year, 6, 29)") { shouldThrow<DateTimeException> { IfcDate.of(year, 6, 29) } }
            }
        }
    }

    @Test
    fun `numeric form round-trips and sorts lexicographically for every day of 1899 to 2101`() {
        var gregorian = LocalDate.of(1899, 1, 1)
        val end = LocalDate.of(2101, 12, 31)
        var previousText = ""
        var days = 0
        while (!gregorian.isAfter(end)) {
            val date = IfcDate.from(gregorian)
            val text = date.toNumericString()
            ensure(text > previousText) { "numeric form not increasing: '$previousText' then '$text' ($gregorian)" }
            ensure(IfcDate.parse(text) == date) { "parse('$text') = ${IfcDate.parse(text)}, expected $date" }
            val prefixed = date.toPrefixedString()
            ensure(prefixed == "IFC $text") { "toPrefixedString() = '$prefixed', expected 'IFC $text'" }
            ensure(IfcDate.parse(prefixed) == date) { "parse('$prefixed') != $date" }
            ensure(IfcDate.of(date.year, date.monthNumber, date.dayOfMonth) == date) { "of(pseudo-fields) != $date" }
            ensure(IfcDate.ofYearDay(date.year, date.dayOfYear) == date) { "ofYearDay != $date" }
            previousText = text
            gregorian = gregorian.plusDays(1)
            days++
        }
        days shouldBe DAYS_1899_TO_2101
    }

    @Test
    fun `quarter boundaries match spec section 5_3 in a common year`() {
        assertSoftly {
            expectQuarter(LocalDate.of(2026, 1, 1), IfcDate.Regular(2026, IfcMonth.JANUARY, 1), quarter = 1, week = 1)
            expectQuarter(LocalDate.of(2026, 4, 1), IfcDate.Regular(2026, IfcMonth.APRIL, 7), quarter = 1, week = 13)
            expectQuarter(LocalDate.of(2026, 4, 2), IfcDate.Regular(2026, IfcMonth.APRIL, 8), quarter = 2, week = 14)
            expectQuarter(LocalDate.of(2026, 7, 1), IfcDate.Regular(2026, IfcMonth.SOL, 14), quarter = 2, week = 26)
            expectQuarter(LocalDate.of(2026, 7, 2), IfcDate.Regular(2026, IfcMonth.SOL, 15), quarter = 3, week = 27)
            expectQuarter(LocalDate.of(2026, 9, 30), IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 21), 3, week = 39)
            expectQuarter(LocalDate.of(2026, 10, 1), IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 22), 4, week = 40)
            expectQuarter(LocalDate.of(2026, 12, 30), IfcDate.Regular(2026, IfcMonth.DECEMBER, 28), 4, week = 52)
            expectQuarter(LocalDate.of(2026, 12, 31), IfcDate.YearDay(2026), quarter = 4, week = null)
        }
    }

    @Test
    fun `quarter boundaries match spec section 5_3 in a leap year`() {
        assertSoftly {
            expectQuarter(LocalDate.of(2024, 1, 1), IfcDate.Regular(2024, IfcMonth.JANUARY, 1), quarter = 1, week = 1)
            expectQuarter(LocalDate.of(2024, 3, 31), IfcDate.Regular(2024, IfcMonth.APRIL, 7), quarter = 1, week = 13)
            expectQuarter(LocalDate.of(2024, 4, 1), IfcDate.Regular(2024, IfcMonth.APRIL, 8), quarter = 2, week = 14)
            expectQuarter(LocalDate.of(2024, 6, 17), IfcDate.LeapDay(2024), quarter = 2, week = null)
            expectQuarter(LocalDate.of(2024, 7, 1), IfcDate.Regular(2024, IfcMonth.SOL, 14), quarter = 2, week = 26)
            expectQuarter(LocalDate.of(2024, 7, 2), IfcDate.Regular(2024, IfcMonth.SOL, 15), quarter = 3, week = 27)
            expectQuarter(LocalDate.of(2024, 9, 30), IfcDate.Regular(2024, IfcMonth.SEPTEMBER, 21), 3, week = 39)
            expectQuarter(LocalDate.of(2024, 10, 1), IfcDate.Regular(2024, IfcMonth.SEPTEMBER, 22), 4, week = 40)
            expectQuarter(LocalDate.of(2024, 12, 30), IfcDate.Regular(2024, IfcMonth.DECEMBER, 28), 4, week = 52)
            expectQuarter(LocalDate.of(2024, 12, 31), IfcDate.YearDay(2024), quarter = 4, week = null)
        }
    }

    private fun expectQuarter(
        gregorian: LocalDate,
        expected: IfcDate,
        quarter: Int,
        week: Int?,
    ) {
        withClue("from($gregorian)") {
            val actual = IfcDate.from(gregorian)
            actual shouldBe expected
            withClue("quarter") { actual.quarter shouldBe quarter }
            withClue("weekOfYear") { actual.weekOfYear shouldBe week }
        }
        withClue("$expected.toLocalDate()") { expected.toLocalDate() shouldBe gregorian }
    }

    private fun expectEquals(
        actual: Any,
        expected: Any,
    ) {
        ensure(actual == expected) { "expected $expected but was $actual" }
    }

    private enum class Shape { REGULAR, LEAP_DAY, YEAR_DAY }

    /**
     * Walks the Gregorian calendar one day at a time next to a purely definitional IFC enumeration
     * (13 months of 28 days, Leap Day after June 28 in leap years, Year Day last). It uses no day-of-year
     * arithmetic of its own: weeks, quarters and weekdays are all obtained by counting.
     */
    private class Sweep {
        var gregorian: LocalDate = LocalDate.of(IfcDate.MIN_YEAR, 1, 1)
        var daysVisited = 0L

        // 0001-01-01 is a Monday (spec §6.4); the real week then runs unbroken through every intercalary day.
        private var realWeekday = DayOfWeek.MONDAY
        private var previous: IfcDate? = null
        private var previousTriple = 0

        private var dayOfYear = 0
        private var nominalIndex = 0
        private var week = 1
        private var regularDays = 0
        private var leapDays = 0
        private var yearDays = 0
        private val quarterLengths = IntArray(QUARTERS + 1)

        fun visitYear(year: Int) {
            val leap = Year.isLeap(year.toLong())
            dayOfYear = 0
            nominalIndex = 0
            week = 1
            regularDays = 0
            leapDays = 0
            yearDays = 0
            quarterLengths.fill(0)
            ensure(gregorian.year == year && gregorian.dayOfYear == 1) { "year $year does not start at $gregorian" }

            for (monthNumber in 1..MONTHS_IN_SPEC_ORDER.size) {
                for (dayOfMonth in 1..DAYS_PER_MONTH) visit(year, monthNumber, dayOfMonth, Shape.REGULAR)
                if (leap && monthNumber == JUNE_NUMBER) visit(year, JUNE_NUMBER, INTERCALARY_DAY, Shape.LEAP_DAY)
            }
            ensure(gregorian.monthValue == 12 && gregorian.dayOfMonth == 31) { "Year Day $year paired with $gregorian" }
            visit(year, MONTHS_IN_SPEC_ORDER.size, INTERCALARY_DAY, Shape.YEAR_DAY)

            ensure(regularDays == REGULAR_DAYS_PER_YEAR) { "year $year has $regularDays regular days" }
            ensure(yearDays == 1) { "year $year has $yearDays Year Days" }
            ensure(leapDays == (if (leap) 1 else 0)) { "year $year (leap=$leap) has $leapDays Leap Days" }
            ensure(nominalIndex == 0 && week == WEEKS_PER_YEAR + 1) { "year $year does not have 52 whole weeks" }
            ensure(dayOfYear == Year.of(year).length()) { "year $year has $dayOfYear days" }
            val expectedLengths = intArrayOf(0, 91, if (leap) 92 else 91, 91, 92)
            ensure(quarterLengths.contentEquals(expectedLengths)) {
                "year $year quarter lengths ${quarterLengths.drop(1)}, expected ${expectedLengths.drop(1)}"
            }
        }

        private fun visit(
            year: Int,
            monthNumber: Int,
            dayOfMonth: Int,
            shape: Shape,
        ) {
            dayOfYear++
            daysVisited++
            val g = gregorian
            val expected: IfcDate =
                when (shape) {
                    Shape.REGULAR -> IfcDate.Regular(year, MONTHS_IN_SPEC_ORDER[monthNumber - 1], dayOfMonth)
                    Shape.LEAP_DAY -> IfcDate.LeapDay(year)
                    Shape.YEAR_DAY -> IfcDate.YearDay(year)
                }
            val actual = IfcDate.from(g)

            // Invariant 1 (both directions) and invariant 2.
            ensure(actual == expected) { "from($g) = $actual, expected $expected" }
            ensure(expected.toLocalDate() == g) { "$expected.toLocalDate() = ${expected.toLocalDate()}, expected $g" }
            ensure(actual.toLocalDate() == g) { "from($g).toLocalDate() = ${actual.toLocalDate()}" }
            ensure(actual.year == year && g.year == year) { "from($g).year = ${actual.year}" }
            ensure(actual.dayOfYear == dayOfYear && g.dayOfYear == dayOfYear) {
                "from($g).dayOfYear = ${actual.dayOfYear}, expected $dayOfYear"
            }
            ensure(actual.monthNumber == monthNumber) { "from($g).monthNumber = ${actual.monthNumber}" }
            ensure(actual.dayOfMonth == dayOfMonth) { "from($g).dayOfMonth = ${actual.dayOfMonth}" }
            ensure(actual.isIntercalary == (shape != Shape.REGULAR)) { "from($g).isIntercalary wrong" }

            // Invariant 8, against both java.time and an unbroken seven-day count.
            ensure(g.dayOfWeek == realWeekday) { "oracle weekday drifted at $g" }
            ensure(actual.actualDayOfWeek == realWeekday) {
                "from($g).actualDayOfWeek = ${actual.actualDayOfWeek}, expected $realWeekday"
            }

            verifyWeekFields(actual, g, dayOfMonth, shape)
            verifyOrdering(actual, g)
            count(actual)

            previous = actual
            gregorian = g.plusDays(1)
            realWeekday = realWeekday.plus(1)
        }

        private fun verifyWeekFields(
            actual: IfcDate,
            g: LocalDate,
            dayOfMonth: Int,
            shape: Shape,
        ) {
            val expectedNominal: DayOfWeek?
            val expectedWeek: Int?
            val expectedQuarter: Int
            when (shape) {
                Shape.REGULAR -> {
                    expectedNominal = SUNDAY_FIRST_WEEK[nominalIndex]
                    expectedWeek = week
                    expectedQuarter = (week - 1) / WEEKS_PER_QUARTER + 1
                    nominalIndex++
                    if (nominalIndex == SUNDAY_FIRST_WEEK.size) {
                        nominalIndex = 0
                        week++
                    }
                }

                Shape.LEAP_DAY, Shape.YEAR_DAY -> {
                    ensure(nominalIndex == 0) { "intercalary day at $g is not between a Saturday and a Sunday" }
                    expectedNominal = null
                    expectedWeek = null
                    expectedQuarter = if (shape == Shape.LEAP_DAY) 2 else QUARTERS
                }
            }
            ensure(actual.nominalDayOfWeek == expectedNominal) {
                "from($g).nominalDayOfWeek = ${actual.nominalDayOfWeek}, expected $expectedNominal"
            }
            ensure(actual.weekOfYear == expectedWeek) {
                "from($g).weekOfYear = ${actual.weekOfYear}, expected $expectedWeek"
            }
            ensure(actual.quarter == expectedQuarter) {
                "from($g).quarter = ${actual.quarter}, expected $expectedQuarter"
            }
            quarterLengths[expectedQuarter]++

            // Invariant 7, stated literally.
            if (shape == Shape.REGULAR) {
                val fixed =
                    when (dayOfMonth) {
                        1 -> DayOfWeek.SUNDAY
                        13 -> DayOfWeek.FRIDAY
                        28 -> DayOfWeek.SATURDAY
                        else -> actual.nominalDayOfWeek
                    }
                ensure(actual.nominalDayOfWeek == fixed) { "from($g): day $dayOfMonth must be a nominal $fixed" }
            }
        }

        // Invariant 3: by compareTo and by the numeric triple with intercalary days as day 29.
        private fun verifyOrdering(
            actual: IfcDate,
            g: LocalDate,
        ) {
            val triple = actual.year * TRIPLE_YEAR_FACTOR + actual.monthNumber * TRIPLE_MONTH_FACTOR + actual.dayOfMonth
            val before = previous
            if (before != null) {
                ensure(before < actual && actual > before) { "compareTo not increasing: $before then $actual ($g)" }
                ensure(triple > previousTriple) { "numeric triple not increasing: $previousTriple then $triple ($g)" }
            }
            ensure(actual.compareTo(actual) == 0) { "$actual does not compare equal to itself" }
            previousTriple = triple
        }

        // Invariant 4, counted from what the implementation actually returned.
        private fun count(actual: IfcDate) {
            when (actual) {
                is IfcDate.Regular -> regularDays++
                is IfcDate.LeapDay -> leapDays++
                is IfcDate.YearDay -> yearDays++
            }
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val DAYS_IN_SUPPORTED_RANGE = 3_652_059L
        const val DAYS_1899_TO_2101 = 74_144
        const val DAYS_PER_MONTH = 28
        const val INTERCALARY_DAY = 29
        const val JUNE_NUMBER = 6
        const val REGULAR_DAYS_PER_YEAR = 364
        const val WEEKS_PER_YEAR = 52
        const val WEEKS_PER_QUARTER = 13
        const val QUARTERS = 4
        const val TRIPLE_YEAR_FACTOR = 10_000
        const val TRIPLE_MONTH_FACTOR = 100

        // Spec §2.2 R4, written out so the oracle does not depend on the enum's declaration order.
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

        // Spec §2.3 R5: every IFC week runs Sunday to Saturday.
        val SUNDAY_FIRST_WEEK =
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            )
    }
}

private inline fun ensure(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw AssertionError(message())
}
