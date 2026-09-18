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
 * **The alarm time is never the date.** The alarm is a windowed one, delivered anywhere inside
 * [WINDOW] and later still in Doze, so nothing may derive "today" from it; listeners recompute it from
 * the injected [Clock] when called, and that is what makes the rollover correct without alarm
 * precision (`docs/ARCHITECTURE.md` "Reconciled decisions" 11).
 *
 * **No exact alarm yet, on purpose.** `docs/ARCHITECTURE.md` §5 has pre-reminder builds run on the
 * windowed alarm, and the exact-alarm permissions are declared only when reminders ship (ROADMAP M6
 * T3). Until the manifest declares one, Android Lint's `MissingPermission` rejects any
 * `setExactAndAllowWhileIdle` call, guarded or not, so the `canScheduleExactAlarms()` branch of §5 is
 * added to [arm] by the change that declares the permission.
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
         * The alarm is `setWindow(RTC_WAKEUP, …, WINDOW)` on every supported API level (26–36), whatever
         * `AlarmManager.canScheduleExactAlarms()` reports. It needs no permission anywhere; API 26–30
         * honour the window as given, and from API 31 the platform stretches any shorter window to
         * these same ten minutes. `RTC_WAKEUP` because the target is a wall-clock instant; one wakeup a
         * day is the whole battery cost (FEATURES Q10).
         */
        public fun arm() {
            // Never null for an installed app; if a broken device ever returns null, the broadcast and
            // updatePeriodMillis layers still cover the rollover, so do not take the process down.
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val triggerAt = nextLocalMidnight(clock.instant(), zoneProvider.currentZone()).plus(ROLLOVER_MARGIN)
            // The only place a java.time value becomes epoch milliseconds: the AlarmManager boundary.
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toEpochMilli(),
                WINDOW.toMillis(),
                rolloverOperation(context),
            )
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

            /** Delivery window of the alarm: 10 minutes (`docs/ARCHITECTURE.md` §5). */
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
