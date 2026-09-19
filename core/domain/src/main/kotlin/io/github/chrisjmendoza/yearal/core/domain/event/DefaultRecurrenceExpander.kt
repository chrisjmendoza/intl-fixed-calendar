package io.github.chrisjmendoza.yearal.core.domain.event

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.Freq
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The production [RecurrenceExpander]: IFC rules by direct construction in `:core:calendar`, Gregorian
 * `RRULE`s through `lib-recur` (`docs/ARCHITECTURE.md` §1 and §3.2, `docs/adr/0005-events-contract.md`
 * decision 11).
 *
 * It is a plain value with a no-argument constructor: stateless, pure, thread-safe, and safe to share
 * as a singleton. It holds no clock, does no I/O and logs nothing (CLAUDE.md rules 2 and 8).
 *
 * ## How the rules are evaluated
 *
 * - An IFC rule is evaluated on **rule positions**, not on days: rule year `anchorYear + k × interval`
 *   for the yearly rules, IFC month index `year × 13 + monthNumber − 1` stepped by `interval` for
 *   [IfcRecurrence.MonthlyOnDay]. Each position is turned into one `IfcDate` — `IfcDate.Regular`,
 *   [IfcDate.YearDay] or [IfcDate.LeapDay] — and converted, so a position costs O(1), century years
 *   are right by construction (2100 has no Leap Day), and no day is ever iterated. A query fast-forwards
 *   straight to the first position its range can touch, so the work never depends on how far the range
 *   is from the anchor.
 * - A Gregorian rule is evaluated by `lib-recur` with the event's start as `DTSTART`, fast-forwarded to
 *   the range. `lib-recur` keeps producing instances after year 9999; this class stops there
 *   (`docs/calendar-spec.md` §7.1).
 *
 * ## What it treats as unsupported ([supports] `== false`)
 *
 * A `RRULE` that `lib-recur` cannot parse, and one whose instances cannot be represented as
 * [Occurrence]s because **every occurrence keeps the anchor's time of day** ([RecurrenceExpander]
 * "Shape"): `FREQ=HOURLY`, `MINUTELY` and `SECONDLY`, and any rule carrying `BYHOUR`, `BYMINUTE` or
 * `BYSECOND`. Those repeat within a day, which this model cannot express, so collapsing them would
 * silently change their meaning. Such an event has exactly one occurrence, its own start, and nothing
 * here ever throws because of rule text (`docs/holidays-and-import.md` §4.2).
 *
 * Spec: `docs/contracts/Events.md` §2 "IFC recurrence semantics", §4 and §5;
 * `docs/adr/0005-events-contract.md` decisions 2, 3, 5, 6 and 11; `docs/calendar-spec.md` §7.7.
 */
public class DefaultRecurrenceExpander : RecurrenceExpander {
    /**
     * Every occurrence of [event] shown on at least one date of [range] in [deviceZone], exdates
     * removed, ordered by [Occurrence.startLocal]; an empty list for an empty [range].
     *
     * The candidate window is [range] widened by the event's own length and by
     * [EventRepository.ZONE_SKEW_DAYS] on both sides, so a multi-day occurrence that began before the
     * range and a zoned occurrence whose own date is outside it are both found; membership is then
     * decided exactly by [Occurrence.dates]. At most [MAX_OCCURRENCES_PER_CALL] occurrences are
     * returned.
     */
    override fun expand(
        event: Event,
        range: ClosedRange<LocalDate>,
        deviceZone: ZoneId,
    ): List<Occurrence> {
        if (range.isEmpty()) return emptyList()
        val span = spanDays(event)
        val windowStart = shiftClamped(range.start, -(span + EventRepository.ZONE_SKEW_DAYS))
        val windowEnd = shiftClamped(range.endInclusive, EventRepository.ZONE_SKEW_DAYS)
        val found = mutableListOf<Occurrence>()
        var steps = 0
        for (occurrence in occurrencesFrom(event, windowStart)) {
            if (occurrence.occurrenceDate.isAfter(windowEnd)) break
            // The window already bounds this loop; the step cap is the guard against an event whose own
            // length makes the window span millennia.
            if (++steps > MAX_OCCURRENCES_PER_CALL) break
            if (occurrence.occurrenceDate in event.exdates) continue
            val dates = occurrence.dates(deviceZone)
            if (!dates.start.isAfter(range.endInclusive) && !dates.endInclusive.isBefore(range.start)) {
                found += occurrence
            }
        }
        return found
    }

