package io.github.chrisjmendoza.yearal.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.chrisjmendoza.yearal.widget.month.MonthGlanceWidget

/**
 * The Month-grid widget's `AppWidgetProvider` (`AndroidManifest.xml`, `res/xml/month_widget_info.xml`;
 * ROADMAP M5 T3). Exported because launchers bind widget receivers directly; hardening is the same as
 * [TodayWidgetReceiver]'s: it only ever re-renders [MonthGlanceWidget] from the injected
 * `Clock`/`ZoneProvider`, so any intent a launcher or another app sends it
 * (docs/security-and-privacy.md §6.3, "A spoofed `APPWIDGET_UPDATE` just causes a refresh") cannot show
 * anything but the correct month and day.
 *
 * Instantiated by the platform, so it takes no constructor dependencies; [MonthGlanceWidget] itself
 * reaches the app's Hilt graph through [io.github.chrisjmendoza.yearal.widget.di.WidgetEntryPoint].
 */
class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthGlanceWidget()
}
