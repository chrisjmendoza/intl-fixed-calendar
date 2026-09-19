package io.github.chrisjmendoza.yearal.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.domain.event.DefaultRecurrenceExpander
import io.github.chrisjmendoza.yearal.core.domain.event.EventUidGenerator
import io.github.chrisjmendoza.yearal.core.domain.event.RandomEventUidGenerator
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander
import javax.inject.Singleton

/**
 * The events bindings that live in pure-JVM `:core:domain`, which cannot carry Hilt modules itself
 * (`docs/contracts/Events.md`). `EventRepository` is bound by `:core:data`'s own `EventModule`, and
 * `ReminderScheduler` by `:core:scheduling`'s `SchedulingModule` — `AlarmReminderScheduler`, which
 * replaced the no-op binding that stood here until ROADMAP M6 T1.
 */
@Module
@InstallIn(SingletonComponent::class)
object EventsModule {
    /** The one recurrence expander; stateless, so a singleton. */
    @Provides
    @Singleton
    fun provideRecurrenceExpander(): RecurrenceExpander = DefaultRecurrenceExpander()

    /** Random UUIDs for new events' `uid` (the `.ics` round-trip key). */
    @Provides
    fun provideEventUidGenerator(): EventUidGenerator = RandomEventUidGenerator
}
