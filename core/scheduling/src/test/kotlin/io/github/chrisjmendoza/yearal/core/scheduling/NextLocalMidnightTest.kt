package io.github.chrisjmendoza.yearal.core.scheduling

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// Expected instants are worked out by hand from the published zone rules (UTC offsets and transition
// dates), not by re-running the java.time pipeline the implementation uses. FEATURES Q2: "tested across
// DST, time-zone changes, and year boundaries".
class NextLocalMidnightTest {
    private val newYork = ZoneId.of("America/New_York")
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private fun instant(text: String): Instant = Instant.parse(text)

    @Test
    fun `an ordinary day - next midnight is the start of tomorrow in the zone`() {
        // 11:00 EDT (UTC-4) on 18 Sep 2026; 19 Sep 00:00 EDT is 04:00Z.
        nextLocalMidnight(instant("2026-09-18T15:00:00Z"), newYork) shouldBe instant("2026-09-19T04:00:00Z")
    }

    @Test
    fun `the last millisecond of a day and the first instant of the next one`() {
        nextLocalMidnight(instant("2026-09-19T03:59:59.999Z"), newYork) shouldBe instant("2026-09-19T04:00:00Z")
        // Exactly at midnight the *next* midnight is a full day away, never "now".
        nextLocalMidnight(instant("2026-09-19T04:00:00Z"), newYork) shouldBe instant("2026-09-20T04:00:00Z")
    }

    @Test
    fun `spring forward - the day is 23 hours long`() {
        // US DST starts Sunday 8 March 2026. 00:00 EST (UTC-5) is 05:00Z; 9 March 00:00 EDT (UTC-4) is 04:00Z.
        val start = instant("2026-03-08T05:00:00Z")
        val next = nextLocalMidnight(start, newYork)
        next shouldBe instant("2026-03-09T04:00:00Z")
        Duration.between(start, next) shouldBe Duration.ofHours(23)
    }

    @Test
    fun `fall back - the day is 25 hours long`() {
        // US DST ends Sunday 1 November 2026. 00:00 EDT is 04:00Z; 2 November 00:00 EST is 05:00Z.
        val start = instant("2026-11-01T04:00:00Z")
        val next = nextLocalMidnight(start, newYork)
        next shouldBe instant("2026-11-02T05:00:00Z")
        Duration.between(start, next) shouldBe Duration.ofHours(25)
    }

    @Test
    fun `a zone whose spring-forward gap swallows midnight - the day starts at 01h00`() {
        // Brazil, Sunday 4 November 2018: at 00:00 (UTC-3) clocks jumped to 01:00 (UTC-2), so 00:00 never
        // existed and the day began at 01:00-02:00 = 03:00Z.
        nextLocalMidnight(instant("2018-11-03T15:00:00Z"), saoPaulo) shouldBe instant("2018-11-04T03:00:00Z")
        // From that first instant of the 23-hour day: 5 November 00:00 (UTC-2) is 02:00Z.
        nextLocalMidnight(instant("2018-11-04T03:00:00Z"), saoPaulo) shouldBe instant("2018-11-05T02:00:00Z")
    }

    @Test
    fun `a zone that falls back at midnight - 23h30 happens twice and both lead to the same midnight`() {
        // Brazil, Sunday 18 February 2018: at 00:00 (UTC-2) = 02:00Z clocks went back to 23:00 (UTC-3) on
        // the 17th. Midnight of the 18th therefore happened once, at 00:00-03:00 = 03:00Z.
        val firstPass = instant("2018-02-18T01:30:00Z") // 23:30 UTC-2, 90 minutes before midnight, not 30
        val secondPass = instant("2018-02-18T02:30:00Z") // 23:30 UTC-3
        nextLocalMidnight(firstPass, saoPaulo) shouldBe instant("2018-02-18T03:00:00Z")
        nextLocalMidnight(secondPass, saoPaulo) shouldBe instant("2018-02-18T03:00:00Z")
    }

    @Test
    fun `a zone change moves the next midnight`() {
        val now = instant("2026-09-18T20:00:00Z")
        // Los Angeles, PDT (UTC-7): it is 13:00 on the 18th; the 19th starts at 07:00Z.
        nextLocalMidnight(now, ZoneId.of("America/Los_Angeles")) shouldBe instant("2026-09-19T07:00:00Z")
        // Auckland, NZST (UTC+12, daylight time starts 27 Sep): it is already 08:00 on the 19th; the
        // 20th starts at 12:00Z on the 19th.
        nextLocalMidnight(now, ZoneId.of("Pacific/Auckland")) shouldBe instant("2026-09-19T12:00:00Z")
    }

    @Test
    fun `year boundary - Year Day rolls over into 1 January`() {
        IfcDate.from(LocalDate.of(2026, 12, 31)).shouldBeInstanceOf<IfcDate.YearDay>()
        nextLocalMidnight(instant("2026-12-31T23:59:30Z"), ZoneOffset.UTC) shouldBe instant("2027-01-01T00:00:00Z")
        // Same boundary seen from a zone east of Greenwich (Tokyo, UTC+9, no DST).
        nextLocalMidnight(instant("2026-12-31T14:59:30Z"), ZoneId.of("Asia/Tokyo")) shouldBe
            instant("2026-12-31T15:00:00Z")
    }

    @Test
    fun `Leap Day - the IFC one in June and the Gregorian one in February`() {
        // IFC Leap Day is Gregorian 17 June of a leap year; the next day is Sol 1.
        IfcDate.from(LocalDate.of(2028, 6, 17)).shouldBeInstanceOf<IfcDate.LeapDay>()
        nextLocalMidnight(instant("2028-06-17T12:00:00Z"), ZoneOffset.UTC) shouldBe instant("2028-06-18T00:00:00Z")
        nextLocalMidnight(instant("2028-02-29T12:00:00Z"), ZoneOffset.UTC) shouldBe instant("2028-03-01T00:00:00Z")
        // And in a common year 28 February is followed by 1 March.
        nextLocalMidnight(instant("2027-02-28T12:00:00Z"), ZoneOffset.UTC) shouldBe instant("2027-03-01T00:00:00Z")
    }
}
