package io.github.chrisjmendoza.yearal.core.domain.holiday

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSorted
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.Test
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate

// Verifies the modifiers and observed policies of docs/holidays-and-import.md §2.2, the US starter set
// of §2.5, the cross-year edge of §6 ("Observed-date cross-year edge"), and the engine contract in
// docs/adr/0003-holiday-rule-model.md. Expected dates come from published US federal calendars.
class HolidayEngineTest {
    private val engine = HolidayEngine()

    // --- observed policies ----------------------------------------------------------------------------

    @Test
    fun `us federal keeps the actual day and adds an observed entry, Saturday to Friday`() {
        // Christmas 2021 was a Saturday; the federal holiday was observed Friday Dec 24.
        val occurrences = engine.occurrences(pack("us", christmas), 2021)
        occurrences.map { it.date to it.observed } shouldContainExactly
            listOf(date(2021, 12, 24) to true, date(2021, 12, 25) to false)
        occurrences.forEach { it.holiday shouldBe christmas }
    }

    @Test
    fun `us federal shifts Sunday to Monday`() {
        // Juneteenth 2022 was a Sunday; observed Monday June 20.
        engine.occurrences(pack("us", juneteenth), 2022).map { it.date to it.observed } shouldContainExactly
            listOf(date(2022, 6, 19) to false, date(2022, 6, 20) to true)
    }

    @Test
    fun `us federal July 4 2026 is observed on Friday July 3`() {
        engine.occurrences(pack("us", independence), 2026).map { it.date to it.observed } shouldContainExactly
            listOf(date(2026, 7, 3) to true, date(2026, 7, 4) to false)
    }

    @Test
    fun `a weekday anchor gets no observed entry`() {
        // Christmas 2025 is a Thursday.
        engine.occurrences(pack("us", christmas), 2025).map { it.date to it.observed } shouldContainExactly
            listOf(date(2025, 12, 25) to false)
    }

    @Test
    fun `new year 2022 on a Saturday is observed on Friday 31 December 2021`() {
        val occurrences = engine.occurrences(pack("us", newYear), 2022)
        occurrences.map { it.date to it.observed } shouldContainExactly
            listOf(date(2021, 12, 31) to true, date(2022, 1, 1) to false)
        // The same holds for 2028 (Jan 1 2028 is a Saturday), the second golden case in §6.
        engine.occurrences(pack("us", newYear), 2028).map { it.date to it.observed } shouldContainExactly
            listOf(date(2027, 12, 31) to true, date(2028, 1, 1) to false)
    }

    @Test
    fun `a December 2021 range query shows the observed New Year of 2022`() {
        val december2021 = date(2021, 12, 1)..date(2021, 12, 31)
        val found = engine.occurrences(listOf(pack("us", newYear)), december2021)
        found.map { it.date to it.observed } shouldContainExactly listOf(date(2021, 12, 31) to true)
    }

    @Test
    fun `next monday shifts both weekend days to the following Monday`() {
        val set = pack("x", holiday("h", HolidayRule.Fixed(12, 25), observed = ObservedPolicy.NEXT_MONDAY))
        assertSoftly {
            // Saturday.
            engine.occurrences(set, 2021).map { it.date to it.observed } shouldContainExactly
                listOf(date(2021, 12, 25) to false, date(2021, 12, 27) to true)
            // Sunday.
            engine.occurrences(set, 2022).map { it.date to it.observed } shouldContainExactly
                listOf(date(2022, 12, 25) to false, date(2022, 12, 26) to true)
            // Monday: nothing added.
            engine.occurrences(set, 2023).map { it.date to it.observed } shouldContainExactly
                listOf(date(2023, 12, 25) to false)
        }
    }

    @Test
    fun `inauguration day moves Sunday to Monday but leaves Saturday alone`() {
        val set = pack("us", inauguration)
        assertSoftly {
            // 2013-01-20 was a Sunday: public ceremony Monday Jan 21.
            engine.occurrences(set, 2013).map { it.date to it.observed } shouldContainExactly
                listOf(date(2013, 1, 20) to false, date(2013, 1, 21) to true)
            // 2029-01-20 is a Saturday: no shift under this policy.
            engine.occurrences(set, 2029).map { it.date to it.observed } shouldContainExactly
                listOf(date(2029, 1, 20) to false)
            // 2025-01-20 was a Monday.
            engine.occurrences(set, 2025).map { it.date to it.observed } shouldContainExactly
                listOf(date(2025, 1, 20) to false)
        }
    }

