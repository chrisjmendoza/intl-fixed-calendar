package io.github.chrisjmendoza.yearal.core.holidays

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayCategory
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayOccurrence
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

// The bundled packs: every one loads, the IFC set behaves per docs/holidays-and-import.md §5, the
// Easter family matches published dates, and the sanity properties of ROADMAP M1 T9 hold across the
// UI year range.
class BundledPacksTest {
    private val loader = HolidayPackLoader()
    private val engine = HolidayEngine()

    @Test
    fun `every bundled pack loads and its id matches the registry`() {
        BundledHolidayPacks.all shouldContainExactly
            listOf(BundledHolidayPacks.IFC, BundledHolidayPacks.US, BundledHolidayPacks.RELIGIOUS_CHRISTIAN)
        loader.loadBundled(BundledHolidayPacks.IFC).id shouldBe "ifc"
        loader.loadBundled(BundledHolidayPacks.US).id shouldBe "us"
        loader.loadBundled(BundledHolidayPacks.RELIGIOUS_CHRISTIAN).id shouldBe "religious-christian"
    }

    @Test
    fun `ifc pack holds year day, leap day and sol 1 in category IFC`() {
        val ifc = loader.loadBundled(BundledHolidayPacks.IFC)

        ifc.region shouldBe null
        ifc.holidays.map { it.id } shouldContainExactly listOf("ifc.year_day", "ifc.leap_day", "ifc.sol_1")
        ifc.holidays.map { it.category }.toSet() shouldBe setOf(HolidayCategory.IFC)
    }

    @Test
    fun `ifc pack in a leap year yields June 17, June 18 and December 31`() {
        val ifc = loader.loadBundled(BundledHolidayPacks.IFC)

        engine.occurrences(ifc, 2024).map { it.holiday.id to it.date } shouldContainExactly
            listOf(
                "ifc.leap_day" to LocalDate.of(2024, 6, 17),
                "ifc.sol_1" to LocalDate.of(2024, 6, 18),
                "ifc.year_day" to LocalDate.of(2024, 12, 31),
            )
    }

    @Test
    fun `ifc pack in a common year has no leap day, including 2100`() {
        val ifc = loader.loadBundled(BundledHolidayPacks.IFC)

        for (year in listOf(2025, 2026, 2027, 2100)) {
            withClue(year) {
                engine.occurrences(ifc, year).map { it.holiday.id to it.date } shouldContainExactly
                    listOf(
                        "ifc.sol_1" to LocalDate.of(year, 6, 18),
                        "ifc.year_day" to LocalDate.of(year, 12, 31),
                    )
            }
        }
    }

    @Test
    fun `ifc pack dates convert back to the IFC days they name`() {
        val ifc = loader.loadBundled(BundledHolidayPacks.IFC)
        val byId = engine.occurrences(ifc, 2028).associateBy { it.holiday.id }

        IfcDate.from(byId.getValue("ifc.year_day").date) shouldBe IfcDate.YearDay(2028)
        IfcDate.from(byId.getValue("ifc.leap_day").date) shouldBe IfcDate.LeapDay(2028)
        IfcDate.from(byId.getValue("ifc.sol_1").date) shouldBe IfcDate.Regular(2028, IfcMonth.SOL, 1)
    }

    @Test
    fun `easter family matches published dates`() {
        val christian = loader.loadBundled(BundledHolidayPacks.RELIGIOUS_CHRISTIAN)
        val rows = OracleCsv.rows("religious_christian.csv")
        rows shouldHaveSize 20

        for (row in rows) {
            val date = LocalDate.parse(row.getValue("date"))
            val id = row.getValue("holiday_id")
            withClue("$id ${date.year}") {
                engine
                    .occurrences(
                        christian,
                        date.year,
                    ).filter { it.holiday.id == id }
                    .map(HolidayOccurrence::date) shouldBe
                    listOf(date)
            }
        }
    }

    @Test
    fun `easter family pack is region-independent and religious`() {
        val christian = loader.loadBundled(BundledHolidayPacks.RELIGIOUS_CHRISTIAN)

        christian.region shouldBe null
        christian.holidays.map { it.category }.toSet() shouldBe setOf(HolidayCategory.RELIGIOUS)
        christian.holidays.map { it.id } shouldContainExactly
            listOf("x.mardi_gras", "x.ash_wednesday", "x.palm_sunday", "x.good_friday", "x.easter", "x.orthodox_easter")
    }

    @Test
    fun `no pack yields two occurrences with the same id, date and observed flag in any year 1583 to 2200`() {
        for (packName in BundledHolidayPacks.all) {
            val set = loader.loadBundled(packName)
            for (year in FIRST_UI_YEAR..LAST_CHECKED_YEAR) {
                val occurrences = engine.occurrences(set, year)
                val duplicates =
                    occurrences
                        .groupBy { Triple(it.holiday.id, it.date, it.observed) }
                        .filterValues { it.size > 1 }
                        .keys
                withClue("$packName $year") { duplicates.shouldBeEmpty() }
            }
        }
    }

    @Test
    fun `every public US holiday occurs in every year from 2022`() {
        val us = loader.loadBundled(BundledHolidayPacks.US)
        val publicIds = us.holidays.filter { it.category == HolidayCategory.PUBLIC }.map { it.id }
        publicIds shouldHaveSize 11

        for (year in 2022..LAST_CHECKED_YEAR) {
            val present =
                engine
                    .occurrences(us, year)
                    .filter { !it.observed }
                    .map { it.holiday.id }
                    .toSet()
            withClue("US $year") { (publicIds - present).shouldBeEmpty() }
        }
    }

    private companion object {
        /** Lower bound of the UI year range (calendar-spec §7.1). */
        const val FIRST_UI_YEAR = 1583
        const val LAST_CHECKED_YEAR = 2200
    }
}
