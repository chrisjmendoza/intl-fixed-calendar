package io.github.chrisjmendoza.yearal.widget.today

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [todayDate] is the widget's only source of "today" (CLAUDE.md rules 1 and 2), so this proves it for
 * every date shape plus the two triggers that most often break a widget: a midnight crossing and a
 * time-zone change (WORKFLOW.md §3, `docs/ARCHITECTURE.md` §5's whole reason for the layered rollover).
 * No Robolectric needed: [Clock] and [ZoneProvider] are pure JVM (`:core:domain`).
 */
class TodayDateTest {
    @Test
    fun `a regular day`() {
        val clock = MutableClock(Instant.parse("2026-09-17T12:00:00Z"))
        val zone = FakeZoneProvider(ZoneOffset.UTC)

        val today = todayDate(clock, zone)

        today.gregorianDate shouldBe LocalDate.of(2026, 9, 17)
        today.ifcDate shouldBe IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)
    }

    @Test
    fun `Sol, the month the Gregorian calendar does not have`() {
        val clock = MutableClock(Instant.parse("2026-06-20T12:00:00Z"))
        val zone = FakeZoneProvider(ZoneOffset.UTC)

        val today = todayDate(clock, zone)

        today.ifcDate shouldBe IfcDate.Regular(2026, IfcMonth.SOL, 3)
    }

    @Test
    fun `Year Day`() {
        val clock = MutableClock(Instant.parse("2026-12-31T12:00:00Z"))
        val zone = FakeZoneProvider(ZoneOffset.UTC)

        val today = todayDate(clock, zone)

        today.ifcDate shouldBe IfcDate.YearDay(2026)
    }

    @Test
    fun `Leap Day, only in a leap year`() {
        val clock = MutableClock(Instant.parse("2028-06-17T12:00:00Z"))
        val zone = FakeZoneProvider(ZoneOffset.UTC)

        val today = todayDate(clock, zone)

        today.ifcDate shouldBe IfcDate.LeapDay(2028)
    }

    @Test
    fun `Year Day rolls into the new year's Regular 1 January, never a stale value`() {
        val clock = MutableClock(Instant.parse("2026-12-31T23:59:59Z"))
        val zone = FakeZoneProvider(ZoneOffset.UTC)

        val yearDay = todayDate(clock, zone)
        clock.advanceBy(java.time.Duration.ofSeconds(2))
        val newYear = todayDate(clock, zone)

        yearDay.ifcDate shouldBe IfcDate.YearDay(2026)
        newYear.gregorianDate shouldBe LocalDate.of(2027, 1, 1)
        newYear.ifcDate shouldBe IfcDate.Regular(2027, IfcMonth.JANUARY, 1)
    }

    @Test
    fun `a midnight crossing shows the new date with no cached state`() {
        val zone = FakeZoneProvider(ZoneId.of("America/New_York"))
        // 23:59:30 on 18 Sep 2026 in New York; that midnight is 04:00:00Z on the 19th.
        val clock = MutableClock(Instant.parse("2026-09-19T03:59:30Z"))

        val beforeMidnight = todayDate(clock, zone)
        clock.set(Instant.parse("2026-09-19T04:00:01Z"))
        val afterMidnight = todayDate(clock, zone)

        beforeMidnight.gregorianDate shouldBe LocalDate.of(2026, 9, 18)
        afterMidnight.gregorianDate shouldBe LocalDate.of(2026, 9, 19)
    }

    @Test
    fun `a zone change is read fresh, not cached from an earlier call`() {
        // 11:00:00Z is still the 19th in UTC, but already 01:00:00 on the 20th in Kiritimati (UTC+14).
        val clock = MutableClock(Instant.parse("2026-09-19T11:00:00Z"))
        val zone = FakeZoneProvider(ZoneOffset.UTC)

        val beforeZoneChange = todayDate(clock, zone)
        zone.set(ZoneId.of("Pacific/Kiritimati"))
        val afterZoneChange = todayDate(clock, zone)

        beforeZoneChange.gregorianDate shouldBe LocalDate.of(2026, 9, 19)
        afterZoneChange.gregorianDate shouldBe LocalDate.of(2026, 9, 20)
    }
}
