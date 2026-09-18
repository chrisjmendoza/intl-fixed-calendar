package io.github.chrisjmendoza.yearal.core.domain.event

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The store of the user's [Event]s and [EventCalendar]s. Implemented over Room 3 in `:core:data`;
 * features depend only on this interface (CLAUDE.md rule 10) and are tested with
 * `io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository`, which follows this contract.
 *
 * ## General rules
 *
 * - **Threading:** every `suspend` function is main-safe — the implementation moves its own I/O off
 *   the caller's thread. Every [Flow] is cold, emits the current value on collection and again after
 *   every change that could affect it, never completes and never throws for an empty store. A flow
 *   **may re-emit an equal value**; collectors that care use `distinctUntilChanged`.
 * - **Atomicity:** each write is all-or-nothing, including an event's exdates and reminders and a
 *   calendar's cascade. Concurrent writers are serialised.
 * - **Errors:** a broken precondition throws `IllegalArgumentException` and changes nothing. A missing
 *   target of a delete or an exdate change is not an error; it returns `false`. Model invariants are
 *   already enforced by [Event] and [EventCalendar] themselves.
 * - **Ids** are assigned by the repository, are positive and are never reused while the data lives.
 *   After [deleteAllData] numbering may start again, so ids must not be kept across it.
 * - **Timestamps** come from the repository's injected `java.time.Clock` (CLAUDE.md rule 2); the
 *   values on an [Event] being written are ignored.
 * - **The built-in calendar** [EventCalendar.DEFAULT_ID] always exists (see [deleteCalendar],
 *   [deleteAllData]).
 * - **Privacy:** implementations never log event content (CLAUDE.md rule 8).
 * - **After every successful write** the production implementation asks the widget updater and the
 *   [ReminderScheduler] to refresh (`docs/ARCHITECTURE.md` §3.2, §5). That is wiring inside
 *   `:core:data`, not observable here; callers must not do it themselves.
 *
 * Search (FEATURES E9) has no query here on purpose: in 1.0 it is an in-memory filter over
 * [observeEvents] in the ViewModel, so the fake and the database cannot disagree on case folding.
 *
 * Spec: `docs/ARCHITECTURE.md` §3.2, §3.4; FEATURES E1, E8, E9, W6; `docs/contracts/Events.md`.
 */
public interface EventRepository {
    /** Every calendar, ordered by [EventCalendar.id]; never empty, because the built-in one exists. */
    public fun observeCalendars(): Flow<List<EventCalendar>>

    /** The calendar with [id], or `null` if there is none. */
    public suspend fun getCalendar(id: Long): EventCalendar?

    /**
     * Inserts [calendar] when its id is [EventCalendar.NEW_ID], otherwise replaces the stored calendar
     * with that id. Returns the id it is stored under.
     *
     * @throws IllegalArgumentException if the id is not [EventCalendar.NEW_ID] and no such calendar
     *   exists, or if the built-in calendar would stop being [CalendarSource.LOCAL].
     */
    public suspend fun upsertCalendar(calendar: EventCalendar): Long

    /**
     * Deletes the calendar **and every event in it** (with their exdates and reminders). Returns
     * `false` if there is no such calendar.
     *
     * @throws IllegalArgumentException for [EventCalendar.DEFAULT_ID]: the built-in calendar cannot be
     *   deleted (hide it, or use [deleteAllData]).
     */
    public suspend fun deleteCalendar(id: Long): Boolean

    /**
     * Every event of every calendar, **visible or not**, in [Event.LIST_ORDER] — the source of the
     * event list and of search. Filter by [EventCalendar.visible] with [observeCalendars] if needed.
     */
    public fun observeEvents(): Flow<List<Event>>

    /** The event with [id], re-emitted when it changes; `null` while it does not exist (or once deleted). */
    public fun observeEvent(id: Long): Flow<Event?>

    /** The event with [id] right now, or `null` if there is none. */
    public suspend fun getEvent(id: Long): Event?

