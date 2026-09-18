package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.Occurrence
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceExpander
import java.time.LocalDate
import java.time.ZoneId

/**
 * A **scripted** [RecurrenceExpander]: it never evaluates a rule. A test says which occurrences a
 * recurring event has with [script], and this fake then applies the parts of the contract that do not
 * depend on the rule — the range test on [Occurrence.dates], exdate subtraction, ordering by
 * [Occurrence.startLocal], [nextOccurrence] and [recurrenceEndDate].
 *
 * A non-recurring event needs no script: its only occurrence is [Event.firstOccurrence]. **A recurring
 * event that was not scripted fails the test** with `IllegalStateException` rather than returning a
 * plausible-looking partial answer.
 *
 * It exists so that code *around* the expander (the agenda use case, the reminder computation, UI)
 * can be tested without the real one, and so that a test can stage cases the real one would need a
 * long rule for. It is not an oracle for the real implementation.
 */
public class FakeRecurrenceExpander : RecurrenceExpander {
    private class Script(
        val occurrences: List<Occurrence>,
        val unbounded: Boolean,
    )

    private val scripts = mutableMapOf<Long, Script>()
    private val unsupported = mutableSetOf<Recurrence>()

    /**
     * Declares every occurrence the event with [eventId] has, exdates **not** yet subtracted (the
     * fake subtracts them). Replaces an earlier script for the same event.
     *
     * @param occurrences in any order; each must carry [eventId].
     * @param unbounded `true` if the staged rule never ends, so that [recurrenceEndDate] is `null`.
     * @throws IllegalArgumentException if an occurrence belongs to another event.
     */
    public fun script(
        eventId: Long,
        occurrences: List<Occurrence>,
        unbounded: Boolean = false,
    ) {
        require(occurrences.all { it.eventId == eventId }) { "Every scripted occurrence must carry event id $eventId" }
        scripts[eventId] = Script(occurrences.sortedBy { it.startLocal }, unbounded)
    }

    /**
     * Convenience for the common case: the event's own [Event.firstOccurrence] repeated on each of
     * [startDates] (which should include the event's start date), keeping its time, length and zone.
     */
    public fun scriptDates(
        event: Event,
        startDates: List<LocalDate>,
        unbounded: Boolean = false,
    ) {
        val first = event.firstOccurrence()
        script(
            event.id,
            startDates.map { date ->
                val shift = date.toEpochDay() - first.occurrenceDate.toEpochDay()
                first.copy(startLocal = first.startLocal.plusDays(shift), endLocal = first.endLocal.plusDays(shift))
            },
            unbounded,
        )
    }

    /** Makes [supports] answer `false` for [recurrence], to stage an unsupported `RRULE`. */
    public fun markUnsupported(recurrence: Recurrence) {
        unsupported += recurrence
    }

    /**
     * The scripted occurrences (or the single own occurrence of a one-off or unsupported event) that touch
     * [range] as seen from [deviceZone], exdates removed, ordered by start.
     *
     * @throws IllegalStateException for a recurring event that was not scripted.
     */
    override fun expand(
        event: Event,
        range: ClosedRange<LocalDate>,
        deviceZone: ZoneId,
    ): List<Occurrence> {
        if (range.isEmpty()) return emptyList()
        return remaining(event).filter { occurrence ->
            val dates = occurrence.dates(deviceZone)
            dates.start <= range.endInclusive && dates.endInclusive >= range.start
        }
    }

    /**
     * The first remaining occurrence starting on or after [from], or `null`.
     *
     * @throws IllegalStateException for a recurring event that was not scripted.
     */
    override fun nextOccurrence(
        event: Event,
        from: LocalDate,
    ): Occurrence? = remaining(event).firstOrNull { !it.occurrenceDate.isBefore(from) }

    /**
     * The event's own end date for a one-off event; `null` for an unsupported rule or an `unbounded`
     * script; otherwise the last date any scripted occurrence touches, exdates ignored.
     *
     * @throws IllegalStateException for a recurring event that was not scripted.
     */
    override fun recurrenceEndDate(event: Event): LocalDate? {
        if (!event.isRecurring) return event.endDate
        if (!supports(event.recurrence)) return null
        val script = scriptOf(event)
        return if (script.unbounded) null else script.occurrences.maxOfOrNull { it.lastDate }
    }

    /** `true` unless [recurrence] was passed to [markUnsupported]. */
    override fun supports(recurrence: Recurrence): Boolean = recurrence !in unsupported

    /** All occurrences of [event] in order, exdates subtracted. */
    private fun remaining(event: Event): List<Occurrence> {
        val all =
            if (!event.isRecurring || !supports(event.recurrence)) {
                // Contract: an unsupported rule behaves as a single occurrence, the event's own start.
                listOf(event.firstOccurrence())
            } else {
                scriptOf(event).occurrences
            }
        return all.filter { it.occurrenceDate !in event.exdates }
    }

    private fun scriptOf(event: Event): Script =
        checkNotNull(scripts[event.id]) {
            "FakeRecurrenceExpander has no script for recurring event ${event.id}; call script() or scriptDates()"
        }
}
