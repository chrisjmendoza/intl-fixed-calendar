package io.github.chrisjmendoza.yearal.core.scheduling

import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.github.chrisjmendoza.yearal.core.scheduling.di.RolloverCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import javax.inject.Inject

/**
 * What both receivers do once they have validated their intent: re-arm the midnight alarm, then
 * notify the listeners (`docs/ARCHITECTURE.md` §5 "Midnight rollover (layered)").
 */
internal class RolloverBroadcastHandler
    @Inject
    constructor(
        private val scheduler: DayRolloverScheduler,
        private val notifier: DayRolloverNotifier,
        @param:RolloverCoroutineScope private val scope: CoroutineScope,
    ) {
        /**
         * Re-arms synchronously on the calling (main) thread **first**, so the alarm chain survives
         * whatever the listeners do, then notifies the listeners on [scope].
         *
         * @param onFinished called exactly once when the listeners are done, have failed, or were
         *   cancelled after [LISTENER_BUDGET]; the receiver passes `PendingResult.finish()` here. A
         *   listener failure is rethrown on [scope] after [onFinished] has run.
         */
        fun handle(
            trigger: DayRolloverTrigger,
            onFinished: () -> Unit,
        ) {
            scheduler.arm()
            scope.launch {
                try {
                    withTimeoutOrNull(LISTENER_BUDGET.toMillis()) { notifier.notify(trigger) }
                } finally {
                    onFinished()
                }
            }
        }

        companion object {
            /**
             * How long listeners may run. A receiver that used `goAsync()` must finish within about ten
             * seconds for a foreground broadcast or the system reports an ANR; this stays under it.
             */
            val LISTENER_BUDGET: Duration = Duration.ofSeconds(8)
        }
    }
