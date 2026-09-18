package io.github.chrisjmendoza.yearal.core.calendar

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.time.Clock
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.Year
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale

// Verifies spec §3.3 (V1–V6), the §6.5 negative vectors and positive counterparts, §7.3 numeric form, §7.8 now().
class IfcDateValidationTest {
    @TestFactory
    fun `negative vectors of section 6_5 throw DateTimeException`(): List<DynamicTest> =
        listOf<Pair<String, () -> Any>>(
            "LeapDay(2025)" to { IfcDate.LeapDay(2025) },
            "LeapDay(2026)" to { IfcDate.LeapDay(2026) },
            "LeapDay(1900)" to { IfcDate.LeapDay(1900) },
            "LeapDay(2100)" to { IfcDate.LeapDay(2100) },
            "of(2025, 6, 29)" to { IfcDate.of(2025, 6, 29) },
            "of(2024, 7, 29)" to { IfcDate.of(2024, 7, 29) },
            "of(2024, 1, 29)" to { IfcDate.of(2024, 1, 29) },
            "of(2024, 13, 30)" to { IfcDate.of(2024, 13, 30) },
            "of(2024, 6, 30)" to { IfcDate.of(2024, 6, 30) },
            "of(2024, 2, 31)" to { IfcDate.of(2024, 2, 31) },
            "of(2024, 1, 0)" to { IfcDate.of(2024, 1, 0) },
            "of(2024, 0, 1)" to { IfcDate.of(2024, 0, 1) },
            "of(2024, 14, 1)" to { IfcDate.of(2024, 14, 1) },
            "Regular(2024, JUNE, 29)" to { IfcDate.Regular(2024, IfcMonth.JUNE, 29) },
            "of(0, 1, 1)" to { IfcDate.of(0, 1, 1) },
            "of(10000, 1, 1)" to { IfcDate.of(10000, 1, 1) },
            "from(LocalDate.of(-1, 1, 1))" to { IfcDate.from(LocalDate.of(-1, 1, 1)) },
        ).map { (name, block) -> dynamicTest(name) { shouldThrow<DateTimeException> { block() } } }

    @TestFactory
    fun `syntax errors of section 6_5 and other non-canonical text throw DateTimeParseException`(): List<DynamicTest> =
        listOf(
            "2026-13-29x",
            "2026-7-1",
            "26-07-01",
            "",
            "IFC",
            "IFC ",
            "2026-01-1",
            "2026/01/01",
            "20260101",
            "02026-01-01",
            "+2026-01-01",
            "-2026-01-01",
            "2026-01-01T00:00",
            " 2026-01-01",
            "2026-01-01 ",
            "IFC 2026-13-29x",
            "IFC2026-01-01",
            "Year Day 2026",
            "2026-260",
            // Spec §7.6: the canonical numeric form always uses ASCII digits.
            ARABIC_INDIC_DATE,
        ).map { text ->
            dynamicTest("parse(\"$text\")") { shouldThrow<DateTimeParseException> { IfcDate.parse(text) } }
        }

    @TestFactory
    fun `well-formed text naming a non-existent date throws DateTimeException`(): List<DynamicTest> =
        listOf(
            "2025-06-29",
            "1900-06-29",
            "2100-06-29",
            "2024-07-29",
            "2024-01-29",
            "2024-13-30",
            "2024-06-30",
            "2024-02-31",
            "2024-01-00",
            "2024-00-01",
            "2024-14-01",
            "2024-99-99",
            "0000-01-01",
            "IFC 2025-06-29",
            "IFC 0000-13-29",
        ).map { text -> dynamicTest("parse(\"$text\")") { shouldThrow<DateTimeException> { IfcDate.parse(text) } } }

