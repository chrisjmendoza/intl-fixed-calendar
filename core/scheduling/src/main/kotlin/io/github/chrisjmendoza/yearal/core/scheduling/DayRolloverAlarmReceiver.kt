package io.github.chrisjmendoza.yearal.core.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.github.chrisjmendoza.yearal.core.scheduling.di.SchedulingEntryPoint

/**
 * Receives the midnight alarm armed by [DayRolloverScheduler]: re-arms it for the following midnight
 * and notifies the listeners with [DayRolloverTrigger.MIDNIGHT].
 *
 * Not exported and without an intent filter, so only the app's own explicit `PendingIntent` reaches it
 * (`docs/security-and-privacy.md` §6.3). It still checks the action, reads nothing else from the
 * intent, and ignores anything that is not [DayRolloverScheduler.ACTION_DAY_ROLLOVER].
 *
 * **The alarm firing does not mean the date changed**: delivery may be late (windowed alarm, Doze) or
 * follow a clock change, which is why the next alarm and the listeners' "today" both come from the
 * injected `Clock`, never from this broadcast.
 */
public class DayRolloverAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != DayRolloverScheduler.ACTION_DAY_ROLLOVER) return
        dispatchRollover(context, DayRolloverTrigger.MIDNIGHT)
    }
}

/**
 * Hands [trigger] to the app's [RolloverBroadcastHandler] and keeps the broadcast (and with it the
 * process) alive with `goAsync()` until the listeners are done. Manifest receivers are created by the
 * system, so the handler comes from the Hilt [SchedulingEntryPoint] rather than a constructor.
 */
internal fun BroadcastReceiver.dispatchRollover(
    context: Context,
    trigger: DayRolloverTrigger,
) {
    val handler =
        EntryPointAccessors
            .fromApplication(context, SchedulingEntryPoint::class.java)
            .rolloverBroadcastHandler()
    // Null only when onReceive is called directly rather than by the system's broadcast dispatch.
    val pendingResult: BroadcastReceiver.PendingResult? = goAsync()
    handler.handle(trigger) { pendingResult?.finish() }
}
