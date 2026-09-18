package io.github.chrisjmendoza.yearal.core.domain.holiday

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.DayOfWeek
import java.time.LocalDate

// Oracle tests against published tables (docs/ARCHITECTURE.md §6, ":core:domain" row: "Holiday rules
// against published tables"). The tables live in src/test/resources/oracle/ and were fetched from the
// sources named in each file's header; they are inputs and are never edited to match the code
// (docs/WORKFLOW.md §4.2 rule 2, CLAUDE.md rule 12).
//
// Written independently of the implementation, from docs/holidays-and-import.md §2.2 and §2.5 only.
// The US federal set below is deliberately not the one in HolidayFixtures.kt.
class HolidayOracleTest {
    private val engine = HolidayEngine()

    // --- Easter, docs/holidays-and-import.md §2.2 rule 5 ------------------------------------------------

    private val westernEaster: Map<Int, LocalDate> by lazy { readYearTable("easter_western.csv") }
    private val orthodoxEaster: Map<Int, LocalDate> by lazy { readYearTable("easter_orthodox.csv") }

    @Test
    fun `the western table covers every year 1900 to 2100 and the orthodox table every year 1875 to 2124`() {
        assertSoftly {
            withClue("western years") { westernEaster.keys.sorted() shouldContainExactly (1900..2100).toList() }
            withClue("orthodox years") { orthodoxEaster.keys.sorted() shouldContainExactly (1875..2124).toList() }
        }
    }

    @TestFactory
    fun `western easter matches the published table for every year 1900 to 2100`(): List<DynamicTest> =
        westernEaster.entries.sortedBy { it.key }.map { (year, expected) ->
            dynamicTest("Western Easter $year = $expected") {
                engine.dates(single(HolidayRule.Easter(EasterCalendar.WESTERN, 0)), year) shouldBe listOf(expected)
            }
        }

    @TestFactory
    fun `orthodox easter matches the published table for every year 1875 to 2124`(): List<DynamicTest> =
        orthodoxEaster.entries.sortedBy { it.key }.map { (year, expected) ->
            dynamicTest("Orthodox Easter $year = $expected") {
                engine.dates(single(HolidayRule.Easter(EasterCalendar.ORTHODOX, 0)), year) shouldBe listOf(expected)
            }
        }

    @Test
    fun `the published tables are self-consistent, every Easter is a Sunday`() {
        // Guards the oracle itself: a mis-parsed row would show up here rather than as a false engine failure.
        assertSoftly {
            for ((year, date) in westernEaster) {
                withClue("Western $year") { date.dayOfWeek shouldBe DayOfWeek.SUNDAY }
            }
            for ((year, date) in orthodoxEaster) {
                withClue("Orthodox $year") { date.dayOfWeek shouldBe DayOfWeek.SUNDAY }
            }
        }
    }

    @Test
    fun `easter offsets of the movable feasts follow the table, Good Friday two days before, Pentecost 49 after`() {
        // §2.5 religious set: Mardi Gras −47, Ash Wednesday −46, Palm Sunday −7, Good Friday −2,
        // Pentecost +49.
        val feasts =
            mapOf(
                -47 to DayOfWeek.TUESDAY,
                -46 to DayOfWeek.WEDNESDAY,
                -7 to DayOfWeek.SUNDAY,
                -2 to DayOfWeek.FRIDAY,
                49 to DayOfWeek.SUNDAY,
            )
        assertSoftly {
            for ((offset, weekday) in feasts) {
                val set = single(HolidayRule.Easter(EasterCalendar.WESTERN, offset))
                for (year in 1900..2100) {
                    val expected = westernEaster.getValue(year).plusDays(offset.toLong())
                    withClue("Easter $offset in $year") {
                        engine.dates(set, year) shouldBe listOf(expected)
                        expected.dayOfWeek shouldBe weekday
                    }
                }
            }
        }
    }

    // --- US federal holidays against OPM, docs/holidays-and-import.md §2.5 --------------------------------

