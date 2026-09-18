package io.github.chrisjmendoza.yearal.core.holidays

import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayCategory
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayOccurrence
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

// "Holiday tables match published data" (ARCHITECTURE.md §6, ROADMAP M1 T9 exit criterion) for the
// US pack: the federal holidays against OPM's per-year tables, the observances against a second
// published source. Every expected row lives in src/test/resources/oracle with its source URL.
class UsPackOracleTest {
    private val engine = HolidayEngine()
    private val us = HolidayPackLoader().loadBundled(BundledHolidayPacks.US)

    private data class Row(
        val date: LocalDate,
        val holidayId: String,
        val observed: Boolean,
    )

    /**
     * OPM lists one date per holiday: the day most federal employees get off, which is the observed
     * date when 5 U.S.C. § 6103(b) shifts a weekend holiday and the actual date otherwise. This
     * projects the engine's output (which keeps both the actual and the observed entry) onto that
     * view, for the PUBLIC holidays only.
     */
    private fun opmView(year: Int): List<Row> {
        val public = engine.occurrences(us, year).filter { it.holiday.category == HolidayCategory.PUBLIC }
        val byHoliday = public.groupBy { it.holiday.id }
        return byHoliday.map { (id, occurrences) ->
            val chosen = occurrences.singleOrNull { it.observed } ?: occurrences.single()
            Row(chosen.date, id, chosen.observed)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [2024, 2025, 2026, 2027, 2028])
    fun `federal holidays reproduce OPM's table for the year`(year: Int) {
        val expected =
            OracleCsv.rows("us_federal_$year.csv").map {
                Row(
                    LocalDate.parse(it.getValue("date")),
                    it.getValue("holiday_id"),
                    it.getValue("observed").toBooleanStrict(),
                )
            }
        expected.size shouldBe 11

        withClue("OPM $year") { opmView(year) shouldContainExactlyInAnyOrder expected }
    }

    @Test
    fun `an unshifted federal holiday has exactly one occurrence and a shifted one keeps the actual date too`() {
        // 2026: Independence Day is a Saturday, observed Friday July 3 (OPM); both entries are present.
        val independence = engine.occurrences(us, 2026).filter { it.holiday.id == "us.independence" }
        independence.map { it.date to it.observed } shouldContainExactlyInAnyOrder
            listOf(LocalDate.of(2026, 7, 4) to false, LocalDate.of(2026, 7, 3) to true)

        // 2028: New Year's Day is a Saturday, observed Friday December 31, 2027 (OPM 2028 table).
        val newYear = engine.occurrences(us, 2028).filter { it.holiday.id == "us.new_year" }
        newYear.map { it.date to it.observed } shouldContainExactlyInAnyOrder
            listOf(LocalDate.of(2028, 1, 1) to false, LocalDate.of(2027, 12, 31) to true)
    }

    @Test
    fun `inauguration day matches OPM and is not shifted from a Saturday`() {
        for (row in OracleCsv.rows("us_inauguration.csv")) {
            val year = row.getValue("year").toInt()
            val inauguration = engine.occurrences(us, year).filter { it.holiday.id == row.getValue("holiday_id") }
            withClue("Inauguration Day $year") {
                inauguration.map { it.date } shouldBe listOf(LocalDate.parse(row.getValue("date")))
                inauguration.single().observed shouldBe false
                inauguration.single().holiday.category shouldBe HolidayCategory.OBSERVANCE
            }
        }
    }

    @Test
    fun `inauguration day exists only in years after a presidential election`() {
        engine.occurrences(us, 2024).none { it.holiday.id == "us.inauguration" } shouldBe true
        engine.occurrences(us, 2026).none { it.holiday.id == "us.inauguration" } shouldBe true
        engine.occurrences(us, 2029).any { it.holiday.id == "us.inauguration" } shouldBe true
    }

    @Test
    fun `2026 observances match their published sources`() {
        val occurrences = engine.occurrences(us, 2026)
        for (row in OracleCsv.rows("us_observances_2026.csv")) {
            val id = row.getValue("holiday_id")
            withClue("$id per ${row.getValue("source")}") {
                occurrences.filter { it.holiday.id == id }.map(HolidayOccurrence::date) shouldBe
                    listOf(LocalDate.parse(row.getValue("date")))
            }
        }
    }

    @Test
    fun `election day exists only in even years`() {
        engine.occurrences(us, 2025).none { it.holiday.id == "us.election" } shouldBe true
        engine.occurrences(us, 2027).none { it.holiday.id == "us.election" } shouldBe true
    }

    @Test
    fun `kwanzaa runs seven days from December 26`() {
        val kwanzaa = engine.occurrences(us, 2026).filter { it.holiday.id == "us.kwanzaa" }

        kwanzaa.map { it.date } shouldBe (0L..6L).map { LocalDate.of(2026, 12, 26).plusDays(it) }
        kwanzaa.map { it.dayIndex } shouldBe (0..6).toList()
    }

    @Test
    fun `pack metadata cites the statute`() {
        us.id shouldBe "us"
        us.region shouldBe "US"
        us.sources.any { it.contains("5 U.S.C. § 6103") } shouldBe true
    }
}