    @TestFactory
    fun `years outside 1 to 9999 are rejected by every entry point`(): List<DynamicTest> =
        listOf<Pair<String, () -> Any>>(
            "Regular(0, JANUARY, 1)" to { IfcDate.Regular(0, IfcMonth.JANUARY, 1) },
            "Regular(10000, JANUARY, 1)" to { IfcDate.Regular(10000, IfcMonth.JANUARY, 1) },
            "Regular(-2024, JANUARY, 1)" to { IfcDate.Regular(-2024, IfcMonth.JANUARY, 1) },
            "LeapDay(0)" to { IfcDate.LeapDay(0) },
            "LeapDay(10000)" to { IfcDate.LeapDay(10000) },
            "LeapDay(-4)" to { IfcDate.LeapDay(-4) },
            "YearDay(0)" to { IfcDate.YearDay(0) },
            "YearDay(10000)" to { IfcDate.YearDay(10000) },
            "of(0, 13, 29)" to { IfcDate.of(0, 13, 29) },
            "of(10000, 13, 29)" to { IfcDate.of(10000, 13, 29) },
            "of(Int.MIN_VALUE, 1, 1)" to { IfcDate.of(Int.MIN_VALUE, 1, 1) },
            "of(Int.MAX_VALUE, 1, 1)" to { IfcDate.of(Int.MAX_VALUE, 1, 1) },
            "from(0000-12-31)" to { IfcDate.from(LocalDate.of(0, 12, 31)) },
            "from(10000-01-01)" to { IfcDate.from(LocalDate.of(10000, 1, 1)) },
            "from(LocalDate.MIN)" to { IfcDate.from(LocalDate.MIN) },
            "from(LocalDate.MAX)" to { IfcDate.from(LocalDate.MAX) },
            "ofYearDay(0, 1)" to { IfcDate.ofYearDay(0, 1) },
            "ofYearDay(10000, 1)" to { IfcDate.ofYearDay(10000, 1) },
        ).map { (name, block) -> dynamicTest(name) { shouldThrow<DateTimeException> { block() } } }

    @TestFactory
    fun `regular days are limited to 1 to 28 in every month`(): List<DynamicTest> =
        IfcMonth.entries.map { month ->
            dynamicTest(month.name) {
                assertSoftly {
                    for (day in listOf(Int.MIN_VALUE, -1, 0, 29, 30, 31, 32, Int.MAX_VALUE)) {
                        withClue("Regular(2024, $month, $day)") {
                            shouldThrow<DateTimeException> { IfcDate.Regular(2024, month, day) }
                        }
                    }
                    for (day in 1..28) {
                        withClue("of(2024, ${month.number}, $day)") {
                            IfcDate.of(2024, month.number, day) shouldBe IfcDate.Regular(2024, month, day)
                        }
                    }
                }
            }
        }

