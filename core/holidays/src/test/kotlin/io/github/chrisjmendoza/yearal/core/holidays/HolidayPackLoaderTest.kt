package io.github.chrisjmendoza.yearal.core.holidays

import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.domain.holiday.EasterCalendar
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayCategory
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayDefinition
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayRule
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySet
import io.github.chrisjmendoza.yearal.core.domain.holiday.ObservedPolicy
import io.github.chrisjmendoza.yearal.core.domain.holiday.YearFilter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

// The loader is the only way JSON reaches the engine, so these tests pin the schema-1 wire format of
// docs/holidays-and-import.md §2.4 (as fixed by docs/adr/0004-holiday-pack-format.md): every rule
// type and modifier round-trips from a hand-written fixture, and every malformed pack is rejected
// with a HolidayPackException that names the pack.
class HolidayPackLoaderTest {
    private val loader = HolidayPackLoader()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixture/$name")) { "missing fixture $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun `fixture pack maps onto the domain model exactly`() {
        val set = loader.load("all-rules", fixture("all-rules.json"))

        set shouldBe
            HolidaySet(
                id = "fixture",
                region = "ZZ",
                name = mapOf("en" to "Fixture", "de" to "Testdaten"),
                sources = listOf("hand-written for HolidayPackLoaderTest"),
                holidays =
                    listOf(
                        HolidayDefinition(
                            id = "f.fixed",
                            name = mapOf("en" to "Fixed", "fr" to "Fixe"),
                            rule = HolidayRule.Fixed(7, 4),
                            observed = ObservedPolicy.US_FEDERAL,
                            category = HolidayCategory.PUBLIC,
                        ),
                        HolidayDefinition(
                            id = "f.nth",
                            name = mapOf("en" to "Nth weekday"),
                            rule = HolidayRule.NthWeekday(11, DayOfWeek.THURSDAY, 4),
                            since = 1942,
                            category = HolidayCategory.PUBLIC,
                        ),
                        HolidayDefinition(
                            id = "f.last",
                            name = mapOf("en" to "Last weekday"),
                            rule = HolidayRule.NthWeekday(5, DayOfWeek.MONDAY, -1),
                            since = 1971,
                            until = 2099,
                            category = HolidayCategory.BANK,
                        ),
                        HolidayDefinition(
                            id = "f.relative",
                            name = mapOf("en" to "Weekday relative"),
                            rule =
                                HolidayRule.WeekdayRelative(
                                    DayOfWeek.MONDAY,
                                    5,
                                    24,
                                    HolidayRule.WeekdayRelative.Direction.ON_OR_BEFORE,
                                ),
                            observed = ObservedPolicy.NEXT_MONDAY,
                            category = HolidayCategory.PUBLIC,
                        ),
                        HolidayDefinition(
                            id = "f.relative_after",
                            name = mapOf("en" to "Weekday relative after"),
                            rule =
                                HolidayRule.WeekdayRelative(
                                    DayOfWeek.SATURDAY,
                                    6,
                                    20,
                                    HolidayRule.WeekdayRelative.Direction.ON_OR_AFTER,
                                ),
                            category = HolidayCategory.OBSERVANCE,
                        ),
                        HolidayDefinition(
                            id = "f.offset",
                            name = mapOf("en" to "Offset"),
                            rule = HolidayRule.Offset(1, HolidayRule.NthWeekday(11, DayOfWeek.MONDAY, 1)),
                            yearFilter = YearFilter(2, 0),
                            category = HolidayCategory.OBSERVANCE,
                        ),
                        HolidayDefinition(
                            id = "f.nested_offset",
                            name = mapOf("en" to "Nested offset"),
                            rule = HolidayRule.Offset(-1, HolidayRule.Offset(-1, HolidayRule.Fixed(1, 1))),
                            category = HolidayCategory.OBSERVANCE,
                        ),
                        HolidayDefinition(
                            id = "f.easter",
                            name = mapOf("en" to "Easter"),
                            rule = HolidayRule.Easter(EasterCalendar.WESTERN, 0),
                            category = HolidayCategory.RELIGIOUS,
                        ),
                        HolidayDefinition(
                            id = "f.orthodox",
                            name = mapOf("en" to "Orthodox offset"),
                            rule = HolidayRule.Easter(EasterCalendar.ORTHODOX, -2),
                            category = HolidayCategory.RELIGIOUS,
                        ),
                        HolidayDefinition(
                            id = "f.table",
                            name = mapOf("en" to "Table"),
                            rule =
                                HolidayRule.Table(
                                    mapOf(2024 to LocalDate.of(2024, 11, 1), 2025 to LocalDate.of(2025, 10, 20)),
                                ),
                            durationDays = 8,
                            startsEveBefore = true,
                            approximate = true,
                            category = HolidayCategory.RELIGIOUS,
                        ),
                        HolidayDefinition(
                            id = "f.ifc_regular",
                            name = mapOf("en" to "Sol Day"),
                            rule = HolidayRule.Ifc.Regular(IfcMonth.SOL, 1),
                            category = HolidayCategory.IFC,
                        ),
                        HolidayDefinition(
                            id = "f.ifc_year_day",
                            name = mapOf("en" to "Year Day"),
                            rule = HolidayRule.Ifc.YearDay,
                            category = HolidayCategory.IFC,
                        ),
                        HolidayDefinition(
                            id = "f.ifc_leap_day",
                            name = mapOf("en" to "Leap Day"),
                            rule = HolidayRule.Ifc.LeapDay,
                            category = HolidayCategory.IFC,
                        ),
                        HolidayDefinition(
                            id = "f.sunday_to_monday",
                            name = mapOf("en" to "Inauguration"),
                            rule = HolidayRule.Fixed(1, 20),
                            since = 1937,
                            yearFilter = YearFilter(4, 1),
                            observed = ObservedPolicy.SUNDAY_TO_MONDAY,
                            category = HolidayCategory.OBSERVANCE,
                        ),
                    ),
            )
    }

