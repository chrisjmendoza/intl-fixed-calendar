package io.github.chrisjmendoza.yearal.feature.events.editor

import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventCategory
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.EventUidGenerator
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.IntercalaryDay
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import io.github.chrisjmendoza.yearal.core.domain.event.RecurrenceEnd
import io.github.chrisjmendoza.yearal.core.domain.event.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Which recurrence the editor offers (`docs/ROADMAP.md` M4 T4; FEATURES E5, E7). Distinct from
 * [io.github.chrisjmendoza.yearal.core.domain.event.Recurrence]'s own shapes because two of these
 * options ([YEARLY_GREGORIAN], [WEEKLY]) both build a
 * [io.github.chrisjmendoza.yearal.core.domain.event.Recurrence.Gregorian], and because the chooser
 * needs a "no recurrence" option distinguishable from "not yet decided".
 */
enum class RecurrenceKind {
    /** The event happens once. */
    NONE,

    /** Yearly on the start date's IFC position ([IfcRecurrence.yearlyOn]). */
    YEARLY_IFC,

    /** Yearly on the start date's Gregorian position (`FREQ=YEARLY`, no `BYMONTH`/`BYMONTHDAY`). */
    YEARLY_GREGORIAN,

    /** Monthly on the start date's IFC day of month ([IfcRecurrence.monthlyOn]); hidden on Year Day and Leap Day. */
    MONTHLY_IFC,

    /** Weekly, the real seven-day week (`FREQ=WEEKLY`). */
    WEEKLY,
}

/** When a recurrence stops, as the editor's three radio choices. */
enum class RecurrenceEndKind {
    /** The rule never ends on its own. */
    NEVER,

    /** Stops after [EventDraft.untilDate], inclusive. */
    UNTIL,

    /** Stops after [EventDraft.count] occurrences. */
    COUNT,
}

/** Whether a timed event's wall clock is tied to a zone or follows the device (`docs/FEATURES.md` E1). */
enum class ZoneChoice {
    /** The device's zone at the time each occurrence happens; the wall time never changes zone. */
    FLOATING,

    /** [EventDraft.fixedZoneId] always, regardless of the device's zone. */
    FIXED,
}

/**
 * The editor's in-progress event, before it is turned into a domain [Event]. Every field is a
 * primitive or a `java.time` value so the whole draft can be written to a `SavedStateHandle`
 * (CLAUDE.md rule 8: this is the editor's own content, not a log or a nav key).
 *
 * A brand-new event with no prefill starts with [startDate] `null`, meaning "follow today" — the same
 * pattern `feature:converter`'s `ConverterInput` uses — so the default start rolls over at local
 * midnight until the user (or a prefill) pins it. Every other field always has a concrete value.
 *
 * The editor supports a multi-day span only for an all-day event ([allDayEndDate]); a timed event is a
 * single day, its end time on the same [startDate] as its start (`docs/contracts/Events.md` T4 covers
 * only single-day timed events in the fixtures this task must round-trip).
 *
 * @property startDate the Gregorian start date, or `null` to follow today.
 * @property allDayEndDate the last all-day date (inclusive), or `null` for a one-day event; ignored
 *   when [isAllDay] is `false`.
 * @property startMinuteOfDay minutes after midnight the timed event starts, 0..1439; ignored when [isAllDay].
 * @property endMinuteOfDay minutes after midnight the timed event ends, on the same day; ignored when [isAllDay].
 * @property fixedZoneId the zone used when [zoneChoice] is [ZoneChoice.FIXED]; always has a value so
 *   switching to "fixed zone" never needs a fresh read of the current zone.
 * @property leapDayPolicy the common-year policy for a Leap Day anchor; ignored unless the effective
 *   start is Leap Day and [recurrenceKind] is [RecurrenceKind.YEARLY_IFC].
 * @property untilDate the inclusive end date when [recurrenceEndKind] is [RecurrenceEndKind.UNTIL];
 *   `null` until the user picks [RecurrenceEndKind.UNTIL], at which point the ViewModel fills in a default.
 * @property count occurrences when [recurrenceEndKind] is [RecurrenceEndKind.COUNT], `≥ 1`.
 * @property reminders minutes-before values, each a preset chip (FEATURES E4; storage only — no
 *   `POST_NOTIFICATIONS` request or scheduling here, that is M6).
 */
