package io.github.chrisjmendoza.yearal.core.scheduling.reminder

import io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler
import io.github.chrisjmendoza.yearal.core.scheduling.DayRolloverScheduler
import io.github.chrisjmendoza.yearal.core.scheduling.di.RolloverCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import javax.inject.Inject

/**
 * What [ReminderAlarmReceiver] and the exact-alarm-permission branch of
 * [io.github.chrisjmendoza.yearal.core.scheduling.SystemEventReceiver] do once they have validated
 * their intent, the counterpart of
 * [io.github.chrisjmendoza.yearal.core.scheduling.RolloverBroadcastHandler] for reminders.
 *
 * The recomputation reads the database, so it cannot run on the broadcast thread; it runs on the
 * process-lifetime rollover scope under a budget that keeps a `goAsync()` receiver inside the
 * platform's ANR limit.
 */
internal class ReminderBroadcastHandler
    @Inject
    constructor(
        private val reminderScheduler: ReminderScheduler,
        private val rolloverScheduler: DayRolloverScheduler,
        @param:RolloverCoroutineScope private val scope: CoroutineScope,
    ) {
        /**
         * Recomputes the reminder alarm, and optionally re-arms the midnight rollover alarm first.
         *
         * @param rearmRollover `true` only for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`:
         *   both alarms were armed under the old capability and both must be replaced under the new
         *   one (`docs/security-and-privacy.md` §5.1; ROADMAP M6 T3). The rollover is re-armed
         *   synchronously on the calling thread first — two binder calls, no I/O — so the daily chain
         *   survives whatever the reminder recomputation does.
         * @param onFinished called exactly once when the recomputation has finished, failed or been
         *   cancelled at [RECOMPUTE_BUDGET]; the receiver passes `PendingResult.finish()` here.
         */
        fun handle(
            rearmRollover: Boolean,
            onFinished: () -> Unit,
        ) {
            if (rearmRollover) rolloverScheduler.arm()
            scope.launch {
                try {
                    withTimeoutOrNull(RECOMPUTE_BUDGET.toMillis()) { reminderScheduler.reschedule() }
                } finally {
                    onFinished()
                }
            }
        }

        companion object {
            /**
             * How long the recomputation may run. A receiver that used `goAsync()` must finish within
             * about ten seconds for a foreground broadcast or the system reports an ANR; this stays
             * under it, like
             * [io.github.chrisjmendoza.yearal.core.scheduling.RolloverBroadcastHandler.LISTENER_BUDGET].
             */
            val RECOMPUTE_BUDGET: Duration = Duration.ofSeconds(8)
        }
    }
