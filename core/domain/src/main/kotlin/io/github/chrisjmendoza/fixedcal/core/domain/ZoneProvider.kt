package io.github.chrisjmendoza.fixedcal.core.domain

import java.time.ZoneId

/**
 * The time zone "today" should be computed in, read fresh every time rather than cached.
 *
 * A [java.time.Clock]'s zone is fixed at construction, but the zone the app should use can change
 * while it runs (a `TIMEZONE_CHANGED` broadcast). Pairing an injected [java.time.Clock] (for the
 * current instant) with a [ZoneProvider] (for the current zone) lets both be faked independently in
 * tests and lets the zone change without rebuilding the clock.
 *
 * **Trap:** do not call [currentZone] once and hold onto the result across a "today" computation
 * that might outlive a zone change; call it again at each use.
 *
 * Spec: `docs/calendar-spec.md` §7.8.
 */
public fun interface ZoneProvider {
    /** Returns the zone to compute "today" in, right now. */
    public fun currentZone(): ZoneId
}

/** [ZoneProvider] backed by the JVM's current default zone, [ZoneId.systemDefault]. */
public object SystemZoneProvider : ZoneProvider {
    override fun currentZone(): ZoneId = ZoneId.systemDefault()
}
