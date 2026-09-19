package io.github.chrisjmendoza.yearal.feature.settings.learn

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Confirms every worked-example date the Learn screen shows (docs/calendar-spec.md §2.4, §3, §4.1, §5)
 * independently, against `:core:calendar` itself, so the screen's copy cannot silently drift from the
 * conversion core it is explaining (CLAUDE.md rule 1, rule 12). This is a plain JUnit test with no
 * Android dependency, so it runs as a fast JVM unit test.
 */
class LearnFactsTest {
    @Test
    fun `the weekday example is the spec's own illustration from section 4_1`() {
        LearnFacts.weekdayExampleGregorian shouldBe LocalDate.of(2026, 9, 17)
        LearnFacts.weekdayExampleGregorian.dayOfWeek shouldBe DayOfWeek.THURSDAY

        val ifc = LearnFacts.weekdayExampleIfc.shouldBeInstanceOf<IfcDate.Regular>()
        ifc.year shouldBe 2026
        ifc.month shouldBe IfcMonth.SEPTEMBER
        ifc.dayOfMonth shouldBe 8
        ifc.nominalDayOfWeek shouldBe DayOfWeek.SUNDAY
        ifc.actualDayOfWeek shouldBe DayOfWeek.THURSDAY

        // Round-trips through the same conversion the rest of the app uses (§3.1).
        IfcDate.from(LearnFacts.weekdayExampleGregorian) shouldBe ifc
    }

    @Test
    fun `Sol 1 is Gregorian June 18 in both a common year and a leap year (section 2_4)`() {
        LearnFacts.sol1CommonYear.toLocalDate() shouldBe LocalDate.of(2026, 6, 18)
        LearnFacts.sol1LeapYear.toLocalDate() shouldBe LocalDate.of(2024, 6, 18)
    }

    @Test
    fun `Year Day is always Gregorian December 31 (section 2_4, rule R8)`() {
        LearnFacts.yearDayExample.toLocalDate() shouldBe LocalDate.of(2026, 12, 31)
    }

    @Test
    fun `Leap Day is Gregorian June 17 and only exists in leap years (section 2_4, rule R9)`() {
        LearnFacts.leapDayExample.toLocalDate() shouldBe LocalDate.of(2024, 6, 17)
    }

    @Test
    fun `the leap-year shift moves Gregorian March 1 one IFC day later (section 2_4)`() {
        val common = LearnFacts.march1CommonYear.shouldBeInstanceOf<IfcDate.Regular>()
        common.month shouldBe IfcMonth.MARCH
        common.dayOfMonth shouldBe 4

        val leap = LearnFacts.march1LeapYear.shouldBeInstanceOf<IfcDate.Regular>()
        leap.month shouldBe IfcMonth.MARCH
        leap.dayOfMonth shouldBe 5
    }

    @Test
    fun `the 13th of every month is a nominal Friday (section 2_3 rule R6)`() {
        LearnFacts.friday13Example.nominalDayOfWeek shouldBe DayOfWeek.FRIDAY
    }
}