data class EventDraft(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val isAllDay: Boolean = true,
    val startDate: LocalDate? = null,
    val allDayEndDate: LocalDate? = null,
    val startMinuteOfDay: Int = DEFAULT_START_MINUTE,
    val endMinuteOfDay: Int = DEFAULT_START_MINUTE + DEFAULT_DURATION_MINUTES,
    val zoneChoice: ZoneChoice = ZoneChoice.FLOATING,
    val fixedZoneId: ZoneId,
    val recurrenceKind: RecurrenceKind = RecurrenceKind.NONE,
    val leapDayPolicy: LeapDayPolicy = LeapDayPolicy.JUNE_28,
    val recurrenceEndKind: RecurrenceEndKind = RecurrenceEndKind.NEVER,
    val untilDate: LocalDate? = null,
    val count: Int = 1,
    val reminders: Set<Int> = emptySet(),
) {
    /** Well-known values. */
    companion object {
        /** Default start time, 09:00. */
        const val DEFAULT_START_MINUTE: Int = 9 * 60

        /** Default length of a new timed event, one hour. */
        const val DEFAULT_DURATION_MINUTES: Int = 60

        /** Minutes-before presets the reminder chips offer (FEATURES E4). */
        val REMINDER_PRESETS: List<Int> = listOf(0, 10, 30, 60, 1440)

        /**
         * The draft that shows an existing [event] for editing, with [deviceZoneId] as the value
         * [fixedZoneId] starts at if the event is floating or all-day (so switching to "fixed zone"
         * later has something to show).
         */
        fun from(
            event: Event,
            deviceZoneId: ZoneId,
        ): EventDraft {
            val timing = event.timing
            val (endKind, until, count) = parseEnd(event.recurrence)
            val leapDayPolicy =
                (event.recurrence as? IfcRecurrence.YearlyOnIntercalary)
                    ?.day
                    ?.let { it as? IntercalaryDay.LeapDay }
                    ?.commonYearPolicy ?: LeapDayPolicy.JUNE_28
            return EventDraft(
                title = event.title,
                description = event.description,
                location = event.location,
                isAllDay = event.isAllDay,
                startDate = event.startDate,
                allDayEndDate = if (timing is EventTiming.AllDay) event.endDate else null,
                startMinuteOfDay = (timing as? EventTiming.Timed)?.startMinuteOfDay ?: DEFAULT_START_MINUTE,
                endMinuteOfDay =
                    (timing as? EventTiming.Timed)?.let { it.startMinuteOfDay + it.durationMinutes }
                        ?: (DEFAULT_START_MINUTE + DEFAULT_DURATION_MINUTES),
                zoneChoice =
                    if ((timing as? EventTiming.Timed)?.zone !=
                        null
                    ) {
                        ZoneChoice.FIXED
                    } else {
                        ZoneChoice.FLOATING
                    },
                fixedZoneId = (timing as? EventTiming.Timed)?.zone ?: deviceZoneId,
                recurrenceKind = kindOf(event.recurrence),
                leapDayPolicy = leapDayPolicy,
                recurrenceEndKind = endKind,
                untilDate = until,
                count = count ?: 1,
                reminders = event.reminders.map { it.minutesBefore }.toSet(),
            )
        }

        private fun kindOf(recurrence: Recurrence): RecurrenceKind =
            when (recurrence) {
                Recurrence.None -> {
                    RecurrenceKind.NONE
                }

                is IfcRecurrence.YearlyOnDate, is IfcRecurrence.YearlyOnIntercalary -> {
                    RecurrenceKind.YEARLY_IFC
                }

                is IfcRecurrence.MonthlyOnDay -> {
                    RecurrenceKind.MONTHLY_IFC
                }

                is Recurrence.Gregorian -> {
                    if (recurrence.rrule.startsWith(
                            FREQ_WEEKLY,
                        )
                    ) {
                        RecurrenceKind.WEEKLY
                    } else {
                        RecurrenceKind.YEARLY_GREGORIAN
                    }
                }
            }

        private fun parseEnd(recurrence: Recurrence): Triple<RecurrenceEndKind, LocalDate?, Int?> =
            when (recurrence) {
                is IfcRecurrence -> {
                    when (val end = recurrence.end) {
                        RecurrenceEnd.Never -> Triple(RecurrenceEndKind.NEVER, null, null)
                        is RecurrenceEnd.Until -> Triple(RecurrenceEndKind.UNTIL, end.date, null)
                        is RecurrenceEnd.Count -> Triple(RecurrenceEndKind.COUNT, null, end.count)
                    }
                }

                is Recurrence.Gregorian -> {
                    parseGregorianEnd(recurrence.rrule)
                }

                Recurrence.None -> {
                    Triple(RecurrenceEndKind.NEVER, null, null)
                }
            }
    }
}

