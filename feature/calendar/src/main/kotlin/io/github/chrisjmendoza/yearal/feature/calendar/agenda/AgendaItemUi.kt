package io.github.chrisjmendoza.yearal.feature.calendar.agenda

import io.github.chrisjmendoza.yearal.core.domain.event.AgendaEntry
import java.time.LocalDate
import java.time.LocalTime

/**
 * One row of an agenda list — Day detail (FEATURES C5) or Today's summary (FEATURES T5) — built from
 * an [AgendaEntry]. Holds display-ready fields only; the screen supplies the localized "(No title)"
 * and "All day" text and formats the times, so this module needs no Android [android.content.res.Resources]
 * dependency (CLAUDE.md rule 9).
 *
 * @property eventId [AgendaEntry.event]'s id — the only thing a tap sends onward, as
 * `EventEditorKey(eventId)` (CLAUDE.md rule 8: ids only, never content).
 * @property title the event's own title; blank when the user left it blank.
 * @property isAllDay whether to show "All day" instead of a time range.
 * @property startTime the resolved start's time of day, in the device zone; ignored when [isAllDay].
 * @property endTime the resolved exclusive end's time of day, in the device zone; ignored when [isAllDay].
 * @property colorArgb the colour to draw the leading mark in, `0xAARRGGBB` ([AgendaEntry.colorArgb]).
 * @property isRecurring whether the underlying event repeats ([io.github.chrisjmendoza.yearal.core.domain.event.Event.isRecurring]);
 * `false` rows offer a plain delete, `true` rows offer "delete this occurrence" (FEATURES E1).
 * @property occurrenceDate the occurrence's **own** wall-clock start date
 * ([io.github.chrisjmendoza.yearal.core.domain.event.Occurrence.occurrenceDate]) — the exdate key.
 * **Not** necessarily the date the row is shown on (a multi-day or zoned occurrence can be shown up to
 * two days away): "delete this occurrence" must pass this value, never the day being viewed
 * (`docs/contracts/Events.md` §4, §7 "T6–T8").
 */
data class AgendaItemUi(
    val eventId: Long,
    val title: String,
    val isAllDay: Boolean,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val colorArgb: Int,
    val isRecurring: Boolean = false,
    val occurrenceDate: LocalDate = LocalDate.ofEpochDay(0),
)

/**
 * Builds the row for [entry]: all-day entries carry no times, timed ones carry the resolved start and
 * end time of day in [AgendaEntry.deviceZone] ([AgendaEntry.start], [AgendaEntry.end]). Nothing here
 * computes a date (CLAUDE.md rule 1); the entry's occurrence is already resolved.
 */
fun AgendaEntry.toAgendaItemUi(): AgendaItemUi =
    AgendaItemUi(
        eventId = event.id,
        title = event.title,
        isAllDay = isAllDay,
        startTime = if (isAllDay) null else start.toLocalTime(),
        endTime = if (isAllDay) null else end.toLocalTime(),
        colorArgb = colorArgb,
        isRecurring = event.isRecurring,
        occurrenceDate = occurrence.occurrenceDate,
    )
