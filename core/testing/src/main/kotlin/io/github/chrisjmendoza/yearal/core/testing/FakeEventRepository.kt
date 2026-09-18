package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.event.CalendarSource
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * In-memory [EventRepository] that follows the contract of the interface and of
 * `docs/contracts/Events.md`: whole-aggregate upserts, cascade deletes, exdates, the always-present
 * built-in calendar, repository-owned timestamps, and the same preconditions and exceptions as the
 * Room implementation.
 *
 * Deterministic: event ids are 1, 2, 3, … and calendar ids 2, 3, 4, … (1 is the built-in calendar),
 * never reused, not even after [deleteAllData]; timestamps come from [clock], which by default is
 * fixed at [DEFAULT_INSTANT] — pass a [MutableClock] to see `updatedAt` move.
 *
 * Flows are backed by one [MutableStateFlow], so they emit the current value on collection and
 * conflate; equal consecutive values are dropped. Subscribe before writing (`runCurrent()`) if a test
 * needs to see every intermediate value.
 *
 * The range queries are deliberately **conservative**, as the contract allows: recurring events are
 * never pruned by their recurrence end, because that needs a `RecurrenceExpander`. Everything else
 * (date window with [EventRepository.ZONE_SKEW_DAYS], visible calendars only, reminders only) is
 * exact.
 *
 * Unlike the production repository it calls neither the widget updater nor the reminder scheduler.
 *
 * @param clock the source of `createdAt` / `updatedAt`.
 */
