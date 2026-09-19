package io.github.chrisjmendoza.yearal.feature.events.list

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.navigation.EventEditorKey
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.feature.events.R

private val RowMinHeight = 48.dp
private val ScreenPadding = 16.dp
private val RowSpacing = 4.dp
private val SwatchSize = 16.dp
private val RowContentSpacing = 12.dp
private val SectionSpacing = 8.dp

/**
 * The events list (`EventListKey`, `docs/FEATURES.md` E1, E3, E5–E7, E9): collects
 * [EventListViewModel.uiState] and renders it through the stateless [EventListScreen]. This is the
 * composable `:app` places behind `EventListKey`.
 *
 * @param navigator receives [EventEditorKey] when the user taps a row or the "Add event" button.
 * @param modifier applied to the screen's root [Scaffold].
 */
@Composable
fun EventListRoute(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: EventListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EventListScreen(
        state = state,
        onQueryChange = viewModel::setQuery,
        onAddEvent = { navigator.navigate(EventEditorKey()) },
        onOpenEvent = { id -> navigator.navigate(EventEditorKey(eventId = id)) },
        modifier = modifier,
    )
}

/**
 * The stateless events list — the unit for previews and Compose tests. A search field, then either an
 * empty state or the rows: title (a placeholder when blank), both dates, time or "all day", a
 * recurrence summary and a "hidden calendar" note, each tappable to edit; a FAB creates a new event.
 *
 * @param onQueryChange receives the search field's text after every edit.
 * @param onAddEvent the FAB's action.
 * @param onOpenEvent receives the tapped row's event id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    state: EventListUiState,
    onQueryChange: (String) -> Unit,
    onAddEvent: () -> Unit,
    onOpenEvent: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val addDescription = stringResource(R.string.events_add)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.events_list_title)) }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddEvent,
                modifier = Modifier.semantics { contentDescription = addDescription },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = if (state is EventListUiState.Loaded) state.query else "",
                onQueryChange = onQueryChange,
            )
            when (state) {
                EventListUiState.Loading -> {
                    Unit
                }

                is EventListUiState.Loaded -> {
                    if (state.items.isEmpty()) {
                        EmptyState(hasAnyEvents = state.hasAnyEvents, hasQuery = state.query.isNotBlank())
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.items, key = EventListItem::eventId) { item ->
                                EventRow(item = item, onClick = { onOpenEvent(item.eventId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = SectionSpacing),
        label = { Text(stringResource(R.string.events_search_label)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.events_search_clear))
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun EmptyState(
    hasAnyEvents: Boolean,
    hasQuery: Boolean,
) {
    val message =
        if (hasAnyEvents && hasQuery) {
            stringResource(R.string.events_empty_no_matches)
        } else {
            stringResource(R.string.events_empty_no_events)
        }
    Box(modifier = Modifier.fillMaxSize().padding(ScreenPadding), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventRow(
    item: EventListItem,
    onClick: () -> Unit,
) {
    val title = item.title.ifBlank { stringResource(R.string.events_no_title) }
    val calendarName = item.calendarName.ifBlank { stringResource(R.string.events_calendar_default_name) }
    val allDayLabel = stringResource(R.string.events_all_day)
    val zoneLabel = item.zoneLabel
    val timeText =
        when {
            item.isAllDay -> allDayLabel
            zoneLabel != null -> stringResource(R.string.events_time_with_zone, item.timeLabel.orEmpty(), zoneLabel)
            else -> item.timeLabel.orEmpty()
        }
    val recurrenceText = item.recurrenceSummary?.let { recurrenceSummaryText(it) }
    val hiddenCalendarText = stringResource(R.string.events_calendar_hidden)
    val rowDescription =
        stringResource(R.string.events_row_description, title, item.ifcLong, item.gregorianLong, timeText)
    val description =
        buildString {
            append(rowDescription)
            if (recurrenceText != null) {
                append(' ')
                append(recurrenceText)
            }
            if (item.calendarHidden) {
                append(' ')
                append(hiddenCalendarText)
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = description
                }.padding(horizontal = ScreenPadding, vertical = SectionSpacing),
        horizontalArrangement = Arrangement.spacedBy(RowContentSpacing),
    ) {
        Box(
            modifier =
                Modifier
                    .size(SwatchSize)
                    .background(color = Color(item.calendarColorArgb), shape = CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = calendarName, style = MaterialTheme.typography.bodySmall)
            Text(text = item.ifcLong, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = item.ifcNumeric,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = item.gregorianLong, style = MaterialTheme.typography.bodyMedium)
            Text(text = timeText, style = MaterialTheme.typography.bodyMedium)
            if (recurrenceText != null) {
                Text(text = recurrenceText, style = MaterialTheme.typography.bodySmall)
            }
            if (item.calendarHidden) {
                Text(
                    text = hiddenCalendarText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Turns a [RecurrenceSummary] into localized text — the only place that maps its branches to strings. */