    @Test
    fun `defaults match the domain model when modifiers are omitted`() {
        val holiday =
            loader
                .load(
                    "p",
                    pack("""{ "id": "a", "name": { "en": "A" }, "rule": $FIXED, "category": "public" }"""),
                ).holidays
                .single()

        holiday.since.shouldBeNull()
        holiday.until.shouldBeNull()
        holiday.yearFilter.shouldBeNull()
        holiday.observed shouldBe ObservedPolicy.NONE
        holiday.durationDays shouldBe 1
        holiday.startsEveBefore shouldBe false
        holiday.approximate shouldBe false
    }

    @Test
    fun `region and sources are optional at the pack level`() {
        val set = loader.load("p", """{ "schema": 1, "id": "p", "name": { "en": "P" }, "holidays": [] }""")

        set.region.shouldBeNull()
        set.sources shouldBe emptyList()
        set.holidays shouldBe emptyList()
    }

    @Test
    fun `every weekday code maps to its real weekday`() {
        val codes = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val holidays =
            codes.mapIndexed { i, code ->
                """{ "id": "w$i", "name": { "en": "$code" },
                    "rule": { "type": "nthWeekday", "month": 1, "weekday": "$code", "n": 1 }, "category": "observance" }"""
            }
        val set = loader.load("p", pack(holidays.joinToString(",")))

        set.holidays.map { (it.rule as HolidayRule.NthWeekday).weekday } shouldContainExactly DayOfWeek.entries
    }

    @Test
    fun `unknown rule type is rejected naming the pack`() {
        val e =
            shouldThrow<HolidayPackException> { loader.load("bad", pack(holiday(rule = """{ "type": "lunar" }"""))) }

        e.packName shouldBe "bad"
        e.message shouldContain "bad"
        e.message shouldContain "lunar"
    }

    @Test
    fun `calendar rule type is rejected with a message that says it is unsupported`() {
        val rule = """{ "type": "calendar", "system": "hebrew", "month": "KISLEV", "day": 25 }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", pack(holiday(rule = rule))) }

        e.message shouldContain "calendar"
        e.message shouldContain "not supported"
    }

    @Test
    fun `rule without a type is rejected`() {
        val e =
            shouldThrow<HolidayPackException> {
                loader.load(
                    "bad",
                    pack(holiday(rule = """{ "month": 1, "day": 1 }""")),
                )
            }

        e.message shouldContain "type"
    }

    @Test
    fun `unknown key on a holiday is rejected`() {
        val text =
            pack("""{ "id": "a", "name": { "en": "A" }, "rule": $FIXED, "category": "public", "colour": "red" }""")
        val e = shouldThrow<HolidayPackException> { loader.load("bad", text) }

        e.message shouldContain "colour"
    }

    @Test
    fun `unknown key inside a rule is rejected`() {
        val e =
            shouldThrow<HolidayPackException> {
                loader.load("bad", pack(holiday(rule = """{ "type": "fixed", "month": 1, "day": 1, "year": 2024 }""")))
            }

        e.message shouldContain "year"
    }

    @Test
    fun `unknown key at the pack level is rejected`() {
        val text = """{ "schema": 1, "id": "p", "name": { "en": "P" }, "holidays": [], "country": "US" }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", text) }

        e.message shouldContain "country"
    }

    @Test
    fun `missing en name is rejected naming the holiday`() {
        val text = pack("""{ "id": "a.no_en", "name": { "de": "A" }, "rule": $FIXED, "category": "public" }""")
        val e = shouldThrow<HolidayPackException> { loader.load("bad", text) }

        e.holidayId shouldBe "a.no_en"
        e.message shouldContain "a.no_en"
        e.message shouldContain "\"en\""
    }

    @Test
    fun `missing en name at the pack level is rejected`() {
        val text = """{ "schema": 1, "id": "p", "name": { "de": "P" }, "holidays": [] }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", text) }

        e.holidayId.shouldBeNull()
        e.message shouldContain "\"en\""
    }

    @Test
    fun `bad weekday spelling is rejected`() {
        val rule = """{ "type": "nthWeekday", "month": 11, "weekday": "Thursday", "n": 4 }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", pack(holiday(rule = rule))) }

        e.message shouldContain "Thursday"
    }