    /**
     * The first occurrence of [event] whose [Occurrence.occurrenceDate] is on or after [from] and is
     * not an exdate, or `null` when the rule has ended or nothing is left inside years 1..9999.
     *
     * Returns `null` rather than searching without end if more than [MAX_OCCURRENCES_PER_CALL]
     * consecutive occurrences are excluded.
     */
    override fun nextOccurrence(
        event: Event,
        from: LocalDate,
    ): Occurrence? {
        var steps = 0
        for (occurrence in occurrencesFrom(event, from)) {
            if (++steps > MAX_OCCURRENCES_PER_CALL) return null
            if (occurrence.occurrenceDate !in event.exdates) return occurrence
        }
        return null
    }

    /**
     * The [Occurrence.lastDate] of the last occurrence of [event] — the
     * `recurrence_until_epoch_day` pruning column — or `null` when the recurrence never ends, when the
     * rule is unsupported, or when the last occurrence cannot be found within
     * [MAX_OCCURRENCES_PER_CALL] steps. `null` always means "do not prune", which is the safe answer;
     * a value that is too early would hide the event, so one is never guessed.
     *
     * Exdates are ignored, [Event.endDate] is the answer for [Recurrence.None], and a `COUNT` that
     * would run past year 9999 ends at the last occurrence that exists.
     */
    override fun recurrenceEndDate(event: Event): LocalDate? =
        when (val recurrence = event.recurrence) {
            Recurrence.None -> {
                event.endDate
            }

            is IfcRecurrence -> {
                ifcEndDate(event, recurrence)
            }

            is Recurrence.Gregorian -> {
                val rule = supportedRule(recurrence.rrule)
                if (rule == null || rule.isInfinite) null else gregorianEndDate(event, rule)
            }
        }

    /**
     * `true` for [Recurrence.None] and every [IfcRecurrence]; for a [Recurrence.Gregorian] `true` only
     * if `lib-recur` parses the text, the rule can be iterated, and its instances fit one-per-day with
     * the anchor's time of day (see the class documentation).
     */
    override fun supports(recurrence: Recurrence): Boolean =
        when (recurrence) {
            Recurrence.None -> true
            is IfcRecurrence -> true
            is Recurrence.Gregorian -> supportedRule(recurrence.rrule) != null
        }

    // --- the occurrence stream --------------------------------------------------------------------

    /**
     * Every occurrence of [event] with [Occurrence.occurrenceDate] on or after [from], in ascending
     * order, **without** subtracting exdates (`COUNT` counts excluded occurrences, ADR 0005 decision 2).
     * Lazy, so a caller that stops early does no further work.
     */
    private fun occurrencesFrom(
        event: Event,
        from: LocalDate,
    ): Sequence<Occurrence> =
        when (val recurrence = event.recurrence) {
            Recurrence.None -> {
                ownOccurrence(event, from)
            }

            is IfcRecurrence -> {
                ifcOccurrences(event, recurrence, from)
            }

            is Recurrence.Gregorian -> {
                val rule = supportedRule(recurrence.rrule)
                if (rule == null) ownOccurrence(event, from) else gregorianOccurrences(event, rule, from)
            }
        }

    /** The event's own start, the only occurrence of a one-off event and of an unsupported rule. */
    private fun ownOccurrence(
        event: Event,
        from: LocalDate,
    ): Sequence<Occurrence> {
        val occurrence = event.firstOccurrence()
        return if (occurrence.occurrenceDate.isBefore(from)) emptySequence() else sequenceOf(occurrence)
    }

    /**
     * The occurrence that starts on [date]: the anchor's time of day, nominal length, zone and all-day
     * flag, moved to [date]. For the anchor's own date this reproduces [Event.firstOccurrence].
     */
    private fun occurrenceOn(
        event: Event,
        date: LocalDate,
    ): Occurrence =
        when (val timing = event.timing) {
            is EventTiming.AllDay -> {
                Occurrence(
                    eventId = event.id,
                    startLocal = date.atStartOfDay(),
                    endLocal = date.plusDays(timing.days.toLong()).atStartOfDay(),
                    zone = null,
                    allDay = true,
                )
            }

            is EventTiming.Timed -> {
                val start = date.atTime(timing.start.toLocalTime())
                Occurrence(
                    eventId = event.id,
                    startLocal = start,
                    endLocal = start.plusMinutes(timing.durationMinutes.toLong()),
                    zone = timing.zone,
                    allDay = false,
                )
            }
        }

