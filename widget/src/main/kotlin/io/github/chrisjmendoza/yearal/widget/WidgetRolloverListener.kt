package io.github.chrisjmendoza.yearal.widget

import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import javax.inject.Inject

/**
 * The widget module's contribution to the app's `Set<DayRolloverListener>` multibinding
 * (docs/ARCHITECTURE.md §5, "As built (M5 T2, `:core:scheduling`)"): every trigger — the midnight
 * alarm, a clock or zone change, boot, or an app update — re-renders **every widget this module owns**
 * through [refresher]. Named for the module rather than for one widget since ROADMAP M5 T3 added the
 * Month-grid widget alongside Today: [refresher] already re-renders both (see [WidgetRefresher]'s
 * KDoc), so one listener, one multibinding entry, covers both.
 *
 * This listener does not read the trigger to decide *whether* the date changed: per
 * [DayRolloverListener], a call is a hint, not a fact, and every widget in this module recomputes
 * "today" from the injected `Clock`/`ZoneProvider` on every render regardless of why it was asked to
 * render, so refreshing unconditionally on every trigger is correct and cheap.
 */
class WidgetRolloverListener
    @Inject
    constructor(
        private val refresher: WidgetRefresher,
    ) : DayRolloverListener {
        /** Refreshes every placed instance of every widget exactly once per call, regardless of [trigger]. */
        override suspend fun onDayRollover(trigger: DayRolloverTrigger) {
            refresher.refreshAll()
        }
    }
