package io.github.chrisjmendoza.yearal.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget
import javax.inject.Inject

/**
 * Re-renders every placed instance of every widget this module owns. The seam between
 * [io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener] and the concrete Glance
 * API, so the listener can be unit-tested with a fake instead of a real `AppWidgetManager`.
 */
fun interface WidgetRefresher {
    /** Suspends until every placed widget instance has been asked to re-render. */
    suspend fun refreshAll()
}

/**
 * [WidgetRefresher] backed by [TodayGlanceWidget.updateAll], which re-invokes
 * [TodayGlanceWidget.provideGlance] for every placed instance — the point where "today" is recomputed
 * from the injected `Clock` and `ZoneProvider` (CLAUDE.md rule 2).
 */
class GlanceWidgetRefresher
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WidgetRefresher {
        override suspend fun refreshAll() {
            TodayGlanceWidget().updateAll(context)
        }
    }
