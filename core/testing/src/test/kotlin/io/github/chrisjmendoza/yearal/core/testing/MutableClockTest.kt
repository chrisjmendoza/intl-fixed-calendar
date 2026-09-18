package io.github.chrisjmendoza.yearal.core.testing

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClockTest {
    @Test
    fun `instant and zone reflect the constructor arguments until changed`() {
        val instant = Instant.parse("2026-09-17T12:00:00Z")
        val zone = ZoneId.of("Pacific/Kiritimati")
        val clock = MutableClock(instant, zone)
        assertSoftly {
            clock.instant() shouldBe instant
            clock.zone shouldBe zone
        }
    }

    @Test
    fun `defaults to UTC when no zone is given`() {
        MutableClock(Instant.EPOCH).zone shouldBe ZoneOffset.UTC
    }

    @Test
    fun `set replaces the instant`() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        clock.set(Instant.parse("2027-06-15T08:30:00Z"))
        clock.instant() shouldBe Instant.parse("2027-06-15T08:30:00Z")
    }

    @Test
    fun `advanceBy moves the instant forward, and accepts a negative duration to move it back`() {
        val start = Instant.parse("2024-06-17T00:00:00Z")
        val clock = MutableClock(start)
        assertSoftly {
            clock.advanceBy(Duration.ofSeconds(1))
            clock.instant() shouldBe start.plusSeconds(1)
            clock.advanceBy(Duration.ofSeconds(-2))
            clock.instant() shouldBe start.minusSeconds(1)
        }
    }

    @Test
    fun `withZone returns a clock with the same instant but a different zone`() {
        val instant = Instant.parse("2026-09-17T12:00:00Z")
        val original = MutableClock(instant, ZoneOffset.UTC)
        val rezoned = original.withZone(ZoneId.of("Etc/GMT+12"))
        assertSoftly {
            rezoned.instant() shouldBe instant
            rezoned.zone shouldBe ZoneId.of("Etc/GMT+12")
            original.zone shouldBe ZoneOffset.UTC
        }
    }

    @Test
    fun `withZone is independent of the original after either is advanced`() {
        val original = MutableClock(Instant.EPOCH, ZoneOffset.UTC)
        val rezoned = original.withZone(ZoneId.of("Asia/Kathmandu")) as MutableClock
        rezoned.advanceBy(Duration.ofDays(1))
        assertSoftly {
            rezoned.instant() shouldBe Instant.EPOCH.plus(Duration.ofDays(1))
            original.instant() shouldBe Instant.EPOCH
        }
    }
}
