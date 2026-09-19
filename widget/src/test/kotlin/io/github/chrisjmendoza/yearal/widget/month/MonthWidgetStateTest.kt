package io.github.chrisjmendoza.yearal.widget.month

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.github.chrisjmendoza.yearal.widget.today.TodayDate
import io.github.chrisjmendoza.yearal.widget.today.todayDate
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * [buildMonthWidgetState] is the Month widget's only source of what to render (CLAUDE.md rules 1 and
 * 2), so this proves it for every month shape a `when` over [IfcDate] must handle (CLAUDE.md rule 6:
 * Year Day and Leap Day), the actual-weekday header against hand-checked real-world dates
 * (calendar-spec §4.1), and that nothing is cached across a midnight, a year rollover, or a zone
 * change (docs/ARCHITECTURE.md §5, WORKFLOW.md §3). Robolectric only for [android.content.res.Resources]
 * ([IfcDateFormatter]); every date and weekday comes from `:core:calendar`.
 */
@RunWith(AndroidJUnit4::class)
class MonthWidgetStateTest {
    private val formatter =
        IfcDateFormatter(ApplicationProvider.getApplicationContext<android.content.Context>().resources, Locale.US)
    private val tapHint = "Double-tap to open Yearal."

    private fun stateFor(gregorian: LocalDate) =
        buildMonthWidgetState(TodayDate(IfcDate.from(gregorian), gregorian), formatter, tapHint)

    @Test
    fun `a regular day shows its month, and only its own cell is today`() {
        val state = stateFor(LocalDate.of(2026, 9, 17)) // IFC September 8, 2026.

        state.monthTitle shouldBe "September 2026"
        state.days.size shouldBe 28
        state.days.map { it.dayOfMonth } shouldBe (1..28).toList()
        state.days.single { it.isToday }.dayOfMonth shouldBe 8
        state.intercalary shouldBe null
    }

    @Test
    fun `a day in Sol, the month the Gregorian calendar does not have`() {
        val state = stateFor(LocalDate.of(2026, 6, 20)) // IFC Sol 3, 2026.

        state.monthTitle shouldBe "Sol 2026"
        state.days.single { it.isToday }.dayOfMonth shouldBe 3
        state.intercalary shouldBe null
    }

    @Test
    fun `June in a leap year shows the Leap Day band, highlighted when today is Leap Day`() {
        val state = stateFor(LocalDate.of(2028, 6, 17)) // Leap Day 2028.

        state.monthTitle shouldBe "June 2028"
        state.days.none { it.isToday } shouldBe true
        val intercalary = state.intercalary
        checkNotNull(intercalary) { "June 2028 must have a trailing Leap Day band" }
        intercalary.label shouldBe "Leap Day"
        intercalary.isToday shouldBe true
    }

    @Test
    fun `June in a common year has no band at all`() {
        val state = stateFor(LocalDate.of(2026, 5, 21)) // IFC June 1, 2026 (common year).

        state.monthTitle shouldBe "June 2026"
        state.intercalary shouldBe null
        state.days.single { it.isToday }.dayOfMonth shouldBe 1
    }

    @Test
    fun `December shows the Year Day band, highlighted when today is Year Day`() {
        val state = stateFor(LocalDate.of(2026, 12, 31)) // Year Day 2026.

        state.monthTitle shouldBe "December 2026"
        state.days.none { it.isToday } shouldBe true
        val intercalary = state.intercalary
        checkNotNull(intercalary) { "December must always have a trailing Year Day band" }
        intercalary.label shouldBe "Year Day"
        intercalary.isToday shouldBe true
    }

    @Test
    fun `the actual weekday header is hand-checked for 2026, where January 1 is a Thursday`() {
        val state = stateFor(LocalDate.of(2026, 1, 5))

        state.nominalWeekdayHeaders shouldBe listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        state.actualWeekdayHeaders shouldBe listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
    }

    @Test
    fun `the actual weekday header shifts after Leap Day, hand-checked for Sol 2028`() {
        // Jan 1, 2028 is a Saturday; Sol 1, 2028 (Gregorian June 18) is one day later, a Sunday, because
        // Leap Day itself sits between them (calendar-spec §4.1).
        val state = stateFor(LocalDate.of(2028, 6, 20)) // Sol 3, 2028.

        state.actualWeekdayHeaders shouldBe listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    }

    @Test
    fun `a midnight crossing shows the new month, with nothing cached from the previous call`() {
        val zone = FakeZoneProvider(ZoneOffset.UTC)
        // 23:59:59 UTC on Jan 28, 2026 -- the last day of IFC January.
        val clock = MutableClock(Instant.parse("2026-01-28T23:59:59Z"))

        val beforeMidnight = buildMonthWidgetState(todayDate(clock, zone), formatter, tapHint)
        clock.advanceBy(Duration.ofSeconds(2))
        val afterMidnight = buildMonthWidgetState(todayDate(clock, zone), formatter, tapHint)

        beforeMidnight.monthTitle shouldBe "January 2026"
        beforeMidnight.days.single { it.isToday }.dayOfMonth shouldBe 28
        afterMidnight.monthTitle shouldBe "February 2026"
        afterMidnight.days.single { it.isToday }.dayOfMonth shouldBe 1
    }

    @Test
    fun `Year Day rolls into January of the next year, never a stale December`() {
        val zone = FakeZoneProvider(ZoneOffset.UTC)
        val clock = MutableClock(Instant.parse("2026-12-31T23:59:59Z"))

        val yearDay = buildMonthWidgetState(todayDate(clock, zone), formatter, tapHint)
        clock.advanceBy(Duration.ofSeconds(2))
        val newYear = buildMonthWidgetState(todayDate(clock, zone), formatter, tapHint)

        yearDay.monthTitle shouldBe "December 2026"
        checkNotNull(yearDay.intercalary).isToday shouldBe true
        newYear.monthTitle shouldBe "January 2027"
        newYear.days.single { it.isToday }.dayOfMonth shouldBe 1
    }

    @Test
    fun `a zone change is read fresh and can flip the shown month, not cached from an earlier call`() {
        // 11:00:00Z on Jan 28, 2026 is still January 28 in UTC, but already 01:00 on Jan 29 in
        // Kiritimati (UTC+14) -- the first day of IFC February.
        val clock = MutableClock(Instant.parse("2026-01-28T11:00:00Z"))
        val zone = FakeZoneProvider(ZoneOffset.UTC)

        val beforeZoneChange = buildMonthWidgetState(todayDate(clock, zone), formatter, tapHint)
        zone.set(ZoneId.of("Pacific/Kiritimati"))
        val afterZoneChange = buildMonthWidgetState(todayDate(clock, zone), formatter, tapHint)

        beforeZoneChange.monthTitle shouldBe "January 2026"
        afterZoneChange.monthTitle shouldBe "February 2026"
        afterZoneChange.days.single { it.isToday }.dayOfMonth shouldBe 1
    }

    @Test
    fun `the content description names the month, today's IFC date, and the Gregorian equivalent`() {
        val state = stateFor(LocalDate.of(2026, 9, 17))

        state.contentDescription shouldBe
            "September 2026. September 8, IFC Sunday. Gregorian Thursday, September 17, 2026. Today. $tapHint"
    }
}
