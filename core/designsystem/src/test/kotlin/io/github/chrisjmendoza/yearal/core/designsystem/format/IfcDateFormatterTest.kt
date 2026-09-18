package io.github.chrisjmendoza.yearal.core.designsystem.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter.MonthNameStyle
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter.WeekdayNameStyle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Oracle test for [IfcDateFormatter], written from `docs/calendar-spec.md` §7.3 (the style table),
 * §7.4, §7.5, §7.6 and §4.1 — not from the implementation. Expected strings are the spec's own
 * examples, in `Locale.US`.
 */
@RunWith(AndroidJUnit4::class)
class IfcDateFormatterTest {
    private lateinit var formatter: IfcDateFormatter

    // The spec's worked example (§4.1): Gregorian Thursday, 17 September 2026 = IFC September 8, 2026.
    private val september8 = IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
    private val sol8 = IfcDate.Regular(2026, IfcMonth.SOL, 8)
    private val leapDay2024 = IfcDate.LeapDay(2024)
    private val yearDay2026 = IfcDate.YearDay(2026)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        formatter = IfcDateFormatter(context.resources, Locale.US)
    }

    // §7.3 table, "Long" row

    @Test
    fun `long style of a regular day`() {
        formatter.formatLong(september8) shouldBe "September 8, 2026"
    }

    @Test
    fun `long style of Leap Day`() {
        formatter.formatLong(leapDay2024) shouldBe "Leap Day, 2024"
    }

    @Test
    fun `long style of Year Day`() {
        formatter.formatLong(yearDay2026) shouldBe "Year Day, 2026"
    }

    @Test
    fun `long style never includes the nominal weekday`() {
        formatter.formatLong(september8).contains("Sunday") shouldBe false
    }

    // §7.3 table, "Medium/short" row

    @Test
    fun `medium style of a regular day`() {
        formatter.formatMedium(september8) shouldBe "Sep 8, 2026"
    }

    @Test
    fun `medium style of a day in Sol`() {
        formatter.formatMedium(sol8) shouldBe "Sol 8, 2026"
    }

    @Test
    fun `medium style of Leap Day`() {
        formatter.formatMedium(leapDay2024) shouldBe "Leap Day 2024"
    }

    @Test
    fun `medium style of Year Day`() {
        formatter.formatMedium(yearDay2026) shouldBe "Year Day 2026"
    }

    // §7.3 table, "Numeric (canonical)" row — the IFC prefix is mandatory (CLAUDE.md rule 5)

    @Test
    fun `numeric style of a regular day keeps the prefix`() {
        formatter.formatNumeric(september8) shouldBe "IFC 2026-10-08"
    }

    @Test
    fun `numeric style of Leap Day is 06-29`() {
        formatter.formatNumeric(leapDay2024) shouldBe "IFC 2024-06-29"
    }

    @Test
    fun `numeric style of Year Day is 13-29`() {
        formatter.formatNumeric(yearDay2026) shouldBe "IFC 2026-13-29"
    }

    // §7.6 month names

    @Test
    fun `Sol comes from resources in both styles`() {
        formatter.monthName(IfcMonth.SOL) shouldBe "Sol"
        formatter.monthName(IfcMonth.SOL, MonthNameStyle.SHORT) shouldBe "Sol"
    }

    @Test
    fun `Gregorian namesakes use the platform's localized names`() {
        formatter.monthName(IfcMonth.SEPTEMBER) shouldBe "September"
        formatter.monthName(IfcMonth.SEPTEMBER, MonthNameStyle.SHORT) shouldBe "Sep"
        formatter.monthName(IfcMonth.JANUARY) shouldBe "January"
        formatter.monthName(IfcMonth.DECEMBER, MonthNameStyle.SHORT) shouldBe "Dec"
    }

    @Test
    fun `the marker is IFC`() {
        formatter.marker shouldBe "IFC"
    }

    // §4.1 nominal vs actual weekday, both labelled

    @Test
    fun `nominal weekday is labelled IFC`() {
        formatter.nominalWeekday(september8) shouldBe "IFC weekday: Sunday"
    }

    @Test
    fun `actual weekday is labelled and is the real one`() {
        formatter.actualWeekday(september8) shouldBe "Actual weekday: Thursday"
    }

    @Test
    fun `intercalary days show no IFC weekday, never blank or Sunday`() {
        formatter.nominalWeekday(leapDay2024) shouldBe "no IFC weekday"
        formatter.nominalWeekday(yearDay2026) shouldBe "no IFC weekday"
        // Leap Day 2024 is Gregorian Monday, June 17, 2024; Year Day 2026 is Thursday, December 31, 2026.
        formatter.actualWeekday(leapDay2024) shouldBe "Actual weekday: Monday"
        formatter.actualWeekday(yearDay2026) shouldBe "Actual weekday: Thursday"
    }

    @Test
    fun `spoken weekday description names both, in order`() {
        formatter.weekdaysDescription(september8) shouldBe "IFC Sunday, actual Thursday"
        formatter.weekdaysDescription(yearDay2026) shouldBe "no IFC weekday, actual Thursday"
    }

    // §7.4 day of year and week, §7.5 quarter

    @Test
    fun `day and week of a regular day`() {
        formatter.dayAndWeek(september8) shouldBe "Day 260 · Week 38 of 52"
    }

    @Test
    fun `Leap Day is day 169 outside the weeks`() {
        formatter.dayAndWeek(leapDay2024) shouldBe "Day 169 · outside the weeks"
    }

    @Test
    fun `Year Day is the last day outside the weeks`() {
        formatter.dayAndWeek(yearDay2026) shouldBe "Day 365 · outside the weeks"
        formatter.dayAndWeek(IfcDate.YearDay(2024)) shouldBe "Day 366 · outside the weeks"
    }

    @Test
    fun `quarters, including the intercalary attachments`() {
        formatter.quarter(september8) shouldBe "Q3"
        formatter.quarter(leapDay2024) shouldBe "Q2"
        formatter.quarter(yearDay2026) shouldBe "Q4"
    }

    // Year progress and countdown (FEATURES T3, T4)

    @Test
    fun `year progress is day of year over the year's length`() {
        formatter.yearProgress(september8) shouldBe "71% of the year"
        formatter.yearProgress(yearDay2026) shouldBe "100% of the year"
        IfcDateFormatter.yearProgressFraction(yearDay2026) shouldBe 1f
        IfcDateFormatter.yearProgressFraction(IfcDate.Regular(2026, IfcMonth.JANUARY, 1)) shouldBe (1f / 365)
    }

    @Test
    fun `countdown pluralizes and names the target`() {
        formatter.countdown(september8, yearDay2026) shouldBe "105 days until Year Day"
        formatter.countdown(IfcDate.Regular(2024, IfcMonth.JUNE, 28), leapDay2024) shouldBe "1 day until Leap Day"
        formatter.countdown(september8, IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 9)) shouldBe
            "1 day until September 9, 2026"
    }

    @Test
    fun `countdown rejects a target that is not later`() {
        shouldThrow<IllegalArgumentException> { formatter.countdown(september8, september8) }
    }

    // Gregorian side

    @Test
    fun `Gregorian full date includes the real weekday`() {
        formatter.formatGregorianLong(LocalDate.of(2026, 9, 17)) shouldBe "Thursday, September 17, 2026"
    }

    @Test
    fun `month and weekday names follow the injected locale`() {
        val french = IfcDateFormatter(ApplicationProvider.getApplicationContext<Context>().resources, Locale.FRANCE)
        french.monthName(IfcMonth.SEPTEMBER) shouldBe "septembre"
        french.monthName(IfcMonth.SOL) shouldBe "Sol"
        french.formatNumeric(september8) shouldBe "IFC 2026-10-08"
    }

    @Test
    fun `Gregorian medium date includes the real weekday`() {
        formatter.formatGregorianMedium(LocalDate.of(2026, 12, 31)) shouldBe "Thu, Dec 31, 2026"
        formatter.formatGregorianMedium(LocalDate.of(2028, 6, 17)) shouldBe "Sat, Jun 17, 2028"
    }

    @Test
    fun `Gregorian month and day omit the year and follow the locale's order`() {
        formatter.formatGregorianMonthDay(LocalDate.of(2026, 6, 18)) shouldBe "Jun 18"
        val british = IfcDateFormatter(ApplicationProvider.getApplicationContext<Context>().resources, Locale.UK)
        british.formatGregorianMonthDay(LocalDate.of(2026, 6, 18)) shouldBe "18 Jun"
    }

    // Month grid (docs/ARCHITECTURE.md §4; spec §7.2)

    @Test
    fun `month title is the month name and the year`() {
        formatter.monthTitle(IfcYearMonth(2028, IfcMonth.SOL)) shouldBe "Sol 2028"
        formatter.monthTitle(IfcYearMonth(2026, IfcMonth.SEPTEMBER)) shouldBe "September 2026"
    }

    @Test
    fun `a day within its month, and the intercalary names`() {
        formatter.formatDay(sol8) shouldBe "Sol 8"
        formatter.formatDay(september8) shouldBe "September 8"
        formatter.formatDay(leapDay2024) shouldBe "Leap Day"
        formatter.formatDay(yearDay2026) shouldBe "Year Day"
    }

    @Test
    fun `weekday names in both lengths`() {
        formatter.weekdayName(DayOfWeek.THURSDAY) shouldBe "Thursday"
        formatter.weekdayName(DayOfWeek.THURSDAY, WeekdayNameStyle.SHORT) shouldBe "Thu"
    }

    @Test
    fun `bare day numbers`() {
        formatter.formatNumber(13) shouldBe "13"
        formatter.formatNumber(1) shouldBe "1"
    }

    @Test
    fun `Gregorian span of a month`() {
        formatter.gregorianSpan(IfcYearMonth(2026, IfcMonth.SOL).gregorianRange) shouldBe "Jun 18 – Jul 15"
        formatter.gregorianSpan(IfcYearMonth(2027, IfcMonth.JUNE).gregorianRange) shouldBe "May 21 – Jun 17"
    }

    @Test
    fun `intercalary band subtitle has the Gregorian date and the no-weekday note`() {
        formatter.intercalarySubtitle(yearDay2026) shouldBe "Thu, Dec 31, 2026 · no IFC weekday"
        formatter.intercalarySubtitle(IfcDate.LeapDay(2028)) shouldBe "Sat, Jun 17, 2028 · no IFC weekday"
    }

    // The cell description in exactly the docs/ARCHITECTURE.md §4 "Accessibility" form.

    @Test
    fun `day description of the ARCHITECTURE example cell`() {
        val sol13 = IfcDate.Regular(2026, IfcMonth.SOL, 13)
        formatter.dayDescription(sol13, eventCount = 2, holidayName = "Canada Day") shouldBe
            "Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026. 2 events. Holiday: Canada Day."
    }

    @Test
    fun `day description omits events and holiday when absent and adds Today when today`() {
        val sol13 = IfcDate.Regular(2026, IfcMonth.SOL, 13)
        formatter.dayDescription(sol13) shouldBe "Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026."
        formatter.dayDescription(sol13, eventCount = 1) shouldBe
            "Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026. 1 event."
        formatter.dayDescription(sol13, isToday = true) shouldBe
            "Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026. Today."
    }

    @Test
    fun `day description of intercalary days says no IFC weekday`() {
        formatter.dayDescription(yearDay2026) shouldBe
            "Year Day, no IFC weekday. Gregorian Thursday, December 31, 2026."
        formatter.dayDescription(leapDay2024, holidayName = "Leap Day") shouldBe
            "Leap Day, no IFC weekday. Gregorian Monday, June 17, 2024. Holiday: Leap Day."
    }
}
