package io.github.chrisjmendoza.yearal.core.domain.holiday

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayRule.WeekdayRelative.Direction
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

// Verifies every rule type of docs/holidays-and-import.md §2.2 and the IFC rule of §5, each evaluated
// through the real engine on a single-holiday set. Expected dates are taken from published calendars,
// never from the implementation.
class HolidayRuleTest {
    private val engine = HolidayEngine()

    // --- rule 1: fixed --------------------------------------------------------------------------------

    @Test
    fun `fixed yields the Gregorian date every year`() {
        engine.dates(single(HolidayRule.Fixed(7, 4)), 2026) shouldBe listOf(date(2026, 7, 4))
    }

    @Test
    fun `fixed Feb 29 exists in leap years and yields nothing in common years, including 2100`() {
        val set = single(HolidayRule.Fixed(2, 29))
        assertSoftly {
            engine.dates(set, 2024) shouldBe listOf(date(2024, 2, 29))
            engine.dates(set, 2023).shouldBeEmpty()
            engine.dates(set, 2100).shouldBeEmpty()
            engine.dates(set, 2000) shouldBe listOf(date(2000, 2, 29))
        }
    }

    @Test
    fun `fixed rejects impossible month and day`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { HolidayRule.Fixed(0, 1) }
            shouldThrow<IllegalArgumentException> { HolidayRule.Fixed(13, 1) }
            shouldThrow<IllegalArgumentException> { HolidayRule.Fixed(2, 30) }
            shouldThrow<IllegalArgumentException> { HolidayRule.Fixed(4, 31) }
            shouldThrow<IllegalArgumentException> { HolidayRule.Fixed(1, 0) }
        }
    }

    // --- rule 2: nthWeekday ---------------------------------------------------------------------------

    @Test
    fun `nth weekday counts from the start of the month on real weekdays`() {
        // Thanksgiving, 4th Thursday of November (5 U.S.C. § 6103).
        val set = single(HolidayRule.NthWeekday(11, DayOfWeek.THURSDAY, 4))
        assertSoftly {
            engine.dates(set, 2019) shouldBe listOf(date(2019, 11, 28))
            engine.dates(set, 2024) shouldBe listOf(date(2024, 11, 28))
            engine.dates(set, 2025) shouldBe listOf(date(2025, 11, 27))
            engine.dates(set, 2026) shouldBe listOf(date(2026, 11, 26))
        }
    }

    @Test
    fun `negative n counts from the end of the month`() {
        // Memorial Day, last Monday of May.
        val set = single(HolidayRule.NthWeekday(5, DayOfWeek.MONDAY, -1))
        assertSoftly {
            engine.dates(set, 2025) shouldBe listOf(date(2025, 5, 26))
            engine.dates(set, 2026) shouldBe listOf(date(2026, 5, 25))
            // Second-to-last Monday.
            engine.dates(single(HolidayRule.NthWeekday(5, DayOfWeek.MONDAY, -2)), 2026) shouldBe
                listOf(date(2026, 5, 18))
        }
    }

    @Test
    fun `a fifth weekday exists only when the month has five of them`() {
        val set = single(HolidayRule.NthWeekday(3, DayOfWeek.MONDAY, 5))
        assertSoftly {
            // March 2026 starts on a Sunday: Mondays are 2, 9, 16, 23, 30.
            engine.dates(set, 2026) shouldBe listOf(date(2026, 3, 30))
            // March 2025 starts on a Saturday: Mondays are 3, 10, 17, 24, 31.
            engine.dates(set, 2025) shouldBe listOf(date(2025, 3, 31))
            // March 2024 starts on a Friday: Mondays are 4, 11, 18, 25 — no fifth.
            engine.dates(set, 2024).shouldBeEmpty()
            // Negative fifth: February 2021 (starts Monday, 28 days) has exactly four Mondays.
            engine.dates(single(HolidayRule.NthWeekday(2, DayOfWeek.MONDAY, -5)), 2021).shouldBeEmpty()
        }
    }

    @Test
    fun `nth weekday rejects n of zero and ordinals beyond five`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { HolidayRule.NthWeekday(1, DayOfWeek.MONDAY, 0) }
            shouldThrow<IllegalArgumentException> { HolidayRule.NthWeekday(1, DayOfWeek.MONDAY, 6) }
            shouldThrow<IllegalArgumentException> { HolidayRule.NthWeekday(1, DayOfWeek.MONDAY, -6) }
            shouldThrow<IllegalArgumentException> { HolidayRule.NthWeekday(13, DayOfWeek.MONDAY, 1) }
        }
    }

    // --- rule 3: weekdayRelative ----------------------------------------------------------------------

    @Test
    fun `weekday relative finds the weekday on or after and on or before a date`() {
        // Victoria Day (Canada): the Monday on or before May 24.
        val victoria = single(HolidayRule.WeekdayRelative(DayOfWeek.MONDAY, 5, 24, Direction.ON_OR_BEFORE))
        // Swedish Midsummer's Day: the Saturday in June 20–26, i.e. the Saturday on or after June 20.
        val midsummer = single(HolidayRule.WeekdayRelative(DayOfWeek.SATURDAY, 6, 20, Direction.ON_OR_AFTER))
        assertSoftly {
            engine.dates(victoria, 2025) shouldBe listOf(date(2025, 5, 19))
            engine.dates(victoria, 2026) shouldBe listOf(date(2026, 5, 18))
            // 2021-05-24 is itself a Monday: "on or before" keeps it.
            engine.dates(victoria, 2021) shouldBe listOf(date(2021, 5, 24))
            engine.dates(midsummer, 2025) shouldBe listOf(date(2025, 6, 21))
            engine.dates(midsummer, 2026) shouldBe listOf(date(2026, 6, 20))
        }
    }

    @Test
    fun `weekday relative to Feb 29 yields nothing in a common year`() {
        val set = single(HolidayRule.WeekdayRelative(DayOfWeek.MONDAY, 2, 29, Direction.ON_OR_AFTER))
        assertSoftly {
            engine.dates(set, 2024) shouldBe listOf(date(2024, 3, 4))
            engine.dates(set, 2025).shouldBeEmpty()
        }
    }

    // --- rule 4: offset -------------------------------------------------------------------------------

    @Test
    fun `black friday is thanksgiving plus one, not the fourth friday`() {
        // Nov 1 2019 is a Friday, so the 4th Friday is Nov 22 but Black Friday is Nov 29.
        engine.dates(single(blackFriday.rule), 2019) shouldBe listOf(date(2019, 11, 29))
        engine.dates(single(HolidayRule.NthWeekday(11, DayOfWeek.FRIDAY, 4)), 2019) shouldBe listOf(date(2019, 11, 22))
    }

    @Test
    fun `offset of a rule with no occurrence has none either`() {
        engine.dates(single(HolidayRule.Offset(1, HolidayRule.Fixed(2, 29))), 2025).shouldBeEmpty()
    }

    @Test
    fun `offset can cross into the previous year and nests`() {
        val newYearsEveOfPrevious = HolidayRule.Offset(-1, HolidayRule.Fixed(1, 1))
        assertSoftly {
            engine.dates(single(newYearsEveOfPrevious), 2026) shouldBe listOf(date(2025, 12, 31))
            engine.dates(single(HolidayRule.Offset(-1, newYearsEveOfPrevious)), 2026) shouldBe
                listOf(date(2025, 12, 30))
        }
    }

    // --- rule 5: easter -------------------------------------------------------------------------------

    @Test
    fun `western easter matches published dates including the earliest and latest possible`() {
        val set = single(HolidayRule.Easter(EasterCalendar.WESTERN, 0))
        val expected =
            mapOf(
                2024 to date(2024, 3, 31),
                2025 to date(2025, 4, 20),
                2026 to date(2026, 4, 5),
                2027 to date(2027, 3, 28),
                2000 to date(2000, 4, 23),
                2038 to date(2038, 4, 25),
                2285 to date(2285, 3, 22),
                1818 to date(1818, 3, 22),
                1943 to date(1943, 4, 25),
            )
        assertSoftly {
            for ((year, easter) in expected) {
                withClue("Easter $year") { engine.dates(set, year) shouldBe listOf(easter) }
            }
        }
    }

    @Test
    fun `orthodox easter matches published dates`() {
        val set = single(HolidayRule.Easter(EasterCalendar.ORTHODOX, 0))
        val expected =
            mapOf(
                2024 to date(2024, 5, 5),
                2025 to date(2025, 4, 20),
                2026 to date(2026, 4, 12),
                2027 to date(2027, 5, 2),
                // Julian–Gregorian gap grows to 14 days from 2100.
                2100 to date(2100, 5, 2),
                1899 to date(1899, 4, 30),
            )
        assertSoftly {
            for ((year, easter) in expected) {
                withClue("Orthodox Easter $year") { engine.dates(set, year) shouldBe listOf(easter) }
            }
        }
    }

    @Test
    fun `easter offsets give the movable feasts`() {
        fun western2026(offset: Int) = engine.dates(single(HolidayRule.Easter(EasterCalendar.WESTERN, offset)), 2026)
        assertSoftly {
            withClue("Good Friday 2026") { western2026(-2) shouldBe listOf(date(2026, 4, 3)) }
            withClue("Ash Wednesday 2026") { western2026(-46) shouldBe listOf(date(2026, 2, 18)) }
            withClue("Mardi Gras 2026") { western2026(-47) shouldBe listOf(date(2026, 2, 17)) }
            withClue("Pentecost 2026") { western2026(49) shouldBe listOf(date(2026, 5, 24)) }
        }
    }

    // --- rule 7: table --------------------------------------------------------------------------------

    @Test
    fun `table yields its date for listed years and nothing for missing ones`() {
        // Diwali (Lakshmi Puja) dates, curated.
        val set = single(HolidayRule.Table(mapOf(2024 to date(2024, 11, 1), 2025 to date(2025, 10, 20))))
        assertSoftly {
            engine.dates(set, 2024) shouldBe listOf(date(2024, 11, 1))
            engine.dates(set, 2025) shouldBe listOf(date(2025, 10, 20))
            engine.dates(set, 2026).shouldBeEmpty()
        }
    }

    @Test
    fun `table rejects a date outside its key year`() {
        shouldThrow<IllegalArgumentException> { HolidayRule.Table(mapOf(2024 to date(2025, 1, 1))) }
    }

    // --- rule 8: ifc ----------------------------------------------------------------------------------

    @Test
    fun `sol 1 is June 18 in common and leap years`() {
        val set = single(HolidayRule.Ifc.Regular(IfcMonth.SOL, 1))
        assertSoftly {
            engine.dates(set, 2025) shouldBe listOf(date(2025, 6, 18))
            engine.dates(set, 2028) shouldBe listOf(date(2028, 6, 18))
        }
    }

    @Test
    fun `year day is December 31 every year`() {
        val set = single(HolidayRule.Ifc.YearDay)
        assertSoftly {
            engine.dates(set, 2025) shouldBe listOf(date(2025, 12, 31))
            engine.dates(set, 2028) shouldBe listOf(date(2028, 12, 31))
            engine.dates(set, 2100) shouldBe listOf(date(2100, 12, 31))
        }
    }

    @Test
    fun `leap day is June 17 in leap years and absent in common years including 2100`() {
        val set = single(HolidayRule.Ifc.LeapDay)
        assertSoftly {
            engine.dates(set, 2028) shouldBe listOf(date(2028, 6, 17))
            engine.dates(set, 2024) shouldBe listOf(date(2024, 6, 17))
            engine.dates(set, 2027).shouldBeEmpty()
            engine.dates(set, 2100).shouldBeEmpty()
            engine.dates(set, 2000) shouldBe listOf(date(2000, 6, 17))
        }
    }

    @Test
    fun `an ifc position inside March 4 to June 28 is one day earlier in leap years`() {
        // §5.1: IFC April 17 = Gregorian Apr 11 in common years, Apr 10 in leap years.
        val april17 = single(HolidayRule.Ifc.Regular(IfcMonth.APRIL, 17))
        // §5.1: outside the window the Gregorian date is fixed. IFC Sol 13 (Friday the 13th) = Jun 30.
        val sol13 = single(HolidayRule.Ifc.Regular(IfcMonth.SOL, 13))
        val february1 = single(HolidayRule.Ifc.Regular(IfcMonth.FEBRUARY, 1))
        assertSoftly {
            engine.dates(april17, 2027) shouldBe listOf(date(2027, 4, 11))
            engine.dates(april17, 2028) shouldBe listOf(date(2028, 4, 10))
            engine.dates(sol13, 2027) shouldBe listOf(date(2027, 6, 30))
            engine.dates(sol13, 2028) shouldBe listOf(date(2028, 6, 30))
            engine.dates(february1, 2027) shouldBe listOf(date(2027, 1, 29))
            engine.dates(february1, 2028) shouldBe listOf(date(2028, 1, 29))
        }
    }

    @Test
    fun `ifc regular rejects day 29`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { HolidayRule.Ifc.Regular(IfcMonth.JUNE, 29) }
            shouldThrow<IllegalArgumentException> { HolidayRule.Ifc.Regular(IfcMonth.JUNE, 0) }
        }
    }

    // --- the library edges ----------------------------------------------------------------------------

    @Test
    fun `every rule type evaluates at years 1 and 9999 without throwing`() {
        val rules =
            listOf(
                HolidayRule.Fixed(12, 31),
                HolidayRule.Fixed(1, 1),
                HolidayRule.NthWeekday(12, DayOfWeek.MONDAY, 5),
                HolidayRule.NthWeekday(1, DayOfWeek.MONDAY, -5),
                HolidayRule.WeekdayRelative(DayOfWeek.MONDAY, 12, 31, Direction.ON_OR_AFTER),
                HolidayRule.WeekdayRelative(DayOfWeek.MONDAY, 1, 1, Direction.ON_OR_BEFORE),
                HolidayRule.Offset(-400, HolidayRule.Fixed(1, 1)),
                HolidayRule.Offset(400, HolidayRule.Fixed(12, 31)),
                HolidayRule.Easter(EasterCalendar.WESTERN, -47),
                HolidayRule.Easter(EasterCalendar.ORTHODOX, 49),
                HolidayRule.Table(mapOf(1 to date(1, 6, 1), 9999 to date(9999, 6, 1))),
                HolidayRule.Ifc.Regular(IfcMonth.SOL, 1),
                HolidayRule.Ifc.YearDay,
                HolidayRule.Ifc.LeapDay,
            )
        for (rule in rules) {
            for (year in listOf(1, 9999)) {
                withClue("$rule in $year") {
                    val dates = engine.dates(single(rule), year)
                    // Whatever comes back is inside the library range (dates outside are dropped).
                    dates.forEach { it.year shouldBe it.year.coerceIn(1, 9999) }
                }
            }
        }
    }

    @Test
    fun `dates pushed outside the library range are dropped rather than returned`() {
        assertSoftly {
            engine.dates(single(HolidayRule.Offset(-1, HolidayRule.Fixed(1, 1))), 1).shouldBeEmpty()
            engine.dates(single(HolidayRule.Offset(1, HolidayRule.Fixed(12, 31))), 9999).shouldBeEmpty()
            engine.dates(single(HolidayRule.Offset(1, HolidayRule.Fixed(12, 30))), 9999) shouldBe
                listOf(LocalDate.of(9999, 12, 31))
        }
    }
}
