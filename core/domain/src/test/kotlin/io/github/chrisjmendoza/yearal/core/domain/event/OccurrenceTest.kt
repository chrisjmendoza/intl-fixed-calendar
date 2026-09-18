package io.github.chrisjmendoza.yearal.core.domain.event

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// Occurrence resolution and day bucketing: docs/ARCHITECTURE.md §3.2 "Time zones" and §3.4 step 3, with the
// daylight-saving rules of docs/adr/0005-events-contract.md decision 3. Expected instants are worked out by
// hand from the published zone rules: US daylight saving 2026 starts Sunday March 8 at 02:00 (EST UTC−5 →
// EDT UTC−4) and ends Sunday November 1 at 02:00; Pacific/Kiritimati is UTC+14, Pacific/Pago_Pago UTC−11,
// Asia/Tokyo UTC+9, none of them with daylight saving.
class OccurrenceTest {
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val kiritimati = ZoneId.of("Pacific/Kiritimati")
    private val pagoPago = ZoneId.of("Pacific/Pago_Pago")
    private val utc = ZoneId.of("UTC")

    private fun timed(
        start: LocalDateTime,
        minutes: Long,
        zone: ZoneId?,
    ) = Occurrence(eventId = 1, startLocal = start, endLocal = start.plusMinutes(minutes), zone = zone, allDay = false)

    private fun allDay(
        first: LocalDate,
        days: Long,
    ) = Occurrence(1, first.atStartOfDay(), first.plusDays(days).atStartOfDay(), zone = null, allDay = true)

    // --- construction -----------------------------------------------------------------------------------

    @Test
    fun `rejects an end before the start and malformed all-day occurrences`() {
        val noon = LocalDateTime.of(2026, 12, 31, 12, 0)
        val midnight = LocalDateTime.of(2026, 12, 31, 0, 0)
        assertSoftly {
            shouldThrow<IllegalArgumentException> { Occurrence(1, noon, noon.minusMinutes(1), null, allDay = false) }
            shouldThrow<IllegalArgumentException> { Occurrence(-1, noon, noon, null, allDay = false) }
            // All-day: floating, midnight to midnight, at least one day.
            shouldThrow<IllegalArgumentException> {
                Occurrence(
                    1,
                    midnight,
                    midnight.plusDays(1),
                    newYork,
                    allDay = true,
                )
            }
            shouldThrow<IllegalArgumentException> { Occurrence(1, noon, noon.plusDays(1), null, allDay = true) }
            shouldThrow<IllegalArgumentException> {
                Occurrence(
                    1,
                    midnight,
                    midnight.plusHours(30),
                    null,
                    allDay = true,
                )
            }
            shouldThrow<IllegalArgumentException> { Occurrence(1, midnight, midnight, null, allDay = true) }
        }
    }

    // --- own wall-clock dates ---------------------------------------------------------------------------

    @Test
    fun `occurrenceDate is the own start date and lastDate excludes a midnight end`() {
        val yearDay = LocalDate.of(2026, 12, 31)
        assertSoftly {
            timed(yearDay.atTime(22, 0), 120, newYork).occurrenceDate shouldBe yearDay
            timed(yearDay.atTime(22, 0), 120, newYork).lastDate shouldBe yearDay
            timed(yearDay.atTime(22, 0), 121, newYork).lastDate shouldBe LocalDate.of(2027, 1, 1)
            timed(yearDay.atTime(22, 0), 0, newYork).lastDate shouldBe yearDay
            allDay(LocalDate.of(2026, 12, 30), 3).occurrenceDate shouldBe LocalDate.of(2026, 12, 30)
            allDay(LocalDate.of(2026, 12, 30), 3).lastDate shouldBe LocalDate.of(2027, 1, 1)
        }
    }

    // --- floating vs zoned ------------------------------------------------------------------------------

    @Test
    fun `a floating occurrence keeps its wall time in whatever zone the device is in`() {
        val nineOnYearDay = timed(LocalDateTime.of(2026, 12, 31, 9, 0), 60, zone = null)
        assertSoftly {
            nineOnYearDay.start(newYork).toLocalDateTime() shouldBe LocalDateTime.of(2026, 12, 31, 9, 0)
            nineOnYearDay.start(tokyo).toLocalDateTime() shouldBe LocalDateTime.of(2026, 12, 31, 9, 0)
            // Same wall time, different instants: 14:00Z in New York (EST), 00:00Z in Tokyo.
            nineOnYearDay.start(newYork).toInstant() shouldBe Instant.parse("2026-12-31T14:00:00Z")
            nineOnYearDay.start(tokyo).toInstant() shouldBe Instant.parse("2026-12-31T00:00:00Z")
            nineOnYearDay.dates(tokyo) shouldBe LocalDate.of(2026, 12, 31)..LocalDate.of(2026, 12, 31)
        }
    }