    @Test
    fun `table date in the wrong year is rejected naming the holiday`() {
        val rule = """{ "type": "table", "dates": { "2024": "2025-11-01" } }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", pack(holiday(id = "t.wrong", rule = rule))) }

        e.holidayId shouldBe "t.wrong"
        e.message shouldContain "2025-11-01"
    }

    @Test
    fun `table date that is not ISO-8601 is rejected`() {
        val rule = """{ "type": "table", "dates": { "2024": "11/01/2024" } }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", pack(holiday(rule = rule))) }

        e.message shouldContain "11/01/2024"
    }

    @Test
    fun `table key that is not a year is rejected`() {
        val rule = """{ "type": "table", "dates": { "twenty24": "2024-11-01" } }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", pack(holiday(rule = rule))) }

        e.message shouldContain "twenty24"
    }

    @Test
    fun `duplicate holiday id is rejected`() {
        val text = pack(holiday(id = "dup") + "," + holiday(id = "dup"))
        val e = shouldThrow<HolidayPackException> { loader.load("bad", text) }

        e.message shouldContain "dup"
    }

    @Test
    fun `ifc rule with both special and month-day is rejected`() {
        val rule = """{ "type": "ifc", "special": "YEAR_DAY", "month": 7, "day": 1 }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", pack(holiday(rule = rule))) }

        e.message shouldContain "special"
    }

    @Test
    fun `ifc rule with neither form is rejected`() {
        val e =
            shouldThrow<HolidayPackException> {
                loader.load(
                    "bad",
                    pack(holiday(rule = """{ "type": "ifc", "month": 7 }""")),
                )
            }

        e.message shouldContain "special"
    }

    @Test
    fun `ifc month outside 1 to 13 and day outside 1 to 28 are rejected`() {
        shouldThrow<HolidayPackException> {
            loader.load("bad", pack(holiday(rule = """{ "type": "ifc", "month": 14, "day": 1 }""")))
        }.message shouldContain "14"
        shouldThrow<HolidayPackException> {
            loader.load("bad", pack(holiday(rule = """{ "type": "ifc", "month": 7, "day": 29 }""")))
        }.message shouldContain "29"
    }

    @Test
    fun `unknown ifc special is rejected`() {
        val e =
            shouldThrow<HolidayPackException> {
                loader.load("bad", pack(holiday(rule = """{ "type": "ifc", "special": "SOL_DAY" }""")))
            }

        e.message shouldContain "SOL_DAY"
    }

    @Test
    fun `domain range errors are reported with the holiday id`() {
        val rule = """{ "type": "fixed", "month": 2, "day": 30 }"""
        val e = shouldThrow<HolidayPackException> { loader.load("bad", pack(holiday(id = "f.feb30", rule = rule))) }

        e.holidayId shouldBe "f.feb30"
        e.message shouldContain "30"
    }

    @Test
    fun `unknown observed policy, category and easter calendar are rejected`() {
        shouldThrow<HolidayPackException> {
            loader.load(
                "bad",
                pack(
                    """{ "id": "a", "name": { "en": "A" }, "rule": $FIXED, "observed": "uk_substitute", "category": "public" }""",
                ),
            )
        }.message shouldContain "uk_substitute"
        shouldThrow<HolidayPackException> {
            loader.load("bad", pack("""{ "id": "a", "name": { "en": "A" }, "rule": $FIXED, "category": "school" }"""))
        }.message shouldContain "school"
        shouldThrow<HolidayPackException> {
            loader.load("bad", pack(holiday(rule = """{ "type": "easter", "calendar": "julian" }""")))
        }.message shouldContain "julian"
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val text = """{ "schema": 2, "id": "p", "name": { "en": "P" }, "holidays": [] }"""
        val e = shouldThrow<HolidayPackException> { loader.load("future", text) }

        e.message shouldContain "schema 2"
    }

    @Test
    fun `malformed JSON is rejected naming the pack`() {
        val e = shouldThrow<HolidayPackException> { loader.load("broken", "{ \"schema\": 1, ") }

        e.packName shouldBe "broken"
        e.holidayId.shouldBeNull()
    }

    @Test
    fun `missing bundled pack is rejected naming the pack`() {
        val e = shouldThrow<HolidayPackException> { loader.loadBundled("no-such-pack") }

        e.packName shouldBe "no-such-pack"
        e.message shouldContain "no-such-pack.json"
    }

    private companion object {
        const val FIXED = """{ "type": "fixed", "month": 1, "day": 1 }"""

        fun pack(holidays: String): String =
            """{ "schema": 1, "id": "p", "region": "US", "name": { "en": "P" }, "sources": [], "holidays": [ $holidays ] }"""

        fun holiday(
            id: String = "h",
            rule: String = FIXED,
        ): String = """{ "id": "$id", "name": { "en": "H" }, "rule": $rule, "category": "observance" }"""
    }
}
