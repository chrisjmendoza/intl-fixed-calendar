package io.github.chrisjmendoza.yearal.core.scheduling.di

import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.scheduling.RolloverBroadcastHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt wiring of the day rollover. The scheduler takes `Clock` and `ZoneProvider` from the app's time
 * bindings; this module adds the listener set and the scope the listeners run on.
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
    /** The handler both receivers delegate to. */
    fun rolloverBroadcastHandler(): RolloverBroadcastHandler
}
