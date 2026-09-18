package io.github.chrisjmendoza.yearal.core.calendar

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

// Verifies calendar-spec §7.7 "Differences": daysUntil is ChronoUnit.DAYS.between on the Gregorian dates.
class IfcDateDaysUntilTest {
    @Test
    fun `worked examples including intercalary days`() {
        IfcDate.Regular(2024, IfcMonth.JUNE, 28).daysUntil(IfcDate.Regular(2024, IfcMonth.SOL, 1)) shouldBe 2
        IfcDate.Regular(2025, IfcMonth.JUNE, 28).daysUntil(IfcDate.Regular(2025, IfcMonth.SOL, 1)) shouldBe 1
        IfcDate.Regular(2026, IfcMonth.DECEMBER, 28).daysUntil(IfcDate.Regular(2027, IfcMonth.JANUARY, 1)) shouldBe 2
        IfcDate.YearDay(2026).daysUntil(IfcDate.Regular(2027, IfcMonth.JANUARY, 1)) shouldBe 1
        IfcDate.LeapDay(2024).daysUntil(IfcDate.LeapDay(2028)) shouldBe 4 * 365L + 1
        IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8).daysUntil(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)) shouldBe 0
        IfcDate.Regular(2026, IfcMonth.SOL, 1).daysUntil(IfcDate.Regular(2026, IfcMonth.JUNE, 28)) shouldBe -1
    }

    @Test
    fun `daysUntil inverts plusDays and matches the Gregorian difference for random dates`() =
        runBlocking {
            checkAll(2_000, Arb.int(1..9999), Arb.int(1..365), Arb.long(-2_000_000L..2_000_000L)) { year, doy, n ->
                val start = IfcDate.ofYearDay(year, doy)
                val target = start.toLocalDate().plusDays(n)
                if (target.year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) {
                    val end = IfcDate.from(target)
                    start.daysUntil(end) shouldBe n
                    end.daysUntil(start) shouldBe -n
                    start.plusDays(start.daysUntil(end)) shouldBe end
                    start.daysUntil(end) shouldBe
                        java.time.temporal.ChronoUnit.DAYS
                            .between(start.toLocalDate(), LocalDate.from(target))
                }
            }
        }
}
