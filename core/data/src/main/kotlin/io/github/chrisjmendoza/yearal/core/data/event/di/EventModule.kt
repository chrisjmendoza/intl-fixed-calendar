package io.github.chrisjmendoza.yearal.core.data.event.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.data.event.RoomEventRepository
import io.github.chrisjmendoza.yearal.core.data.event.YearalDatabase
import io.github.chrisjmendoza.yearal.core.data.event.dao.CalendarDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.EventDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.ExdateDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.ReminderDao
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt wiring for [YearalDatabase] and [io.github.chrisjmendoza.yearal.core.data.event.RoomEventRepository]
 * (`docs/ARCHITECTURE.md` §2 "Dependency direction": only `:app` depends on `:core:data`; features see
 * [EventRepository]).
 *
 * `RoomEventRepository` also takes a `java.time.Clock`, a
 * `io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander` and a
 * `io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler` by constructor injection.
 * `Clock` is already bound in `:app`'s `TimeModule`. **`RecurrenceExpander` and `ReminderScheduler`
 * have no binding yet** — their implementations are ROADMAP M4 T3 and M6 T1 — so `:app`'s Hilt graph
 * does not link until both are bound in `SingletonComponent` (see the M4 T2 completion report for the
 * exact bindings needed).
 */
@Module
@InstallIn(SingletonComponent::class)
public abstract class EventModule {
    /** Features and `:app` inject [EventRepository]; the Room-backed implementation is the only one. */
    @Binds
    internal abstract fun bindEventRepository(impl: RoomEventRepository): EventRepository

    /** Provider side of the module (Dagger reads a module's companion object as static providers). */
    public companion object {
        /**
         * The one [YearalDatabase] of the process. **Must be a singleton**: two Room instances open on
         * the same file corrupt Room's write-ahead-log bookkeeping.
         */
        @Provides
        @Singleton
        public fun provideYearalDatabase(
            @ApplicationContext context: Context,
            @IoDispatcher dispatcher: CoroutineDispatcher,
        ): YearalDatabase = YearalDatabase.create(context, dispatcher)

        /** [CalendarDao] from the singleton database. */
        @Provides
        public fun provideCalendarDao(database: YearalDatabase): CalendarDao = database.calendarDao()

        /** [EventDao] from the singleton database. */
        @Provides
        public fun provideEventDao(database: YearalDatabase): EventDao = database.eventDao()

        /** [ExdateDao] from the singleton database. */
        @Provides
        public fun provideExdateDao(database: YearalDatabase): ExdateDao = database.exdateDao()

        /** [ReminderDao] from the singleton database. */
        @Provides
        public fun provideReminderDao(database: YearalDatabase): ReminderDao = database.reminderDao()

        /**
         * The dispatcher Room runs queries on (`RoomDatabase.Builder.setQueryCoroutineContext`) and
         * `RoomEventRepository` uses for its own transaction blocks. Never the main thread
         * (`docs/contracts/Events.md`: "every suspend function is main-safe").
         */
        @Provides
        @IoDispatcher
        internal fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}

/** Qualifies the [CoroutineDispatcher] event storage runs its I/O on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class IoDispatcher
