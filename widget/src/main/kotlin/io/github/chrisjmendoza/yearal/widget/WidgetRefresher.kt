package io.github.chrisjmendoza.yearal.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chrisjmendoza.yearal.widget.month.MonthGlanceWidget
import io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget
import javax.inject.Inject

/**
 * Re-renders every placed instance of every widget this module owns. The seam between
 * [io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener] and the concrete Glance
 * API, so the listener ([WidgetRolloverListener]) can be unit-tested with a fake instead of a real
 * `AppWidgetManager`. One [refreshAll] call updates every widget this module owns exactly once each --
 * as of ROADMAP M5 T3, [TodayGlanceWidget] and [MonthGlanceWidget].
 */
fun interface WidgetRefresher {
    /** Suspends until every placed instance of every widget has been asked to re-render, once each. */
    suspend fun refreshAll()
}

/**
 * [WidgetRefresher] backed by [GlanceAppWidget.updateAll] for each of [widgets], which re-invokes each
 * widget's `provideGlance` for every placed instance -- the point where "today" is recomputed from the
 * injected `Clock` and `ZoneProvider` (CLAUDE.md rule 2).
 */
class GlanceWidgetRefresher
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WidgetRefresher {
        override suspend fun refreshAll() {
            widgets.forEach { widget -> widget.updateAll(context) }
        }

        /**
         * The widgets this refresher re-renders, one call each. `internal` so a test can prove both
         * widget types are covered -- one call each, no duplicates -- without needing a real
         * `AppWidgetManager` with placed instances (matching this class's own reason for existing).
         */
        internal val widgets: List<GlanceAppWidget>
            get() = listOf(TodayGlanceWidget(), MonthGlanceWidget())
    }
