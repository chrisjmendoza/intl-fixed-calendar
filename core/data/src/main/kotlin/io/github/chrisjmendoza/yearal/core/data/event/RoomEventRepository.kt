package io.github.chrisjmendoza.yearal.core.data.event

import androidx.room3.withWriteTransaction
import io.github.chrisjmendoza.yearal.core.data.event.dao.CalendarDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.EventDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.ExdateDao
import io.github.chrisjmendoza.yearal.core.data.event.dao.ReminderDao
import io.github.chrisjmendoza.yearal.core.data.event.mapper.toDomainOrNull
import io.github.chrisjmendoza.yearal.core.data.event.mapper.toEntity
import io.github.chrisjmendoza.yearal.core.data.event.mapper.toExdateEntity
import io.github.chrisjmendoza.yearal.core.domain.event.CalendarSource
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander
import io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * [EventRepository] over [YearalDatabase] (`docs/contracts/Events.md` "T2"). Follows the same
 * preconditions, exceptions and timestamp rules as
 * `io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository`; the two are exercised against one
 * shared contract test suite (`core/data/src/test/…/event/EventRepositoryContractTest.kt`).
 *
 * **Atomicity:** every write validates its preconditions first — while holding [mutex], but **before**
 * opening a Room transaction — and only then performs the actual row changes inside one
 * [androidx.room3.withWriteTransaction] block. **A precondition failure never throws while a write
 * transaction is open**: an earlier version validated inside the transaction block and a thrown
 * `IllegalArgumentException` there left the in-memory database's single writer connection unreleased,
 * so the *next* write hung forever waiting for it (found while testing this class: the "adding an
 * exdate to a non-recurring event throws" case deadlocked `EventRepositoryContractTest`, see that
 * class's history). [mutex] also gives the same "concurrent writers are serialised" guarantee the fake
 * gets from SQLite's single writer, without relying on exactly how a thrown exception unwinds a
 * transaction lambda.
 * **Threading:** all I/O runs on the dispatcher [YearalDatabase] was built with
 * (`RoomDatabase.Builder.setQueryCoroutineContext`), never the caller's thread.
 * **Privacy:** nothing here logs event content (CLAUDE.md rule 8); a corrupted row is skipped by the
 * mappers in `event/mapper/EventMappers.kt`, never thrown or logged.
 *
 * @property clock source of `createdAt` / `updatedAt` (CLAUDE.md rule 2).
 * @property recurrenceExpander answers `recurrence_until_epoch_day` on every event write
 *   (`docs/adr/0005-events-contract.md` decision 6); no implementation exists in this module — `:app`
 *   must bind one (ROADMAP M4 T3).
 * @property reminderScheduler asked to recompute the next alarm after every successful write
 *   (`docs/ARCHITECTURE.md` §3.2 "Reminders"); no implementation exists in this module — `:app` must
 *   bind one (ROADMAP M6 T1).
 */
public class RoomEventRepository
    @Inject
    constructor(
        private val database: YearalDatabase,
        private val calendarDao: CalendarDao,
        private val eventDao: EventDao,
        private val exdateDao: ExdateDao,
        private val reminderDao: ReminderDao,
        private val clock: Clock,
        private val recurrenceExpander: RecurrenceExpander,
        private val reminderScheduler: ReminderScheduler,
    ) : EventRepository {
        /** Serialises writers around the validate-then-transact sequence; see the class KDoc. */
        private val mutex = Mutex()

        override fun observeCalendars(): Flow<List<EventCalendar>> =
            calendarDao.observeAll().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

        override suspend fun getCalendar(id: Long): EventCalendar? = calendarDao.findById(id)?.toDomainOrNull()

        override suspend fun upsertCalendar(calendar: EventCalendar): Long {
            val id =
                mutex.withLock {
                    if (calendar.id != EventCalendar.NEW_ID) {
                        requireNotNull(calendarDao.findById(calendar.id)) { "No calendar with id ${calendar.id}" }
                        require(calendar.id != EventCalendar.DEFAULT_ID || calendar.source == CalendarSource.LOCAL) {
                            "The built-in calendar must stay LOCAL"
                        }
                    }
                    database.withWriteTransaction {
                        if (calendar.id == EventCalendar.NEW_ID) {
                            calendarDao.insert(calendar.toEntity(id = 0L))
                        } else {
                            calendarDao.update(calendar.toEntity())
                            calendar.id
                        }
                    }
                }
            reminderScheduler.reschedule()
            return id
        }

        override suspend fun deleteCalendar(id: Long): Boolean {
            require(id != EventCalendar.DEFAULT_ID) { "The built-in calendar cannot be deleted" }
            val deleted = mutex.withLock { database.withWriteTransaction { calendarDao.deleteById(id) > 0 } }
            if (deleted) reminderScheduler.reschedule()
            return deleted
        }

        override fun observeEvents(): Flow<List<Event>> =
            eventDao.observeAllWithRelations().map { rows -> rows.mapNotNull { it.toDomainOrNull() } }

        override fun observeEvent(id: Long): Flow<Event?> =
            eventDao.observeOneWithRelations(id).map { it?.toDomainOrNull() }

        override suspend fun getEvent(id: Long): Event? = eventDao.getWithRelations(id)?.toDomainOrNull()

        override fun observeAgendaCandidates(range: ClosedRange<LocalDate>): Flow<List<Event>> {
            if (range.isEmpty()) return flowOf(emptyList())
            val lo = range.start.minusDays(EventRepository.ZONE_SKEW_DAYS).toEpochDay()
            val hi = range.endInclusive.plusDays(EventRepository.ZONE_SKEW_DAYS).toEpochDay()
            return eventDao.observeAgendaCandidatesWithRelations(lo, hi).map { rows ->
                rows.mapNotNull { it.toDomainOrNull() }
            }
        }

        override suspend fun getReminderCandidates(from: LocalDate): List<Event> {
            val lo = from.minusDays(EventRepository.ZONE_SKEW_DAYS).toEpochDay()
            return eventDao.getReminderCandidatesWithRelations(lo).mapNotNull { it.toDomainOrNull() }
        }

        override suspend fun upsertEvent(event: Event): Long {
            val stored =
                mutex.withLock {
                    requireNotNull(calendarDao.findById(event.calendarId)) { "No calendar with id ${event.calendarId}" }
                    val duplicate = eventDao.findByUid(event.uid)
                    require(duplicate == null || duplicate.id == event.id) { "Another event already has this uid" }

                    // The one case that can fail after this point is a concurrent delete of `event.id`
                    // between this check and the transaction below; the mutex rules that out.
                    val previousCreatedAt =
                        if (event.id == Event.NEW_ID) {
                            null
                        } else {
                            val previous =
                                eventDao.findById(event.id)
                                    ?: throw IllegalArgumentException("No event with id ${event.id}")
                            Instant.ofEpochMilli(previous.createdAt)
                        }

                    val now = clock.instant()
                    val recurrenceUntilEpochDay = recurrenceExpander.recurrenceEndDate(event)?.toEpochDay()

                    database.withWriteTransaction {
                        val storedEvent =
                            if (previousCreatedAt == null) {
                                val entity =
                                    event.toEntity(
                                        id = 0L,
                                        createdAt = now,
                                        updatedAt = now,
                                        recurrenceUntilEpochDay,
                                    )
                                val newId = eventDao.insert(entity)
                                event.copy(id = newId, createdAt = now, updatedAt = now)
                            } else {
                                val updatedAt = if (now.isBefore(previousCreatedAt)) previousCreatedAt else now
                                val entity =
                                    event.toEntity(
                                        event.id,
                                        previousCreatedAt,
                                        updatedAt,
                                        recurrenceUntilEpochDay,
                                    )
                                eventDao.update(entity)
                                event.copy(createdAt = previousCreatedAt, updatedAt = updatedAt)
                            }

                        exdateDao.deleteAllForEvent(storedEvent.id)
                        if (storedEvent.exdates.isNotEmpty()) {
                            exdateDao.insertAll(storedEvent.exdates.map { it.toExdateEntity(storedEvent.id) })
                        }
                        reminderDao.deleteAllForEvent(storedEvent.id)
                        if (storedEvent.reminders.isNotEmpty()) {
                            reminderDao.insertAll(storedEvent.reminders.map { it.toEntity(storedEvent.id) })
                        }
                        storedEvent
                    }
                }
            reminderScheduler.reschedule()
            return stored.id
        }

        override suspend fun deleteEvent(id: Long): Boolean {
            val deleted = mutex.withLock { database.withWriteTransaction { eventDao.deleteById(id) > 0 } }
            if (deleted) reminderScheduler.reschedule()
            return deleted
        }

        override suspend fun addExdate(
            eventId: Long,
            date: LocalDate,
        ): Boolean {
            val added =
                mutex.withLock {
                    val current = eventDao.getWithRelations(eventId)?.toDomainOrNull() ?: return@withLock false
                    if (date in current.exdates) return@withLock false
                    // Validates before opening the transaction: throws for a non-recurring event or a
                    // date before the start (see the class KDoc for why this must not happen inside one).
                    current.copy(exdates = current.exdates + date)
                    database.withWriteTransaction {
                        exdateDao.insert(date.toExdateEntity(eventId))
                        touchUpdatedAt(eventId, current.createdAt)
                    }
                    true
                }
            if (added) reminderScheduler.reschedule()
            return added
        }

        override suspend fun removeExdate(
            eventId: Long,
            date: LocalDate,
        ): Boolean {
            val removed =
                mutex.withLock {
                    val current = eventDao.getWithRelations(eventId)?.toDomainOrNull() ?: return@withLock false
                    if (date !in current.exdates) return@withLock false
                    database.withWriteTransaction {
                        exdateDao.delete(eventId, date.toEpochDay())
                        touchUpdatedAt(eventId, current.createdAt)
                    }
                    true
                }
            if (removed) reminderScheduler.reschedule()
            return removed
        }

        override suspend fun deleteAllData() {
            mutex.withLock {
                database.withWriteTransaction {
                    eventDao.deleteAll()
                    calendarDao.deleteAll()
                    calendarDao.insert(EventCalendar.DEFAULT.toEntity())
                }
            }
            reminderScheduler.reschedule()
        }

        /** Sets `updated_at` to the clock's instant, never before [createdAt] (the [Event] invariant). */
        private suspend fun touchUpdatedAt(
            eventId: Long,
            createdAt: Instant,
        ) {
            val now = clock.instant()
            val updatedAt = if (now.isBefore(createdAt)) createdAt else now
            eventDao.touchUpdatedAt(eventId, updatedAt.toEpochMilli())
        }
    }
