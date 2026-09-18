package io.github.chrisjmendoza.yearal.core.domain.event

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One happening of an [Event]: the event itself for a non-recurring event, or one result of
 * [RecurrenceExpander]. It holds **nominal wall-clock** values exactly as the rule produced them;
 * instants and device-zone dates are derived on demand by [start], [end] and [dates], which are the
 * single definition of daylight-saving resolution and of day bucketing for the whole app.
 *
 * ## Resolution rules (frozen)
 *
 * - A **floating** occurrence ([zone] `null`) is read in the device zone passed in; a zoned one is
 *   read in its [zone] and then shown in the device zone.
 * - A wall time that **does not exist** (a spring-forward gap) resolves to the instant it would have
 *   had with the offset before the gap, i.e. it moves later by the length of the gap (02:30 in a
 *   01:59→03:00 gap is 03:30). A wall time that **exists twice** (a fall-back overlap) resolves to
 *   the **earlier** instant. This is `ZonedDateTime.ofLocal(local, zone, null)` and also the RFC 5545
 *   §3.3.5 rule.
 * - [endLocal] is **exclusive** and resolved the same way; if resolution would put the end before
 *   the start (start inside a gap, nominal end just after it), the end is the start.
 * - An **all-day** occurrence names calendar dates. Its [dates] are never shifted between zones; its
 *   [start] and [end] are the device-zone starts of those days (`atStartOfDay`, which is correct even
 *   where daylight saving removes 00:00 — `docs/calendar-spec.md` §7.8).
 *
 * Everything tied to real life uses these Gregorian values and the actual weekday, never an IFC
 * nominal weekday (CLAUDE.md rule 3). No epoch-millisecond arithmetic is involved (rule 2).
 *
 * Spec: `docs/ARCHITECTURE.md` §3.2 "Time zones"; `docs/adr/0005-events-contract.md` decisions 3–5.
 *
 * @property eventId the [Event.id] this occurrence belongs to; `0` only for a not-yet-stored event.
 * @property startLocal nominal wall-clock start, in [zone] (or floating). For an all-day occurrence,
 *   midnight at the start of the first date.
 * @property endLocal nominal wall-clock end, **exclusive**, never before [startLocal]. For an all-day
 *   occurrence, midnight at the start of the day *after* the last date.
 * @property zone the zone [startLocal] and [endLocal] are fixed in, or `null` for floating. Always
 *   `null` when [allDay].
 * @property allDay `true` for an occurrence of an [EventTiming.AllDay] event.
 * @throws IllegalArgumentException if [eventId] is negative, [endLocal] is before [startLocal], or an
 *   all-day occurrence has a zone, a time of day other than midnight, or no whole day.
 */
public data class Occurrence(
    val eventId: Long,
    val startLocal: LocalDateTime,
    val endLocal: LocalDateTime,
    val zone: ZoneId?,
    val allDay: Boolean,
) {
    init {
        require(eventId >= 0) { "Event id must not be negative: $eventId" }
        require(!endLocal.isBefore(startLocal)) { "Occurrence end $endLocal is before its start $startLocal" }
        if (allDay) {
            require(zone == null) { "An all-day occurrence is always floating" }
            require(startLocal.toLocalTime() == LocalTime.MIDNIGHT && endLocal.toLocalTime() == LocalTime.MIDNIGHT) {
                "An all-day occurrence starts and ends at midnight"
            }
            require(endLocal.isAfter(startLocal)) { "An all-day occurrence covers at least one day" }
        }
    }

    /**
     * The occurrence's own wall-clock start date. **This is the key an exdate is matched against**
     * ([Event.exdates], `event_exdates.epoch_day`) and the date an IFC rule or an `UNTIL` is
     * evaluated on. It is not necessarily the date the occurrence is shown on: use [dates] for that.
     */
    public val occurrenceDate: LocalDate get() = startLocal.toLocalDate()

    /**
     * The last own wall-clock date the occurrence touches: the date of the last minute before
     * [endLocal], or [occurrenceDate] for a zero-length occurrence. An occurrence ending exactly at
     * midnight does not touch the next day. For the first occurrence this is `end_epoch_day`.
     */
    public val lastDate: LocalDate
        get() = if (endLocal.isAfter(startLocal)) endLocal.minusNanos(1).toLocalDate() else occurrenceDate

    /** The resolved start as seen in [deviceZone] (see the resolution rules on [Occurrence]). */
    public fun start(deviceZone: ZoneId): ZonedDateTime =
        if (allDay) occurrenceDate.atStartOfDay(deviceZone) else resolve(startLocal, deviceZone)

    /**
     * The resolved **exclusive** end as seen in [deviceZone]; never before [start]. For an all-day
     * occurrence, the start of the day after its last date.
     */
    public fun end(deviceZone: ZoneId): ZonedDateTime {
        if (allDay) return endLocal.toLocalDate().atStartOfDay(deviceZone)
        val start = start(deviceZone)
        val end = resolve(endLocal, deviceZone)
        return if (end.isBefore(start)) start else end
    }

    /**
     * The device-zone calendar dates this occurrence is shown on — **the bucketing rule** of
     * `docs/ARCHITECTURE.md` §3.4 step 3. The range is never empty.
     *
     * All-day: [occurrenceDate]..[lastDate], untouched by [deviceZone]. Timed: from the date of
     * [start] to the date of the last instant before [end] (so an event ending at 00:00 is not on the
     * next day), or just the start date for a zero-length occurrence. A zoned occurrence can land on a
     * different date than its own [occurrenceDate], up to two days away between the extreme zones.
     */
    public fun dates(deviceZone: ZoneId): ClosedRange<LocalDate> {
        if (allDay) return occurrenceDate..lastDate
        val start = start(deviceZone)
        val end = end(deviceZone)
        val first = start.toLocalDate()
        val last = if (end.isAfter(start)) end.minusNanos(1).toLocalDate() else first
        return first..last
    }

    private fun resolve(
        local: LocalDateTime,
        deviceZone: ZoneId,
    ): ZonedDateTime = ZonedDateTime.ofLocal(local, zone ?: deviceZone, null).withZoneSameInstant(deviceZone)
}