    // --- IFC rules ---------------------------------------------------------------------------------

    private fun ifcOccurrences(
        event: Event,
        rule: IfcRecurrence,
        from: LocalDate,
    ): Sequence<Occurrence> {
        val positions = IfcPositions(rule, event.startDate)
        if (positions.yieldsNothing) return emptySequence()
        val until = (rule.end as? RecurrenceEnd.Until)?.date
        val count = (rule.end as? RecurrenceEnd.Count)?.count?.toLong()
        val firstIndex = positions.firstIndexAtOrAfter(from)
        val alreadyProduced = positions.producedBefore(firstIndex)
        if (count != null && alreadyProduced >= count) return emptySequence()
        return sequence {
            var index = firstIndex
            var produced = alreadyProduced
            while (index <= positions.maxIndex) {
                if (count != null && produced >= count) return@sequence
                val date = positions.dateAt(index)
                if (date != null) {
                    if (until != null && date.isAfter(until)) return@sequence
                    produced++
                    val occurrence = occurrenceOn(event, date)
                    // An occurrence reaching past year 9999 does not exist, and neither does any later one.
                    if (occurrence.lastDate.year > IfcDate.MAX_YEAR) return@sequence
                    if (!date.isBefore(from)) yield(occurrence)
                }
                index++
            }
        }
    }

    private fun ifcEndDate(
        event: Event,
        rule: IfcRecurrence,
    ): LocalDate? {
        val positions = IfcPositions(rule, event.startDate)
        if (positions.yieldsNothing) return event.endDate
        val until = (rule.end as? RecurrenceEnd.Until)?.date
        val upperBound =
            when (val end = rule.end) {
                RecurrenceEnd.Never -> return null
                is RecurrenceEnd.Until -> positions.lastIndexAtOrBefore(end.date)
                is RecurrenceEnd.Count -> positions.indexOfProduced(end.count - 1L)
            }
        // The bound above is the last *candidate* position: it can name a rule year that produces nothing
        // (SKIP), one whose date overshoots UNTIL, or one reaching past year 9999. Walk back to the last
        // position that really exists; the SKIP pattern repeats within 400 steps, so this is short.
        var index = minOf(upperBound, positions.maxIndex)
        var steps = 0
        while (index >= 0) {
            if (++steps > MAX_OCCURRENCES_PER_CALL) return null
            val date = positions.dateAt(index)
            if (date != null && (until == null || !date.isAfter(until))) {
                val occurrence = occurrenceOn(event, date)
                if (occurrence.lastDate.year <= IfcDate.MAX_YEAR) return occurrence.lastDate
            }
            index--
        }
        return event.endDate
    }