    @Test
    fun `day 29 resolves only for June in leap years and for December`() {
        assertSoftly {
            for (year in listOf(1, 1900, 2000, 2024, 2025, 2026, 2100, 9999)) {
                for (month in 1..13) {
                    withClue("of($year, $month, 29)") {
                        when {
                            month == 13 -> {
                                IfcDate.of(year, month, 29) shouldBe IfcDate.YearDay(year)
                            }

                            month == 6 && Year.isLeap(year.toLong()) -> {
                                IfcDate.of(year, month, 29) shouldBe IfcDate.LeapDay(year)
                            }

                            else -> {
                                shouldThrow<DateTimeException> { IfcDate.of(year, month, 29) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `positive counterparts of section 6_5 hold`() {
        assertSoftly {
            IfcDate.of(2024, 6, 29) shouldBe IfcDate.LeapDay(2024)
            IfcDate.of(2000, 6, 29) shouldBe IfcDate.LeapDay(2000)
            IfcDate.of(2025, 13, 29) shouldBe IfcDate.YearDay(2025)
            IfcDate.of(2024, 13, 29) shouldBe IfcDate.YearDay(2024)
        }
    }

    @Test
    fun `equality and hashCode agree across constructors and factories`() {
        val leapDay = IfcDate.LeapDay(2024)
        val yearDay = IfcDate.YearDay(2026)
        val regular = IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
        assertSoftly {
            for (other in listOf(
                IfcDate.of(2024, 6, 29),
                IfcDate.from(LocalDate.of(2024, 6, 17)),
                IfcDate.ofYearDay(2024, 169),
                IfcDate.parse("2024-06-29"),
                IfcDate.parse("IFC 2024-06-29"),
                LocalDate.of(2024, 6, 17).toIfcDate(),
            )) {
                other shouldBe leapDay
                other.hashCode() shouldBe leapDay.hashCode()
                other.compareTo(leapDay) shouldBe 0
            }
            for (other in listOf(
                IfcDate.of(2026, 13, 29),
                IfcDate.from(LocalDate.of(2026, 12, 31)),
                IfcDate.ofYearDay(2026, 365),
                IfcDate.parse("2026-13-29"),
                LocalDate.of(2026, 12, 31).toIfcDate(),
            )) {
                other shouldBe yearDay
                other.hashCode() shouldBe yearDay.hashCode()
            }
            for (other in listOf(
                IfcDate.of(2026, 10, 8),
                IfcDate.from(LocalDate.of(2026, 9, 17)),
                IfcDate.ofYearDay(2026, 260),
                IfcDate.parse("2026-10-08"),
                LocalDate.of(2026, 9, 17).toIfcDate(),
            )) {
                other shouldBe regular
                other.hashCode() shouldBe regular.hashCode()
            }
            val differentDates: List<IfcDate> =
                listOf(
                    IfcDate.YearDay(2024),
                    IfcDate.LeapDay(2028),
                    IfcDate.Regular(2024, IfcMonth.JUNE, 28),
                    IfcDate.Regular(2024, IfcMonth.SOL, 1),
                )
            for (other in differentDates) other shouldNotBe leapDay
        }
    }

    @Test
    fun `intercalary days report the pseudo-fields of the month they follow`() {
        val leapDay: IfcDate = IfcDate.LeapDay(2024)
        val leapYearDay: IfcDate = IfcDate.YearDay(2024)
        val commonYearDay: IfcDate = IfcDate.YearDay(2026)
        assertSoftly {
            leapDay.monthNumber shouldBe 6
            leapDay.dayOfMonth shouldBe 29
            leapDay.dayOfYear shouldBe 169
            leapDay.quarter shouldBe 2
            leapDay.nominalDayOfWeek shouldBe null
            leapDay.weekOfYear shouldBe null
            leapDay.isIntercalary shouldBe true
            leapDay.toLocalDate() shouldBe LocalDate.of(2024, 6, 17)
            leapDay.actualDayOfWeek shouldBe DayOfWeek.MONDAY

            leapYearDay.monthNumber shouldBe 13
            leapYearDay.dayOfMonth shouldBe 29
            leapYearDay.dayOfYear shouldBe 366
            leapYearDay.quarter shouldBe 4
            leapYearDay.nominalDayOfWeek shouldBe null
            leapYearDay.weekOfYear shouldBe null
            leapYearDay.isIntercalary shouldBe true
            leapYearDay.toLocalDate() shouldBe LocalDate.of(2024, 12, 31)

            commonYearDay.dayOfYear shouldBe 365
            commonYearDay.toLocalDate() shouldBe LocalDate.of(2026, 12, 31)
            commonYearDay.actualDayOfWeek shouldBe DayOfWeek.THURSDAY
        }
    }

    @Test
    fun `parse accepts the numeric form with and without the IFC prefix`() {
        assertSoftly {
            IfcDate.parse("2026-10-08") shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
            IfcDate.parse("IFC 2026-10-08") shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
            IfcDate.parse("IFC 2026-10-08").toLocalDate() shouldBe LocalDate.of(2026, 9, 17)
            IfcDate.parse("2024-06-29") shouldBe IfcDate.LeapDay(2024)
            IfcDate.parse("IFC 2024-06-29") shouldBe IfcDate.LeapDay(2024)
            IfcDate.parse("2026-13-29") shouldBe IfcDate.YearDay(2026)
            IfcDate.parse("IFC 2026-13-29") shouldBe IfcDate.YearDay(2026)
            IfcDate.parse("0001-01-01") shouldBe IfcDate.Regular(1, IfcMonth.JANUARY, 1)
            IfcDate.parse("9999-13-29") shouldBe IfcDate.YearDay(9999)
            IfcDate.parse("2026-07-01") shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 1)
            IfcDate.parse(StringBuilder("IFC 2026-08-01")) shouldBe IfcDate.Regular(2026, IfcMonth.JULY, 1)
        }
    }

    @Test
    fun `numeric and prefixed strings follow section 7_3`() {
        assertSoftly {
            IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8).toNumericString() shouldBe "2026-10-08"
            IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8).toPrefixedString() shouldBe "IFC 2026-10-08"
            IfcDate.LeapDay(2024).toNumericString() shouldBe "2024-06-29"
            IfcDate.LeapDay(2024).toPrefixedString() shouldBe "IFC 2024-06-29"
            IfcDate.YearDay(2026).toNumericString() shouldBe "2026-13-29"
            IfcDate.YearDay(2026).toPrefixedString() shouldBe "IFC 2026-13-29"
            IfcDate.Regular(1, IfcMonth.JANUARY, 1).toNumericString() shouldBe "0001-01-01"
            IfcDate.Regular(33, IfcMonth.SOL, 9).toPrefixedString() shouldBe "IFC 0033-07-09"
            IfcDate.NUMERIC_PREFIX shouldBe "IFC "
        }
    }

    @Test
    fun `numeric strings use ASCII digits whatever the default locale is - section 7_6`() {
        // calendar-spec 7.6: "the canonical numeric form always uses ASCII digits". These locales format
        // %d with their own digits (Arabic-Indic, Devanagari, Persian), which parse() would then reject.
        val saved = Locale.getDefault()
        try {
            listOf("ar-EG", "hi-IN-u-nu-deva", "fa-IR").forEach { tag ->
                Locale.setDefault(Locale.forLanguageTag(tag))
                withClue(tag) {
                    assertSoftly {
                        IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8).toNumericString() shouldBe "2026-10-08"
                        IfcDate.LeapDay(2024).toPrefixedString() shouldBe "IFC 2024-06-29"
                        IfcDate.YearDay(2026).toNumericString() shouldBe "2026-13-29"
                        IfcDate.Regular(33, IfcMonth.SOL, 9).toNumericString() shouldBe "0033-07-09"
                        IfcDate.parse(IfcDate.YearDay(9999).toPrefixedString()) shouldBe IfcDate.YearDay(9999)
                    }
                }
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `parse inverts toNumericString and toPrefixedString`() {
        val samples: List<IfcDate> =
            listOf(
                IfcDate.Regular(1, IfcMonth.JANUARY, 1),
                IfcDate.Regular(476, IfcMonth.SOL, 28),
                IfcDate.Regular(2026, IfcMonth.DECEMBER, 28),
                IfcDate.LeapDay(4),
                IfcDate.LeapDay(2000),
                IfcDate.YearDay(1),
                IfcDate.YearDay(9999),
            )
        assertSoftly {
            for (date in samples) {
                withClue(date.toString()) {
                    IfcDate.parse(date.toNumericString()) shouldBe date
                    IfcDate.parse(date.toPrefixedString()) shouldBe date
                }
            }
        }
    }

    @Test
    fun `now rolls over at local midnight from Year Day to January 1`() {
        for (zone in EXTREME_ZONES) {
            withClue(zone.id) {
                IfcDate.now(clockAt("2026-12-31T23:59:59", zone)) shouldBe IfcDate.YearDay(2026)
                IfcDate.now(clockAt("2027-01-01T00:00:00", zone)) shouldBe IfcDate.Regular(2027, IfcMonth.JANUARY, 1)
            }
        }
    }

    @Test
    fun `now rolls over at local midnight into and out of Leap Day`() {
        for (zone in EXTREME_ZONES) {
            withClue(zone.id) {
                IfcDate.now(clockAt("2024-06-16T23:59:59", zone)) shouldBe IfcDate.Regular(2024, IfcMonth.JUNE, 28)
                IfcDate.now(clockAt("2024-06-17T00:00:00", zone)) shouldBe IfcDate.LeapDay(2024)
                IfcDate.now(clockAt("2024-06-17T23:59:59", zone)) shouldBe IfcDate.LeapDay(2024)
                IfcDate.now(clockAt("2024-06-18T00:00:00", zone)) shouldBe IfcDate.Regular(2024, IfcMonth.SOL, 1)
            }
        }
    }

    @Test
    fun `now depends on the zone for one and the same instant`() {
        val instant = Instant.parse("2026-12-31T10:30:00Z")
        assertSoftly {
            IfcDate.now(Clock.fixed(instant, ZoneId.of("Pacific/Kiritimati"))) shouldBe
                IfcDate.Regular(2027, IfcMonth.JANUARY, 1)
            IfcDate.now(Clock.fixed(instant, ZoneOffset.UTC)) shouldBe IfcDate.YearDay(2026)
            IfcDate.now(Clock.fixed(instant, ZoneId.of("Etc/GMT+12"))) shouldBe
                IfcDate.Regular(2026, IfcMonth.DECEMBER, 28)
        }
    }

    @Test
    fun `now is unaffected by a daylight saving gap`() {
        // 2026-03-08 02:00 does not exist in New York; by §5.1 March 1 is Feb 26, so Mar 8 is March 11.
        val zone = ZoneId.of("America/New_York")
        val expected = IfcDate.Regular(2026, IfcMonth.MARCH, 11)
        assertSoftly {
            IfcDate.now(Clock.fixed(Instant.parse("2026-03-08T06:59:59Z"), zone)) shouldBe expected
            IfcDate.now(Clock.fixed(Instant.parse("2026-03-08T07:00:00Z"), zone)) shouldBe expected
            IfcDate.now(Clock.fixed(Instant.parse("2026-03-08T04:59:59Z"), zone)) shouldBe
                IfcDate.Regular(2026, IfcMonth.MARCH, 10)
        }
    }

    @Test
    fun `ofYearDay accepts exactly the days of the year`() {
        assertSoftly {
            IfcDate.ofYearDay(2026, 1) shouldBe IfcDate.Regular(2026, IfcMonth.JANUARY, 1)
            IfcDate.ofYearDay(2026, 260) shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
            IfcDate.ofYearDay(2025, 168) shouldBe IfcDate.Regular(2025, IfcMonth.JUNE, 28)
            IfcDate.ofYearDay(2025, 169) shouldBe IfcDate.Regular(2025, IfcMonth.SOL, 1)
            IfcDate.ofYearDay(2024, 169) shouldBe IfcDate.LeapDay(2024)
            IfcDate.ofYearDay(2024, 170) shouldBe IfcDate.Regular(2024, IfcMonth.SOL, 1)
            IfcDate.ofYearDay(2025, 364) shouldBe IfcDate.Regular(2025, IfcMonth.DECEMBER, 28)
            IfcDate.ofYearDay(2025, 365) shouldBe IfcDate.YearDay(2025)
            IfcDate.ofYearDay(2024, 365) shouldBe IfcDate.Regular(2024, IfcMonth.DECEMBER, 28)
            IfcDate.ofYearDay(2024, 366) shouldBe IfcDate.YearDay(2024)
            for ((year, day) in listOf(2025 to 366, 1900 to 366, 2100 to 366, 2024 to 367, 2025 to 0, 2025 to -1)) {
                withClue("ofYearDay($year, $day)") { shouldThrow<DateTimeException> { IfcDate.ofYearDay(year, day) } }
            }
        }
    }

    @Test
    fun `dates sort chronologically`() {
        val ordered: List<IfcDate> =
            listOf(
                IfcDate.YearDay(2023),
                IfcDate.Regular(2024, IfcMonth.JANUARY, 1),
                IfcDate.Regular(2024, IfcMonth.JUNE, 28),
                IfcDate.LeapDay(2024),
                IfcDate.Regular(2024, IfcMonth.SOL, 1),
                IfcDate.Regular(2024, IfcMonth.JULY, 1),
                IfcDate.Regular(2024, IfcMonth.DECEMBER, 28),
                IfcDate.YearDay(2024),
                IfcDate.Regular(2025, IfcMonth.JANUARY, 1),
            )
        ordered.reversed().sorted() shouldBe ordered
        ordered.sortedBy { it.toNumericString() } shouldBe ordered
    }

    @Test
    fun `IfcMonth of maps 1 to 13 in the order of rule R4 and rejects everything else`() {
        val names =
            listOf(
                "JANUARY",
                "FEBRUARY",
                "MARCH",
                "APRIL",
                "MAY",
                "JUNE",
                "SOL",
                "JULY",
                "AUGUST",
                "SEPTEMBER",
                "OCTOBER",
                "NOVEMBER",
                "DECEMBER",
            )
        assertSoftly {
            IfcMonth.entries.map { it.name } shouldBe names
            names.forEachIndexed { index, name ->
                withClue("month ${index + 1}") {
                    IfcMonth.of(index + 1).name shouldBe name
                    IfcMonth.of(index + 1).number shouldBe index + 1
                }
            }
            for (number in listOf(0, 14, -1, 15, 100, Int.MIN_VALUE, Int.MAX_VALUE)) {
                withClue("IfcMonth.of($number)") { shouldThrow<DateTimeException> { IfcMonth.of(number) } }
            }
            IfcMonth.DAYS_PER_MONTH shouldBe 28
            IfcMonth.MONTHS_PER_YEAR shouldBe 13
        }
    }

    @Test
    fun `gregorianNamesake is the Gregorian month of the same name and null only for Sol`() {
        assertSoftly {
            for (month in IfcMonth.entries) {
                withClue(month.name) {
                    val expected = if (month.name == "SOL") null else Month.valueOf(month.name)
                    month.gregorianNamesake shouldBe expected
                }
            }
            IfcMonth.entries.count { it.gregorianNamesake == null } shouldBe 1
            IfcMonth.JUNE.gregorianNamesake?.value shouldBe IfcMonth.JUNE.number
            IfcMonth.JULY.number shouldBe 8
            IfcMonth.JULY.gregorianNamesake shouldBe Month.JULY
            IfcMonth.DECEMBER.number shouldBe 13
            IfcMonth.DECEMBER.gregorianNamesake shouldBe Month.DECEMBER
        }
    }

    private fun clockAt(
        localDateTime: String,
        zone: ZoneId,
    ): Clock = Clock.fixed(LocalDateTime.parse(localDateTime).atZone(zone).toInstant(), zone)

    private companion object {
        // "2026-01-01" written with the Arabic-Indic digits U+0660..U+0669.
        val ARABIC_INDIC_DATE: String =
            "2026-01-01".map { if (it in '0'..'9') Char(ARABIC_INDIC_ZERO + (it - '0')) else it }.joinToString("")
        const val ARABIC_INDIC_ZERO = 0x0660

        val EXTREME_ZONES: List<ZoneId> =
            listOf(
                ZoneOffset.UTC,
                ZoneId.of("Pacific/Kiritimati"),
                ZoneId.of("Etc/GMT+12"),
                ZoneId.of("America/New_York"),
                ZoneId.of("Asia/Kathmandu"),
            )
    }
}