    @Test
    fun `none never adds an observed entry`() {
        val set = pack("x", holiday("h", HolidayRule.Fixed(12, 25)))
        engine.occurrences(set, 2021).map { it.observed } shouldContainExactly listOf(false)
        engine.occurrences(set, 2022).map { it.observed } shouldContainExactly listOf(false)
    }

    @Test
    fun `observed date policy table`() {
        val saturday = date(2021, 12, 25)
        val sunday = date(2022, 12, 25)
        val monday = date(2023, 12, 25)
        assertSoftly {
            ObservedPolicy.NONE.observedDate(saturday) shouldBe null
            ObservedPolicy.NONE.observedDate(sunday) shouldBe null
            ObservedPolicy.US_FEDERAL.observedDate(saturday) shouldBe date(2021, 12, 24)
            ObservedPolicy.US_FEDERAL.observedDate(sunday) shouldBe date(2022, 12, 26)
            ObservedPolicy.US_FEDERAL.observedDate(monday) shouldBe null
            ObservedPolicy.NEXT_MONDAY.observedDate(saturday) shouldBe date(2021, 12, 27)
            ObservedPolicy.NEXT_MONDAY.observedDate(sunday) shouldBe date(2022, 12, 26)
            ObservedPolicy.NEXT_MONDAY.observedDate(monday) shouldBe null
            ObservedPolicy.SUNDAY_TO_MONDAY.observedDate(saturday) shouldBe null
            ObservedPolicy.SUNDAY_TO_MONDAY.observedDate(sunday) shouldBe date(2022, 12, 26)
            ObservedPolicy.SUNDAY_TO_MONDAY.observedDate(monday) shouldBe null
        }
    }

    // --- since, until, yearFilter ---------------------------------------------------------------------

    @Test
    fun `since excludes earlier years and includes the first`() {
        val set = pack("us", mlk)
        assertSoftly {
            engine.dates(set, 1985).shouldBeEmpty()
            engine.dates(set, 1986) shouldBe listOf(date(1986, 1, 20))
            engine.dates(set, 2026) shouldBe listOf(date(2026, 1, 19))
        }
    }

    @Test
    fun `until includes the last year and excludes later ones`() {
        // Veterans Day was the 4th Monday of October only in 1971–1977.
        val oldVeterans = holiday("v", HolidayRule.NthWeekday(10, DayOfWeek.MONDAY, 4), since = 1971, until = 1977)
        val set = pack("us", oldVeterans)
        assertSoftly {
            engine.dates(set, 1970).shouldBeEmpty()
            engine.dates(set, 1971) shouldBe listOf(date(1971, 10, 25))
            engine.dates(set, 1977) shouldBe listOf(date(1977, 10, 24))
            engine.dates(set, 1978).shouldBeEmpty()
        }
    }

    @Test
    fun `election day is the day after the first Monday of November in even years only`() {
        val set = pack("us", election)
        assertSoftly {
            engine.dates(set, 2024) shouldBe listOf(date(2024, 11, 5))
            engine.dates(set, 2026) shouldBe listOf(date(2026, 11, 3))
            engine.dates(set, 2025).shouldBeEmpty()
        }
    }

    @Test
    fun `year filter uses floor modulus and validates its arguments`() {
        assertSoftly {
            YearFilter(4, 1).matches(2025) shouldBe true
            YearFilter(4, 1).matches(2024) shouldBe false
            YearFilter(1, 0).matches(1) shouldBe true
            shouldThrow<IllegalArgumentException> { YearFilter(0, 0) }
            shouldThrow<IllegalArgumentException> { YearFilter(4, 4) }
            shouldThrow<IllegalArgumentException> { YearFilter(4, -1) }
        }
    }

    // --- durationDays ---------------------------------------------------------------------------------

    @Test
    fun `kwanzaa yields seven consecutive days running into the next year`() {
        val occurrences = engine.occurrences(pack("us", kwanzaa), 2025)
        occurrences.map { it.date } shouldContainExactly
            (0L..6L).map { date(2025, 12, 26).plusDays(it) }
        occurrences.map { it.dayIndex } shouldContainExactly (0..6).toList()
        occurrences.forEach { it.observed shouldBe false }
        occurrences.last().date shouldBe date(2026, 1, 1)
    }