    /**
     * The positions of one [IfcRecurrence] as an index sequence counted from the event's anchor:
     * index `k` is rule year `anchorYear + k × interval` for the yearly rules and IFC month index
     * `anchorMonthIndex + k × interval` for [IfcRecurrence.MonthlyOnDay]. Every index maps to at most
     * one date, and indices are ordered exactly as their dates are, which is what makes the fast-forward
     * and the `COUNT` bookkeeping pure arithmetic.
     */
    private class IfcPositions(
        private val rule: IfcRecurrence,
        anchorDate: LocalDate,
    ) {
        private val monthly = rule is IfcRecurrence.MonthlyOnDay
        private val interval: Long = rule.interval.toLong()
        private val anchorPosition: Long = if (monthly) monthIndexOf(anchorDate) else anchorDate.year.toLong()
        private val lastPosition: Long = if (monthly) MAX_MONTH_INDEX else IfcDate.MAX_YEAR.toLong()

        /** Highest index whose position is still inside years 1..9999. */
        val maxIndex: Long = (lastPosition - anchorPosition) / interval

        /**
         * Offsets, inside one repeat of the produced/skipped pattern, of the indices that yield an
         * occurrence. Only [LeapDayPolicy.SKIP] skips anything: its rule years produce only when they are
         * leap years, and leap years repeat every 400 years, so with a step of `interval` years the
         * pattern closes after `400 / gcd(interval, 400)` indices. Every other rule produces at every
         * index, which is the degenerate period 1.
         */
        private val period: Int
        private val producedOffsets: IntArray

        init {
            val leapDay = (rule as? IfcRecurrence.YearlyOnIntercalary)?.day as? IntercalaryDay.LeapDay
            val skips = leapDay?.commonYearPolicy == LeapDayPolicy.SKIP
            if (!skips) {
                period = 1
                producedOffsets = intArrayOf(0)
            } else {
                period = (LEAP_CYCLE_YEARS / gcd(interval, LEAP_CYCLE_YEARS)).toInt()
                val offsets = mutableListOf<Int>()
                for (offset in 0 until period) {
                    if (Year.isLeap(anchorPosition + offset * interval)) offsets += offset
                }
                producedOffsets = offsets.toIntArray()
            }
        }

        /** `true` if the rule can never produce anything (no rule year of a `SKIP` rule is a leap year). */
        val yieldsNothing: Boolean get() = producedOffsets.isEmpty() || maxIndex < 0

        /** The Gregorian date of index [index], or `null` when that position produces nothing. */
        fun dateAt(index: Long): LocalDate? {
            val position = anchorPosition + index * interval
            return when (rule) {
                is IfcRecurrence.YearlyOnDate -> {
                    IfcDate.Regular(position.toInt(), rule.month, rule.day).toLocalDate()
                }

                is IfcRecurrence.YearlyOnIntercalary -> {
                    when (val day = rule.day) {
                        IntercalaryDay.YearDay -> IfcDate.YearDay(position.toInt()).toLocalDate()
                        is IntercalaryDay.LeapDay -> leapDayDate(position, day.commonYearPolicy)
                    }
                }

                is IfcRecurrence.MonthlyOnDay -> {
                    IfcDate
                        .Regular(
                            (position / IfcMonth.MONTHS_PER_YEAR).toInt(),
                            IfcMonth.of((position % IfcMonth.MONTHS_PER_YEAR).toInt() + 1),
                            rule.day,
                        ).toLocalDate()
                }
            }
        }

        /** The lowest index whose date can be on or after [date]. */
        fun firstIndexAtOrAfter(date: LocalDate): Long {
            if (date.year < IfcDate.MIN_YEAR) return 0
            if (date.year > IfcDate.MAX_YEAR) return maxIndex + 1
            val gap = targetPosition(date) - anchorPosition
            // Ceiling division: the first whole step that reaches the target position.
            return if (gap <= 0) 0 else -Math.floorDiv(-gap, interval)
        }

        /** The highest index whose date can be on or before [date]; `-1` when [date] precedes the anchor. */
        fun lastIndexAtOrBefore(date: LocalDate): Long {
            if (date.year > IfcDate.MAX_YEAR) return maxIndex
            if (date.year < IfcDate.MIN_YEAR) return -1
            val gap = targetPosition(date) - anchorPosition
            return if (gap < 0) -1 else minOf(gap / interval, maxIndex)
        }

        /** How many occurrences indices `0 until` [index] produce (what a `COUNT` has already spent). */
        fun producedBefore(index: Long): Long {
            if (index <= 0) return 0
            val rest = (index % period).toInt()
            return index / period * producedOffsets.size + producedOffsets.count { it < rest }
        }

        /** The index of occurrence number [ordinal], counted from `0` at the anchor. */
        fun indexOfProduced(ordinal: Long): Long {
            val perPeriod = producedOffsets.size.toLong()
            return ordinal / perPeriod * period + producedOffsets[(ordinal % perPeriod).toInt()]
        }

        /**
         * IFC June 29 in a leap [year], and in a common one what [policy] says: IFC June 28 (the same
         * Gregorian June 17), nothing, or Sol 1 (`docs/contracts/Events.md` §2; FEATURES E6).
         */
        private fun leapDayDate(
            year: Long,
            policy: LeapDayPolicy,
        ): LocalDate? {
            if (Year.isLeap(year)) return IfcDate.LeapDay(year.toInt()).toLocalDate()
            return when (policy) {
                LeapDayPolicy.JUNE_28 -> {
                    IfcDate.Regular(year.toInt(), IfcMonth.JUNE, IfcMonth.DAYS_PER_MONTH).toLocalDate()
                }

                LeapDayPolicy.SKIP -> {
                    null
                }

                LeapDayPolicy.SOL_1 -> {
                    IfcDate.Regular(year.toInt(), IfcMonth.SOL, 1).toLocalDate()
                }
            }
        }

        // The position a date belongs to. A monthly rule compares IFC month indices, so that an occurrence
        // and the date share an index exactly when they share an IFC month; the intercalary days report the
        // month they follow, which keeps the ordering right.
        private fun targetPosition(date: LocalDate): Long = if (monthly) monthIndexOf(date) else date.year.toLong()

        private companion object {
            /** Highest IFC month index, December of year 9999. */
            const val MAX_MONTH_INDEX: Long =
                IfcDate.MAX_YEAR.toLong() * IfcMonth.MONTHS_PER_YEAR + IfcMonth.MONTHS_PER_YEAR - 1

            /** Length of the Gregorian leap-year cycle, the period the `SKIP` pattern repeats in. */
            const val LEAP_CYCLE_YEARS: Long = 400L

            fun monthIndexOf(date: LocalDate): Long {
                val ifc = IfcDate.from(date)
                return ifc.year.toLong() * IfcMonth.MONTHS_PER_YEAR + (ifc.monthNumber - 1)
            }

            fun gcd(
                a: Long,
                b: Long,
            ): Long {
                var x = a
                var y = b
                while (y != 0L) {
                    val next = x % y
                    x = y
                    y = next
                }
                return x
            }
        }
    }