    /**
     * Events that **might** have an occurrence shown on a date of [range] in some device zone: the
     * candidate query of `docs/ARCHITECTURE.md` §3.4. Only events of [EventCalendar.visible]
     * calendars, in [Event.LIST_ORDER].
     *
     * The result is a **superset**: it must contain every event with an occurrence touching [range]
     * and may contain others. Callers decide exactly with [RecurrenceExpander.expand] and
     * [Occurrence.dates]. With `lo = range.start − `[ZONE_SKEW_DAYS] and `hi = range.endInclusive +
     * `[ZONE_SKEW_DAYS], the database selects
     *
     * - non-recurring events with `startDate ≤ hi` and `endDate ≥ lo`;
     * - recurring events with `startDate ≤ hi` and a stored recurrence end
     *   ([RecurrenceExpander.recurrenceEndDate], the `recurrence_until_epoch_day` column) that is
     *   `null` or `≥ lo`.
     *
     * An empty [range] (`start > endInclusive`) yields an empty list. This is the one query behind
     * the month grid (28–29 days), the Year view's presence bitmap (one year), Day detail and Today
     * (one day).
     */
    public fun observeAgendaCandidates(range: ClosedRange<LocalDate>): Flow<List<Event>>

    /**
     * Events that have at least one [Reminder], belong to a visible calendar, and might still start
     * on or after [from]: what the next-alarm computation of `docs/ARCHITECTURE.md` §3.2 "Reminders"
     * iterates. A superset in the same sense as [observeAgendaCandidates]: non-recurring events with
     * `endDate ≥ from − `[ZONE_SKEW_DAYS], and recurring events whose stored recurrence end is `null`
     * or `≥ from − `[ZONE_SKEW_DAYS]. In [Event.LIST_ORDER].
     */
    public suspend fun getReminderCandidates(from: LocalDate): List<Event>

    /**
     * Writes [event] whole — the row, its [Event.exdates] and its [Event.reminders] replace whatever
     * was stored — and returns the id it is stored under.
     *
     * - [Event.id] equal to [Event.NEW_ID]: inserts, assigns the next id, and sets both timestamps to
     *   the repository clock's instant.
     * - Any other id: replaces that event, keeps its stored [Event.createdAt] and sets
     *   [Event.updatedAt] to the clock's instant (or to `createdAt`, should the clock have been moved
     *   back before it). "Edit all occurrences" is this call; stale exdates are the caller's to drop
     *   when the rule or the start changes.
     *
     * @throws IllegalArgumentException if the id is not [Event.NEW_ID] and no such event exists, if
     *   [Event.calendarId] names no calendar, or if another event already has [Event.uid].
     */
    public suspend fun upsertEvent(event: Event): Long

    /** Deletes the event with its exdates and reminders ("delete all"). `false` if there is none. */
    public suspend fun deleteEvent(id: Long): Boolean

    /**
     * "Delete this occurrence": adds [date] — the occurrence's [Occurrence.occurrenceDate], **not**
     * the device-zone date it is shown on — to the event's exdates and bumps [Event.updatedAt].
     * Returns `true` if it was added; `false` if the event does not exist or already has it.
     *
     * Deleting the only occurrence of a non-recurring event is [deleteEvent], not an exdate.
     *
     * @throws IllegalArgumentException if the event is not recurring or [date] is before its start
     *   (the invariants of [Event]).
     */
    public suspend fun addExdate(
        eventId: Long,
        date: LocalDate,
    ): Boolean

    /**
     * Undoes [addExdate]: removes [date] from the event's exdates and bumps [Event.updatedAt].
     * Returns `true` if it was removed; `false` if the event does not exist or has no such exdate.
     */
    public suspend fun removeExdate(
        eventId: Long,
        date: LocalDate,
    ): Boolean

    /**
     * The storage half of "Delete all data" (FEATURES W6, `docs/security-and-privacy.md` §2.4):
     * removes every event, exdate, reminder and calendar, then restores the built-in calendar as
     * [EventCalendar.DEFAULT]. Settings, caches and alarms are cleared by the caller of the action,
     * not here.
     */
    public suspend fun deleteAllData()

    /** Constants of the range queries. */
    public companion object {
        /**
         * Days a range query is widened by on both sides. A zoned occurrence is shown on a device-zone
         * date up to **two** days from its own date: zone offsets span UTC−12..UTC+14, 26 hours, so
         * 00:30 on the 3rd in `Pacific/Kiritimati` is 23:30 on the 1st in `Pacific/Pago_Pago`.
         */
        public const val ZONE_SKEW_DAYS: Long = 2L
    }
}
