package io.github.chrisjmendoza.yearal.core.domain.holiday

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveAtMostSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year

// Property tests over random years in the UI range (calendar-spec §7.1: 1583..9999) for the invariants
// of docs/holidays-and-import.md §2.2 and §5.1.
class HolidayEnginePropertyTest {
    private val engine = HolidayEngine()
    private val years = Arb.int(1583..9999)

    @Test
    fun `fixed, nth weekday and easter anchors always fall in the requested year`() {
        runBlocking {
            checkAll(
                years,
                Arb.int(1..12),
                Arb.int(1..28),
                Arb.enum<DayOfWeek>(),
                Arb.int(-5..5).filter { it != 0 },
                Arb.enum<EasterCalendar>(),
            ) { year, month, day, weekday, n, calendar ->
                val rules =
                    listOf(
                        HolidayRule.Fixed(month, day),
                        HolidayRule.NthWeekday(month, weekday, n),
                        HolidayRule.Easter(calendar, 0),
                    )
                for (rule in rules) {
                    val dates = engine.dates(single(rule), year)
                    withClue("$rule in $year") {
                        dates shouldHaveAtMostSize 1
                        dates.forEach { it.year shouldBe year }
                    }
                }
            }
        }
    }

    @Test
    fun `fixed and easter always yield exactly one date, easter on a Sunday inside its window`() {
        runBlocking {
            checkAll(years, Arb.int(1..12), Arb.int(1..28), Arb.enum<EasterCalendar>()) { year, month, day, calendar ->
                engine.dates(single(HolidayRule.Fixed(month, day)), year) shouldBe
                    listOf(LocalDate.of(year, month, day))
                val easter = engine.dates(single(HolidayRule.Easter(calendar, 0)), year).single()
                withClue("$calendar Easter $year = $easter") {
                    easter.dayOfWeek shouldBe DayOfWeek.SUNDAY
                    val gregorianWindow = LocalDate.of(year, 3, 22)..LocalDate.of(year, 4, 25)
                    when (calendar) {
                        EasterCalendar.WESTERN -> (easter in gregorianWindow) shouldBe true

                        // Orthodox: Julian March 22 – April 25 plus the century shift (13–14 days now,
                        // more later), so only the lower bound is fixed.
                        EasterCalendar.ORTHODOX -> (easter >= gregorianWindow.start) shouldBe true
                    }
                }
            }
        }
    }

    @Test
    fun `nth weekday lands on that weekday in that month, first within 7 days of the start, last of the end`() {
        runBlocking {
            val ordinals = Arb.int(-5..5).filter { it != 0 }
            checkAll(years, Arb.int(1..12), Arb.enum<DayOfWeek>(), ordinals) { year, month, weekday, n ->
                val dates = engine.dates(single(HolidayRule.NthWeekday(month, weekday, n)), year)
                withClue("$n-th $weekday of $month $year") {
                    dates.forEach {
                        it.dayOfWeek shouldBe weekday
                        it.monthValue shouldBe month
                    }
                    if (n == 1 || n == -1) dates.size shouldBe 1
                    if (n == -1) (dates.single().lengthOfMonth() - dates.single().dayOfMonth < 7) shouldBe true
                    if (n == 1) (dates.single().dayOfMonth <= 7) shouldBe true
                }
            }
        }
    }

    @Test
    fun `sol 1 is always June 18 and year day always December 31`() {
        runBlocking {
            checkAll(years) { year ->
                val sol1 = single(HolidayRule.Ifc.Regular(IfcMonth.SOL, 1))
                engine.dates(sol1, year) shouldBe listOf(LocalDate.of(year, 6, 18))
                engine.dates(single(HolidayRule.Ifc.YearDay), year) shouldBe listOf(LocalDate.of(year, 12, 31))
            }
        }
    }

    @Test
    fun `leap day is June 17 exactly in Gregorian leap years`() {
        runBlocking {
            checkAll(years) { year ->
                val expected = if (Year.isLeap(year.toLong())) listOf(LocalDate.of(year, 6, 17)) else emptyList()
                engine.dates(single(HolidayRule.Ifc.LeapDay), year) shouldBe expected
            }
        }
    }

    @Test
    fun `every ifc regular position converts back to itself through IfcDate`() {
        runBlocking {
            checkAll(years, Arb.enum<IfcMonth>(), Arb.int(1..28)) { year, month, day ->
                val date = engine.dates(single(HolidayRule.Ifc.Regular(month, day)), year).single()
                IfcDate.from(date) shouldBe IfcDate.Regular(year, month, day)
            }
        }
    }

    @Test
    fun `the observed entry of a us federal holiday is always a Friday or Monday adjacent to a weekend anchor`() {
        runBlocking {
            checkAll(years, Arb.int(1..12), Arb.int(1..28)) { year, month, day ->
                val set = pack("p", holiday("h", HolidayRule.Fixed(month, day), observed = ObservedPolicy.US_FEDERAL))
                val occurrences = engine.occurrences(set, year)
                val actual = occurrences.single { !it.observed }.date
                val observed = occurrences.filter { it.observed }.map { it.date }
                withClue("$actual (${actual.dayOfWeek})") {
                    when (actual.dayOfWeek) {
                        DayOfWeek.SATURDAY -> observed shouldBe listOf(actual.minusDays(1))
                        DayOfWeek.SUNDAY -> observed shouldBe listOf(actual.plusDays(1))
                        else -> observed shouldBe emptyList()
                    }
                }
            }
        }
    }
}