    /**
     * The federal holidays of 5 U.S.C. § 6103 exactly as §2.5 defines them, plus Inauguration Day. Written from
     * the spec table, not copied from the implementer's fixtures. Ids are keyed by the names OPM prints.
     */
    private val usFederal =
        HolidaySet(
            id = "oracle.us",
            region = "US",
            name = mapOf("en" to "United States (oracle)"),
            sources = listOf("5 U.S.C. § 6103", "docs/holidays-and-import.md §2.5"),
            holidays =
                listOf(
                    federal("New Year's Day", HolidayRule.Fixed(1, 1), observed = ObservedPolicy.US_FEDERAL),
                    federal(
                        "Birthday of Martin Luther King, Jr.",
                        HolidayRule.NthWeekday(1, DayOfWeek.MONDAY, 3),
                        since = 1986,
                    ),
                    federal("Washington's Birthday", HolidayRule.NthWeekday(2, DayOfWeek.MONDAY, 3), since = 1971),
                    federal("Memorial Day", HolidayRule.NthWeekday(5, DayOfWeek.MONDAY, -1), since = 1971),
                    federal(
                        "Juneteenth National Independence Day",
                        HolidayRule.Fixed(6, 19),
                        since = 2021,
                        observed = ObservedPolicy.US_FEDERAL,
                    ),
                    federal("Independence Day", HolidayRule.Fixed(7, 4), observed = ObservedPolicy.US_FEDERAL),
                    federal("Labor Day", HolidayRule.NthWeekday(9, DayOfWeek.MONDAY, 1)),
                    federal("Columbus Day", HolidayRule.NthWeekday(10, DayOfWeek.MONDAY, 2), since = 1971),
                    federal("Veterans Day", HolidayRule.Fixed(11, 11), observed = ObservedPolicy.US_FEDERAL),
                    federal("Thanksgiving Day", HolidayRule.NthWeekday(11, DayOfWeek.THURSDAY, 4), since = 1942),
                    federal("Christmas Day", HolidayRule.Fixed(12, 25), observed = ObservedPolicy.US_FEDERAL),
                    // §2.5: Jan 20, every fourth year (mod 4 == 1), Sunday → Monday, DC-area observance.
                    HolidayDefinition(
                        id = "Inauguration Day",
                        name = mapOf("en" to "Inauguration Day"),
                        rule = HolidayRule.Fixed(1, 20),
                        yearFilter = YearFilter(4, 1),
                        observed = ObservedPolicy.SUNDAY_TO_MONDAY,
                        category = HolidayCategory.OBSERVANCE,
                    ),
                ),
        )

    private fun federal(
        opmName: String,
        rule: HolidayRule,
        since: Int? = null,
        observed: ObservedPolicy = ObservedPolicy.NONE,
    ): HolidayDefinition =
        HolidayDefinition(
            id = opmName,
            name = mapOf("en" to opmName),
            rule = rule,
            since = since,
            observed = observed,
            category = HolidayCategory.PUBLIC,
        )

    /** OPM publishes 2011–2030 (fetched 2026-09-17); 2031–2035 of the ARCHITECTURE §6 row do not exist yet. */
    private val opmYears = 2020..2030

    private val opm: Map<Int, Map<String, LocalDate>> by lazy {
        opmYears.associateWith { readHolidayTable("us_federal_$it.csv") }
    }

    @Test
    fun `an OPM table exists for every year 2020 to 2030 with the eleven federal holidays of that year`() {
        assertSoftly {
            for (year in opmYears) {
                val rows = opm.getValue(year)
                val expectedCount = 10 + (if (year >= 2021) 1 else 0) + (if (year in setOf(2021, 2025)) 1 else 0)
                withClue("$year rows ${rows.keys}") { rows.size shouldBe expectedCount }
            }
        }
    }

