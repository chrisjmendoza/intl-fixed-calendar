package io.github.chrisjmendoza.yearal.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import java.time.Duration
import java.time.Instant

/**
 * The one exact-versus-windowed alarm policy of the app, shared by the midnight rollover
 * ([DayRolloverScheduler]) and the reminder alarm
 * ([io.github.chrisjmendoza.yearal.core.scheduling.reminder.AlarmReminderScheduler]) so the two can
 * never drift apart: layer 1 of `docs/ARCHITECTURE.md` §5 "Midnight rollover (layered)".
 *
 * Arms `operation` to be delivered at [triggerAt] with `RTC_WAKEUP` (the target is a wall-clock
 * instant, and the device must wake for it), replacing whatever the same `PendingIntent` had armed
 * before:
 *
 * - **Exact** (`setExactAndAllowWhileIdle`) when the app may use exact alarms. Below API 31 an exact
 *   alarm needs no permission at all; from API 31 the capability is
 *   [AlarmManager.canScheduleExactAlarms], which is `true` whenever `USE_EXACT_ALARM` (API 33+,
 *   auto-granted) or `SCHEDULE_EXACT_ALARM` (API 31–32, pre-granted but revocable) applies. Both are
 *   declared in this module's manifest and justified by reminders
 *   (`docs/security-and-privacy.md` §5.1; ARCHITECTURE "Reconciled decisions" 11). `AllowWhileIdle`
 *   is what gets the alarm out of Doze, which is what makes FEATURES E4's "fires within a minute"
 *   true.
 * - **Windowed** (`setWindow` over [window]) when it may not — an API 31 or 32 user who revoked
 *   "Alarms & reminders", or a build whose Play declaration was rejected. Everything the app does
 *   must still be correct then (ARCHITECTURE §5: "the design must be correct without exact alarms"),
 *   so the fallback is never an error path: a listener recomputes "today" from the [java.time.Clock]
 *   and the reminder scheduler posts whatever is due when it is finally called.
 *
 * The capability is read **at every call**, never cached: the user can revoke it at any moment on API
 * 31–32, and `SystemEventReceiver` re-arms both schedulers when the system says so.
 *
 * @param triggerAt when the alarm should be delivered; the only place a `java.time` value in this
 *   module becomes epoch milliseconds (CLAUDE.md rule 2).
 * @param operation the explicit, immutable `PendingIntent` to deliver; `AlarmManager` keeps one alarm
 *   per matching `PendingIntent`, so re-arming replaces rather than stacks.
 * @param window how late a windowed delivery may be. Ignored on the exact branch. From API 31 the
 *   platform stretches any window shorter than ten minutes to ten minutes anyway.
 */
internal fun AlarmManager.armWakeup(
    triggerAt: Instant,
    operation: PendingIntent,
    window: Duration,
) {
    val atMillis = triggerAt.toEpochMilli()
    // The guard is inline rather than extracted so that Android Lint's MissingPermission /
    // ScheduleExactAlarm checks can see it guarding the call it protects.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()) {
        setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
    } else {
        setWindow(AlarmManager.RTC_WAKEUP, atMillis, window.toMillis(), operation)
    }
}
