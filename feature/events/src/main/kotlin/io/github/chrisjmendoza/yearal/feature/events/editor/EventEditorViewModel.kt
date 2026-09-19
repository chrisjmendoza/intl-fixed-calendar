package io.github.chrisjmendoza.yearal.feature.events.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.ZoneProvider
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.EventUidGenerator
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.navigation.EventEditorKey
import io.github.chrisjmendoza.yearal.feature.events.notification.NotificationPermissionGate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * State holder for the event editor (`EventEditorKey`, `docs/ROADMAP.md` M4 T4; FEATURES E1, E2, E3,
 * E5, E6, E7). Builds an [EventDraft] from user intents, persists it to [savedStateHandle] as
 * primitives so it survives process death, and turns it into a domain [Event] only on [save] (via
 * [buildEvent], which derives IFC rules fresh from the current start date so the anchor invariant
 * always holds — `docs/contracts/Events.md` T4 guidance).
 *
 * The default start of a brand-new, unprefilled event follows [dateTicker] until the user picks a
 * date, so it rolls over at local midnight (CLAUDE.md rule 2). Editing an existing event loads it once
 * with [EventRepository.getEvent] rather than observing it continuously, so a concurrent change
 * elsewhere never clobbers what the user is typing.
 *
 * @param key which event to edit, or none for a new one; assisted-injected (see [Factory]).
 * @param savedStateHandle the draft's process-death storage.
 * @param eventRepository loads the event to edit and saves or deletes it.
 * @param uidGenerator supplies the uid of a brand-new event.
 * @param zoneProvider the device zone: the initial value of a "fixed zone" choice.
 * @param dateTicker "today", for a new event with no prefilled start.
 * @param formatter renders every date through `:core:calendar`, never computing one itself (CLAUDE.md rule 1).
 * @param notificationPermission whether this device needs a runtime `POST_NOTIFICATIONS` grant
 * (FEATURES E4, P2); abstracted so the permission state machine is testable without Robolectric.
 */
