package io.github.chrisjmendoza.yearal.widget.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.widget.GlanceWidgetRefresher
import io.github.chrisjmendoza.yearal.widget.TodayWidgetRolloverListener
import io.github.chrisjmendoza.yearal.widget.WidgetRefresher
import java.time.Clock

/**
 * Hilt wiring for `:widget`. Contributes [TodayWidgetRolloverListener] to the
 * `Set<DayRolloverListener>` multibinding `:core:scheduling` declares
 * (docs/ARCHITECTURE.md §5, "`:widget` contributes a listener with `@Binds @IntoSet` that calls its
 * `updateAll`").
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    /** Adds [TodayWidgetRolloverListener] to the app-wide listener set; see the class KDoc above. */
    @Binds
    @IntoSet
    internal abstract fun bindTodayWidgetRolloverListener(impl: TodayWidgetRolloverListener): DayRolloverListener

    /** The production [WidgetRefresher]: a real `GlanceAppWidget.updateAll` call. */
    @Binds
    internal abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
}

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
}
