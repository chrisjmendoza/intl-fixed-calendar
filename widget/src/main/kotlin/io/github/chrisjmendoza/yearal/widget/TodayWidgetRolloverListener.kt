package io.github.chrisjmendoza.yearal.widget

import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import javax.inject.Inject

/**
 * The widget's contribution to the app's `Set<DayRolloverListener>` multibinding
 * (docs/ARCHITECTURE.md §5, "As built (M5 T2, `:core:scheduling`)"): every trigger — the midnight
 * alarm, a clock or zone change, boot, or an app update — re-renders the widget through [refresher].
 *
 * This listener does not read the trigger to decide *whether* the date changed: per
 * [DayRolloverListener], a call is a hint, not a fact, and [TodayGlanceWidget][io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget]
 * recomputes "today" from the injected `Clock`/`ZoneProvider` on every render regardless of why it was
 * asked to render, so refreshing unconditionally on every trigger is correct and cheap.
 */
class TodayWidgetRolloverListener
    @Inject
    constructor(
        private val refresher: WidgetRefresher,
    ) : DayRolloverListener {
        /** Refreshes every placed instance exactly once per call, regardless of [trigger]. */
        override suspend fun onDayRollover(trigger: DayRolloverTrigger) {
            refresher.refreshAll()
        }
    }