    // --- Gregorian rules ---------------------------------------------------------------------------

    /**
     * The parsed rule if this implementation can evaluate it, else `null`. Parsing is cheap and the
     * result is never cached, so the expander keeps no mutable state (`docs/contracts/Events.md` §5
     * "Pure, synchronous, thread-safe").
     */
    private fun supportedRule(rrule: String): RecurrenceRule? {
        val rule =
            try {
                RecurrenceRule(rrule)
            } catch (invalid: InvalidRecurrenceRuleException) {
                return null
            } catch (malformed: RuntimeException) {
                // lib-recur also reports some malformed text unchecked (a bad number, a bad value list).
                return null
            }
        if (rule.freq == Freq.HOURLY || rule.freq == Freq.MINUTELY || rule.freq == Freq.SECONDLY) return null
        if (rule.hasPart(RecurrenceRule.Part.BYHOUR) ||
            rule.hasPart(RecurrenceRule.Part.BYMINUTE) ||
            rule.hasPart(RecurrenceRule.Part.BYSECOND)
        ) {
            return null
        }
        return try {
            // lib-recur rejects some rule/DTSTART pairs when the iterator is built, so prove it can be
            // built here: supports() must answer the same question expand() will act on.
            rule.iterator(ruleDateTime(rule, SUPPORT_PROBE_START))
            rule
        } catch (refused: RuntimeException) {
            null
        }
    }

    private fun gregorianOccurrences(
        event: Event,
        rule: RecurrenceRule,
        from: LocalDate,
    ): Sequence<Occurrence> {
        val own = event.firstOccurrence().occurrenceDate
        // RFC 5545 §3.8.5.3: DTSTART is the first instance. lib-recur leaves it out when the rule's parts
        // do not match it, so it is merged in here, first and exactly once (contract: "Occurrence 1 is the
        // event's own start", for every recurrence shape).
        val ruleDates = ruleDates(event, rule, from).filter { it != own }
        val dates = if (own.isBefore(from)) ruleDates else sequenceOf(own) + ruleDates
        return dates
            .map { occurrenceOn(event, it) }
            .takeWhile { it.lastDate.year <= IfcDate.MAX_YEAR }
    }

    private fun ruleDates(
        event: Event,
        rule: RecurrenceRule,
        from: LocalDate,
    ): Sequence<LocalDate> {
        val anchor = event.firstOccurrence().startLocal
        val iterator =
            try {
                rule.iterator(ruleDateTime(rule, anchor)).also {
                    // Fast-forward instead of walking from the anchor, so the cost follows the range.
                    if (from.isAfter(anchor.toLocalDate())) {
                        it.fastForward(ruleDateTime(rule, from.atTime(anchor.toLocalTime())))
                    }
                }
            } catch (refused: RuntimeException) {
                // The contract forbids throwing for rule text; an unevaluable rule simply has no dates of
                // its own and the event keeps its single first occurrence.
                return emptySequence()
            }
        return generateSequence {
            try {
                if (iterator.hasNext()) iterator.nextDateTime() else null
            } catch (exhausted: RuntimeException) {
                // lib-recur gives up on pathological rules mid-iteration; treat that as the end.
                null
            }
        }.map { LocalDate.of(it.year, it.month + 1, it.dayOfMonth) }
            .takeWhile { it.year <= IfcDate.MAX_YEAR }
            .filter { !it.isBefore(from) }
    }