private const val FREQ_YEARLY = "FREQ=YEARLY"
private const val FREQ_WEEKLY = "FREQ=WEEKLY"
private val UNTIL_PATTERN = Regex(""";UNTIL=(\d{8})""")
private val COUNT_PATTERN = Regex(""";COUNT=(\d+)""")

private fun parseGregorianEnd(rrule: String): Triple<RecurrenceEndKind, LocalDate?, Int?> {
    UNTIL_PATTERN.find(rrule)?.let { match ->
        val date = runCatching { LocalDate.parse(match.groupValues[1], DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
        if (date != null) return Triple(RecurrenceEndKind.UNTIL, date, null)
    }
    COUNT_PATTERN.find(rrule)?.let { match ->
        val count = match.groupValues[1].toIntOrNull()
        if (count != null) return Triple(RecurrenceEndKind.COUNT, null, count)
    }
    return Triple(RecurrenceEndKind.NEVER, null, null)
}

private fun gregorianRrule(
    kind: RecurrenceKind,
    end: RecurrenceEnd,
): String {
    val freq = if (kind == RecurrenceKind.WEEKLY) FREQ_WEEKLY else FREQ_YEARLY
    return when (end) {
        RecurrenceEnd.Never -> freq
        is RecurrenceEnd.Until -> "$freq;UNTIL=${end.date.format(DateTimeFormatter.BASIC_ISO_DATE)}"
        is RecurrenceEnd.Count -> "$freq;COUNT=${end.count}"
    }
}

/**
 * The [RecurrenceEnd] [draft] describes, anchored on [startDate] — a fresh default when the user chose
 * [RecurrenceEndKind.UNTIL] but has not opened the date picker yet.
 */
internal fun recurrenceEndFrom(
    draft: EventDraft,
    startDate: LocalDate,
): RecurrenceEnd =
    when (draft.recurrenceEndKind) {
        RecurrenceEndKind.NEVER -> RecurrenceEnd.Never
        RecurrenceEndKind.UNTIL -> RecurrenceEnd.Until(draft.untilDate ?: startDate.plusYears(1))
        RecurrenceEndKind.COUNT -> RecurrenceEnd.Count(draft.count.coerceAtLeast(1))
    }

/**
 * The [Recurrence] [draft] describes, anchored on [startDate]: an IFC rule is always **rebuilt** from
 * the current start with [IfcRecurrence.yearlyOn] / [IfcRecurrence.monthlyOn]
 * (`docs/contracts/Events.md` T4 guidance), so the anchor invariant always holds; a Gregorian rule is
 * this editor's own canonical text (`FREQ=YEARLY` or `FREQ=WEEKLY`, no `BYDAY`), which drops any extra
 * qualifier an imported rule might have carried — out of scope for the 1.0 editor (`docs/contracts/Events.md`
 * §6: no import in 1.0).
 */
internal fun buildRecurrence(
    draft: EventDraft,
    startDate: LocalDate,
): Recurrence {
    val end = recurrenceEndFrom(draft, startDate)
    return when (draft.recurrenceKind) {
        RecurrenceKind.NONE -> Recurrence.None
        RecurrenceKind.YEARLY_IFC -> IfcRecurrence.yearlyOn(startDate, draft.leapDayPolicy, end = end)
        RecurrenceKind.MONTHLY_IFC -> IfcRecurrence.monthlyOn(startDate, end = end) ?: Recurrence.None
        RecurrenceKind.YEARLY_GREGORIAN -> Recurrence.Gregorian(gregorianRrule(RecurrenceKind.YEARLY_GREGORIAN, end))
        RecurrenceKind.WEEKLY -> Recurrence.Gregorian(gregorianRrule(RecurrenceKind.WEEKLY, end))
    }
}

/**
 * The [EventTiming] [draft] describes, anchored on [startDate]. All-day covers [EventDraft.allDayEndDate]
 * inclusive (or one day when `null`); timed is built with [EventTiming.timed] from [startDate] at
 * [EventDraft.startMinuteOfDay] to the same day at [EventDraft.endMinuteOfDay].
 *
 * @throws IllegalArgumentException if the timed end is before the timed start ([EventTiming.timed]).
 */
internal fun buildTiming(
    draft: EventDraft,
    startDate: LocalDate,
): EventTiming =
    if (draft.isAllDay) {
        val end = draft.allDayEndDate?.takeIf { !it.isBefore(startDate) } ?: startDate
        val days = ChronoUnit.DAYS.between(startDate, end).toInt() + 1
        EventTiming.AllDay(startDate, days)
    } else {
        val zone = if (draft.zoneChoice == ZoneChoice.FIXED) draft.fixedZoneId else null
        EventTiming.timed(
            start = startDate.atTime(minuteToTime(draft.startMinuteOfDay)),
            end = startDate.atTime(minuteToTime(draft.endMinuteOfDay)),
            zone = zone,
        )
    }

/** Converts a minute-of-day (0..1439) to a [LocalTime]. */
internal fun minuteToTime(minuteOfDay: Int): LocalTime =
    LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE.toLong())

private const val SECONDS_PER_MINUTE = 60

/**
 * The [Event] [draft] describes, anchored on the effective [startDate] (today when
 * [EventDraft.startDate] is `null`). Exdates are kept only when neither the start date nor the
 * recurrence changed from [existing] (`docs/contracts/Events.md` T4: "drop exdates before saving when
 * the start or rule changes"); a brand-new event gets its uid from [newUid], called only when
 * [existing] is `null`.
 */
internal fun buildEvent(
    draft: EventDraft,
    startDate: LocalDate,
    existing: Event?,
    newUid: EventUidGenerator,
): Event {
    val recurrence = buildRecurrence(draft, startDate)
    val timing = buildTiming(draft, startDate)
    val recurrenceChanged = existing == null || existing.recurrence != recurrence
    val startChanged = existing == null || existing.startDate != startDate
    val exdates = if (existing != null && !startChanged && !recurrenceChanged) existing.exdates else emptySet()
    return Event(
        id = existing?.id ?: Event.NEW_ID,
        uid = existing?.uid ?: newUid.newUid(),
        calendarId = existing?.calendarId ?: EventCalendar.DEFAULT_ID,
        title = draft.title,
        description = draft.description,
        location = draft.location,
        colorArgb = existing?.colorArgb,
        category = existing?.category ?: EventCategory.EVENT,
        timing = timing,
        recurrence = recurrence,
        exdates = exdates,
        reminders = draft.reminders.map(::Reminder).toSet(),
    )
}

/** Whether the effective start date (today when `null`) is Leap Day — when to ask for [LeapDayPolicy]. */
internal fun isLeapDayAnchor(startDate: LocalDate): Boolean = IfcDate.from(startDate) is IfcDate.LeapDay

/** Whether "monthly (IFC)" is offered for [startDate] — hidden on Year Day and Leap Day. */
internal fun isMonthlyIfcAvailable(startDate: LocalDate): Boolean = IfcRecurrence.monthlyOn(startDate) != null