    @Test
    fun `a zoned occurrence keeps its instant and is bucketed on the device-zone date`() {
        // 20:00 on December 30 in New York (EST) is 01:00Z, which is 10:00 on Year Day in Tokyo.
        val occurrence = timed(LocalDateTime.of(2026, 12, 30, 20, 0), 60, newYork)
        assertSoftly {
            occurrence.start(tokyo).toInstant() shouldBe Instant.parse("2026-12-31T01:00:00Z")
            occurrence.start(tokyo).toLocalDateTime() shouldBe LocalDateTime.of(2026, 12, 31, 10, 0)
            occurrence.end(tokyo).toLocalDateTime() shouldBe LocalDateTime.of(2026, 12, 31, 11, 0)
            occurrence.dates(tokyo) shouldBe LocalDate.of(2026, 12, 31)..LocalDate.of(2026, 12, 31)
            occurrence.dates(newYork) shouldBe LocalDate.of(2026, 12, 30)..LocalDate.of(2026, 12, 30)
            // The exdate key stays the own date, whatever the device shows.
            occurrence.occurrenceDate shouldBe LocalDate.of(2026, 12, 30)
        }
    }

    @Test
    fun `the device-zone date can be two days from the own date between the extreme zones`() {
        // 00:30 on June 3 at UTC+14 is 10:30Z on June 2, which is 23:30 on June 1 at UTC−11.
        val occurrence = timed(LocalDateTime.of(2026, 6, 3, 0, 30), 15, kiritimati)
        assertSoftly {
            occurrence.start(pagoPago).toLocalDateTime() shouldBe LocalDateTime.of(2026, 6, 1, 23, 30)
            occurrence.dates(pagoPago) shouldBe LocalDate.of(2026, 6, 1)..LocalDate.of(2026, 6, 1)
            occurrence.occurrenceDate.toEpochDay() - occurrence.dates(pagoPago).start.toEpochDay() shouldBe
                EventRepository.ZONE_SKEW_DAYS
        }
    }

    @Test
    fun `an all-day occurrence is never shifted between zones`() {
        val yearDayAndAfter = allDay(LocalDate.of(2026, 12, 31), 2)
        val expected = LocalDate.of(2026, 12, 31)..LocalDate.of(2027, 1, 1)
        assertSoftly {
            for (zone in listOf(kiritimati, pagoPago, newYork, utc)) {
                yearDayAndAfter.dates(zone) shouldBe expected
                yearDayAndAfter.start(zone).toLocalDateTime() shouldBe LocalDateTime.of(2026, 12, 31, 0, 0)
                yearDayAndAfter.end(zone).toLocalDateTime() shouldBe LocalDateTime.of(2027, 1, 2, 0, 0)
            }
        }
    }

    @Test
    fun `a Leap Day all-day occurrence stays on June 17 of the leap year`() {
        val leapDay = allDay(LocalDate.of(2028, 6, 17), 1)
        leapDay.dates(kiritimati) shouldBe LocalDate.of(2028, 6, 17)..LocalDate.of(2028, 6, 17)
        leapDay.dates(pagoPago) shouldBe LocalDate.of(2028, 6, 17)..LocalDate.of(2028, 6, 17)
    }

    // --- bucketing edges --------------------------------------------------------------------------------

    @Test
    fun `an occurrence that crosses midnight is on both dates, one that ends at midnight is not`() {
        val yearDay = LocalDate.of(2026, 12, 31)
        val newYearsDay = LocalDate.of(2027, 1, 1)
        assertSoftly {
            timed(yearDay.atTime(23, 0), 120, null).dates(utc) shouldBe yearDay..newYearsDay
            timed(yearDay.atTime(23, 0), 60, null).dates(utc) shouldBe yearDay..yearDay
            timed(yearDay.atTime(23, 0), 61, null).dates(utc) shouldBe yearDay..newYearsDay
            // Zero length: on its start date, even exactly at midnight.
            timed(newYearsDay.atStartOfDay(), 0, null).dates(utc) shouldBe newYearsDay..newYearsDay
            // Crossing Leap Day: June 16 23:00 for 25 hours touches June 16, Leap Day (17) and Sol 1 (18).
            timed(LocalDateTime.of(2028, 6, 16, 23, 0), 25 * 60 + 1, null).dates(utc) shouldBe
                LocalDate.of(2028, 6, 16)..LocalDate.of(2028, 6, 18)
        }
    }

