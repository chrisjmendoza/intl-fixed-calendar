package io.github.chrisjmendoza.yearal.feature.calendar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import javax.inject.Singleton

/**
 * Hilt wiring for holiday evaluation in the calendar feature: the one [HolidayEngine] of the process.
 *
 * The engine memoises per (set, year), so a single instance is what makes repeated month renders a map
 * lookup (docs/ARCHITECTURE.md §3.3). `:core:domain` is a pure-JVM module with no Hilt, hence the
 * binding lives in its first consumer; the `HolidayPackLoader` binding is in `:feature:settings` for the
 * same reason. **If a third module needs either, move both providers to `:app`** rather than
 * duplicating them — Hilt rejects two bindings of the same type.
 */
@Module
@InstallIn(SingletonComponent::class)
object HolidayModule {
    /** One thread-safe, memoising engine for the process. */
    @Provides
    @Singleton
    fun provideHolidayEngine(): HolidayEngine = HolidayEngine()
}
