package io.github.chrisjmendoza.yearal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler
import io.github.chrisjmendoza.yearal.core.scheduling.DayRolloverScheduler
import io.github.chrisjmendoza.yearal.widget.preview.WidgetPreviewUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Hilt root. Feature modules contribute their own bindings; what is wired here is only what has to
 * happen whenever the process starts, with or without a screen: the day rollover and the reminder
 * alarm are re-armed, and the widget-picker previews are registered.
 */
@HiltAndroidApp
class IfcApplication : Application() {
    /** Injected by Hilt during `super.onCreate()`. */
    @Inject
    lateinit var dayRolloverScheduler: DayRolloverScheduler

    /** Injected by Hilt during `super.onCreate()`. */
    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    /** Injected by Hilt during `super.onCreate()`. */
    @Inject
    lateinit var widgetPreviewUpdater: WidgetPreviewUpdater

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Arms the midnight alarm on every process start (`docs/ARCHITECTURE.md` §5 "Midnight rollover
     * (layered)"), so an alarm lost to a force-stop or an OEM task killer is back as soon as anything
     * starts the app. Setting that one alarm is all that happens on the main thread: no listener is
     * notified, because whatever started the process renders "today" from the `Clock` itself.
     *
     * Off the main thread, [AppStartup] then recomputes the next reminder alarm for the same reason
     * (`docs/ARCHITECTURE.md` §3.2 "Reminders": a lost reminder alarm must not wait for the next
     * midnight or event edit) and registers the widget-picker previews, which is a no-op below API 35
     * and when the (version, locale) pair is unchanged (§5 "Picker previews").
     */
    override fun onCreate() {
        super.onCreate()
        dayRolloverScheduler.arm()
        AppStartup(startupScope).run(
            listOf(
                "reminders" to { reminderScheduler.reschedule() },
                "widgetPreviews" to { widgetPreviewUpdater.updateIfNeeded() },
            ),
        )
    }
}
