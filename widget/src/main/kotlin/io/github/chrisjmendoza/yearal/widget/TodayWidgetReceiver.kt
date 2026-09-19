package io.github.chrisjmendoza.yearal.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget

/**
 * The Today widget's `AppWidgetProvider` (`AndroidManifest.xml`, `res/xml/today_widget_info.xml`).
 * Exported because launchers bind widget receivers directly; hardening is that it only ever re-renders
 * [TodayGlanceWidget] from the injected `Clock`/`ZoneProvider`, so any intent a launcher or another app
 * sends it (docs/security-and-privacy.md §6.3, "A spoofed `APPWIDGET_UPDATE` just causes a refresh")
 * cannot show anything but the correct date.
 *
 * Instantiated by the platform, so it takes no constructor dependencies; [TodayGlanceWidget] itself
 * reaches the app's Hilt graph through [io.github.chrisjmendoza.yearal.widget.di.WidgetEntryPoint].
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayGlanceWidget()
}
