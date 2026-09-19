package io.github.chrisjmendoza.yearal.feature.events.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
import io.github.chrisjmendoza.yearal.core.domain.event.IfcRecurrence
import io.github.chrisjmendoza.yearal.core.domain.event.IntercalaryDay
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/**
 * State holder for the events list (`EventListKey`, `docs/ROADMAP.md` M4 T5). Joins
 * [EventRepository.observeEvents] with [EventRepository.observeCalendars] for colour and calendar
 * visibility, and applies an in-memory search over the title, notes and location (FEATURES E9;
 * `docs/contracts/Events.md` §2 "Search is an in-memory filter" and decision 12) — there is no search
 * query on the repository.
 *
 * The list keeps [Event.LIST_ORDER] (the contract's list order) unchanged; it does not reorder around
 * "today" because that needs `RecurrenceExpander`, which is out of this task's scope
 * (`docs/contracts/Events.md` §7 "T5").
 *
 * @param savedStateHandle keeps the search text across process death.
 * @param eventRepository the events and calendars to show.
 * @param formatter renders every date through `:core:calendar`, never computing one itself (CLAUDE.md rule 1).
 */
@HiltViewModel
class EventListViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        eventRepository: EventRepository,
        formatter: IfcDateFormatter,
    ) : ViewModel() {
        private val query = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY) ?: "")

        /** [EventListUiState.Loading] until both flows have emitted, then a [EventListUiState.Loaded] per change. */
        val uiState: StateFlow<EventListUiState> =
            combine(
                eventRepository.observeEvents(),
                eventRepository.observeCalendars(),
                query,
            ) { events, calendars, q ->
                buildEventListUiState(events, calendars, q, formatter)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EventListUiState.Loading)

        /** Updates the search text; an empty string shows every event. */
        fun setQuery(text: String) {
            savedStateHandle[KEY_QUERY] = text
            query.value = text
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val KEY_QUERY = "events.list.query"
        }
    }

/** Builds the loaded state: filters [events] by [query], then joins each with its calendar. */
internal fun buildEventListUiState(
    events: List<Event>,
    calendars: List<EventCalendar>,
    query: String,
    formatter: IfcDateFormatter,
): EventListUiState.Loaded {
    val calendarsById = calendars.associateBy(EventCalendar::id)
    val items =
        events
            .filter { matchesQuery(it, query) }
            .map { event -> buildEventListItem(event, calendarsById[event.calendarId], formatter) }
    return EventListUiState.Loaded(items = items, query = query, hasAnyEvents = events.isNotEmpty())
}

/** Case-insensitive substring match on the title, notes and location — the fields a user searches by. */
internal fun matchesQuery(
    event: Event,
    query: String,
): Boolean {
    if (query.isBlank()) return true
    return event.title.contains(query, ignoreCase = true) ||
        event.description.contains(query, ignoreCase = true) ||
        event.location.contains(query, ignoreCase = true)
}

private fun buildEventListItem(
    event: Event,
    calendar: EventCalendar?,
    formatter: IfcDateFormatter,
): EventListItem {
    val ifcDate = IfcDate.from(event.startDate)
    val timing = event.timing
    return EventListItem(
        eventId = event.id,
        title = event.title,
        calendarName = calendar?.name.orEmpty(),
        calendarColorArgb = event.colorArgb ?: calendar?.colorArgb ?: EventCalendar.DEFAULT_COLOR_ARGB,
        calendarHidden = calendar?.visible == false,
        ifcLong = formatter.formatLong(ifcDate),
        ifcNumeric = formatter.formatNumeric(ifcDate),
        gregorianLong = formatter.formatGregorianLong(event.startDate),
        isAllDay = event.isAllDay,
        timeLabel = (timing as? EventTiming.Timed)?.let { formatMinuteOfDay(it.startMinuteOfDay) },
        zoneLabel = (timing as? EventTiming.Timed)?.zone?.id,
        recurrenceSummary = recurrenceSummaryFor(event.recurrence, ifcDate, event.startDate, formatter),
    )
}

private fun formatMinuteOfDay(minuteOfDay: Int): String {
    val time = LocalTime.ofSecondOfDay(minuteOfDay * SECONDS_PER_MINUTE.toLong())
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(time)
}

/**
 * The [RecurrenceSummary] of [recurrence], anchored on [ifcDate] / [startDate]. Because
 * [IfcRecurrence.isAnchoredOn] is an [Event] invariant, an IFC rule's own position (month/day,
 * intercalary day or day of month) always matches [ifcDate] — deriving the label from the anchor
 * rather than the rule avoids reconstructing a second [IfcDate] (CLAUDE.md rule 1).
 */
internal fun recurrenceSummaryFor(
    recurrence: Recurrence,
    ifcDate: IfcDate,
    startDate: java.time.LocalDate,
    formatter: IfcDateFormatter,
): RecurrenceSummary? =
    when (recurrence) {
        Recurrence.None -> {
            null
        }

        is IfcRecurrence.YearlyOnDate -> {
            RecurrenceSummary.YearlyIfc(formatter.formatDay(ifcDate))
        }

        is IfcRecurrence.YearlyOnIntercalary -> {
            when (val day = recurrence.day) {
                is IntercalaryDay.YearDay -> RecurrenceSummary.YearlyYearDay
                is IntercalaryDay.LeapDay -> RecurrenceSummary.YearlyLeapDay(day.commonYearPolicy)
            }
        }

        is IfcRecurrence.MonthlyOnDay -> {
            RecurrenceSummary.MonthlyIfc(recurrence.day)
        }

        is Recurrence.Gregorian -> {
            when {
                recurrence.rrule.startsWith("FREQ=YEARLY") -> {
                    RecurrenceSummary.YearlyGregorian(formatter.formatGregorianMonthDay(startDate))
                }

                recurrence.rrule.startsWith("FREQ=WEEKLY") -> {
                    RecurrenceSummary.Weekly
                }

                else -> {
                    RecurrenceSummary.OtherRecurring
                }
            }
        }
    }

private const val SECONDS_PER_MINUTE = 60
