package io.github.chrisjmendoza.yearal.core.scheduling.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.scheduling.RolloverBroadcastHandler
import io.github.chrisjmendoza.yearal.core.scheduling.reminder.AlarmReminderScheduler
import io.github.chrisjmendoza.yearal.core.scheduling.reminder.ReminderBroadcastHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt wiring of the day rollover and of reminders. Both schedulers take `Clock` and `ZoneProvider`
 * from the app's time bindings and the reminder one takes `EventRepository` and `RecurrenceExpander`
 * from `:core:data` and `:app`; this module adds the listener set, the scope the listeners run on,
 * and the reminder bindings themselves.
 */
@Module
@InstallIn(SingletonComponent::class)
public abstract class SchedulingModule {
    /**
     * Declares the `Set<DayRolloverListener>` multibinding so that it is valid while **empty**. A
     * module that wants the rollover signal (`:widget`, the reminder scheduler) contributes with
     * `@Binds @IntoSet`; this module never learns who listens (`docs/ARCHITECTURE.md` §5).
     */
    @Multibinds
    internal abstract fun dayRolloverListeners(): Set<DayRolloverListener>

    /**
     * The app's one reminder scheduler (ROADMAP M6 T1), which the production `EventRepository` calls
     * after every write (`docs/contracts/Events.md` §5). It replaced the no-op binding `:app` carried
     * while reminders did not exist.
     */
    @Binds
    internal abstract fun bindReminderScheduler(scheduler: AlarmReminderScheduler): ReminderScheduler

    /**
     * The same singleton, contributed to the rollover multibinding, so that boot, a time or zone
     * change, an app update and every midnight all recompute the next reminder alarm
     * (`docs/ARCHITECTURE.md` §3.2 "Reminders": "It also recomputes on event writes, boot, time or
     * zone changes, and app update").
     */
    @Binds
    @IntoSet
    internal abstract fun bindReminderRolloverListener(scheduler: AlarmReminderScheduler): DayRolloverListener

    /** Provider side of the module (Dagger reads a module's companion object as static providers). */
    public companion object {
        /**
         * Process-lifetime scope for listener work started from a broadcast. `Dispatchers.Default`
         * keeps it off the main thread; the `SupervisorJob` keeps one failed notification from
         * cancelling the scope for every later broadcast.
         */
        @Provides
        @Singleton
        @RolloverCoroutineScope
        internal fun provideRolloverCoroutineScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/** Qualifies the [CoroutineScope] that day-rollover listeners run on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class RolloverCoroutineScope

/**
 * How the manifest receivers, which the system instantiates, reach the dependency graph: resolved
 * from the application with `EntryPointAccessors.fromApplication`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SchedulingEntryPoint {
    /** The handler the two rollover receivers delegate to. */
    fun rolloverBroadcastHandler(): RolloverBroadcastHandler

    /**
     * The handler the reminder alarm receiver, and the exact-alarm-permission branch of
     * [io.github.chrisjmendoza.yearal.core.scheduling.SystemEventReceiver], delegate to.
     */
    fun reminderBroadcastHandler(): ReminderBroadcastHandler
}