    @Test
    fun `a January range query sees the tail of the previous year's Kwanzaa`() {
        val january2026 = date(2026, 1, 1)..date(2026, 1, 31)
        val found = engine.occurrences(listOf(pack("us", kwanzaa)), january2026)
        found.map { it.date to it.dayIndex } shouldContainExactly listOf(date(2026, 1, 1) to 6)
    }

    @Test
    fun `observed shift applies to the anchor only, not to later days`() {
        // A two-day holiday whose first day is a Saturday: one observed entry (Friday), two real days.
        val set =
            pack("x", holiday("h", HolidayRule.Fixed(12, 25), observed = ObservedPolicy.US_FEDERAL, durationDays = 2))
        engine.occurrences(set, 2021).map { Triple(it.date, it.observed, it.dayIndex) } shouldContainExactly
            listOf(
                Triple(date(2021, 12, 24), true, 0),
                Triple(date(2021, 12, 25), false, 0),
                Triple(date(2021, 12, 26), false, 1),
            )
    }

    // --- occurrences(set, year): whole US set ---------------------------------------------------------

    @Test
    fun `the us starter set for 2026 matches the published federal calendar`() {
        val byId = engine.occurrences(usSet, 2026).groupBy { it.holiday.id }
        assertSoftly {
            byId.getValue("us.new_year").map { it.date to it.observed } shouldContainExactly
                listOf(date(2026, 1, 1) to false)
            byId.getValue("us.mlk").map { it.date } shouldContainExactly listOf(date(2026, 1, 19))
            byId.getValue("us.memorial").map { it.date } shouldContainExactly listOf(date(2026, 5, 25))
            byId.getValue("us.juneteenth").map { it.date to it.observed } shouldContainExactly
                listOf(date(2026, 6, 19) to false)
            byId.getValue("us.independence").map { it.date to it.observed } shouldContainExactly
                listOf(date(2026, 7, 3) to true, date(2026, 7, 4) to false)
            byId.getValue("us.thanksgiving").map { it.date } shouldContainExactly listOf(date(2026, 11, 26))
            byId.getValue("us.black_friday").map { it.date } shouldContainExactly listOf(date(2026, 11, 27))
            byId.getValue("us.election").map { it.date } shouldContainExactly listOf(date(2026, 11, 3))
            byId.getValue("us.christmas").map { it.date to it.observed } shouldContainExactly
                listOf(date(2026, 12, 25) to false)
            byId.getValue("us.kwanzaa").size shouldBe 7
            byId.containsKey("us.inauguration") shouldBe false
        }
    }

    @Test
    fun `per-year results are sorted by date then holiday id and carry the set id`() {
        val occurrences = engine.occurrences(usSet, 2026)
        occurrences.shouldBeSorted()
        occurrences.map { it.date } shouldBe occurrences.map { it.date }.sorted()
        occurrences.forEach { it.setId shouldBe "us" }
        // Two holidays on the same date sort by id, whatever their pack order.
        val sameDay = pack("x", holiday("b", HolidayRule.Fixed(3, 1)), holiday("a", HolidayRule.Fixed(3, 1)))
        engine.occurrences(sameDay, 2026).map { it.holiday.id } shouldContainExactly listOf("a", "b")
    }

    @Test
    fun `the ifc set for a leap and a common year`() {
        assertSoftly {
            engine.occurrences(ifcSet, 2028).map { it.holiday.id to it.date } shouldContainExactly
                listOf(
                    "ifc.leap_day" to date(2028, 6, 17),
                    "ifc.sol_day" to date(2028, 6, 18),
                    "ifc.year_day" to date(2028, 12, 31),
                )
            engine.occurrences(ifcSet, 2027).map { it.holiday.id to it.date } shouldContainExactly
                listOf(
                    "ifc.sol_day" to date(2027, 6, 18),
                    "ifc.year_day" to date(2027, 12, 31),
                )
        }
    }

