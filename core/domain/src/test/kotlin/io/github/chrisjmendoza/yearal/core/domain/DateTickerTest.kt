package io.github.chrisjmendoza.yearal.core.domain

import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// Verifies calendar-spec.md §7.8: RealDateTicker never calls LocalDate.now() itself, re-emits at
// local midnight (computed from the clock, not a fixed 24h), and re-reads the zone every cycle.
@OptIn(ExperimentalCoroutinesApi::class)
class DateTickerTest {
    @Test
    fun `emits the current local date immediately on collection`() =
        runTest {
            val clock = MutableClock(Instant.parse("2026-09-17T10:00:00Z"), ZoneOffset.UTC)
            val ticker = RealDateTicker(clock, FakeZoneProvider(ZoneOffset.UTC))
            ticker.today.first() shouldBe LocalDate.of(2026, 9, 17)
        }

    @Test
    fun `does not re-emit before local midnight, and re-emits from Year Day into January 1`() =
        runTest {
            val zone = ZoneOffset.UTC
            val clock = MutableClock(Instant.parse("2026-12-31T23:59:59Z"), zone)
            val ticker = RealDateTicker(clock, FakeZoneProvider(zone))
            val emissions = mutableListOf<LocalDate>()
            val collector = launch { ticker.today.toList(emissions) }
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2026, 12, 31))

            // Half a second short of midnight: must not have fired yet.
            advanceTimeBy(500)
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2026, 12, 31))

            // Past midnight, with the clock updated to match: must fire now.
            clock.set(Instant.parse("2027-01-01T00:00:00Z"))
            advanceTimeBy(600)
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1))

            collector.cancel()
        }

    @Test
    fun `three consecutive rollovers land on Leap Day then Sol 1 in a leap year`() =
        runTest {
            val zone = ZoneOffset.UTC

            fun startOfDay(date: LocalDate) = date.atStartOfDay(zone).toInstant()
            val clock = MutableClock(startOfDay(LocalDate.of(2024, 6, 16)), zone)
            val ticker = RealDateTicker(clock, FakeZoneProvider(zone))
            val emissions = mutableListOf<LocalDate>()
            val collector = launch { ticker.today.toList(emissions) }
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2024, 6, 16))

            clock.set(startOfDay(LocalDate.of(2024, 6, 17)))
            advanceTimeBy(Duration.ofDays(1).plusSeconds(1).toMillis())
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2024, 6, 16), LocalDate.of(2024, 6, 17))

            clock.set(startOfDay(LocalDate.of(2024, 6, 18)))
            advanceTimeBy(Duration.ofDays(1).plusSeconds(1).toMillis())
            runCurrent()
            emissions shouldBe
                listOf(LocalDate.of(2024, 6, 16), LocalDate.of(2024, 6, 17), LocalDate.of(2024, 6, 18))

            collector.cancel()
        }

    @Test
    fun `same instant yields different today in zones with extreme offsets`() =
        runTest {
            val instant = Instant.parse("2026-12-31T10:30:00Z")
            val kiritimati = ZoneId.of("Pacific/Kiritimati")
            val gmtMinus12 = ZoneId.of("Etc/GMT+12")
            assertSoftly {
                RealDateTicker(MutableClock(instant, kiritimati), FakeZoneProvider(kiritimati))
                    .today
                    .first() shouldBe LocalDate.of(2027, 1, 1)
                RealDateTicker(MutableClock(instant, gmtMinus12), FakeZoneProvider(gmtMinus12))
                    .today
                    .first() shouldBe LocalDate.of(2026, 12, 30)
            }
        }

    @Test
    fun `delay to next local midnight across a DST spring-forward is the real elapsed time, not a naive 24 hours`() =
        runTest {
            val zone = ZoneId.of("America/New_York")
            // US clocks spring forward at 02:00 on 2026-03-08 (jumping straight to 03:00), so this
            // calendar day itself, from its midnight to the next, is only 23 hours of wall time.
            val today = LocalDate.of(2026, 3, 8)
            val startInstant = today.atStartOfDay(zone).toInstant()
            val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
            val realElapsed = Duration.between(startInstant, nextMidnight)
            realElapsed shouldNotBe Duration.ofHours(24)
            realElapsed shouldBe Duration.ofHours(23)

            val clock = MutableClock(startInstant, zone)
            val ticker = RealDateTicker(clock, FakeZoneProvider(zone))
            val emissions = mutableListOf<LocalDate>()
            val collector = launch { ticker.today.toList(emissions) }
            runCurrent()
            emissions shouldBe listOf(today)

            // One second short of the real (23h) elapsed time: must not have fired yet.
            advanceTimeBy(realElapsed.minusSeconds(1).toMillis())
            runCurrent()
            emissions shouldBe listOf(today)

            // Crossing it, with the clock updated to match: must fire now.
            clock.set(nextMidnight)
            advanceTimeBy(2_000)
            runCurrent()
            emissions shouldBe listOf(today, today.plusDays(1))

            collector.cancel()
        }

    @Test
    fun `a zone change mid-run is picked up on the next emission, never cached from construction`() =
        runTest {
            val utc = ZoneOffset.UTC
            val clock = MutableClock(LocalDate.of(2026, 6, 15).atStartOfDay(utc).toInstant(), utc)
            val zoneProvider = FakeZoneProvider(utc)
            val ticker = RealDateTicker(clock, zoneProvider)
            val emissions = mutableListOf<LocalDate>()
            val collector = launch { ticker.today.toList(emissions) }
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2026, 6, 15))

            // Move to a zone 14 hours ahead and to an instant that is still June 15 in UTC but
            // already June 16 in the new zone. Only re-reading the zone (not the one cached at
            // construction) makes the ticker see June 16 here.
            zoneProvider.set(ZoneId.of("Pacific/Kiritimati"))
            clock.set(Instant.parse("2026-06-15T23:00:00Z"))
            advanceTimeBy(Duration.ofDays(1).plusSeconds(1).toMillis())
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 16))

            collector.cancel()
        }
}
