package io.github.chrisjmendoza.yearal.core.domain.event

import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns an [Event] and its [Recurrence] into [Occurrence]s. The only place occurrence dates are
 * derived; there is no materialised occurrences table (`docs/ARCHITECTURE.md` §3.2).
 *
 * **This file is the contract only.** The implementation is ROADMAP M4 T3 and its oracle tests are
 * written by a different agent from this documentation and the specs cited, not from the
 * implementation (`docs/WORKFLOW.md` §6).
 *
 * ## Guarantees every implementation gives
 *
 * - **Pure and thread-safe:** no I/O, no clock, no mutable shared state, no logging of event content.
 *   The same arguments always give the same result. Callable from any thread; callers run it off the
 *   main thread.
 * - **Occurrence 1 is the event's own start**, [Event.firstOccurrence], for every recurrence shape.
 *   For [Recurrence.None] it is the only one. For [Recurrence.Gregorian] it is `DTSTART`
 *   (RFC 5545 §3.8.5.3) and the rule is evaluated in the Gregorian calendar on the event's own
 *   wall-clock time. For an [IfcRecurrence] the semantics are those documented on [IfcRecurrence]:
 *   each occurrence is built by constructing the `IfcDate` of the rule year or month in
 *   `:core:calendar` and converting it — O(1) per year, never by iterating days — so a
 *   [IfcRecurrence.YearlyOnDate] occurrence converts back to the same IFC month and day, Year Day
 *   is always December 31, Leap Day occurs only in leap years (2100 has none), and a common rule
 *   year follows the [LeapDayPolicy]: IFC June 28 (Gregorian June 17), nothing, or Sol 1 (June 18).
 * - **Shape:** every occurrence carries the event's [Event.id], zone and all-day flag, the anchor's
 *   time of day, and the anchor's nominal length (`endLocal − startLocal` is constant: whole days
 *   for all-day, [EventTiming.durationMinutes] wall-clock minutes for timed).
 * - **Nominal values:** [Occurrence.startLocal] is what the rule produced, even when that wall time
 *   falls in a daylight-saving gap or overlap. Resolution is [Occurrence.start]'s job, so a 02:30
 *   daily event is still *listed* at 02:30 on the gap day and *happens* at 03:30.
 * - **Exdates:** an occurrence whose [Occurrence.occurrenceDate] is in [Event.exdates] is never
 *   returned, by any function here. Exdates do not change how `COUNT` counts.
 * - **Year range:** occurrences whose [Occurrence.occurrenceDate] or [Occurrence.lastDate] lies after
 *   year 9999 do not exist, so every date returned converts with `IfcDate.from`.
 * - **Unsupported rules never throw.** A [Recurrence.Gregorian] text the implementation cannot
 *   evaluate (malformed, or using a part it does not support) behaves as if the event had exactly
 *   one occurrence, its own start, and [supports] returns `false` so the UI can flag it
 *   (`docs/holidays-and-import.md` §4.2).
 *
 * Spec: `docs/ARCHITECTURE.md` §3.2, §3.4, §6; `docs/calendar-spec.md` §7.7 "Recurrence";
 * `docs/holidays-and-import.md` §5; `docs/adr/0005-events-contract.md`.
 */
public interface RecurrenceExpander {
    /**
     * Every occurrence of [event] that is **shown on at least one date of [range]** when seen from
     * [deviceZone] — that is, whose [Occurrence.dates]`(deviceZone)` intersects [range] — minus
     * exdates, ordered by [Occurrence.startLocal] ascending.
     *
     * The test is on the dates the occurrence *touches*, not on its start: a three-day event that
     * began two days before [range] is returned, and so is a zoned event whose own date is outside
     * [range] but whose device-zone date is inside. An occurrence that touches several dates of the
     * range is returned once.
     *
     * An empty [range] gives an empty list. The work is bounded by the length of [range] and of the
     * event, never by the distance from the anchor; callers pass ranges of about a year at most.
     *
     * @param range inclusive device-zone dates.
     * @param deviceZone the zone floating occurrences are read in and all dates are reported in.
     */
    public fun expand(
        event: Event,
        range: ClosedRange<LocalDate>,
        deviceZone: ZoneId,
    ): List<Occurrence>

    /**
     * The first occurrence of [event], in [Occurrence.startLocal] order and after subtracting
     * exdates, whose [Occurrence.occurrenceDate] is **on or after** [from]; `null` if there is none
     * (the rule has ended, or nothing is left before year 9999).
     *
     * [from] is compared with the occurrence's *own* wall-clock date; no device zone is involved.
     * The next-alarm computation calls this with `today − `[EventRepository.ZONE_SKEW_DAYS], checks
     * the resolved instant itself, and asks again from the following date if that one has passed.
     */
    public fun nextOccurrence(
        event: Event,
        from: LocalDate,
    ): Occurrence?

    /**
     * The [Occurrence.lastDate] of the **last** occurrence of [event], ignoring exdates, or `null`
     * when the recurrence never ends (no `UNTIL`/`COUNT`, [RecurrenceEnd.Never]) — the value of the
     * `recurrence_until_epoch_day` pruning column. For [Recurrence.None] it is [Event.endDate]. For an
     * unsupported rule it is `null`, so the event is never pruned by mistake. A `COUNT` that would run
     * past year 9999 ends at the last occurrence that exists.
     */
    public fun recurrenceEndDate(event: Event): LocalDate?

    /**
     * `true` if this implementation can evaluate [recurrence]. Always `true` for [Recurrence.None]
     * and every [IfcRecurrence]; for [Recurrence.Gregorian] it depends on the rule text.
     */
    public fun supports(recurrence: Recurrence): Boolean
}
