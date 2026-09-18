package io.github.chrisjmendoza.yearal.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.RealDateTicker
import io.github.chrisjmendoza.yearal.core.domain.SystemZoneProvider
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import java.time.Clock
import javax.inject.Singleton

/**
 * The one place the real clock and zone enter the app. Everything else takes [Clock], [ZoneProvider]
 * or [DateTicker] by injection and is tested with the fakes in `:core:testing` (CLAUDE.md rule 2).
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /** The system clock in the current default zone. Callers must not cache the zone. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /** Reads the device zone afresh on every call, so a zone change is seen without a restart. */
    @Provides
    @Singleton
    fun provideZoneProvider(): ZoneProvider = SystemZoneProvider

    /** The app-wide "today" stream. */
    @Provides
    @Singleton
    fun provideDateTicker(
        clock: Clock,
        zoneProvider: ZoneProvider,
    ): DateTicker = RealDateTicker(clock, zoneProvider)
}
