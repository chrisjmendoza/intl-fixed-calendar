package io.github.chrisjmendoza.yearal.core.scheduling

import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

/**
 * Calls every [DayRolloverListener] in the app's multibound set, which is empty until the widgets (M5)
 * and the reminder scheduler (M6) contribute theirs.
 */
internal class DayRolloverNotifier
    @Inject
    constructor(
        private val listeners: Set<@JvmSuppressWildcards DayRolloverListener>,
    ) {
        /**
         * Calls each listener exactly once with [trigger], concurrently, and returns when all have
         * finished. Listeners are isolated from each other: one that throws or hangs does not keep the
         * others from running.
         *
         * @throws Exception the first listener failure, with any further failures attached as
         *   suppressed, after every listener has finished. Failures are not swallowed: the only crash
         *   signal this app has is Play vitals (`docs/security-and-privacy.md` §7).
         */
        suspend fun notify(trigger: DayRolloverTrigger) {
            val failures =
                supervisorScope {
                    listeners
                        .map { listener -> async { listener.onDayRollover(trigger) } }
                        .mapNotNull { call ->
                            try {
                                call.await()
                                null
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Exception) {
                                failure
                            }
                        }
                }
            val first = failures.firstOrNull() ?: return
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }
