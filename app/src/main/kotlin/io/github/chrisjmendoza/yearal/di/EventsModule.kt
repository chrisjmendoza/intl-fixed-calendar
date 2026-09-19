package io.github.chrisjmendoza.yearal.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.domain.event.DefaultRecurrenceExpander
import io.github.chrisjmendoza.yearal.core.domain.event.EventUidGenerator
import io.github.chrisjmendoza.yearal.core.domain.event.RandomEventUidGenerator
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander
import io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler
import javax.inject.Singleton

/**
 * The events bindings that live in pure-JVM `:core:domain`, which cannot carry Hilt modules itself
 * (`docs/contracts/Events.md`). `EventRepository` is bound by `:core:data`'s own `EventModule`.
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

    /**
     * Reminder delivery does not exist before ROADMAP M6 T1: the editor already stores reminders, and
     * nothing fires them yet. Until the real `AlarmManager`-backed scheduler in `:core:scheduling`
     * replaces this binding, the repository's post-write hook has nothing to re-arm, so it does nothing.
     */
    @Provides
    @Singleton
    fun provideReminderScheduler(): ReminderScheduler = ReminderScheduler { }
}