@HiltViewModel(assistedFactory = EventEditorViewModel.Factory::class)
class EventEditorViewModel
    @AssistedInject
    constructor(
        @Assisted key: EventEditorKey,
        private val savedStateHandle: SavedStateHandle,
        private val eventRepository: EventRepository,
        private val uidGenerator: EventUidGenerator,
        zoneProvider: ZoneProvider,
        dateTicker: DateTicker,
        private val formatter: IfcDateFormatter,
        private val notificationPermission: NotificationPermissionGate,
    ) : ViewModel() {
        /** Creates an [EventEditorViewModel] for the entry's key; used by `hiltViewModel(creationCallback)`. */
        @AssistedFactory
        interface Factory {
            /** @param key the editor entry's key: which event, or none, and an optional prefilled start. */
            fun create(key: EventEditorKey): EventEditorViewModel
        }

        private val isNew = key.eventId == null
        private var existingEvent: Event? = null
        private var loadedDraft: EventDraft? = null

        private val draft = MutableStateFlow(restoreDraft(savedStateHandle) ?: initialDraft(key, zoneProvider))
        private val flags = MutableStateFlow(EditorFlags())
        private val loading = MutableStateFlow(!isNew)
        private val notFound = MutableStateFlow(false)
        private val outbox = Channel<EventEditorEvent>(Channel.BUFFERED)

        /** One-shot outcomes ([EventEditorEvent.Saved], [Deleted][EventEditorEvent.Deleted] or a discarded back). */
        val editorEvents: Flow<EventEditorEvent> = outbox.receiveAsFlow()

        init {
            val id = key.eventId
            if (id != null) {
                viewModelScope.launch {
                    when (val event = eventRepository.getEvent(id)) {
                        null -> {
                            notFound.value = true
                        }

                        else -> {
                            existingEvent = event
                            val fromEvent = EventDraft.from(event, zoneProvider.currentZone())
                            loadedDraft = fromEvent
                            if (savedStateHandle.get<String>(KEY_TITLE) == null) draft.value = fromEvent
                            flags.update { it.copy(exdateCount = event.exdates.size) }
                        }
                    }
                    loading.value = false
                }
            } else {
                loadedDraft = draft.value
            }
        }

        /** [EventEditorUiState.Loading], then [EventEditorUiState.NotFound] or a [EventEditorUiState.Loaded] per change. */
        val uiState: StateFlow<EventEditorUiState> =
            combine(dateTicker.today, draft, flags, loading, notFound) { today, d, f, isLoading, nf ->
                when {
                    isLoading -> EventEditorUiState.Loading
                    nf -> EventEditorUiState.NotFound
                    else -> buildEventEditorUiState(today, d, isNew, loadedDraft, f, formatter)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EventEditorUiState.Loading)

        /** Sets the title, clipped to [Event.MAX_TITLE_LENGTH]. */
        fun setTitle(text: String) = update { it.copy(title = text.take(Event.MAX_TITLE_LENGTH)) }

        /** Sets the notes, clipped to [Event.MAX_DESCRIPTION_LENGTH]. */
        fun setDescription(text: String) = update { it.copy(description = text.take(Event.MAX_DESCRIPTION_LENGTH)) }

        /** Sets the location, clipped to [Event.MAX_LOCATION_LENGTH]. */
        fun setLocation(text: String) = update { it.copy(location = text.take(Event.MAX_LOCATION_LENGTH)) }

        /** Switches between all-day and timed. */
        fun setAllDay(allDay: Boolean) = update { it.copy(isAllDay = allDay) }

        /** Pins the start date, picked in either calendar (FEATURES E2). */
        fun setStartDate(date: LocalDate) = update { it.copy(startDate = date) }

        /** Sets the all-day end date (inclusive); `null` means "same as start". */
        fun setAllDayEndDate(date: LocalDate?) = update { it.copy(allDayEndDate = date) }

        /** Sets the timed start minute of day, 0..1439. */
        fun setStartMinuteOfDay(minute: Int) = update { it.copy(startMinuteOfDay = minute) }

        /** Sets the timed end minute of day, 0..1439. */
        fun setEndMinuteOfDay(minute: Int) = update { it.copy(endMinuteOfDay = minute) }

        /** Chooses device (floating) or a fixed zone (`docs/ROADMAP.md` M4 T4: no zone picker in 1.0). */
        fun setZoneChoice(choice: ZoneChoice) = update { it.copy(zoneChoice = choice) }

        /** Chooses which recurrence to build (FEATURES E5, E7). */
        fun setRecurrenceKind(kind: RecurrenceKind) = update { it.copy(recurrenceKind = kind) }

        /** Chooses the common-year Leap Day policy (FEATURES E6); only meaningful on a Leap Day anchor. */
        fun setLeapDayPolicy(policy: LeapDayPolicy) = update { it.copy(leapDayPolicy = policy) }

        /** Chooses never / until / count as the recurrence end. */
        fun setRecurrenceEndKind(kind: RecurrenceEndKind) = update { it.copy(recurrenceEndKind = kind) }

        /** Sets the inclusive "until" date. */
        fun setUntilDate(date: LocalDate) = update { it.copy(untilDate = date) }

        /** Sets the occurrence count, clamped to at least 1. */
        fun setCount(count: Int) = update { it.copy(count = count.coerceAtLeast(1)) }

        /**
         * Toggles a minutes-before reminder chip (FEATURES E4; storage only — [ReminderScheduler] is
         * M6 T1). The **first** time a chip is added to an empty set, on a device that
         * [NotificationPermissionGate.needsRuntimePermission] (API 33+), sends
         * [EventEditorEvent.RequestNotificationPermission] once — never again this session, whatever
         * the outcome (FEATURES P2: no re-prompt loop). Below API 33, or after the first ask, nothing
         * is requested.
         */
        fun toggleReminder(minutesBefore: Int) {
            val adding = minutesBefore !in draft.value.reminders
            update {
                it.copy(
                    reminders =
                        if (minutesBefore in
                            it.reminders
                        ) {
                            it.reminders - minutesBefore
                        } else {
                            it.reminders + minutesBefore
                        },
                )
            }
            if (adding && !flags.value.notificationPermissionRequested &&
                notificationPermission.needsRuntimePermission()
            ) {
                flags.update { it.copy(notificationPermissionRequested = true) }
                viewModelScope.launch { outbox.send(EventEditorEvent.RequestNotificationPermission) }
            }
        }

        /**
         * Records the outcome of [EventEditorEvent.RequestNotificationPermission] (the Activity Result
         * launcher's callback). A denial never blocks saving; it only shows a dismissible explanation
         * ([EventEditorUiState.Loaded.showNotificationPermissionNotice]).
         */
        fun onNotificationPermissionResult(granted: Boolean) {
            flags.update { it.copy(notificationPermissionDenied = !granted) }
        }

        /** Dismisses the "notifications are off" notice without changing anything else. */
        fun dismissNotificationPermissionNotice() = flags.update { it.copy(notificationPermissionDenied = false) }

        /**
         * Clears every exdate of the event being edited — "restore all" for occurrences individually
         * deleted from Day detail. The contract has no bulk clear, so this loops
         * [EventRepository.removeExdate] one date at a time, then refreshes the cached event and
         * [EventEditorUiState.Loaded.exdateCount]. No-op for a new event or one with nothing to restore.
         */
        fun restoreAllOccurrences() {
            val event = existingEvent ?: return
            if (event.exdates.isEmpty()) return
            viewModelScope.launch {
                event.exdates.forEach { date -> eventRepository.removeExdate(event.id, date) }
                val refreshed = eventRepository.getEvent(event.id)
                existingEvent = refreshed
                flags.update { it.copy(exdateCount = refreshed?.exdates?.size ?: 0) }
            }
        }

        /** Opens the delete confirmation. No-op while creating a new event. */
        fun requestDelete() {
            if (!isNew) flags.update { it.copy(deleteConfirm = true, saveFailed = false) }
        }

        /** Dismisses the delete confirmation without deleting. */
        fun cancelDelete() = flags.update { it.copy(deleteConfirm = false) }

        /** Deletes the event ("edit all / delete all" — per-occurrence delete is a later task) and signals [EventEditorEvent.Deleted]. */
        fun confirmDelete() {
            val id = existingEvent?.id ?: return
            flags.update { it.copy(deleteConfirm = false) }
            viewModelScope.launch {
                eventRepository.deleteEvent(id)
                outbox.send(EventEditorEvent.Deleted)
            }
        }

        /** The screen's back action: opens the unsaved-changes guard if the draft is dirty, else leaves at once. */
        fun requestBack() {
            val state = uiState.value
            if (state is EventEditorUiState.Loaded && state.isDirty) {
                flags.update { it.copy(discardConfirm = true) }
            } else {
                viewModelScope.launch { outbox.send(EventEditorEvent.NavigatedAway) }
            }
        }

        /** Dismisses the unsaved-changes guard and stays on the editor. */
        fun cancelDiscard() = flags.update { it.copy(discardConfirm = false) }

        /** Confirms leaving without saving. */
        fun confirmDiscard() {
            flags.update { it.copy(discardConfirm = false) }
            viewModelScope.launch { outbox.send(EventEditorEvent.NavigatedAway) }
        }

        /**
         * Builds and saves the event ([buildEvent]); no-op while [EventEditorUiState.Loaded.canSave] is
         * `false`. Signals [EventEditorEvent.Saved] on success; a broken precondition (the event, or its
         * calendar, was deleted elsewhere) fails soft — the draft is kept and [EditorFlags.saveFailed] is
         * set instead of crashing (`docs/contracts/Events.md` T4 guidance).
         */
        fun save() {
            val state = uiState.value as? EventEditorUiState.Loaded ?: return
            if (!state.canSave) return
            viewModelScope.launch {
                try {
                    val event = buildEvent(draft.value, state.startDate, existingEvent, uidGenerator)
                    eventRepository.upsertEvent(event)
                    outbox.send(EventEditorEvent.Saved)
                } catch (invalid: IllegalArgumentException) {
                    flags.update { it.copy(saveFailed = true) }
                }
            }
        }

        private fun update(transform: (EventDraft) -> EventDraft) {
            val next = transform(draft.value)
            draft.value = next
            next.saveTo(savedStateHandle)
            flags.update { it.copy(saveFailed = false) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** One-shot outcomes of the editor. */
sealed interface EventEditorEvent {
    /** The event was saved; carries nothing but the fact (CLAUDE.md rule 8). */
    data object Saved : EventEditorEvent

    /** The event was deleted. */
    data object Deleted : EventEditorEvent

    /** The user left without saving (no changes, or the unsaved-changes guard was confirmed). */
    data object NavigatedAway : EventEditorEvent

    /**
     * Ask the platform for `POST_NOTIFICATIONS` with the Activity Result API (FEATURES E4, P2): sent
     * once, the first time a reminder chip is added on a device that needs the runtime grant. The
     * result reaches [EventEditorViewModel.onNotificationPermissionResult].
     */
    data object RequestNotificationPermission : EventEditorEvent
}

/** Transient dialog and error flags, not part of [EventDraft] because they are never persisted. */
internal data class EditorFlags(
    val deleteConfirm: Boolean = false,
    val discardConfirm: Boolean = false,
    val saveFailed: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val notificationPermissionDenied: Boolean = false,
    val exdateCount: Int = 0,
)

/** Builds the loaded state from [draft] against today's date, comparing with [loadedDraft] for [EventEditorUiState.Loaded.isDirty]. */
internal fun buildEventEditorUiState(
    today: LocalDate,
    draft: EventDraft,
    isNew: Boolean,
    loadedDraft: EventDraft?,
    flags: EditorFlags,
    formatter: IfcDateFormatter,
): EventEditorUiState.Loaded {
    val startDate = draft.startDate ?: today
    val startIfc = IfcDate.from(startDate)
    val allDayEnd = draft.allDayEndDate ?: startDate
    val untilDate = draft.untilDate ?: startDate.plusYears(1)
    val endBeforeStart = !draft.isAllDay && draft.endMinuteOfDay < draft.startMinuteOfDay
    val allDayEndBeforeStart = draft.isAllDay && allDayEnd.isBefore(startDate)
    val untilBeforeStart = draft.recurrenceEndKind == RecurrenceEndKind.UNTIL && untilDate.isBefore(startDate)
    return EventEditorUiState.Loaded(
        isNew = isNew,
        title = draft.title,
        description = draft.description,
        location = draft.location,
        isAllDay = draft.isAllDay,
        startDate = startDate,
        startIfcLabel = formatter.formatLong(startIfc),
        startIfcDayLabel = formatter.formatDay(startIfc),
        startGregorianLabel = formatter.formatGregorianLong(startDate),
        startGregorianDayLabel = formatter.formatGregorianMonthDay(startDate),
        allDayEndDate = allDayEnd,
        allDayEndBeforeStart = allDayEndBeforeStart,
        startMinuteOfDay = draft.startMinuteOfDay,
        endMinuteOfDay = draft.endMinuteOfDay,
        endBeforeStart = endBeforeStart,
        zoneChoice = draft.zoneChoice,
        fixedZoneId = draft.fixedZoneId,
        recurrenceKind = draft.recurrenceKind,
        monthlyIfcAvailable = isMonthlyIfcAvailable(startDate),
        isLeapDayAnchor = isLeapDayAnchor(startDate),
        leapDayPolicy = draft.leapDayPolicy,
        recurrenceEndKind = draft.recurrenceEndKind,
        untilDate = untilDate,
        untilBeforeStart = untilBeforeStart,
        count = draft.count,
        reminders = draft.reminders,
        canSave = !endBeforeStart && !allDayEndBeforeStart && !untilBeforeStart,
        isDirty = isDirty(draft, isNew, loadedDraft),
        saveFailed = flags.saveFailed,
        showDeleteConfirm = flags.deleteConfirm,
        showDiscardConfirm = flags.discardConfirm,
        exdateCount = flags.exdateCount,
        showNotificationPermissionNotice = flags.notificationPermissionDenied,
    )
}

/**
 * Whether the draft has unsaved work: for an existing event, whether it differs from [loadedDraft] (the
 * event as stored); for a new event — which has nothing stored to compare with — whether it differs
 * from a pristine draft, i.e. whether the user (or a prefill) has entered anything at all.
 */
private fun isDirty(
    draft: EventDraft,
    isNew: Boolean,
    loadedDraft: EventDraft?,
): Boolean =
    if (isNew) {
        draft != EventDraft(fixedZoneId = draft.fixedZoneId)
    } else {
        loadedDraft != null && loadedDraft != draft
    }

private fun initialDraft(
    key: EventEditorKey,
    zoneProvider: ZoneProvider,
): EventDraft =
    EventDraft(
        fixedZoneId = zoneProvider.currentZone(),
        startDate = key.prefillEpochDay?.let(::validEpochDayToDate),
    )

// A key can be synthesized from an intent, so its Long is untrusted (docs/security-and-privacy.md §6.3):
// fail soft rather than let IfcDate.from throw later when the state is built.
private fun validEpochDayToDate(epochDay: Long): LocalDate? {
    val date = runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull() ?: return null
    return date.takeIf { it.year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR }
}

private const val KEY_TITLE = "events.editor.title"
private const val KEY_DESCRIPTION = "events.editor.description"
private const val KEY_LOCATION = "events.editor.location"
private const val KEY_ALL_DAY = "events.editor.allDay"
private const val KEY_START_DATE = "events.editor.startDate"
private const val KEY_ALL_DAY_END_DATE = "events.editor.allDayEndDate"
private const val KEY_START_MINUTE = "events.editor.startMinute"
private const val KEY_END_MINUTE = "events.editor.endMinute"
private const val KEY_ZONE_CHOICE = "events.editor.zoneChoice"
private const val KEY_FIXED_ZONE = "events.editor.fixedZone"
private const val KEY_RECURRENCE_KIND = "events.editor.recurrenceKind"
private const val KEY_LEAP_DAY_POLICY = "events.editor.leapDayPolicy"
private const val KEY_RECURRENCE_END_KIND = "events.editor.recurrenceEndKind"
private const val KEY_UNTIL_DATE = "events.editor.untilDate"
private const val KEY_COUNT = "events.editor.count"
private const val KEY_REMINDERS = "events.editor.reminders"

private fun EventDraft.saveTo(handle: SavedStateHandle) {
    handle[KEY_TITLE] = title
    handle[KEY_DESCRIPTION] = description
    handle[KEY_LOCATION] = location
    handle[KEY_ALL_DAY] = isAllDay
    handle[KEY_START_DATE] = startDate?.toEpochDay()
    handle[KEY_ALL_DAY_END_DATE] = allDayEndDate?.toEpochDay()
    handle[KEY_START_MINUTE] = startMinuteOfDay
    handle[KEY_END_MINUTE] = endMinuteOfDay
    handle[KEY_ZONE_CHOICE] = zoneChoice.name
    handle[KEY_FIXED_ZONE] = fixedZoneId.id
    handle[KEY_RECURRENCE_KIND] = recurrenceKind.name
    handle[KEY_LEAP_DAY_POLICY] = leapDayPolicy.name
    handle[KEY_RECURRENCE_END_KIND] = recurrenceEndKind.name
    handle[KEY_UNTIL_DATE] = untilDate?.toEpochDay()
    handle[KEY_COUNT] = count
    handle[KEY_REMINDERS] = reminders.joinToString(separator = ",")
}

/** Rebuilds a draft from saved primitives, or `null` if nothing was saved yet. Every part is re-validated. */
private fun restoreDraft(handle: SavedStateHandle): EventDraft? {
    val title = handle.get<String>(KEY_TITLE) ?: return null
    val zoneName = handle.get<String>(KEY_FIXED_ZONE)
    val fixedZone = zoneName?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
    return EventDraft(
        title = title,
        description = handle.get<String>(KEY_DESCRIPTION).orEmpty(),
        location = handle.get<String>(KEY_LOCATION).orEmpty(),
        isAllDay = handle.get<Boolean>(KEY_ALL_DAY) != false,
        startDate = handle.get<Long>(KEY_START_DATE)?.let(LocalDate::ofEpochDay),
        allDayEndDate = handle.get<Long>(KEY_ALL_DAY_END_DATE)?.let(LocalDate::ofEpochDay),
        startMinuteOfDay = handle.get<Int>(KEY_START_MINUTE) ?: EventDraft.DEFAULT_START_MINUTE,
        endMinuteOfDay =
            handle.get<Int>(KEY_END_MINUTE) ?: (EventDraft.DEFAULT_START_MINUTE + EventDraft.DEFAULT_DURATION_MINUTES),
        zoneChoice =
            ZoneChoice.entries.firstOrNull { it.name == handle.get<String>(KEY_ZONE_CHOICE) } ?: ZoneChoice.FLOATING,
        fixedZoneId = fixedZone,
        recurrenceKind =
            RecurrenceKind.entries.firstOrNull { it.name == handle.get<String>(KEY_RECURRENCE_KIND) }
                ?: RecurrenceKind.NONE,
        leapDayPolicy =
            LeapDayPolicy.entries.firstOrNull { it.name == handle.get<String>(KEY_LEAP_DAY_POLICY) }
                ?: LeapDayPolicy.JUNE_28,
        recurrenceEndKind =
            RecurrenceEndKind.entries.firstOrNull { it.name == handle.get<String>(KEY_RECURRENCE_END_KIND) }
                ?: RecurrenceEndKind.NEVER,
        untilDate = handle.get<Long>(KEY_UNTIL_DATE)?.let(LocalDate::ofEpochDay),
        count = handle.get<Int>(KEY_COUNT)?.coerceAtLeast(1) ?: 1,
        reminders =
            handle
                .get<String>(KEY_REMINDERS)
                .orEmpty()
                .split(",")
                .mapNotNull { it.toIntOrNull() }
                .toSet(),
    )
}
