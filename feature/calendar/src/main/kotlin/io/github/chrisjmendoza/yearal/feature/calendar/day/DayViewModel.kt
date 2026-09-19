package io.github.chrisjmendoza.yearal.feature.calendar.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.AgendaItemUi
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.toAgendaItemUi
import io.github.chrisjmendoza.yearal.feature.calendar.holiday.HolidayCatalog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * State holder for the Day detail sheet (docs/ARCHITECTURE.md §4 "State management"; FEATURES C5).
 * The day is fixed at creation; whether it is "today" comes only from [DateTicker] (CLAUDE.md rule
 * 2), so the badge rolls over at local midnight, and the holiday list and the agenda follow
 * `UserSettings.enabledHolidaySets` and the store live. The agenda comes from [observeAgenda] — the
 * single place events and holidays are combined (docs/ARCHITECTURE.md §3.4) — queried for exactly
 * this one day.
 *
 * "Delete this occurrence" / "delete event" (FEATURES E1) is a two-step intent: [requestDelete] opens
 * a confirmation ([DayUiState.Loaded.pendingDelete]), [confirmDelete] applies it — an exdate on the
 * occurrence's **own** [AgendaItemUi.occurrenceDate] for a recurring event
 * ([EventRepository.addExdate]; never the date this sheet is showing, `docs/contracts/Events.md` §4),
 * or [EventRepository.deleteEvent] outright otherwise — and [cancelDelete] backs out. A deleted
 * occurrence is undoable through [events] and [undoDeleteOccurrence]; a deleted event is not (the
 * editor's own delete has no undo either).
 *
 * [epochDay] is assisted-injected from the `DayKey` of the entry (see [Factory]) and can be
 * synthesized from a widget or notification intent, so it is untrusted
 * (`docs/security-and-privacy.md` §6.3): [epochDayToDate] fails soft — as
 * `feature/converter`'s `ConverterViewModel` and `feature/events`'s `EventEditorViewModel` already do
 * for their own epoch-day inputs — to [DayUiState.Unavailable] rather than letting
 * `LocalDate.ofEpochDay` or `IfcDate.from` throw for a `Long` outside `LocalDate`'s own range or
 * outside years [IfcDate.MIN_YEAR]..[IfcDate.MAX_YEAR]. Tests build the ViewModel with the
 * constructor. Stops collecting five seconds after the last subscriber leaves.
 *
 * @param epochDay the Gregorian epoch day to show (CLAUDE.md rule 4: dates travel Gregorian).
 */
@HiltViewModel(assistedFactory = DayViewModel.Factory::class)
class DayViewModel
    @AssistedInject
    constructor(
        @Assisted epochDay: Long,
        dateTicker: DateTicker,
        settingsRepository: SettingsRepository,
        catalog: HolidayCatalog,
        formatter: IfcDateFormatter,
        observeAgenda: ObserveAgendaUseCase,
        private val eventRepository: EventRepository,
    ) : ViewModel() {
        /** Creates a [DayViewModel] for the entry's day; used by `hiltViewModel(creationCallback)`. */
        @AssistedFactory
        interface Factory {
            /** @param epochDay the Gregorian epoch day to show, from `DayKey.epochDay`. */
            fun create(epochDay: Long): DayViewModel
        }

        private val day: LocalDate? = epochDayToDate(epochDay)

        private val pendingDelete = MutableStateFlow<AgendaItemUi?>(null)
        private val outbox = Channel<DayEvent>(Channel.BUFFERED)

        /** One-shot outcomes of a delete: only [DayEvent.OccurrenceDeleted], to offer undo. */
        val events: Flow<DayEvent> = outbox.receiveAsFlow()

        /**
         * [DayUiState.Loading] until the first tick (immediate), then a [DayUiState.Loaded] per
         * change, or [DayUiState.Unavailable] once and for good when [epochDay] named no showable date.
         */
        val uiState: StateFlow<DayUiState> =
            (
                day?.let { validDay ->
                    combine(
                        dateTicker.today,
                        settingsRepository.settings,
                        observeAgenda(validDay..validDay),
                        pendingDelete,
                    ) { today, settings, agendas, pending ->
                        val holidays =
                            catalog
                                .labels(
                                    settings.enabledHolidaySets,
                                    validDay..validDay,
                                )[validDay]
                                .orEmpty()
                        val agenda = agendas[validDay]?.entries.orEmpty().map { it.toAgendaItemUi() }
                        buildDayUiState(validDay, today, formatter, holidays, agenda, pending)
                    }
                } ?: flowOf(DayUiState.Unavailable)
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DayUiState.Loading)

        /** Opens the delete confirmation for [item] (a row's overflow menu or long-press). */
        fun requestDelete(item: AgendaItemUi) {
            pendingDelete.value = item
        }

        /** Dismisses the delete confirmation without changing anything. */
        fun cancelDelete() {
            pendingDelete.value = null
        }

        /**
         * Applies the pending delete: [EventRepository.addExdate] on
         * [AgendaItemUi.occurrenceDate] for a recurring row (offering undo through [events]), or
         * [EventRepository.deleteEvent] for a non-recurring one. No-op if nothing is pending.
         */
        fun confirmDelete() {
            val pending = pendingDelete.value ?: return
            pendingDelete.value = null
            viewModelScope.launch {
                if (pending.isRecurring) {
                    eventRepository.addExdate(pending.eventId, pending.occurrenceDate)
                    outbox.send(DayEvent.OccurrenceDeleted(pending.eventId, pending.occurrenceDate))
                } else {
                    eventRepository.deleteEvent(pending.eventId)
                }
            }
        }

        /** Undoes an occurrence delete ([DayEvent.OccurrenceDeleted]'s snackbar action). */
        fun undoDeleteOccurrence(
            eventId: Long,
            occurrenceDate: LocalDate,
        ) {
            viewModelScope.launch { eventRepository.removeExdate(eventId, occurrenceDate) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * [LocalDate.ofEpochDay] throws outside `LocalDate`'s own representable range, and a value inside it
 * can still name a year outside [IfcDate.MIN_YEAR]..[IfcDate.MAX_YEAR], where `IfcDate.from` would
 * throw later. Both fail soft to `null` (`DayUiState.Unavailable`) — the same rule
 * `EventEditorViewModel`'s `validEpochDayToDate` applies to a prefill epoch day.
 */
private fun epochDayToDate(epochDay: Long): LocalDate? {
    val date = runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull() ?: return null
    return date.takeIf { it.year in IfcDate.MIN_YEAR..IfcDate.MAX_YEAR }
}

/** One-shot outcomes of the Day detail sheet's delete flow. */
sealed interface DayEvent {
    /**
     * One occurrence was excluded; the UI should offer undo.
     *
     * @property eventId the event it belongs to.
     * @property occurrenceDate the exdate added — the occurrence's own start date, for
     * [DayViewModel.undoDeleteOccurrence].
     */
    data class OccurrenceDeleted(
        val eventId: Long,
        val occurrenceDate: LocalDate,
    ) : DayEvent
}
