package io.github.chrisjmendoza.yearal.core.scheduling.reminder

import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.Occurrence
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One reminder of one occurrence, resolved to the instant it should be delivered at.
 *
 * Carries **ids and times only**, never event text: it travels no further than the notification
 * builder, which reads the title from the [Event] the same recomputation already loaded
 * (CLAUDE.md rule 8).
 *
 * @property eventId the [Event.id] the reminder belongs to.
 * @property occurrenceDate the occurrence's own wall-clock start date ([Occurrence.occurrenceDate]),
 *   which together with [eventId] and [minutesBefore] identifies a reminder
 *   (`docs/contracts/Events.md` §2, [io.github.chrisjmendoza.yearal.core.domain.event.Reminder]).
 * @property minutesBefore the reminder's lead time in minutes, `≥ 0`.
 * @property triggerAt the instant the notification is due: [reference] minus [minutesBefore] minutes.
 * @property reference the occurrence's reference instant in the device zone — the resolved start for
 *   a timed occurrence, [ReminderPlanner.ALL_DAY_REMINDER_TIME] on the first date for an all-day one.
 * @property allDay `true` when the occurrence is all-day, so the notification says "All day" instead
 *   of a clock time.
 */
internal data class PendingReminder(
    val eventId: Long,
    val occurrenceDate: LocalDate,
    val minutesBefore: Int,
    val triggerAt: Instant,
    val reference: ZonedDateTime,
    val allDay: Boolean,
)

/**
 * Turns stored events into the [PendingReminder]s that are still ahead: the computation half of the
 * single next-alarm pattern of `docs/ARCHITECTURE.md` §3.2 "Reminders" (FEATURES E4).
 *
 * Pure and synchronous — no clock, no I/O, no Android — so every expected instant in its tests is one
 * that can be worked out by hand. [AlarmReminderScheduler] supplies "now", the device zone and the
 * candidate events, arms one alarm for the earliest result and posts the ones that are due.
 *
 * **Reference instants** (`docs/contracts/Events.md` §2, `Reminder`):
 *
 * - **Timed:** [Occurrence.start] in the device zone, so a zoned event keeps its own wall time and a
 *   floating one is read where the device is. Daylight-saving gaps and overlaps resolve by the frozen
 *   rule on [Occurrence] (a gap wall time moves later by the gap; an overlapping one is the earlier
 *   instant).
 * - **All-day:** [ALL_DAY_REMINDER_TIME] on the occurrence's **first** date, in the device zone. This
 *   app has no setting for it yet (`UserSettings` has no reminder-time field and
 *   `docs/contracts/Events.md` §6 puts one out of scope for 1.0), so the documented 09:00 default is
 *   a constant here and the only place it is defined.
 *
 * So `minutesBefore = 0` means "at the start" for a timed event and "at 09:00 on the day" for an
 * all-day one, and `1440` means "at 09:00 the day before" — exactly what `Reminder`'s KDoc promises.
 */
internal object ReminderPlanner {
    /**
     * When an all-day event's reminders are measured from, in the device zone: 09:00
     * (`docs/ARCHITECTURE.md` §3.2 "Reminders", "All-day reminders fire at a configurable local time,
     * 09:00 by default"). **Not yet configurable** — see the class KDoc.
     */
    val ALL_DAY_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * How many occurrences of one event the search may look at. A hard bound on the work per call
     * (`docs/contracts/Events.md` §5: callers never iterate the expander unboundedly); the search
     * normally stops after two, as soon as no later occurrence could produce an earlier instant.
     *
     * At most one occurrence per calendar date is examined, so the bound is reached only by a lead
     * time longer than 64 occurrence steps (a reminder over two months before a daily event). Such a
     * reminder may be armed late — it is armed as soon as an earlier step brings it into view — which
     * is why the bound is generous rather than tight.
     */
    const val MAX_OCCURRENCES_PER_EVENT: Int = 64

