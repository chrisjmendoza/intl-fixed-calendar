package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A hand-written [DayRolloverListener] fake that records every call, for tests of whatever notifies
 * the listeners (the `:core:scheduling` alarm and system-event receivers).
 *
 * @param onCall runs inside each [onDayRollover] call **after** the trigger has been recorded. Use it
 *   to observe state at call time (for example, read a `MutableClock` to prove "today" is recomputed
 *   when called), to throw (a failing listener) or to suspend (a listener that never finishes). The
 *   default does nothing.
 */
public class RecordingDayRolloverListener(
    private val onCall: suspend (DayRolloverTrigger) -> Unit = {},
) : DayRolloverListener {
    private val recorded = CopyOnWriteArrayList<DayRolloverTrigger>()

    /** Every trigger received so far, oldest first; one entry per [onDayRollover] call. */
    public val triggers: List<DayRolloverTrigger>
        get() = recorded.toList()

    /** Records [trigger], then runs the `onCall` hook, letting whatever it throws propagate. */
    override suspend fun onDayRollover(trigger: DayRolloverTrigger) {
        recorded += trigger
        onCall(trigger)
    }
}