    // --- daylight saving --------------------------------------------------------------------------------

    @Test
    fun `a wall time inside a spring-forward gap happens later by the length of the gap`() {
        // 02:30 does not exist in New York on 2026-03-08; with the offset before the gap (UTC−5) it is
        // 07:30Z, which is 03:30 EDT.
        val occurrence = timed(LocalDateTime.of(2026, 3, 8, 2, 30), 60, newYork)
        assertSoftly {
            occurrence.startLocal shouldBe LocalDateTime.of(2026, 3, 8, 2, 30)
            occurrence.start(newYork).toInstant() shouldBe Instant.parse("2026-03-08T07:30:00Z")
            occurrence.start(newYork).toLocalDateTime() shouldBe LocalDateTime.of(2026, 3, 8, 3, 30)
            // The nominal end 03:30 exists (EDT): also 07:30Z, so the resolved occurrence is zero-length.
            occurrence.end(newYork).toInstant() shouldBe Instant.parse("2026-03-08T07:30:00Z")
            // A floating event behaves the same on a device in that zone, and is untouched elsewhere.
            timed(LocalDateTime.of(2026, 3, 8, 2, 30), 60, null).start(newYork).toInstant() shouldBe
                Instant.parse("2026-03-08T07:30:00Z")
            timed(LocalDateTime.of(2026, 3, 8, 2, 30), 60, null).start(tokyo).toLocalDateTime() shouldBe
                LocalDateTime.of(2026, 3, 8, 2, 30)
        }
    }

    @Test
    fun `the end is never before the start when only the start falls in the gap`() {
        // Start 02:30 (in the gap, resolves to 03:30 EDT); nominal end 03:10 exists and is earlier.
        val occurrence = timed(LocalDateTime.of(2026, 3, 8, 2, 30), 40, newYork)
        assertSoftly {
            occurrence.endLocal shouldBe LocalDateTime.of(2026, 3, 8, 3, 10)
            occurrence.end(newYork) shouldBe occurrence.start(newYork)
            occurrence.dates(newYork) shouldBe LocalDate.of(2026, 3, 8)..LocalDate.of(2026, 3, 8)
        }
    }

    @Test
    fun `a wall time inside a fall-back overlap is the earlier instant`() {
        // 01:30 happens twice in New York on 2026-11-01; the first is EDT (UTC−4) = 05:30Z.
        val occurrence = timed(LocalDateTime.of(2026, 11, 1, 1, 30), 60, newYork)
        assertSoftly {
            occurrence.start(newYork).toInstant() shouldBe Instant.parse("2026-11-01T05:30:00Z")
            // The nominal end 02:30 is unambiguous EST (UTC−5) = 07:30Z: two real hours for 60 nominal minutes.
            occurrence.end(newYork).toInstant() shouldBe Instant.parse("2026-11-01T07:30:00Z")
        }
    }

    @Test
    fun `a nominal duration keeps the wall-clock end across a transition`() {
        // 22:00 to 06:00 over the fall-back night: ends at 06:00 EST, nine real hours later.
        val overnight = timed(LocalDateTime.of(2026, 10, 31, 22, 0), 8 * 60, newYork)
        assertSoftly {
            overnight.end(newYork).toLocalDateTime() shouldBe LocalDateTime.of(2026, 11, 1, 6, 0)
            overnight.start(newYork).toInstant() shouldBe Instant.parse("2026-11-01T02:00:00Z")
            overnight.end(newYork).toInstant() shouldBe Instant.parse("2026-11-01T11:00:00Z")
        }
    }

    @Test
    fun `an all-day occurrence starts at the first instant of the day even where midnight does not exist`() {
        // America/Sao_Paulo moved its clocks forward at 00:00 on 2018-11-04: the day starts at 01:00 (−02:00).
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val occurrence = allDay(LocalDate.of(2018, 11, 4), 1)
        assertSoftly {
            occurrence.start(saoPaulo).toInstant() shouldBe Instant.parse("2018-11-04T03:00:00Z")
            occurrence.start(saoPaulo).toLocalDateTime() shouldBe LocalDateTime.of(2018, 11, 4, 1, 0)
            occurrence.dates(saoPaulo) shouldBe LocalDate.of(2018, 11, 4)..LocalDate.of(2018, 11, 4)
        }
    }
}