    /**
     * Every reminder of every event in [events] that is still ahead of [after], ordered by
     * [PendingReminder.triggerAt], then event id, then lead time.
     *
     * The result is exact at the front, which is all the caller needs. It is guaranteed to contain
     * **every** reminder whose trigger instant lies in `(after, now]` — so nothing that has come due
     * is missed, however many reminders and occurrences fell inside that span — **and** the earliest
     * reminder after [now], ties included. Reminders further out may be missing, because the per-event
     * walk stops once no later occurrence of that event can produce an instant that early.
     *
     * @param events the repository's reminder candidates
     *   ([io.github.chrisjmendoza.yearal.core.domain.event.EventRepository.getReminderCandidates]),
     *   already restricted to visible calendars. Events without reminders are ignored.
     * @param expander the real [RecurrenceExpander]; exdates and recurrence ends are its job.
     * @param deviceZone the zone floating occurrences and all-day reminder times are read in.
     * @param from the first occurrence date to consider — `today − EventRepository.ZONE_SKEW_DAYS`,
     *   because a zoned occurrence can be shown up to two days from its own date.
     * @param after exclusive lower bound on the trigger instant: reminders at or before it have been
     *   dealt with already ([AlarmReminderScheduler]'s high-water mark).
     * @param now the instant that divides "due" from "still ahead". Pass [Instant.MIN] to ask only
     *   for what is ahead.
     */
    fun pending(
        events: List<Event>,
        expander: RecurrenceExpander,
        deviceZone: ZoneId,
        from: LocalDate,
        after: Instant,
        now: Instant,
    ): List<PendingReminder> =
        events
            .flatMap { event -> pendingFor(event, expander, deviceZone, from, after, now) }
            .sortedWith(compareBy({ it.triggerAt }, { it.eventId }, { it.minutesBefore }))

    /**
     * The reminders of a single [event] after [after], walking its occurrences forward from [from].
     *
     * The walk stops once it has found a reminder **after** [now] and `start − longest lead time` of
     * the occurrence just seen has reached it. Occurrence references strictly increase (at most one
     * occurrence per date is examined), so every occurrence still to come has all its reminders
     * strictly later than that floor: none of them can be due, and none can be earlier than the one
     * already found. Until such a reminder exists the walk keeps going, which is what collects a
     * backlog of missed occurrences after a reboot.
     */
    private fun pendingFor(
        event: Event,
        expander: RecurrenceExpander,
        deviceZone: ZoneId,
        from: LocalDate,
        after: Instant,
        now: Instant,
    ): List<PendingReminder> {
        if (event.reminders.isEmpty()) return emptyList()
        val longestLead = Duration.ofMinutes(event.reminders.maxOf { it.minutesBefore }.toLong())
        val found = mutableListOf<PendingReminder>()
        var earliestAhead: Instant? = null
        var date = from
        var examined = 0
        while (examined < MAX_OCCURRENCES_PER_EVENT) {
            val occurrence = expander.nextOccurrence(event, date) ?: break
            examined++
            val reference = referenceOf(occurrence, deviceZone)
            val referenceInstant = reference.toInstant()
            for (reminder in event.reminders) {
                val triggerAt = referenceInstant.minus(Duration.ofMinutes(reminder.minutesBefore.toLong()))
                if (!triggerAt.isAfter(after)) continue
                found +=
                    PendingReminder(
                        eventId = event.id,
                        occurrenceDate = occurrence.occurrenceDate,
                        minutesBefore = reminder.minutesBefore,
                        triggerAt = triggerAt,
                        reference = reference,
                        allDay = occurrence.allDay,
                    )
                val ahead = earliestAhead
                if (triggerAt.isAfter(now) && (ahead == null || triggerAt.isBefore(ahead))) {
                    earliestAhead = triggerAt
                }
            }
            val ahead = earliestAhead
            if (ahead != null && !referenceInstant.minus(longestLead).isBefore(ahead)) break
            // `nextOccurrence` answers "the first occurrence on or after this date", so the only way
            // forward is the day after the one just seen.
            date = occurrence.occurrenceDate.plusDays(1)
        }
        return found
    }

    /** The instant an occurrence's reminders are measured from; see the class KDoc. */
    private fun referenceOf(
        occurrence: Occurrence,
        deviceZone: ZoneId,
    ): ZonedDateTime =
        if (occurrence.allDay) {
            occurrence.occurrenceDate.atTime(ALL_DAY_REMINDER_TIME).atZone(deviceZone)
        } else {
            occurrence.start(deviceZone)
        }
}
