package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import java.time.Instant
import java.time.LocalDate

/**
 * A user's event, as one aggregate: the `events` row of `docs/ARCHITECTURE.md` §3.2 together with its
 * `event_exdates` and `reminders` rows. [EventRepository] reads and writes it whole.
 *
 * **Dates are Gregorian** (CLAUDE.md rule 4). The only IFC data is inside an [IfcRecurrence]. Convert
 * with `IfcDate.from` for display; never compute a date here or in a feature (rule 1).
 *
 * **[toString] is redacted.** It never prints [title], [description] or [location], so an event that
 * ends up in a log line or an exception message leaks no content (rule 8). Do not work around it.
 *
 * ## Invariants (checked at construction; `copy` re-checks them)
 *
 * - [id] `≥ 0`, [calendarId] `> 0`, [uid] not blank.
 * - [title] at most [MAX_TITLE_LENGTH], [description] at most [MAX_DESCRIPTION_LENGTH], [location] at
 *   most [MAX_LOCATION_LENGTH] characters (`docs/security-and-privacy.md` §6.1). A blank title is
 *   allowed; the UI shows a localized "(No title)".
 * - The start and [endDate] lie in years [IfcDate.MIN_YEAR]..[IfcDate.MAX_YEAR], so every date the
 *   first occurrence touches converts with `IfcDate.from`.
 * - A non-recurring event has no [exdates]; no exdate is before [startDate].
 * - An [IfcRecurrence] is anchored on [startDate] ([IfcRecurrence.isAnchoredOn]) and an
 *   [RecurrenceEnd.Until] is not before it, so the event's own start is always occurrence 1.
 * - [updatedAt] is not before [createdAt].
 *
 * [category] is a label and is **not** cross-validated: by convention the editor creates
 * [EventCategory.OBSERVANCE] and [EventCategory.BIRTHDAY] events as all-day yearly events
 * (`docs/ARCHITECTURE.md` §3.2 "Scope cuts"), but the model accepts any combination.
 *
 * Spec: `docs/ARCHITECTURE.md` §3.2; FEATURES E1, E3, E5–E8, E10; `docs/contracts/Events.md`.
 *
 * @property id the row id; [NEW_ID] (`0`) for an event that has not been stored yet.
 *   [EventRepository.upsertEvent] assigns the real one.
 * @property uid globally unique and stable for the event's life (the `.ics` `UID`); unique within the
 *   repository. New events take it from an [EventUidGenerator].
 * @property calendarId the [EventCalendar.id] the event belongs to; defaults to the built-in local
 *   calendar, [EventCalendar.DEFAULT_ID].
 * @property title one line of user text; may be blank.
 * @property description free user text ("notes"); empty for none.
 * @property location free user text; empty for none.
 * @property colorArgb the event's own colour as `0xAARRGGBB`, or `null` to inherit
 *   [EventCalendar.colorArgb].
 * @property category what kind of entry this is.
 * @property timing all-day or timed start, duration and zone.
 * @property recurrence how the event repeats.
 * @property exdates own wall-clock start dates ([Occurrence.occurrenceDate]) of occurrences deleted
 *   with "delete this occurrence". A date that is not an occurrence has no effect. An exdate removes
 *   **every** occurrence starting on that date.
 * @property reminders when to notify; a set, so two reminders never share
 *   [Reminder.minutesBefore] (`UNIQUE(event_id, minutes_before)`).
 * @property createdAt when the event was first stored. **Owned by the repository**: whatever is
 *   passed to [EventRepository.upsertEvent] is ignored. [NOT_STORED] before the first write.
 * @property updatedAt when the event was last written; owned by the repository like [createdAt].
 * @throws IllegalArgumentException if an invariant above is violated.
 */