    @Test
    fun `every returned date converts to an IfcDate and the ifc set round-trips to its own positions`() {
        for (year in listOf(1, 1900, 2000, 2024, 2100, 9999)) {
            for (occurrence in engine.occurrences(ifcSet, year)) {
                val ifc = IfcDate.from(occurrence.date)
                withClue("${occurrence.holiday.id} $year") {
                    when (occurrence.holiday.rule) {
                        HolidayRule.Ifc.YearDay -> ifc shouldBe IfcDate.YearDay(year)
                        HolidayRule.Ifc.LeapDay -> ifc shouldBe IfcDate.LeapDay(year)
                        is HolidayRule.Ifc.Regular -> ifc shouldBe IfcDate.Regular(year, IfcMonth.SOL, 1)
                        else -> error("unexpected rule")
                    }
                }
            }
        }
    }

    @Test
    fun `year outside the library range throws DateTimeException`() {
        assertSoftly {
            shouldThrow<DateTimeException> { engine.occurrences(usSet, 0) }
            shouldThrow<DateTimeException> { engine.occurrences(usSet, 10000) }
            engine.occurrences(usSet, 1) shouldNotBe null
            engine.occurrences(usSet, 9999) shouldNotBe null
        }
    }

    // --- occurrences(sets, range) ---------------------------------------------------------------------

    @Test
    fun `range query returns only dates inside the inclusive range from all sets, sorted`() {
        val range = date(2025, 12, 24)..date(2026, 1, 1)
        val found = engine.occurrences(listOf(usSet, ifcSet), range)
        found.shouldBeSorted()
        found.map { Triple(it.setId, it.holiday.id, it.date) } shouldContainExactly
            listOf(
                Triple("us", "us.christmas", date(2025, 12, 25)),
                Triple("us", "us.kwanzaa", date(2025, 12, 26)),
                Triple("us", "us.kwanzaa", date(2025, 12, 27)),
                Triple("us", "us.kwanzaa", date(2025, 12, 28)),
                Triple("us", "us.kwanzaa", date(2025, 12, 29)),
                Triple("us", "us.kwanzaa", date(2025, 12, 30)),
                Triple("ifc", "ifc.year_day", date(2025, 12, 31)),
                Triple("us", "us.kwanzaa", date(2025, 12, 31)),
                Triple("us", "us.kwanzaa", date(2026, 1, 1)),
                Triple("us", "us.new_year", date(2026, 1, 1)),
            )
    }

    @Test
    fun `range query sorts by date then set id then holiday id`() {
        val a = pack("a", holiday("z", HolidayRule.Fixed(3, 1)))
        val b = pack("b", holiday("y", HolidayRule.Fixed(3, 1)), holiday("x", HolidayRule.Fixed(3, 1)))
        val found = engine.occurrences(listOf(b, a), date(2026, 3, 1)..date(2026, 3, 1))
        found.map { it.setId to it.holiday.id } shouldContainExactly listOf("a" to "z", "b" to "x", "b" to "y")
    }

    @Test
    fun `range query catches spill-over at both ends of a year`() {
        assertSoftly {
            // End of 2021: New Year 2022 observed on Dec 31 2021 comes from rule year 2022.
            engine
                .occurrences(listOf(usSet), date(2021, 12, 31)..date(2021, 12, 31))
                .map { it.holiday.id to it.observed } shouldContain ("us.new_year" to true)
            // Start of 2022: Kwanzaa's last day comes from rule year 2021.
            engine
                .occurrences(listOf(usSet), date(2022, 1, 1)..date(2022, 1, 1))
                .map { it.holiday.id to it.dayIndex } shouldContain ("us.kwanzaa" to 6)
            // A single-day range in the middle of a year sees nothing from other years.
            engine.occurrences(listOf(usSet), date(2022, 6, 1)..date(2022, 6, 1)).shouldBeEmpty()
        }
    }

    @Test
    fun `range query never duplicates an occurrence across the years it evaluates`() {
        val found = engine.occurrences(listOf(usSet, ifcSet), date(2024, 1, 1)..date(2026, 12, 31))
        found.distinct().size shouldBe found.size
        found.count { it.holiday.id == "ifc.sol_day" } shouldBe 3
        found.count { it.holiday.id == "ifc.leap_day" } shouldBe 1
        found.count { it.holiday.id == "us.new_year" && !it.observed } shouldBe 3
    }

