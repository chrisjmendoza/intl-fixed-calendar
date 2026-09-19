package io.github.chrisjmendoza.yearal.feature.events.editor

import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the event editor (`EventEditorKey`, `docs/ROADMAP.md` M4 T4) shows
 * (`docs/ARCHITECTURE.md` §4 "State management"). Immutable; a new value is built for every draft edit
 * and every date the `DateTicker` emits, so the default start never sticks past local midnight.
 */
sealed interface EventEditorUiState {
    /** Before the event to edit (or the ticker's first date, for a new event) has loaded. */
    data object Loading : EventEditorUiState

    /** The event named by `EventEditorKey.eventId` no longer exists (deleted elsewhere). */
    data object NotFound : EventEditorUiState

    /**
     * @property isNew `true` while creating an event; `false` while editing one.
     * @property title one line, at most [io.github.chrisjmendoza.yearal.core.domain.event.Event.MAX_TITLE_LENGTH]; may be blank.
     * @property description free text, at most [io.github.chrisjmendoza.yearal.core.domain.event.Event.MAX_DESCRIPTION_LENGTH].
     * @property location free text, at most [io.github.chrisjmendoza.yearal.core.domain.event.Event.MAX_LOCATION_LENGTH].
     * @property isAllDay `true` for an all-day event.
     * @property startDate the effective start date: [EventDraft.startDate], or today while it is `null`.
     * @property startIfcLabel [startDate] in the IFC long style, with its `IFC` marker context (CLAUDE.md rule 5).
     * @property startIfcDayLabel [startDate]'s IFC day named within its month, no year (`Sol 13`) — the
     *   "yearly on this IFC date" recurrence option's label, where the year would be misleading.
     * @property startGregorianLabel [startDate] in the Gregorian long style.
     * @property startGregorianDayLabel [startDate]'s Gregorian month and day, no year (`Jun 18`) — the
     *   "yearly on this Gregorian date" recurrence option's label.
     * @property allDayEndDate the effective all-day end date (inclusive); only meaningful when [isAllDay].
     * @property allDayEndBeforeStart `true` when [allDayEndDate] is before [startDate] — blocks saving.
     * @property startMinuteOfDay the timed start, 0..1439; only meaningful when not [isAllDay].
     * @property endMinuteOfDay the timed end, 0..1439, same day as [startDate]; only meaningful when not [isAllDay].
     * @property endBeforeStart `true` when [endMinuteOfDay] is before [startMinuteOfDay] — blocks saving.
     * @property zoneChoice device (floating) or a fixed zone.
     * @property fixedZoneId the zone used when [zoneChoice] is [ZoneChoice.FIXED].
     * @property recurrenceKind which recurrence is selected.
     * @property monthlyIfcAvailable `false` on Year Day and Leap Day — hide "monthly (IFC)" then (CLAUDE.md rule 6).
     * @property isLeapDayAnchor `true` when [startDate] is Leap Day — only then is [leapDayPolicy] asked for.
     * @property leapDayPolicy the common-year policy for a Leap Day yearly rule (FEATURES E6).
     * @property recurrenceEndKind never / until / count.
     * @property untilDate the effective "until" date (defaults to one year after [startDate] until the user picks one).
     * @property untilBeforeStart `true` when [untilDate] is before [startDate] — blocks saving.
     * @property count the effective occurrence count, `≥ 1`.
     * @property reminders the selected minutes-before presets.
     * @property canSave `false` while any blocking condition above holds.
     * @property isDirty `true` once the draft differs from what was first loaded — gates the unsaved-changes guard.
     * @property saveFailed `true` after [save][EventEditorViewModel.save] failed soft (the event or its
     *   calendar was deleted elsewhere) — never a crash (CLAUDE.md rule: no `TODO()` or stub, and per
     *   `docs/contracts/Events.md` T4 guidance to catch `IllegalArgumentException` and fail soft).
     * @property showDeleteConfirm the delete confirmation dialog is open.
     * @property showDiscardConfirm the unsaved-changes (back) confirmation dialog is open.
     */
    data class Loaded(
        val isNew: Boolean,
        val title: String,
        val description: String,
        val location: String,
        val isAllDay: Boolean,
        val startDate: LocalDate,
        val startIfcLabel: String,
        val startIfcDayLabel: String,
        val startGregorianLabel: String,
        val startGregorianDayLabel: String,
        val allDayEndDate: LocalDate,
        val allDayEndBeforeStart: Boolean,
        val startMinuteOfDay: Int,
        val endMinuteOfDay: Int,
        val endBeforeStart: Boolean,
        val zoneChoice: ZoneChoice,
        val fixedZoneId: ZoneId,
        val recurrenceKind: RecurrenceKind,
        val monthlyIfcAvailable: Boolean,
        val isLeapDayAnchor: Boolean,
        val leapDayPolicy: LeapDayPolicy,
        val recurrenceEndKind: RecurrenceEndKind,
        val untilDate: LocalDate,
        val untilBeforeStart: Boolean,
        val count: Int,
        val reminders: Set<Int>,
        val canSave: Boolean,
        val isDirty: Boolean,
        val saveFailed: Boolean,
        val showDeleteConfirm: Boolean,
        val showDiscardConfirm: Boolean,
    ) : EventEditorUiState
}
