package io.github.chrisjmendoza.yearal.core.data.event.mapper

import io.github.chrisjmendoza.yearal.core.data.event.entity.CalendarEntity
import io.github.chrisjmendoza.yearal.core.data.event.entity.EventEntity
import io.github.chrisjmendoza.yearal.core.data.event.entity.EventExdateEntity
import io.github.chrisjmendoza.yearal.core.data.event.entity.EventWithRelations
import io.github.chrisjmendoza.yearal.core.data.event.entity.ReminderEntity
import io.github.chrisjmendoza.yearal.core.domain.event.CalendarSource
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventCategory
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRuleText
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import io.github.chrisjmendoza.yearal.core.domain.event.Reminder
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Conversion between the Room row shapes of `core/data/event/entity/` and the domain models of
// `io.github.chrisjmendoza.yearal.core.domain.event` (`docs/contracts/Events.md` "T2").
//
// Reads fail soft. A stored row can only become invalid through direct database tampering or a bug
// elsewhere, since every write goes through the domain model's own validating constructors first. Per
// the contract ("T2" notes), EventWithRelations.toDomainOrNull and CalendarEntity.toDomainOrNull return
// `null` for such a row instead of throwing, so one corrupted row never breaks a whole Flow; callers
// filter nulls out with `mapNotNull`. Nothing about the row is logged (CLAUDE.md rule 8).
//
// Writes never fail soft. EventCalendar.toEntity and Event.toEntity assume their receiver is already
// valid (its own constructor already checked that) and only reshape it into columns.

// ---- Calendars ----

/** [CalendarEntity] to [EventCalendar], or `null` for a row an unknown [CalendarEntity.source] corrupts. */
internal fun CalendarEntity.toDomainOrNull(): EventCalendar? =
    try {
        EventCalendar(
            id = id,
            name = name,
            colorArgb = colorArgb,
            source = CalendarSource.valueOf(source),
            visible = visible,
        )
    } catch (e: IllegalArgumentException) {
        null
    }

/** [EventCalendar] to the row it is stored as, under [id] (`0` for a new insert). */
internal fun EventCalendar.toEntity(id: Long = this.id): CalendarEntity =
    CalendarEntity(
        id = id,
        name = name,
        colorArgb = colorArgb,
        source = source.name,
        visible = visible,
    )

// ---- Events ----

/**
 * [EventWithRelations] to [Event], or `null` if the row (or an exdate/reminder) violates a model
 * invariant — an unknown [EventEntity.category], a malformed [EventEntity.zoneId], a
 * [EventEntity.recurrenceType] whose companion text column is missing, an [IfcRuleText] that does not
 * parse, or anything [Event]'s own constructor rejects.
 */
internal fun EventWithRelations.toDomainOrNull(): Event? =
    try {
        Event(
            id = event.id,
            uid = event.uid,
            calendarId = event.calendarId,
            title = event.title,
            description = event.description,
            location = event.location,
            colorArgb = event.colorArgb,
            category = EventCategory.valueOf(event.category),
            timing = event.toTiming(),
            recurrence = event.toRecurrence(),
            exdates = exdates.map { LocalDate.ofEpochDay(it.epochDay) }.toSet(),
            reminders = reminders.map { Reminder(it.minutesBefore) }.toSet(),
            createdAt = Instant.ofEpochMilli(event.createdAt),
            updatedAt = Instant.ofEpochMilli(event.updatedAt),
        )
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: DateTimeException) {
        null
    }

private fun EventEntity.toTiming(): EventTiming =
    if (allDay) {
        EventTiming.AllDay(
            startDate = LocalDate.ofEpochDay(startEpochDay),
            days = durationMinutes / EventTiming.MINUTES_PER_DAY,
        )
    } else {
        EventTiming.Timed(
            startDate = LocalDate.ofEpochDay(startEpochDay),
            startMinuteOfDay = requireNotNull(startMinuteOfDay) { "Timed event row $id has no start_minute_of_day" },
            durationMinutes = durationMinutes,
            zone = zoneId?.let(ZoneId::of),
        )
    }

private fun EventEntity.toRecurrence(): Recurrence =
    when (recurrenceType) {
        0 -> Recurrence.None
        1 -> Recurrence.Gregorian(requireNotNull(rrule) { "Event row $id has recurrence_type=1 but no rrule" })
        2 -> IfcRuleText.parse(requireNotNull(ifcRule) { "Event row $id has recurrence_type=2 but no ifc_rule" })
        else -> throw IllegalArgumentException("Event row $id has an unknown recurrence_type $recurrenceType")
    }

/**
 * [Event] to the row it is stored as. [id], [createdAt], [updatedAt] and [recurrenceUntilEpochDay] are
 * repository-owned and passed in explicitly rather than read off [Event] (`docs/contracts/Events.md`
 * "T2": `recurrence_until_epoch_day` is a write-time parameter, not something the mapper computes).
 */
internal fun Event.toEntity(
    id: Long,
    createdAt: Instant,
    updatedAt: Instant,
    recurrenceUntilEpochDay: Long?,
): EventEntity {
    val recurrenceType: Int
    val rrule: String?
    val ifcRule: String?
    when (val r = recurrence) {
        Recurrence.None -> {
            recurrenceType = 0
            rrule = null
            ifcRule = null
        }

        is Recurrence.Gregorian -> {
            recurrenceType = 1
            rrule = r.rrule
            ifcRule = null
        }

        is IfcRecurrence -> {
            recurrenceType = 2
            rrule = null
            ifcRule = r.toRuleText()
        }
    }
    return EventEntity(
        id = id,
        uid = uid,
        calendarId = calendarId,
        title = title,
        description = description,
        location = location,
        colorArgb = colorArgb,
        category = category.name,
        allDay = isAllDay,
        startEpochDay = startDate.toEpochDay(),
        startMinuteOfDay = timing.startMinuteOfDay,
        durationMinutes = timing.durationMinutes,
        endEpochDay = endDate.toEpochDay(),
        zoneId = timing.zone?.id,
        recurrenceType = recurrenceType,
        rrule = rrule,
        ifcRule = ifcRule,
        recurrenceUntilEpochDay = recurrenceUntilEpochDay,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )
}

/** [EventExdateEntity] for one exdate of [eventId]. */
internal fun LocalDate.toExdateEntity(eventId: Long): EventExdateEntity =
    EventExdateEntity(eventId = eventId, epochDay = toEpochDay())

/** [ReminderEntity] for one reminder of [eventId]. */
internal fun Reminder.toEntity(eventId: Long): ReminderEntity =
    ReminderEntity(eventId = eventId, minutesBefore = minutesBefore)
