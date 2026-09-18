package io.github.chrisjmendoza.yearal.core.calendar

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.DateTimeException
import java.time.LocalDate

// Verifies spec §7.7 (day/week/month/year arithmetic and clamping) and docs/adr/0002-ifc-date-arithmetic.md
// (overflow surfaces as DateTimeException, never ArithmeticException).
class IfcDateArithmeticTest {
    // --- §7.7 worked-example table, quoted verbatim -----------------------------------------------------

    @Test
    fun `worked examples of section 7_7 hold exactly`() {
        assertSoftly {
            withClue("Leap Day 2024 + 1 month") {
                IfcDate.LeapDay(2024).plusMonths(1) shouldBe IfcDate.Regular(2024, IfcMonth.SOL, 28)
            }
            withClue("Leap Day 2024 - 1 month") {
                IfcDate.LeapDay(2024).minusMonths(1) shouldBe IfcDate.Regular(2024, IfcMonth.MAY, 28)
            }
            withClue("Leap Day 2024 + 1 year") {
                IfcDate.LeapDay(2024).plusYears(1) shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)
            }
            withClue("Leap Day 2024 + 4 years") {
                IfcDate.LeapDay(2024).plusYears(4) shouldBe IfcDate.LeapDay(2028)
            }
            withClue("Leap Day 2096 + 4 years") {
                IfcDate.LeapDay(2096).plusYears(4) shouldBe IfcDate.Regular(2100, IfcMonth.JUNE, 28)
            }
            withClue("Leap Day 2024 + 7 months") {
                IfcDate.LeapDay(2024).plusMonths(7) shouldBe IfcDate.YearDay(2024)
            }
            withClue("Year Day 2026 + 1 month") {
                IfcDate.YearDay(2026).plusMonths(1) shouldBe IfcDate.Regular(2027, IfcMonth.JANUARY, 28)
            }
            withClue("Year Day 2026 + 13 months = + 1 year") {
                val plusThirteenMonths = IfcDate.YearDay(2026).plusMonths(13)
                plusThirteenMonths shouldBe IfcDate.YearDay(2027)
                plusThirteenMonths shouldBe IfcDate.YearDay(2026).plusYears(1)
            }
            withClue("Year Day 2027 + 6 months") {
                IfcDate.YearDay(2027).plusMonths(6) shouldBe IfcDate.LeapDay(2028)
            }
            withClue("June 28, 2024 + 1 day") {
                IfcDate.Regular(2024, IfcMonth.JUNE, 28).plusDays(1) shouldBe IfcDate.LeapDay(2024)
            }
            withClue("June 28, 2025 + 1 day") {
                IfcDate.Regular(2025, IfcMonth.JUNE, 28).plusDays(1) shouldBe IfcDate.Regular(2025, IfcMonth.SOL, 1)
            }
        }
    }

    @Test
    fun `additional worked statements from the task brief hold`() {
        assertSoftly {
            IfcDate.LeapDay(2024).plusMonths(1) shouldBe IfcDate.Regular(2024, IfcMonth.SOL, 28)
            IfcDate.LeapDay(2024).plusYears(1) shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)
            IfcDate.LeapDay(2024).plusYears(4) shouldBe IfcDate.LeapDay(2028)
            IfcDate.YearDay(2026).plusYears(1) shouldBe IfcDate.YearDay(2027)
        }
    }

    // --- Clamping from both intercalary days, in leap and common years ----------------------------------

    @Test
    fun `Leap Day clamps to June 28 whenever the target year has no Leap Day`() {
        assertSoftly {
            for (delta in listOf(1L, 2L, 3L, 5L, 6L, 100L)) {
                val target = 2024 + delta
                if (!java.time.Year.isLeap(target)) {
                    withClue("+$delta years") {
                        IfcDate.LeapDay(2024).plusYears(delta) shouldBe
                            IfcDate.Regular(target.toInt(), IfcMonth.JUNE, 28)
                    }
                }
            }
            for (month in IfcMonth.entries) {
                if (month == IfcMonth.JUNE || month == IfcMonth.DECEMBER) continue
                withClue("Leap Day 2024 -> month ${month.number}") {
                    val delta = (month.number - IfcMonth.JUNE.number).toLong()
                    IfcDate.LeapDay(2024).plusMonths(delta) shouldBe IfcDate.Regular(2024, month, 28)
                }
            }
        }
    }

    @Test
    fun `Year Day clamps to day 28 of every month except June in a leap year and December`() {
        // delta = month.number - 13 always lands on (year = 2026, month), since IFC has 13 months and
        // Year Day's pseudo-field monthNumber is 13: index = 2026*13 + 12 + (month.number - 13) =
        // 2026*13 + (month.number - 1), and month.number - 1 is already in 0..12.
        assertSoftly {
            for (month in IfcMonth.entries) {
                if (month == IfcMonth.DECEMBER) continue
                val delta = (month.number - IfcMonth.DECEMBER.number).toLong()
                withClue("Year Day 2026 + $delta months -> ${month.name}") {
                    // 2026 is a common year, so June has no 29th either: every non-December target clamps.
                    IfcDate.YearDay(2026).plusMonths(delta) shouldBe IfcDate.Regular(2026, month, 28)
                }
            }
            // A Year Day step that lands on a LEAP year's June does not clamp (already in the worked-example
            // table: Year Day 2027 + 6 months = Leap Day 2028).
            IfcDate.YearDay(2027).plusMonths(6) shouldBe IfcDate.LeapDay(2028)
        }
    }

    @Test
    fun `regular days never clamp under month or year arithmetic`() {
        runBlocking {
            checkAll(
                PROPERTY_ITERATIONS,
                Arb.int(IfcDate.MIN_YEAR + 20..IfcDate.MAX_YEAR - 20),
                Arb.enum<IfcMonth>(),
                Arb.int(1..28),
                Arb.long(-200L..200L),
            ) { year, month, day, delta ->
                val start = IfcDate.Regular(year, month, day)
                val moved = start.plusMonths(delta)
                withClue("$start.plusMonths($delta)") {
                    moved.dayOfMonth shouldBe day
                    moved.nominalDayOfWeek shouldBe start.nominalDayOfWeek
                }
            }
        }
    }

    // --- Month arithmetic across year boundaries, negative and zero amounts -----------------------------

    @Test
    fun `plusMonths crosses year boundaries in both directions`() {
        assertSoftly {
            IfcDate.Regular(2026, IfcMonth.DECEMBER, 5).plusMonths(1) shouldBe
                IfcDate.Regular(2027, IfcMonth.JANUARY, 5)
            IfcDate.Regular(2027, IfcMonth.JANUARY, 5).plusMonths(-1) shouldBe
                IfcDate.Regular(2026, IfcMonth.DECEMBER, 5)
            IfcDate.Regular(2026, IfcMonth.JANUARY, 10).plusMonths(13) shouldBe
                IfcDate.Regular(2027, IfcMonth.JANUARY, 10)
            IfcDate.Regular(2026, IfcMonth.JANUARY, 10).plusMonths(-13) shouldBe
                IfcDate.Regular(2025, IfcMonth.JANUARY, 10)
        }
    }

    @Test
    fun `plusMonths and plusYears with a zero amount are the identity`() {
        val samples: List<IfcDate> =
            listOf(
                IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8),
                IfcDate.LeapDay(2024),
                IfcDate.YearDay(2026),
            )
        assertSoftly {
            for (date in samples) {
                withClue(date.toString()) {
                    date.plusMonths(0) shouldBe date
                    date.plusYears(0) shouldBe date
                    date.plusDays(0) shouldBe date
                    date.plusWeeks(0) shouldBe date
                }
            }
        }
    }

    @Test
    fun `plusMonths with negative amounts is the inverse direction`() {
        assertSoftly {
            IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8).plusMonths(-2) shouldBe
                IfcDate.Regular(2026, IfcMonth.JULY, 8)
            IfcDate.Regular(2026, IfcMonth.JANUARY, 8).plusMonths(-1) shouldBe
                IfcDate.Regular(2025, IfcMonth.DECEMBER, 8)
        }
    }

    // --- Day/week arithmetic equals LocalDate arithmetic, property-checked ------------------------------

    @Test
    fun `plusDays matches LocalDate plusDays for property-generated dates over the full range`() {
        runBlocking {
            checkAll(
                PROPERTY_ITERATIONS,
                Arb.int(IfcDate.MIN_YEAR..IfcDate.MAX_YEAR),
                Arb.int(1..365),
                Arb.long(-4000L..4000L),
            ) { year, dayOfYear, delta ->
                val gregorian =
                    LocalDate.ofYearDay(
                        year,
                        minOf(
                            dayOfYear,
                            java.time.Year
                                .of(year)
                                .length(),
                        ),
                    )
                val start = IfcDate.from(gregorian)
                // LocalDate itself never throws for these modest deltas (its supported range is far
                // larger than 1..9999); only the IFC year-range check can reject the result.
                val expectedGregorian = gregorian.plusDays(delta)
                if (expectedGregorian.year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) {
                    start.plusDays(delta) shouldBe IfcDate.from(expectedGregorian)
                } else {
                    shouldThrow<DateTimeException> { start.plusDays(delta) }
                }
            }
        }
    }

    @Test
    fun `plusWeeks matches LocalDate plusWeeks for property-generated dates over the full range`() {
        runBlocking {
            checkAll(
                PROPERTY_ITERATIONS,
                Arb.int(IfcDate.MIN_YEAR..IfcDate.MAX_YEAR),
                Arb.int(1..365),
                Arb.long(-500L..500L),
            ) { year, dayOfYear, weeks ->
                val gregorian =
                    LocalDate.ofYearDay(
                        year,
                        minOf(
                            dayOfYear,
                            java.time.Year
                                .of(year)
                                .length(),
                        ),
                    )
                val start = IfcDate.from(gregorian)
                val expectedGregorian = gregorian.plusWeeks(weeks)
                if (expectedGregorian.year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) {
                    start.plusWeeks(weeks) shouldBe IfcDate.from(expectedGregorian)
                } else {
                    shouldThrow<DateTimeException> { start.plusWeeks(weeks) }
                }
            }
        }
    }

    @Test
    fun `intercalary days count as ordinary days under plusDays`() {
        assertSoftly {
            IfcDate.LeapDay(2024).plusDays(1) shouldBe IfcDate.Regular(2024, IfcMonth.SOL, 1)
            IfcDate.LeapDay(2024).plusDays(-1) shouldBe IfcDate.Regular(2024, IfcMonth.JUNE, 28)
            IfcDate.YearDay(2026).plusDays(1) shouldBe IfcDate.Regular(2027, IfcMonth.JANUARY, 1)
            IfcDate.YearDay(2026).plusDays(-1) shouldBe IfcDate.Regular(2026, IfcMonth.DECEMBER, 28)
        }
    }

    @Test
    fun `plusWeeks does not preserve nominal weekday across an intercalary day`() {
        // Spec §7.7: June 25, 2024 (nominal Wed) + 1 week = Sol 3, 2024 (nominal Tue).
        val start = IfcDate.Regular(2024, IfcMonth.JUNE, 25)
        val moved = start.plusWeeks(1)
        assertSoftly {
            moved shouldBe IfcDate.Regular(2024, IfcMonth.SOL, 3)
            start.nominalDayOfWeek shouldBe java.time.DayOfWeek.WEDNESDAY
            moved.nominalDayOfWeek shouldBe java.time.DayOfWeek.TUESDAY
            moved.actualDayOfWeek shouldBe start.actualDayOfWeek
        }
    }

    // --- plusDays(n).minusDays(n) round trip -------------------------------------------------------------

    @Test
    fun `plusDays and minusDays round-trip for every shape of date`() {
        runBlocking {
            checkAll(
                PROPERTY_ITERATIONS,
                Arb.int(IfcDate.MIN_YEAR + 40..IfcDate.MAX_YEAR - 40),
                Arb.int(1..365),
                Arb.long(-3000L..3000L),
            ) { year, dayOfYear, deltaDays ->
                val gregorian =
                    LocalDate.ofYearDay(
                        year,
                        minOf(
                            dayOfYear,
                            java.time.Year
                                .of(year)
                                .length(),
                        ),
                    )
                val start = IfcDate.from(gregorian)
                start.plusDays(deltaDays).minusDays(deltaDays) shouldBe start
            }
        }
    }

    @Test
    fun `plusWeeks and minusWeeks round-trip for every shape of date`() {
        runBlocking {
            checkAll(
                PROPERTY_ITERATIONS,
                Arb.int(IfcDate.MIN_YEAR + 40..IfcDate.MAX_YEAR - 40),
                Arb.int(1..365),
                Arb.long(-1000L..1000L),
            ) { year, dayOfYear, deltaWeeks ->
                val gregorian =
                    LocalDate.ofYearDay(
                        year,
                        minOf(
                            dayOfYear,
                            java.time.Year
                                .of(year)
                                .length(),
                        ),
                    )
                val start = IfcDate.from(gregorian)
                start.plusWeeks(deltaWeeks).minusWeeks(deltaWeeks) shouldBe start
            }
        }
    }

    @Test
    fun `plusDays round trip holds for the intercalary days themselves`() {
        assertSoftly {
            val leapDay = IfcDate.LeapDay(2024)
            val yearDay = IfcDate.YearDay(2026)
            for (delta in listOf(1L, -1L, 7L, -7L, 365L, -365L, 3652058L, -3652058L)) {
                withClue("LeapDay(2024).plusDays($delta)") {
                    runCatching { leapDay.plusDays(delta) }.getOrNull()?.let { moved ->
                        moved.minusDays(delta) shouldBe leapDay
                    }
                }
                withClue("YearDay(2026).plusDays($delta)") {
                    runCatching { yearDay.plusDays(delta) }.getOrNull()?.let { moved ->
                        moved.minusDays(delta) shouldBe yearDay
                    }
                }
            }
        }
    }

    // --- Documented non-invertible operations ------------------------------------------------------------

    @Test
    fun `plusMonths and plusYears are not invertible across a clamp`() {
        // Documented in spec §7.7 and docs/adr/0002-ifc-date-arithmetic.md: a clamped step loses information,
        // so plusMonths(n).plusMonths(-n) need not return the start, unlike plusDays/plusWeeks (always exact).
        assertSoftly {
            val leapDay = IfcDate.LeapDay(2024)
            val steppedForward = leapDay.plusMonths(1)
            withClue("Leap Day 2024 +1 month -1 month") {
                steppedForward shouldBe IfcDate.Regular(2024, IfcMonth.SOL, 28)
                // Sol - 1 month is June (Sol sits right after June, spec §2.2), not back to Leap Day.
                steppedForward.plusMonths(-1) shouldBe IfcDate.Regular(2024, IfcMonth.JUNE, 28)
                (steppedForward.plusMonths(-1) == leapDay) shouldBe false
            }
            val steppedYear = leapDay.plusYears(1)
            withClue("Leap Day 2024 +1 year -1 year") {
                steppedYear shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)
                steppedYear.plusYears(-1) shouldBe IfcDate.Regular(2024, IfcMonth.JUNE, 28)
                (steppedYear.plusYears(-1) == leapDay) shouldBe false
            }
        }
    }

    @Test
    fun `plusMonths is not associative around a clamped step`() {
        // (Leap Day 2024 + 1 month) - 1 month != Leap Day 2024 + (1 month - 1 month), i.e. clamping is
        // order-dependent, exactly as java.time.LocalDate.plusMonths documents for Jan 31 + 1 month - 1 month.
        val leapDay = IfcDate.LeapDay(2024)
        val throughIntermediateStep = leapDay.plusMonths(1).plusMonths(-1)
        val direct = leapDay.plusMonths(0)
        assertSoftly {
            direct shouldBe leapDay
            throughIntermediateStep shouldBe IfcDate.Regular(2024, IfcMonth.JUNE, 28)
            (throughIntermediateStep == direct) shouldBe false
        }
    }

    // --- Range limits and Long overflow throw DateTimeException -------------------------------------------

    @Test
    fun `plusDays and plusWeeks throw on Long MIN_VALUE and MAX_VALUE`() {
        val start = IfcDate.Regular(5000, IfcMonth.SEPTEMBER, 8)
        assertSoftly {
            for (amount in listOf(Long.MIN_VALUE, Long.MAX_VALUE)) {
                withClue("plusDays($amount)") { shouldThrow<DateTimeException> { start.plusDays(amount) } }
                withClue("minusDays($amount)") { shouldThrow<DateTimeException> { start.minusDays(amount) } }
                withClue("plusWeeks($amount)") { shouldThrow<DateTimeException> { start.plusWeeks(amount) } }
                withClue("minusWeeks($amount)") { shouldThrow<DateTimeException> { start.minusWeeks(amount) } }
            }
        }
    }

    @Test
    fun `plusMonths and plusYears throw on Long MIN_VALUE and MAX_VALUE`() {
        val start = IfcDate.Regular(5000, IfcMonth.SEPTEMBER, 8)
        assertSoftly {
            for (amount in listOf(Long.MIN_VALUE, Long.MAX_VALUE)) {
                withClue("plusMonths($amount)") { shouldThrow<DateTimeException> { start.plusMonths(amount) } }
                withClue("minusMonths($amount)") { shouldThrow<DateTimeException> { start.minusMonths(amount) } }
                withClue("plusYears($amount)") { shouldThrow<DateTimeException> { start.plusYears(amount) } }
                withClue("minusYears($amount)") { shouldThrow<DateTimeException> { start.minusYears(amount) } }
            }
        }
    }

    // Note: `shouldThrow<DateTimeException>` above already fails if the wrong exception type (in
    // particular the raw `ArithmeticException` from `Math.addExact`/`Math.multiplyExact` or
    // `LocalDate.plusDays`) escapes instead — that is exactly the contract docs/adr/0002 records.

    @Test
    fun `arithmetic that would leave the supported year range throws DateTimeException`() {
        val nearMin = IfcDate.Regular(IfcDate.MIN_YEAR, IfcMonth.JANUARY, 1)
        val nearMax = IfcDate.Regular(IfcDate.MAX_YEAR, IfcMonth.DECEMBER, 28)
        // The true last day of the range: December 28 is followed by Year Day of the SAME year, so
        // plusDays/plusWeeks must step from there, not from December 28, to actually leave the range.
        val lastDayOfRange = IfcDate.YearDay(IfcDate.MAX_YEAR)
        assertSoftly {
            withClue("min year - 1 day") { shouldThrow<DateTimeException> { nearMin.plusDays(-1) } }
            withClue("min year - 1 week") { shouldThrow<DateTimeException> { nearMin.plusWeeks(-1) } }
            withClue("min year - 1 month") { shouldThrow<DateTimeException> { nearMin.plusMonths(-1) } }
            withClue("min year - 1 year") { shouldThrow<DateTimeException> { nearMin.plusYears(-1) } }
            withClue("max year + 1 day") { shouldThrow<DateTimeException> { lastDayOfRange.plusDays(1) } }
            withClue("max year + 1 week") { shouldThrow<DateTimeException> { lastDayOfRange.plusWeeks(1) } }
            withClue("max year + 1 month") { shouldThrow<DateTimeException> { nearMax.plusMonths(1) } }
            withClue("max year + 1 year") { shouldThrow<DateTimeException> { nearMax.plusYears(1) } }
            // But staying exactly at the boundary is fine.
            nearMin.plusDays(0) shouldBe nearMin
            nearMax.plusDays(0) shouldBe nearMax
            IfcDate.YearDay(IfcDate.MAX_YEAR).plusDays(-1) shouldBe
                IfcDate.Regular(IfcDate.MAX_YEAR, IfcMonth.DECEMBER, 28)
        }
    }

    private companion object {
        const val PROPERTY_ITERATIONS = 1_000
    }
}
