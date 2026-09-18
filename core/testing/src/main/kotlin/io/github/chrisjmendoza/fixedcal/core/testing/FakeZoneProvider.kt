package io.github.chrisjmendoza.fixedcal.core.testing

import io.github.chrisjmendoza.fixedcal.core.domain.ZoneProvider
import java.time.ZoneId

/**
 * A hand-written [ZoneProvider] fake that returns a fixed zone until [set] changes it, so a test can
 * simulate a `TIMEZONE_CHANGED` event mid-test without rebuilding the object under test.
 */
public class FakeZoneProvider(
    zone: ZoneId,
) : ZoneProvider {
    @Volatile
    private var zone: ZoneId = zone

    /** The zone last set by the constructor or [set]. */
    override fun currentZone(): ZoneId = zone

    /** Changes the zone future calls to [currentZone] return. */
    public fun set(zone: ZoneId) {
        this.zone = zone
    }
}
