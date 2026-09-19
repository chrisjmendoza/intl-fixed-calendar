package io.github.chrisjmendoza.yearal.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.widget.month.MonthGlanceWidget
import io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [GlanceWidgetRefresher] is the production [WidgetRefresher]: [WidgetRolloverListenerTest] already
 * proves the listener calls [WidgetRefresher.refreshAll] exactly once per rollover trigger, so this
 * proves the other half -- that one [GlanceWidgetRefresher.refreshAll] call reaches **both** widgets
 * this module owns, one call each, no duplicates (ROADMAP M5 T3 requirement: "the refresher updates
 * both widgets once per rollover call").
 *
 * [GlanceWidgetRefresher.widgets] is `internal` for exactly this reason: proving the target list is
 * right does not need a real `AppWidgetManager` with placed instances, matching the reason
 * [WidgetRefresher] exists as a seam at all (its own KDoc).
 */
@RunWith(AndroidJUnit4::class)
class GlanceWidgetRefresherTest {
    @Test
    fun `refreshAll targets both the Today and the Month widget, one each`() {
        val refresher = GlanceWidgetRefresher(ApplicationProvider.getApplicationContext())

        refresher.widgets.map { it::class } shouldContainExactlyInAnyOrder
            listOf(TodayGlanceWidget::class, MonthGlanceWidget::class)
    }
}
