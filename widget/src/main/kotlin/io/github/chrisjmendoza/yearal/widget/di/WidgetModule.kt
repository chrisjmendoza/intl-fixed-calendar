package io.github.chrisjmendoza.yearal.widget.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.domain.widget.WidgetUpdater
import io.github.chrisjmendoza.yearal.widget.DebouncedWidgetUpdater
import io.github.chrisjmendoza.yearal.widget.GlanceWidgetRefresher
import io.github.chrisjmendoza.yearal.widget.WidgetRefresher
import io.github.chrisjmendoza.yearal.widget.WidgetRolloverListener
import io.github.chrisjmendoza.yearal.widget.preview.GlancePreviewRegistrar
import io.github.chrisjmendoza.yearal.widget.preview.PreviewRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt wiring for `:widget`. Contributes [WidgetRolloverListener] to the
 * `Set<DayRolloverListener>` multibinding `:core:scheduling` declares
 * (docs/ARCHITECTURE.md §5, "`:widget` contributes a listener with `@Binds @IntoSet` that calls its
 * `updateAll`"). One listener refreshes both the Today and the Month-grid widget (ROADMAP M5 T3).
 *
 * Also binds [WidgetUpdater] (ROADMAP M5 T6): `:core:data`'s `RoomEventRepository` depends only on the
 * `:core:domain` interface, and this is where the concrete, debounced implementation is supplied --
 * `:app` links without any change of its own because it already depends on `:widget`
 * (docs/ARCHITECTURE.md §2 "Dependency direction").
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    /** Adds [WidgetRolloverListener] to the app-wide listener set; see the class KDoc above. */
    @Binds
    @IntoSet
    internal abstract fun bindWidgetRolloverListener(impl: WidgetRolloverListener): DayRolloverListener

    /** The production [WidgetRefresher]: a real `GlanceAppWidget.updateAll` call. */
    @Binds
    internal abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher

    /** The production [WidgetUpdater]: [DebouncedWidgetUpdater], one instance for the whole process. */
    @Binds
    @Singleton
    internal abstract fun bindWidgetUpdater(impl: DebouncedWidgetUpdater): WidgetUpdater

    /** The production [PreviewRegistrar]: a real `GlanceAppWidgetManager.setWidgetPreviews` call. */
    @Binds
    internal abstract fun bindPreviewRegistrar(impl: GlancePreviewRegistrar): PreviewRegistrar

    /** Provider side of the module (Dagger reads a module's companion object as static providers). */
    companion object {
        /**
         * The background scope [DebouncedWidgetUpdater]'s debounce collector runs on: a
         * [SupervisorJob] so one failed refresh does not cancel the collector, [Dispatchers.Default]
         * since re-rendering widgets is CPU-bound composition work, never the main thread.
         */
        @Provides
        @Singleton
        @WidgetCoroutineScope
        internal fun provideWidgetCoroutineScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/** Qualifies the [CoroutineScope] [DebouncedWidgetUpdater]'s debounce collector runs on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class WidgetCoroutineScope

/**
 * How [io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget] reaches the app's time bindings.
 *
 * `GlanceAppWidgetReceiver` and `GlanceAppWidget` are instantiated by the platform and by Glance's own
 * session machinery, not by Hilt, so — exactly like `:core:scheduling`'s `SchedulingEntryPoint` —
 * `provideGlance` resolves this with `EntryPointAccessors.fromApplication` instead of constructor
 * injection. `Clock` and `ZoneProvider` are bound in `:app`'s `TimeModule`; this module only declares
 * that it needs them, and never depends on `:app` to compile (docs/ARCHITECTURE.md §2).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    /** The app-wide clock (CLAUDE.md rule 2: never call `Clock.systemUTC()`/`now()` here instead). */
    fun clock(): Clock

    /** The app-wide zone provider, read fresh on every render (CLAUDE.md rule 2). */
    fun zoneProvider(): ZoneProvider

    /**
     * The agenda read model, used by [io.github.chrisjmendoza.yearal.widget.month.MonthGlanceWidget]
     * (ROADMAP M5 T6) to fetch a single [ObserveAgendaUseCase.presence] snapshot per render -- never
     * collected continuously, unlike a screen's own use of this interface (docs/ARCHITECTURE.md §5
     * "Data").
     */
    fun observeAgendaUseCase(): ObserveAgendaUseCase
}
