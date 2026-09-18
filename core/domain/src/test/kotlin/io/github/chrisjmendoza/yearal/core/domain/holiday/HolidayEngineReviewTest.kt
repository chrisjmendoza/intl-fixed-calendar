package io.github.chrisjmendoza.yearal.core.domain.holiday

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayRule.WeekdayRelative.Direction
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Independent review tests (docs/WORKFLOW.md §7) written against the spec and the ADR, not the code:
// the Gregorian-weekday rule of docs/holidays-and-import.md §2.2, the year-window contract of
// docs/adr/0003-holiday-rule-model.md decision 1, and the thread-safety claim in HolidayEngine's KDoc.
class HolidayEngineReviewTest {
    private val engine = HolidayEngine()

    @Test
    fun `weekday rules use the real Gregorian weekday, which differs from the IFC nominal weekday`() {
        // §2.2 "Weekday semantics": the 4th Thursday of November is a real Thursday and will generally not be
        // an IFC Thursday. If the engine ever used nominalDayOfWeek, the first assertion would fail; the
        // second proves the two weekdays actually differ in this sample, so the first is not vacuous.
        val thanksgiving = single(HolidayRule.NthWeekday(11, DayOfWeek.THURSDAY, 4))
        val dates = (2020..2030).map { engine.dates(thanksgiving, it).single() }
        assertSoftly {
            for (date in dates) {
                withClue("$date") { IfcDate.from(date).actualDayOfWeek shouldBe DayOfWeek.THURSDAY }
            }
            withClue("at least one Thanksgiving is not an IFC nominal Thursday") {
                dates.any { IfcDate.from(it).nominalDayOfWeek != DayOfWeek.THURSDAY } shouldBe true
            }
        }
    }

    @Test
    fun `observed policies use the real Gregorian weekday too`() {
        // Jul 4 2026 is a real Saturday but IFC Sol 17 (day 185 of a common year), a nominal Tuesday: a policy
        // evaluated on the IFC weekday would not shift it at all.
        val july4 = LocalDate.of(2026, 7, 4)
        IfcDate.from(july4).nominalDayOfWeek shouldBe DayOfWeek.TUESDAY
        july4.dayOfWeek shouldBe DayOfWeek.SATURDAY
        ObservedPolicy.US_FEDERAL.observedDate(july4) shouldBe LocalDate.of(2026, 7, 3)
    }

    @Test
    fun `a weekday-relative anchor can lie in the previous year and a December range query still finds it`() {
        // Jan 1 2026 is a Thursday, so "the Monday on or before Jan 1" is Dec 29 2025: the anchor of rule year
        // 2026 lies in 2025. ADR 0003 decision 1 and the occurrences(set, year) KDoc say only Offset does this;
        // the behaviour is still within the ±1 year window, so the range overload must see it.
        val rule = HolidayRule.WeekdayRelative(DayOfWeek.MONDAY, 1, 1, Direction.ON_OR_BEFORE)
        val set = single(rule)
        assertSoftly {
            engine.dates(set, 2026) shouldBe listOf(LocalDate.of(2025, 12, 29))
            engine
                .occurrences(listOf(set), LocalDate.of(2025, 12, 1)..LocalDate.of(2025, 12, 31))
                .map { it.date } shouldContainExactly listOf(LocalDate.of(2025, 12, 29))
            // The rule year 2025 evaluation gives Dec 30 2024, so December 2025 shows exactly one hit.
            engine.dates(set, 2025) shouldBe listOf(LocalDate.of(2024, 12, 30))
        }
    }

    @Test
    fun `a range query equals the per-year results of the neighbouring rule years, filtered, with no duplicates`() {
        // ADR 0003 decision 1 made concrete for a legal pack: the range overload is exactly the union of
        // occurrences(set, y) for y in start.year-1..end.year+1 restricted to the range, sorted, and nothing is
        // produced twice even though three rule years overlap every calendar year.
        val range = LocalDate.of(2027, 12, 1)..LocalDate.of(2028, 1, 31)
        val sets = listOf(usSet, ifcSet)
        val expected =
            sets
                .flatMap { set -> (2026..2029).flatMap { engine.occurrences(set, it) } }
                .filter { it.date in range }
                .sorted()
        val found = engine.occurrences(sets, range)
        assertSoftly {
            found shouldContainExactly expected
            found.distinct().size shouldBe found.size
            // The interesting rows: Kwanzaa 2027 runs into 2028, New Year 2028 is observed on Dec 31 2027.
            found.filter { it.holiday.id == "us.new_year" }.map { it.date to it.observed } shouldContainExactly
                listOf(LocalDate.of(2027, 12, 31) to true, LocalDate.of(2028, 1, 1) to false)
            found.filter { it.holiday.id == "us.kwanzaa" }.map { it.date } shouldContainExactly
                (0L..6L).map { LocalDate.of(2027, 12, 26).plusDays(it) }
        }
    }

