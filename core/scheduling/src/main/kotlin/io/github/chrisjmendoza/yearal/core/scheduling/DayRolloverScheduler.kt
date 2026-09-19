package io.github.chrisjmendoza.yearal.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one `AlarmManager` alarm armed for the next local midnight: layer 1 of
 * `docs/ARCHITECTURE.md` §5 "Midnight rollover (layered)" and the foundation of FEATURES S3.
 *
 * The alarm is delivered to [DayRolloverAlarmReceiver], which re-arms this scheduler for the following
 * midnight and notifies every
 * [io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener]. [SystemEventReceiver] does
 * the same after a clock, zone or locale change, a reboot and an app update, and the application arms
 * it once at process start, so a lost alarm is replaced the next time anything runs.
 *
 * **The alarm time is never the date.** Delivery can be late — the windowed fallback below, Doze, a
 * clock change — so nothing may derive "today" from it; listeners recompute it from the injected
 * [Clock] when called, and that is what makes the rollover correct without alarm precision
 * (`docs/ARCHITECTURE.md` "Reconciled decisions" 11).
 *
 * **Exact where it may be, windowed where it may not.** Since ROADMAP M6 T3 the manifest declares the
 * exact-alarm permissions that *reminders* justify (`docs/security-and-privacy.md` §5.1), so [arm]
 * uses the shared [armWakeup] policy: `setExactAndAllowWhileIdle` where
 * `AlarmManager.canScheduleExactAlarms()` allows it, and a [WINDOW] window where it does not. The
 * rollover only reuses the capability and must stay correct without it, which is why nothing here
 * treats the windowed branch as degraded (ARCHITECTURE "Reconciled decisions" 11).
 */
@Singleton
public class DayRolloverScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val clock: Clock,
        private val zoneProvider: ZoneProvider,
    ) {
        /**
         * Arms the alarm for [ROLLOVER_MARGIN] after the next local midnight, computed now from the
         * [Clock] and the [ZoneProvider], replacing the alarm armed by any earlier call.
         *
         * Idempotent: every call uses the same explicit, immutable `PendingIntent`, and `AlarmManager`
         * keeps one alarm per matching `PendingIntent`, so calling this from several triggers never
         * stacks alarms. Cheap enough for the main thread (two binder calls, no I/O).
         *
         * `RTC_WAKEUP` because the target is a wall-clock instant; one wakeup a day is the whole
         * battery cost (FEATURES Q10). Whether that wakeup is exact or spread over [WINDOW] is
         * [armWakeup]'s decision, taken afresh here on every call because the capability can be
         * revoked at any time on API 31–32.
         */
        public fun arm() {
            // Never null for an installed app; if a broken device ever returns null, the broadcast and
            // updatePeriodMillis layers still cover the rollover, so do not take the process down.
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAt = nextLocalMidnight(clock.instant(), zoneProvider.currentZone()).plus(ROLLOVER_MARGIN)
            alarmManager.armWakeup(triggerAt, rolloverOperation(context), WINDOW)
        }

        /** Constants shared with [DayRolloverAlarmReceiver] and the tests. */
        public companion object {
            /**
             * Action of the alarm broadcast. The intent is explicit, so this is not used for routing;
             * [DayRolloverAlarmReceiver] checks it so that it acts on nothing but this alarm.
             */
            public const val ACTION_DAY_ROLLOVER: String =
                "io.github.chrisjmendoza.yearal.core.scheduling.action.DAY_ROLLOVER"

            /**
             * How long after midnight the alarm is set for, so that a clock read at delivery is
             * already on the new day (`docs/ARCHITECTURE.md` §5: "nextLocalMidnight + about 1s").
             */
            public val ROLLOVER_MARGIN: Duration = Duration.ofSeconds(1)

            /**
             * Delivery window of the alarm when exact alarms are unavailable: 10 minutes
             * (`docs/ARCHITECTURE.md` §5). Ignored on the exact branch of [armWakeup].
             */
            public val WINDOW: Duration = Duration.ofMinutes(10)

            /** The app has one rollover alarm, so one fixed request code. */
            internal const val REQUEST_CODE: Int = 0

            /**
             * The one `PendingIntent` of the rollover alarm: explicit (component set), immutable, and
             * without extras (`docs/security-and-privacy.md` §6.4). `FLAG_UPDATE_CURRENT` returns the
             * existing instance on later calls, which is what makes [arm] replace rather than add.
             */
            internal fun rolloverOperation(context: Context): PendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    Intent(context, DayRolloverAlarmReceiver::class.java).setAction(ACTION_DAY_ROLLOVER),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
        }
    }

/**
 * The first instant of the local day after the one [now] falls on in [zone].
 *
 * Built from calendar fields, never by adding 24 hours: a day is 23 or 25 hours long across a DST
 * change, and where the change happens at midnight itself (America/Sao_Paulo until 2019, Africa/Cairo,
 * Asia/Beirut) 00:00 does not exist and the day starts at 01:00 — `atStartOfDay(zone)` returns that.
 */
internal fun nextLocalMidnight(
    now: Instant,
    zone: ZoneId,
): Instant =
    now
        .atZone(zone)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zone)
        .toInstant()
