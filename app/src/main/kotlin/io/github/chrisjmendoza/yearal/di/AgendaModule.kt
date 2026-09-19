package io.github.chrisjmendoza.yearal.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import io.github.chrisjmendoza.yearal.core.domain.event.DefaultObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySetProvider
import javax.inject.Singleton

/**
 * Binds [ObserveAgendaUseCase] — the single place events and holidays are combined for the month
 * grid, Day detail and Today (`docs/ARCHITECTURE.md` §3.4) — from bindings that live in `:core:data`
 * ([EventRepository]), this module's neighbour `EventsModule` ([RecurrenceExpander]), `:app`'s own
 * `TimeModule` ([ZoneProvider]) and `:feature:calendar` ([HolidayEngine], [HolidaySetProvider]).
 *
 * `:core:domain` is a pure-JVM module with no Hilt, hence the binding lives here, in `:app`, where
 * every dependency it needs is already on the classpath through the feature and data modules `:app`
 * assembles.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgendaModule {
    /** One [DefaultObserveAgendaUseCase] for the process; it holds no state of its own. */
    @Provides
    @Singleton
    fun provideObserveAgendaUseCase(
        eventRepository: EventRepository,
        recurrenceExpander: RecurrenceExpander,
        holidayEngine: HolidayEngine,
        holidaySetProvider: HolidaySetProvider,
        zoneProvider: ZoneProvider,
    ): ObserveAgendaUseCase =
        DefaultObserveAgendaUseCase(
            eventRepository = eventRepository,
            recurrenceExpander = recurrenceExpander,
            holidayEngine = holidayEngine,
            holidaySetProvider = holidaySetProvider,
            zoneProvider = zoneProvider,
        )
}
