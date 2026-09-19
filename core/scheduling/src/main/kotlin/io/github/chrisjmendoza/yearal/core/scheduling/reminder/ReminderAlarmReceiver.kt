package io.github.chrisjmendoza.yearal.core.scheduling.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import io.github.chrisjmendoza.yearal.core.scheduling.di.SchedulingEntryPoint

/**
 * Receives the single reminder alarm armed by [AlarmReminderScheduler] and asks it to recompute:
 * post whatever has come due, then arm the next alarm.
 *
 * Not exported and without an intent filter, so only the app's own explicit `PendingIntent` reaches
 * it (`docs/security-and-privacy.md` §6.3). It checks the action and reads **nothing else** from the
 * intent — which reminder fired is derived from the store and the clock, never carried in an extra
 * (CLAUDE.md rule 8), which is also why a late, early or duplicated delivery is harmless.
 */
public class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != AlarmReminderScheduler.ACTION_REMINDER) return
        dispatchReminderRecompute(context, rearmRollover = false)
    }
}

/**
 * Hands a reminder recomputation to the app's [ReminderBroadcastHandler] and keeps the broadcast —
 * and with it the process — alive with `goAsync()` until it is done. Manifest receivers are created
 * by the system, so the handler comes from the Hilt [SchedulingEntryPoint] rather than a constructor.
 *
 * @param rearmRollover also re-arm the midnight rollover alarm, which shares the exact-alarm
 *   capability with the reminder alarm. Only the exact-alarm permission broadcast sets this.
 */
internal fun BroadcastReceiver.dispatchReminderRecompute(
    context: Context,
    rearmRollover: Boolean,
) {
    val handler =
        EntryPointAccessors
            .fromApplication(context, SchedulingEntryPoint::class.java)
            .reminderBroadcastHandler()
    // Null only when onReceive is called directly rather than by the system's broadcast dispatch.
    val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
    handler.handle(rearmRollover) { pendingResult?.finish() }
}
