package io.github.chrisjmendoza.yearal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.chrisjmendoza.yearal.core.scheduling.DayRolloverScheduler
import javax.inject.Inject

/**
 * Hilt root. Feature modules contribute their own bindings; the one thing wired here is the day
 * rollover, which has to be armed whenever the process starts, with or without a screen.
 */
@HiltAndroidApp
class IfcApplication : Application() {
    /** Injected by Hilt during `super.onCreate()`. */
    @Inject
    lateinit var dayRolloverScheduler: DayRolloverScheduler

    /**
     * Arms the midnight alarm on every process start (`docs/ARCHITECTURE.md` §5 "Midnight rollover
     * (layered)"), so an alarm lost to a force-stop or an OEM task killer is back as soon as anything
     * starts the app. Setting one alarm is all that happens here: no listener is notified, because
     * whatever started the process renders "today" from the `Clock` itself.
     */
    override fun onCreate() {
        super.onCreate()
        dayRolloverScheduler.arm()
    }
}
