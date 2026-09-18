package io.github.chrisjmendoza.fixedcal.core.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/**
 * A stream of the current local date that re-emits at every local midnight.
 *
 * This is the single source of "today" for anything that must not call [java.time.LocalDate.now]
 * itself (CLAUDE.md rule 2): a "today" highlight, a widget, a notification. It never derives a date
 * from epoch milliseconds.
 *
 * Spec: `docs/calendar-spec.md` §7.8; `docs/ARCHITECTURE.md` "State management" and "Midnight
 * rollover".
 */
public interface DateTicker {
    /**
     * Emits the current local date immediately on collection, then again after every local
     * midnight for as long as it is collected. Never completes on its own; cancel the collecting
     * coroutine to stop it.
     */
    public val today: Flow<LocalDate>
}

/**
 * [DateTicker] driven by a real [clock] and [zoneProvider].
 *
 * Each cycle re-reads [zoneProvider], so a zone change while the app is alive is picked up on the
 * next emission rather than baked in at construction, and computes the delay until the next local
 * midnight from [clock] instead of a fixed 24 hours, so it self-corrects after being suspended
 * (device sleep, a DST transition that is shorter or longer than 24 hours) for a different amount
 * of real time than expected.
 *
 * This class supplies no periodic re-check beyond that single scheduled delay: the Android-specific
 * "also re-emit on resume and on `TIME_SET`/`TIMEZONE_CHANGED`/`DATE_CHANGED`"
 * behaviour (`docs/ARCHITECTURE.md` "State management") is layered on top of it, outside this pure
 * JVM module.
 *
 * Spec: `docs/calendar-spec.md` §7.8.
 */
public class RealDateTicker(
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : DateTicker {
    override val today: Flow<LocalDate>
        get() =
            flow {
                while (true) {
                    val zone = zoneProvider.currentZone()
                    val now = clock.instant()
                    val today = LocalDate.ofInstant(now, zone)
                    emit(today)
                    val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant()
                    val untilMidnight = Duration.between(now, nextMidnight).coerceAtLeast(Duration.ZERO)
                    delay(untilMidnight.toMillis())
                }
            }
}
