package io.github.chrisjmendoza.yearal.core.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A hand-written fake [Clock] whose instant can be moved explicitly with [set] and [advanceBy],
 * instead of being fixed for the test's lifetime like [Clock.fixed].
 *
 * Use this where a test needs to simulate real time passing around code that also suspends with
 * `kotlinx.coroutines.delay` — advance a coroutine test scheduler's virtual time and this clock
 * together so that code waking from a delay observes the instant the test intends, for example to
 * simulate crossing a local midnight (`docs/calendar-spec.md` §7.7, §7.8; `docs/WORKFLOW.md` "Inject
 * a fake `Clock`; include a midnight-crossing case for anything that shows 'today'").
 *
 * Not thread-confined beyond `@Volatile` visibility: it is meant for single-threaded test
 * dispatchers, not for concurrent production use.
 */
public class MutableClock(
    instant: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    @Volatile
    private var current: Instant = instant

    /** The fixed zone this clock was constructed with. */
    override fun getZone(): ZoneId = zone

    /** Returns a new [MutableClock] sharing this clock's current instant but with [zone] instead. */
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    /** The instant last set by the constructor, [set] or [advanceBy]. */
    override fun instant(): Instant = current

    /** Replaces the current instant with [instant]. */
    public fun set(instant: Instant) {
        current = instant
    }

    /** Moves the current instant forward (or backward, for a negative [duration]) by [duration]. */
    public fun advanceBy(duration: Duration) {
        current = current.plus(duration)
    }
}