public data class Event(
    val id: Long = NEW_ID,
    val uid: String,
    val calendarId: Long = EventCalendar.DEFAULT_ID,
    val title: String,
    val description: String = "",
    val location: String = "",
    val colorArgb: Int? = null,
    val category: EventCategory = EventCategory.EVENT,
    val timing: EventTiming,
    val recurrence: Recurrence = Recurrence.None,
    val exdates: Set<LocalDate> = emptySet(),
    val reminders: Set<Reminder> = emptySet(),
    val createdAt: Instant = NOT_STORED,
    val updatedAt: Instant = NOT_STORED,
) {
    init {
        require(id >= 0) { "Event id must not be negative: $id" }
        require(calendarId > 0) { "Calendar id must be positive: $calendarId" }
        require(uid.isNotBlank()) { "Event uid must not be blank" }
        // Messages below state lengths and dates only, never the text itself (CLAUDE.md rule 8).
        require(title.length <= MAX_TITLE_LENGTH) { "Title longer than $MAX_TITLE_LENGTH characters" }
        require(description.length <= MAX_DESCRIPTION_LENGTH) {
            "Description longer than $MAX_DESCRIPTION_LENGTH characters"
        }
        require(location.length <= MAX_LOCATION_LENGTH) { "Location longer than $MAX_LOCATION_LENGTH characters" }
        require(timing.startDate.year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR) {
            "Event start year out of range: ${timing.startDate.year}"
        }
        require(endDate.year <= IfcDate.MAX_YEAR) { "Event end year out of range: ${endDate.year}" }
        require(!updatedAt.isBefore(createdAt)) { "updatedAt is before createdAt" }
        requireRecurrenceInvariants()
    }

    /** Local date the first occurrence starts on, in the event's own zone. */
    public val startDate: LocalDate get() = timing.startDate

    /**
     * Last own wall-clock date the **first** occurrence touches ([Occurrence.lastDate]); the
     * denormalised `end_epoch_day` range-index column. Equal to [startDate] for a one-day event.
     */
    public val endDate: LocalDate get() = firstOccurrence().lastDate

    /** `true` for an [EventTiming.AllDay] event. */
    public val isAllDay: Boolean get() = timing is EventTiming.AllDay

    /** `true` unless [recurrence] is [Recurrence.None]. */
    public val isRecurring: Boolean get() = recurrence != Recurrence.None

    /**
     * The event's own start as an [Occurrence]: the only occurrence of a non-recurring event, and
     * occurrence 1 of a recurring one. **Exdates are not applied** — a recurring event whose first
     * date is in [exdates] still returns it here; [RecurrenceExpander] is what subtracts exdates.
     */
    public fun firstOccurrence(): Occurrence =
        when (val t = timing) {
            is EventTiming.AllDay -> {
                Occurrence(
                    eventId = id,
                    startLocal = t.startDate.atStartOfDay(),
                    endLocal = t.startDate.plusDays(t.days.toLong()).atStartOfDay(),
                    zone = null,
                    allDay = true,
                )
            }

            is EventTiming.Timed -> {
                Occurrence(
                    eventId = id,
                    startLocal = t.start,
                    endLocal = t.start.plusMinutes(t.durationMinutes.toLong()),
                    zone = t.zone,
                    allDay = false,
                )
            }
        }

    /**
     * A redacted description: ids, category, timing and the kind of recurrence, and **never** the
     * title, description or location (CLAUDE.md rule 8).
     */
    override fun toString(): String =
        "Event(id=$id, calendarId=$calendarId, category=$category, timing=$timing, " +
            "recurrence=${recurrence::class.simpleName}, exdates=${exdates.size}, reminders=${reminders.size})"

    private fun requireRecurrenceInvariants() {
        require(exdates.all { !it.isBefore(startDate) }) { "An exdate is before the event start $startDate" }
        when (val r = recurrence) {
            Recurrence.None -> {
                require(exdates.isEmpty()) { "A non-recurring event cannot have exdates" }
            }

            is Recurrence.Gregorian -> {
                Unit
            }

            is IfcRecurrence -> {
                require(r.isAnchoredOn(startDate)) {
                    "Event start $startDate is not a position of its IFC rule ${r.toRuleText()}"
                }
                val end = r.end
                if (end is RecurrenceEnd.Until) {
                    require(!end.date.isBefore(startDate)) { "UNTIL ${end.date} is before the event start $startDate" }
                }
            }
        }
    }

    /** Limits and well-known values. */
    public companion object {
        /** The [id] of an event that has not been stored yet. */
        public const val NEW_ID: Long = 0L

        /** Longest [title], in characters (`docs/security-and-privacy.md` §6.1). */
        public const val MAX_TITLE_LENGTH: Int = 500

        /** Longest [description], in characters (`docs/security-and-privacy.md` §6.1). */
        public const val MAX_DESCRIPTION_LENGTH: Int = 10_000

        /** Longest [location], in characters: one line of text, capped like [MAX_TITLE_LENGTH]. */
        public const val MAX_LOCATION_LENGTH: Int = 500

        /** [createdAt] and [updatedAt] of an event the repository has not stamped yet. */
        public val NOT_STORED: Instant = Instant.EPOCH

        /**
         * The order event lists are returned in: [startDate], then all-day before timed, then start
         * minute, then [id]. It uses each event's **own** wall-clock values (no zone conversion), so a
         * database can produce it with `ORDER BY start_epoch_day, start_minute_of_day, id` (SQLite
         * sorts `NULL` first).
         */
        public val LIST_ORDER: Comparator<Event> =
            compareBy<Event> { it.startDate }
                .thenBy { it.timing.startMinuteOfDay ?: -1 }
                .thenBy { it.id }
    }
}

/**
 * What kind of entry an [Event] is (the `category` column; FEATURES E8, E10). Drives presentation
 * only; see [Event] for why it is not validated against the timing or recurrence.
 */
public enum class EventCategory {
    /** An ordinary event. The default. */
    EVENT,

    /**
     * A user-defined holiday: by convention a yearly all-day event, which is why user holidays need no
     * table of their own (`docs/ARCHITECTURE.md` §3.2 "Scope cuts").
     */
    OBSERVANCE,

    /** A birthday or anniversary; by convention yearly and all-day (FEATURES E10). */
    BIRTHDAY,
}

/**
 * A notification some minutes before an occurrence starts (FEATURES E4).
 *
 * For a timed event the reference instant is the occurrence's resolved start ([Occurrence.start]).
 * **For an all-day event it is the user's all-day reminder time on the first date, in the device
 * zone** — 09:00 by default (`docs/ARCHITECTURE.md` §3.2 "Reminders") — so `0` means "at 09:00 on the
 * day" and `1440` means "at 09:00 the day before".
 *
 * Reminders carry no id: within an event [minutesBefore] is unique, and intents identify a reminder
 * by event id, occurrence date and [minutesBefore] (ids only, CLAUDE.md rule 8).
 *
 * @property minutesBefore minutes before the reference instant, `≥ 0`; `0` is "at the time".
 * @throws IllegalArgumentException if [minutesBefore] is negative.
 */
public data class Reminder(
    val minutesBefore: Int,
) : Comparable<Reminder> {
    init {
        require(minutesBefore >= 0) { "Reminder minutesBefore must not be negative: $minutesBefore" }
    }

    /** Orders by [minutesBefore], closest to the event first. */
    override fun compareTo(other: Reminder): Int = minutesBefore.compareTo(other.minutesBefore)
}