    @Test
    fun `a set listed twice in a range query is evaluated once`() {
        val range = LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31)
        engine.occurrences(listOf(usSet, usSet, ifcSet), range) shouldContainExactly
            engine.occurrences(listOf(usSet, ifcSet), range)
    }

    @Test
    fun `an observed entry never appears without its actual day in the same per-year result`() {
        for (year in 2020..2040) {
            val byId = engine.occurrences(usSet, year).groupBy { it.holiday.id }
            for ((id, entries) in byId) {
                withClue("$id $year") {
                    entries.count { it.observed } shouldBe (if (entries.any { it.observed }) 1 else 0)
                    entries.filter { !it.observed && it.dayIndex == 0 }.size shouldBe 1
                }
            }
        }
    }

    @Test
    fun `the cache is safe under concurrent use and never serves another set's or year's result`() {
        val reference = HolidayEngine()
        val sets = listOf(usSet, ifcSet, pack("us", newYear)) // a different pack under the id "us" thrashes on purpose
        val years = 2020..2030
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks =
                (0 until 64).map { i ->
                    Callable {
                        val set = sets[i % sets.size]
                        var mismatches = 0
                        for (year in years) {
                            val expected = reference.occurrences(set, year)
                            if (engine.occurrences(set, year) != expected) mismatches++
                            if (engine.occurrences(
                                    listOf(set),
                                    LocalDate.of(year, 12, 1)..LocalDate.of(year, 12, 31),
                                ) !=
                                reference.occurrences(
                                    listOf(set),
                                    LocalDate.of(year, 12, 1)..LocalDate.of(year, 12, 31),
                                )
                            ) {
                                mismatches++
                            }
                        }
                        mismatches
                    }
                }
            val results = executor.invokeAll(tasks).map { it.get(60, TimeUnit.SECONDS) }
            results.sum() shouldBe 0
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a zero offset and an empty table are legal and behave as documented`() {
        assertSoftly {
            engine.dates(single(HolidayRule.Offset(0, HolidayRule.Fixed(7, 4))), 2026) shouldBe
                listOf(LocalDate.of(2026, 7, 4))
            engine.dates(single(HolidayRule.Table(emptyMap())), 2026).shouldBeEmpty()
        }
    }

    @Test
    fun `an offset of a table rule keyed to the year yields nothing in years the table lacks`() {
        val diwaliEve = HolidayRule.Offset(-1, HolidayRule.Table(mapOf(2025 to LocalDate.of(2025, 10, 20))))
        assertSoftly {
            engine.dates(single(diwaliEve), 2025) shouldBe listOf(LocalDate.of(2025, 10, 19))
            engine.dates(single(diwaliEve), 2026).shouldBeEmpty()
        }
    }

    @Test
    fun `leap day and year day through the full us plus ifc pack for the century boundary`() {
        // 2100 is not a leap year (§5.3 century rule): no Leap Day, but Year Day and Sol 1 are unaffected, and
        // a Fixed Feb 29 rule is silent too.
        val feb29 = holiday("feb29", HolidayRule.Fixed(2, 29))
        val set = pack("p", yearDay, leapDay, solDay, feb29)
        assertSoftly {
            engine.occurrences(set, 2100).map { it.holiday.id to it.date } shouldContainExactly
                listOf("ifc.sol_day" to LocalDate.of(2100, 6, 18), "ifc.year_day" to LocalDate.of(2100, 12, 31))
            engine.occurrences(set, 2000).map { it.holiday.id to it.date } shouldContainExactly
                listOf(
                    "feb29" to LocalDate.of(2000, 2, 29),
                    "ifc.leap_day" to LocalDate.of(2000, 6, 17),
                    "ifc.sol_day" to LocalDate.of(2000, 6, 18),
                    "ifc.year_day" to LocalDate.of(2000, 12, 31),
                )
        }
    }
}