public class FakeEventRepository(
    private val clock: Clock = Clock.fixed(DEFAULT_INSTANT, ZoneOffset.UTC),
) : EventRepository {
    private data class Store(
        val calendars: Map<Long, EventCalendar> = mapOf(EventCalendar.DEFAULT_ID to EventCalendar.DEFAULT),
        val events: Map<Long, Event> = emptyMap(),
    )

    private val store = MutableStateFlow(Store())
    private val mutex = Mutex()
    private var nextEventId = 1L
    private var nextCalendarId = EventCalendar.DEFAULT_ID + 1

    /** Every stored event in [Event.LIST_ORDER], for assertions that do not want to collect a flow. */
    public val currentEvents: List<Event> get() =
        store.value.events.values
            .sortedWith(Event.LIST_ORDER)

    /** Every stored calendar ordered by id, for assertions that do not want to collect a flow. */
    public val currentCalendars: List<EventCalendar> get() =
        store.value.calendars.values
            .sortedBy { it.id }

    /**
     * Stores [events] in order with [upsertEvent] and returns them as stored (ids and timestamps
     * assigned). With `EventFixtures.all()` on a fresh repository the ids are 1..9.
     */
    public suspend fun seed(events: List<Event>): List<Event> = events.map { checkNotNull(getEvent(upsertEvent(it))) }

    /** Every calendar ordered by id; emits at once and after each calendar change. */
    override fun observeCalendars(): Flow<List<EventCalendar>> =
        store.map { s -> s.calendars.values.sortedBy { it.id } }.distinctUntilChanged()

    /** The stored calendar with [id], or `null`. */
    override suspend fun getCalendar(id: Long): EventCalendar? = store.value.calendars[id]

    /**
     * Inserts (id `0`, next id from 2) or replaces a calendar.
     *
     * @throws IllegalArgumentException for an unknown non-zero id, or a built-in calendar that is not `LOCAL`.
     */
    override suspend fun upsertCalendar(calendar: EventCalendar): Long =
        mutex.withLock {
            val current = store.value
            val id =
                if (calendar.id == EventCalendar.NEW_ID) {
                    nextCalendarId++
                } else {
                    require(calendar.id in current.calendars) { "No calendar with id ${calendar.id}" }
                    calendar.id
                }
            require(id != EventCalendar.DEFAULT_ID || calendar.source == CalendarSource.LOCAL) {
                "The built-in calendar must stay LOCAL"
            }
            store.value = current.copy(calendars = current.calendars + (id to calendar.copy(id = id)))
            id
        }

    /**
     * Deletes the calendar and its events; `false` if it does not exist.
     *
     * @throws IllegalArgumentException for the built-in calendar.
     */
    override suspend fun deleteCalendar(id: Long): Boolean =
        mutex.withLock {
            require(id != EventCalendar.DEFAULT_ID) { "The built-in calendar cannot be deleted" }
            val current = store.value
            if (id !in current.calendars) return@withLock false
            store.value =
                Store(
                    calendars = current.calendars - id,
                    events = current.events.filterValues { it.calendarId != id },
                )
            true
        }

    /** Every event, hidden calendars included, in [Event.LIST_ORDER]; emits at once and after each change. */
    override fun observeEvents(): Flow<List<Event>> =
        store.map { s -> s.events.values.sortedWith(Event.LIST_ORDER) }.distinctUntilChanged()

    /** The event with [id], or `null` while it does not exist; re-emits only when that event changes. */
    override fun observeEvent(id: Long): Flow<Event?> = store.map { it.events[id] }.distinctUntilChanged()

    /** The stored event with [id], or `null`. */
    override suspend fun getEvent(id: Long): Event? = store.value.events[id]

    /**
     * Events of visible calendars that start on or before `range.endInclusive + 2` days and, when not
     * recurring, end on or after `range.start − 2` days. **Recurring events are never pruned by their
     * recurrence end** (a superset, as the contract allows). Empty for an empty [range].
     */
    override fun observeAgendaCandidates(range: ClosedRange<LocalDate>): Flow<List<Event>> =
        store
            .map { s ->
                if (range.isEmpty()) {
                    emptyList()
                } else {
                    val lo = range.start.minusDays(EventRepository.ZONE_SKEW_DAYS)
                    val hi = range.endInclusive.plusDays(EventRepository.ZONE_SKEW_DAYS)
                    s
                        .visibleEvents()
                        .filter { !it.startDate.isAfter(hi) && (it.isRecurring || !it.endDate.isBefore(lo)) }
                        .sortedWith(Event.LIST_ORDER)
                }
            }.distinctUntilChanged()

    /**
     * Events of visible calendars with at least one reminder that are recurring (never pruned) or end on
     * or after `from − 2` days.
     */
    override suspend fun getReminderCandidates(from: LocalDate): List<Event> {
        val lo = from.minusDays(EventRepository.ZONE_SKEW_DAYS)
        return store.value
            .visibleEvents()
            .filter { it.reminders.isNotEmpty() && (it.isRecurring || !it.endDate.isBefore(lo)) }
            .sortedWith(Event.LIST_ORDER)
    }

    /**
     * Inserts (id `0`, next id from 1) or replaces the whole event, stamping the timestamps from the clock.
     *
     * @throws IllegalArgumentException for an unknown non-zero id, an unknown calendar, or a uid another
     *   event already has. Nothing is changed and no id is consumed.
     */
    override suspend fun upsertEvent(event: Event): Long =
        mutex.withLock {
            val current = store.value
            require(event.calendarId in current.calendars) { "No calendar with id ${event.calendarId}" }
            require(current.events.values.none { it.uid == event.uid && it.id != event.id }) {
                "Another event already has this uid"
            }
            val now = clock.instant()
            val stored =
                if (event.id == Event.NEW_ID) {
                    event.copy(id = nextEventId++, createdAt = now, updatedAt = now)
                } else {
                    val previous = requireNotNull(current.events[event.id]) { "No event with id ${event.id}" }
                    event.copy(createdAt = previous.createdAt, updatedAt = latest(now, previous.createdAt))
                }
            store.value = current.copy(events = current.events + (stored.id to stored))
            stored.id
        }

    /** Deletes the event; `false` if it does not exist. */
    override suspend fun deleteEvent(id: Long): Boolean =
        mutex.withLock {
            val current = store.value
            if (id !in current.events) return@withLock false
            store.value = current.copy(events = current.events - id)
            true
        }

    /**
     * Adds [date] to the event's exdates and bumps `updatedAt`; `false` if the event does not exist or
     * already has it.
     *
     * @throws IllegalArgumentException if the event is not recurring or [date] is before its start.
     */
    override suspend fun addExdate(
        eventId: Long,
        date: LocalDate,
    ): Boolean =
        mutex.withLock {
            val current = store.value
            val event = current.events[eventId] ?: return@withLock false
            require(event.isRecurring) { "Event $eventId is not recurring; delete it instead" }
            require(!date.isBefore(event.startDate)) { "Exdate $date is before the event start ${event.startDate}" }
            if (date in event.exdates) return@withLock false
            replace(current, event.copy(exdates = event.exdates + date))
            true
        }

    /** Removes [date] from the event's exdates and bumps `updatedAt`; `false` if there was nothing to remove. */
    override suspend fun removeExdate(
        eventId: Long,
        date: LocalDate,
    ): Boolean =
        mutex.withLock {
            val current = store.value
            val event = current.events[eventId] ?: return@withLock false
            if (date !in event.exdates) return@withLock false
            replace(current, event.copy(exdates = event.exdates - date))
            true
        }

    /** Drops every event and calendar and restores [EventCalendar.DEFAULT]. Ids keep counting. */
    override suspend fun deleteAllData() {
        mutex.withLock { store.value = Store() }
    }

    private fun replace(
        current: Store,
        event: Event,
    ) {
        val stamped = event.copy(updatedAt = latest(clock.instant(), event.createdAt))
        store.value = current.copy(events = current.events + (stamped.id to stamped))
    }

    // A test clock may be moved backwards; updatedAt must still not precede createdAt (Event invariant).
    private fun latest(
        now: Instant,
        createdAt: Instant,
    ): Instant = if (now.isBefore(createdAt)) createdAt else now

    private fun Store.visibleEvents(): List<Event> = events.values.filter { calendars.getValue(it.calendarId).visible }

    /** Well-known values. */
    public companion object {
        /** The instant of the default fixed clock: 2026-01-01T00:00:00Z. */
        public val DEFAULT_INSTANT: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
