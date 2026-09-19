package io.github.chrisjmendoza.yearal.feature.calendar.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySetProvider
import io.github.chrisjmendoza.yearal.feature.calendar.holiday.PackHolidaySetProvider
import javax.inject.Singleton

/**
 * Hilt wiring for holiday evaluation in the calendar feature: the one [HolidayEngine] of the process,
 * and the [HolidaySetProvider] binding for [PackHolidaySetProvider].
 *
 * The engine memoises per (set, year), so a single instance is what makes repeated month renders a map
 * lookup (docs/ARCHITECTURE.md §3.3). `:core:domain` is a pure-JVM module with no Hilt, hence both
 * bindings live in this feature, their first consumer; the `HolidayPackLoader` binding is in
 * `:feature:settings` for the same reason, and [PackHolidaySetProvider] is injected across that
 * boundary the same way [io.github.chrisjmendoza.yearal.feature.calendar.holiday.HolidayCatalog]
 * already is. **If a further module needs the engine or the provider, move these to `:app`** rather
 * than duplicating them — Hilt rejects two bindings of the same type.
 */
@Module
@InstallIn(SingletonComponent::class)
object HolidayModule {
    /** One thread-safe, memoising engine for the process. */
    @Provides
    @Singleton
    fun provideHolidayEngine(): HolidayEngine = HolidayEngine()
}

/** Separate module ([Binds] requires an abstract class or interface) for the [HolidaySetProvider] binding. */
@Module
@InstallIn(SingletonComponent::class)
abstract class HolidaySetProviderModule {
    /** The enabled holiday sets, resolved from the bundled packs and the user's settings. */
    @Binds
    @Singleton
    abstract fun bindHolidaySetProvider(impl: PackHolidaySetProvider): HolidaySetProvider
}