    @Test
    fun `range query is empty for an empty range and safe outside the library range`() {
        assertSoftly {
            engine.occurrences(listOf(usSet), date(2026, 3, 2)..date(2026, 3, 1)).shouldBeEmpty()
            engine.occurrences(emptyList(), date(2026, 1, 1)..date(2026, 12, 31)).shouldBeEmpty()
            engine.occurrences(listOf(usSet), LocalDate.of(0, 1, 1)..LocalDate.of(0, 12, 31)).shouldBeEmpty()
            engine.occurrences(listOf(usSet), LocalDate.of(10000, 1, 1)..LocalDate.of(10000, 1, 2)).shouldBeEmpty()
            // Straddling the upper edge still returns year 9999 and does not throw on year 10000.
            engine
                .occurrences(listOf(ifcSet), LocalDate.of(9999, 12, 31)..LocalDate.of(10000, 1, 1))
                .map { it.holiday.id } shouldContainExactly listOf("ifc.year_day")
        }
    }

    // --- memoisation ----------------------------------------------------------------------------------

    @Test
    fun `repeated calls return the same memoised list and equal contents`() {
        val first = engine.occurrences(usSet, 2026)
        val second = engine.occurrences(usSet, 2026)
        second shouldBeSameInstanceAs first
        second shouldBe first
        // A structurally equal copy of the set hits the cache too.
        engine.occurrences(usSet.copy(), 2026) shouldBeSameInstanceAs first
    }

    @Test
    fun `a different set under the same id is re-evaluated, and clearCache forgets everything`() {
        val original = pack("same", holiday("h", HolidayRule.Fixed(1, 2)))
        val changed = pack("same", holiday("h", HolidayRule.Fixed(1, 3)))
        val first = engine.occurrences(original, 2026)
        first.map { it.date } shouldContainExactly listOf(date(2026, 1, 2))
        engine.occurrences(changed, 2026).map { it.date } shouldContainExactly listOf(date(2026, 1, 3))
        engine.occurrences(original, 2026).map { it.date } shouldContainExactly listOf(date(2026, 1, 2))

        val cached = engine.occurrences(original, 2026)
        engine.clearCache()
        val recomputed = engine.occurrences(original, 2026)
        recomputed shouldNotBeSameInstanceAs cached
        recomputed shouldBe cached
    }

    @Test
    fun `memoisation is per year`() {
        engine.occurrences(usSet, 2025) shouldNotBe engine.occurrences(usSet, 2026)
    }

    // --- definition and set validation ----------------------------------------------------------------

    @Test
    fun `holiday definition validates id, english name, duration and year bounds`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { holiday(" ", HolidayRule.Fixed(1, 1)) }
            shouldThrow<IllegalArgumentException> {
                HolidayDefinition("h", mapOf("de" to "x"), HolidayRule.Fixed(1, 1), category = HolidayCategory.PUBLIC)
            }
            shouldThrow<IllegalArgumentException> { holiday("h", HolidayRule.Fixed(1, 1), durationDays = 0) }
            shouldThrow<IllegalArgumentException> { holiday("h", HolidayRule.Fixed(1, 1), since = 2000, until = 1999) }
            holiday("h", HolidayRule.Fixed(1, 1), since = 2000, until = 2000) shouldNotBe null
        }
    }

    @Test
    fun `names fall back to english`() {
        val h =
            HolidayDefinition(
                id = "h",
                name = mapOf("en" to "Year Day", "de" to "Jahrestag"),
                rule = HolidayRule.Ifc.YearDay,
                category = HolidayCategory.IFC,
            )
        assertSoftly {
            h.nameFor("de") shouldBe "Jahrestag"
            h.nameFor("fr") shouldBe "Year Day"
            h.nameFor("en") shouldBe "Year Day"
            pack("p", h).nameFor("xx") shouldBe "p"
        }
    }

    @Test
    fun `holiday set rejects duplicate ids, a blank id and a missing english name`() {
        assertSoftly {
            shouldThrow<IllegalArgumentException> {
                pack("us", holiday("dup", HolidayRule.Fixed(1, 1)), holiday("dup", HolidayRule.Fixed(1, 2)))
            }
            shouldThrow<IllegalArgumentException> { pack("", holiday("h", HolidayRule.Fixed(1, 1))) }
            shouldThrow<IllegalArgumentException> {
                HolidaySet("x", null, mapOf("de" to "x"), emptyList(), emptyList())
            }
            pack("empty") shouldNotBe null
            engine.occurrences(pack("empty"), 2026).shouldBeEmpty()
        }
    }
}