@Composable
internal fun recurrenceSummaryText(summary: RecurrenceSummary): String =
    when (summary) {
        is RecurrenceSummary.YearlyIfc -> {
            stringResource(R.string.events_recurrence_yearly_ifc, summary.dayLabel)
        }

        RecurrenceSummary.YearlyYearDay -> {
            stringResource(R.string.events_recurrence_year_day)
        }

        is RecurrenceSummary.YearlyLeapDay -> {
            when (summary.policy) {
                LeapDayPolicy.JUNE_28 -> stringResource(R.string.events_recurrence_leap_day_june28)
                LeapDayPolicy.SKIP -> stringResource(R.string.events_recurrence_leap_day_skip)
                LeapDayPolicy.SOL_1 -> stringResource(R.string.events_recurrence_leap_day_sol1)
            }
        }

        is RecurrenceSummary.MonthlyIfc -> {
            stringResource(R.string.events_recurrence_monthly_ifc, summary.day)
        }

        is RecurrenceSummary.YearlyGregorian -> {
            stringResource(R.string.events_recurrence_yearly_gregorian, summary.dayLabel)
        }

        RecurrenceSummary.Weekly -> {
            stringResource(R.string.events_recurrence_weekly)
        }

        RecurrenceSummary.OtherRecurring -> {
            stringResource(R.string.events_recurrence_other)
        }
    }

// Previews — a populated list, the empty state, and 200% font (docs/ARCHITECTURE.md §4 "Accessibility").

private val previewItems =
    listOf(
        EventListItem(
            eventId = 1,
            title = "Sol 13 picnic",
            calendarName = "",
            calendarColorArgb = EventCalendar.DEFAULT_COLOR_ARGB,
            calendarHidden = false,
            ifcLong = "Sol 13, 2026",
            ifcNumeric = "IFC 2026-07-13",
            gregorianLong = "Tuesday, June 30, 2026",
            isAllDay = true,
            timeLabel = null,
            zoneLabel = null,
            recurrenceSummary = RecurrenceSummary.YearlyIfc("Sol 13"),
        ),
        EventListItem(
            eventId = 2,
            title = "",
            calendarName = "Work",
            calendarColorArgb = 0xFF5B8DEF.toInt(),
            calendarHidden = true,
            ifcLong = "Year Day, 2026",
            ifcNumeric = "IFC 2026-13-29",
            gregorianLong = "Thursday, December 31, 2026",
            isAllDay = false,
            timeLabel = "9:30 AM",
            zoneLabel = "America/New_York",
            recurrenceSummary = null,
        ),
    )

@Preview(name = "Events list", showBackground = true)
@Preview(name = "Events list, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Events list, font 2.0", showBackground = true, fontScale = 2f, heightDp = 1000)
@Composable
internal fun EventListScreenPreview() {
    IfcTheme(dynamicColor = false) {
        EventListScreen(
            state = EventListUiState.Loaded(items = previewItems, query = "", hasAnyEvents = true),
            onQueryChange = {},
            onAddEvent = {},
            onOpenEvent = {},
        )
    }
}

@Preview(name = "Events list, empty", showBackground = true)
@Composable
internal fun EventListScreenEmptyPreview() {
    IfcTheme(dynamicColor = false) {
        EventListScreen(
            state = EventListUiState.Loaded(items = emptyList(), query = "", hasAnyEvents = false),
            onQueryChange = {},
            onAddEvent = {},
            onOpenEvent = {},
        )
    }
}