    private fun gregorianEndDate(
        event: Event,
        rule: RecurrenceRule,
    ): LocalDate? {
        val cutoff = LocalDate.of(IfcDate.MAX_YEAR, 12, LAST_DAY_OF_DECEMBER)
        // skipAllButLast() jumps to the final instance of a finite rule without iterating it here. It
        // refuses an impractical number of instances with an IllegalArgumentException; the walk below is
        // then the fallback.
        val jumped =
            try {
                val iterator = rule.iterator(ruleDateTime(rule, event.firstOccurrence().startLocal))
                iterator.skipAllButLast()
                if (iterator.hasNext()) {
                    val last = iterator.nextDateTime()
                    LocalDate.of(last.year, last.month + 1, last.dayOfMonth)
                } else {
                    null
                }
            } catch (refused: RuntimeException) {
                null
            }
        if (jumped != null) {
            val occurrence = occurrenceOn(event, jumped)
            if (!occurrence.lastDate.isAfter(cutoff)) return maxOf(occurrence.lastDate, event.endDate)
        }
        // Either the rule runs past year 9999 or lib-recur refused to jump. Walk from the anchor and keep
        // the last occurrence that exists; give up with `null` ("unbounded", never pruned) rather than
        // report the last one seen, which would prune the event far too early.
        var last = event.endDate
        var steps = 0
        for (occurrence in gregorianOccurrences(event, rule, event.startDate)) {
            if (++steps > MAX_OCCURRENCES_PER_CALL) return null
            last = occurrence.lastDate
        }
        return last
    }

    /**
     * [local] as a `lib-recur` `DateTime`. Its kind has to match the rule's `UNTIL` — lib-recur refuses a
     * floating start with an absolute `UNTIL` and the other way round — so an absolute `UNTIL` gets a UTC
     * value, a floating one a floating value, and everything else the simplest all-day value. Only the
     * date fields are ever read back, so UTC is a frame for the comparison, not a zone for the event.
     */
    private fun ruleDateTime(
        rule: RecurrenceRule,
        local: LocalDateTime,
    ): DateTime {
        val until = rule.until
        // lib-recur months are 0-based, like java.util.Calendar.
        val month = local.monthValue - 1
        return when {
            until == null || until.isAllDay -> DateTime(local.year, month, local.dayOfMonth)
            until.isFloating -> DateTime(local.year, month, local.dayOfMonth, local.hour, local.minute, 0)
            else -> DateTime(DateTime.UTC, local.year, month, local.dayOfMonth, local.hour, local.minute, 0)
        }
    }

    // --- small helpers -----------------------------------------------------------------------------

    /** Days from an occurrence's own start date to the last date it touches; `0` for a one-day event. */
    private fun spanDays(event: Event): Long = ChronoUnit.DAYS.between(event.startDate, event.endDate)

    /** [date] moved by [days], clamped to the representable range instead of overflowing. */
    private fun shiftClamped(
        date: LocalDate,
        days: Long,
    ): LocalDate =
        LocalDate.ofEpochDay(
            (date.toEpochDay() + days).coerceIn(LocalDate.MIN.toEpochDay(), LocalDate.MAX.toEpochDay()),
        )

    /** Work limits. */
    public companion object {
        /**
         * How many occurrences one call will look at. The contract bounds real work by the range and the
         * event's length; this is the backstop for data that is legal but absurd — a rule with billions of
         * instances, an event thousands of years long, or more excluded occurrences in a row than this.
         * [expand] stops adding occurrences at this many, [nextOccurrence] and [recurrenceEndDate] answer
         * `null`.
         */
        public const val MAX_OCCURRENCES_PER_CALL: Int = 10_000

        private const val LAST_DAY_OF_DECEMBER = 31

        /** Stand-in `DTSTART` used only to prove that a rule can be iterated, in [supports]. */
        private val SUPPORT_PROBE_START: LocalDateTime = LocalDateTime.of(2000, 1, 1, 12, 0)
    }
}