    @TestFactory
    fun `the day OPM lists is the observed entry when there is one, else the actual day`(): List<DynamicTest> =
        opmYears.map { year ->
            dynamicTest("OPM $year") {
                val occurrences = engine.occurrences(usFederal, year)
                // OPM's row is the day treated as the holiday: 5 U.S.C. § 6103(b) moves a Saturday to Friday and
                // E.O. 11582 a Sunday to Monday. Our model keeps the actual day and adds an observed entry, so
                // OPM's date is the observed entry if present, otherwise the actual date.
                val dayOff =
                    occurrences
                        .groupBy { it.holiday.id }
                        .mapValues { (_, list) -> (list.firstOrNull { it.observed } ?: list.single()).date }
                val expected =
                    dayOff.filterKeys { id ->
                        val definition = usFederal.holidays.single { it.id == id }
                        // 6103(c) substitutes only for a Sunday; on a Saturday (2029) there is no holiday and
                        // OPM lists nothing, so the Saturday Inauguration Day is the one row OPM omits.
                        definition.category == HolidayCategory.PUBLIC ||
                            dayOff.getValue(id).dayOfWeek != DayOfWeek.SATURDAY
                    }
                expected shouldContainExactly opm.getValue(year)
            }
        }

    @Test
    fun `OPM's weekend shifts are reproduced as observed entries on the actual weekend day`() {
        // Every OPM row that is not on the holiday's statutory date must be our observed entry, and the actual
        // day must still be present (§2.2: "the actual date is kept").
        assertSoftly {
            for (year in opmYears) {
                val byId = engine.occurrences(usFederal, year).groupBy { it.holiday.id }
                for ((name, opmDate) in opm.getValue(year)) {
                    val entries = byId.getValue(name)
                    val actual = entries.single { !it.observed }
                    if (opmDate != actual.date) {
                        withClue("$name $year: OPM $opmDate vs actual ${actual.date} (${actual.date.dayOfWeek})") {
                            entries.map { it.date to it.observed } shouldContainExactly
                                listOf(actual.date to false, opmDate to true).sortedBy { it.first }
                            val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                            (actual.date.dayOfWeek in weekend) shouldBe true
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `new year's day observed on December 31 of the previous year appears in that December's range query`() {
        // OPM's 2022 and 2028 tabs list New Year's Day on Friday, December 31 of the previous year (§6 risk row).
        val expected = listOf(2022, 2028).associateWith { opm.getValue(it).getValue("New Year's Day") }
        expected shouldBe mapOf(2022 to LocalDate.of(2021, 12, 31), 2028 to LocalDate.of(2027, 12, 31))
        assertSoftly {
            for ((year, date) in expected) {
                val december = LocalDate.of(year - 1, 12, 1)..LocalDate.of(year - 1, 12, 31)
                val found = engine.occurrences(listOf(usFederal), december).filter { it.holiday.id == "New Year's Day" }
                withClue("December ${year - 1}") {
                    found.map { it.date to it.observed } shouldContainExactly listOf(date to true)
                }
                // And the January range of the rule year shows the actual day only.
                val january = LocalDate.of(year, 1, 1)..LocalDate.of(year, 1, 31)
                engine
                    .occurrences(listOf(usFederal), january)
                    .filter { it.holiday.id == "New Year's Day" }
                    .map { it.date to it.observed } shouldContainExactly listOf(LocalDate.of(year, 1, 1) to false)
            }
        }
    }

    @Test
    fun `juneteenth is absent before 2021 in both the engine and OPM`() {
        engine.occurrences(usFederal, 2020).filter { it.holiday.id.startsWith("Juneteenth") }.shouldBeEmpty()
        opm
            .getValue(2020)
            .keys
            .filter { it.startsWith("Juneteenth") }
            .shouldBeEmpty()
    }

    // --- CSV readers ------------------------------------------------------------------------------------

    private fun readRows(name: String): List<Pair<String, String>> {
        val url = checkNotNull(javaClass.getResource("/oracle/$name")) { "missing test resource oracle/$name" }
        return url
            .openStream()
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1) // header
            .map { line -> line.substringBeforeLast(',') to line.substringAfterLast(',') }
    }

    private fun readYearTable(name: String): Map<Int, LocalDate> =
        readRows(name).associate { (year, date) -> year.toInt() to LocalDate.parse(date) }

    private fun readHolidayTable(name: String): Map<String, LocalDate> =
        readRows(name).associate { (holiday, date) -> holiday to LocalDate.parse(date) }
}
