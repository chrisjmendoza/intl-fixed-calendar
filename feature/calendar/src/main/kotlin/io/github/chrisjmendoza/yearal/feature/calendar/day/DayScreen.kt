package io.github.chrisjmendoza.yearal.feature.calendar.day

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.navigation.DayKey
import io.github.chrisjmendoza.yearal.core.navigation.EventEditorKey
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.feature.calendar.R
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.AgendaItemUi
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SheetHorizontalPadding = 24.dp
private val SheetBottomPadding = 32.dp
private val BlockSpacing = 16.dp
private val LineSpacing = 4.dp
private val ChipHorizontalPadding = 12.dp
private val ChipVerticalPadding = 4.dp
private val LoadingHeight = 160.dp
private val ColorDotSize = 12.dp
private val AgendaRowSpacing = 12.dp

/**
 * The Day detail (docs/FEATURES.md C5): collects [DayViewModel.uiState] with the lifecycle and renders
 * it through the stateless [DayScreen]. This is the composable `:app` places behind [DayKey].
 *
 * The ViewModel is created for [key]'s date through [DayViewModel.Factory]; dismissing the sheet pops
 * the entry with [Navigator.goBack]. Tapping an agenda row or "Add event" pushes [EventEditorKey]
 * with the event's id or [DayKey.epochDay] as the prefill, ids only (CLAUDE.md rule 8).
 *
 * @param key the day to show, as a Gregorian epoch day (CLAUDE.md rule 4).
 * @param navigator popped when the sheet is dismissed; navigated to the event editor on a tap.
 * @param modifier applied to the sheet.
 */
@Composable
fun DayRoute(
    key: DayKey,
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: DayViewModel =
        hiltViewModel<DayViewModel, DayViewModel.Factory>(
            creationCallback = { factory -> factory.create(LocalDate.ofEpochDay(key.epochDay)) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DayScreen(
        state = state,
        onDismiss = navigator::goBack,
        onEventClick = { eventId -> navigator.navigate(EventEditorKey(eventId = eventId)) },
        onAddEvent = { navigator.navigate(EventEditorKey(prefillEpochDay = key.epochDay)) },
        modifier = modifier,
    )
}

/**
 * The stateless Day detail — a [ModalBottomSheet] (docs/ARCHITECTURE.md §4 "Screen behaviors":
 * a bottom sheet on compact widths; the expanded-width pane is docs/ROADMAP.md M3) around
 * [DayDetail], the unit for previews, screenshot and Compose tests.
 *
 * Navigation 3 renders the day as its own entry, so the sheet sits over whatever the tab shows
 * beneath; [onDismiss] is invoked by the scrim, the back gesture, a swipe down and the close button.
 *
 * Opts in to the Material 3 experimental marker because `ModalBottomSheet` still carries it.
 *
 * @param state what to show.
 * @param onDismiss invoked when the user dismisses the sheet.
 * @param modifier applied to the sheet.
 * @param onEventClick invoked with an agenda row's event id.
 * @param onAddEvent invoked by the "Add event" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    state: DayUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onEventClick: (Long) -> Unit = {},
    onAddEvent: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        DayDetail(state = state, onClose = onDismiss, onEventClick = onEventClick, onAddEvent = onAddEvent)
    }
}

/**
 * The sheet's content: the IFC date as a heading with its numeric form and the `IFC` marker, the
 * Gregorian date, a "Today" badge when the day is today, both weekdays explicitly labelled (spec
 * §4.1; "no IFC weekday" on Leap Day and Year Day), day/week/quarter, the day's holidays under a
 * "Holidays" heading, and its events under an "Events" heading (FEATURES C5) — both headings omitted
 * when there is nothing to show — followed by an "Add event" action. "Open in converter" arrives with
 * its own feature; nothing here is a dead control.
 *
 * @param state what to show.
 * @param onClose the close button's action.
 * @param modifier applied to the content column.
 * @param onEventClick invoked with an agenda row's event id.
 * @param onAddEvent invoked by the "Add event" action.
 */
@Composable
fun DayDetail(
    state: DayUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onEventClick: (Long) -> Unit = {},
    onAddEvent: () -> Unit = {},
) {
    when (state) {
        DayUiState.Loading -> LoadingContent(modifier)
        is DayUiState.Loaded -> LoadedContent(state, onClose, onEventClick, onAddEvent, modifier)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    val loading = stringResource(R.string.day_loading)
    Box(
        modifier = modifier.fillMaxWidth().height(LoadingHeight),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loading })
    }
}

@Composable
private fun LoadedContent(
    state: DayUiState.Loaded,
    onClose: () -> Unit,
    onEventClick: (Long) -> Unit,
    onAddEvent: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = SheetHorizontalPadding, end = SheetHorizontalPadding, bottom = SheetBottomPadding),
        verticalArrangement = Arrangement.spacedBy(LineSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.ifcLong,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.day_close))
            }
        }
        Text(
            text = state.numeric,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.day_gregorian, state.gregorianLong),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.isToday) {
            TodayBadge()
        }

        Spacer(modifier = Modifier.height(BlockSpacing))

        WeekdayBlock(state)

        Spacer(modifier = Modifier.height(BlockSpacing))

        Text(
            text = stringResource(R.string.day_day_week_quarter, state.dayAndWeek, state.quarter),
            style = MaterialTheme.typography.bodyLarge,
        )

        if (state.holidays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(BlockSpacing))
            Text(
                text = stringResource(R.string.day_holidays),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            for (holiday in state.holidays) {
                Text(text = holiday, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (state.agenda.isNotEmpty()) {
            Spacer(modifier = Modifier.height(BlockSpacing))
            Text(
                text = stringResource(R.string.day_events),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(AgendaRowSpacing)) {
                for (item in state.agenda) {
                    AgendaRow(item = item, onClick = { onEventClick(item.eventId) })
                }
            }
        }

        Spacer(modifier = Modifier.height(BlockSpacing))

        TextButton(onClick = onAddEvent) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(ChipHorizontalPadding))
            Text(text = stringResource(R.string.day_add_event))
        }
    }
}

/** A static "Today" pill, deliberately not a button: it states a fact and does nothing (no dead controls). */
@Composable
private fun TodayBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(R.string.day_today),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = ChipHorizontalPadding, vertical = ChipVerticalPadding),
        )
    }
}

/**
 * Both weekdays, each line labelled by the formatter (spec §4.1 item 4) and merged into one spoken
 * description, "IFC Sunday, actual Thursday" (§4.1 item 7), so neither can be mistaken for the other.
 */
@Composable
private fun WeekdayBlock(state: DayUiState.Loaded) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { contentDescription = state.weekdaysDescription },
    ) {
        Column(
            modifier = Modifier.padding(BlockSpacing),
            verticalArrangement = Arrangement.spacedBy(LineSpacing),
        ) {
            Text(text = state.nominalWeekday, style = MaterialTheme.typography.bodyLarge)
            Text(text = state.actualWeekday, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * One row of the day's agenda (FEATURES C5): a coloured dot (never colour alone — the title and time
 * carry the same information in text), the title with a localized placeholder when blank, and "All
 * day" or the locale-formatted time range. Tapping the row invokes [onClick] with nothing but the
 * event id already bound by the caller (CLAUDE.md rule 8).
 */
@Composable
private fun AgendaRow(
    item: AgendaItemUi,
    onClick: () -> Unit,
) {
    val title = item.title.ifBlank { stringResource(R.string.agenda_untitled_event) }
    val timeLabel =
        if (item.isAllDay) {
            stringResource(R.string.agenda_all_day)
        } else {
            val locale = LocalConfiguration.current.locales[0]
            val timeFormatter =
                remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
            stringResource(
                R.string.agenda_time_range,
                timeFormatter.format(item.startTime ?: LocalTime.MIDNIGHT),
                timeFormatter.format(item.endTime ?: LocalTime.MIDNIGHT),
            )
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = "$title, $timeLabel" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipHorizontalPadding),
    ) {
        Box(
            modifier =
                Modifier
                    .size(ColorDotSize)
                    .background(Color(item.colorArgb), CircleShape),
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Previews of the sheet content — one per date shape (CLAUDE.md rule 6). A ModalBottomSheet opens a
// window of its own, which the preview scanner cannot capture, so the previews render DayDetail.
// Dynamic colour is off for determinism.

/** IFC December 23, 2026 = Gregorian Friday, December 25, 2026, with its holiday. */
@Preview(name = "Regular day", showBackground = true)
@Composable
internal fun DayDetailRegularPreview() {
    DayPreview(day = LocalDate.of(2026, 12, 25), today = LocalDate.of(2026, 12, 25), holidays = listOf("Christmas Day"))
}

/** Leap Day 2028 = Gregorian Saturday, June 17, 2028. */
@Preview(name = "Leap Day", showBackground = true)
@Composable
internal fun DayDetailLeapDayPreview() {
    DayPreview(day = LocalDate.of(2028, 6, 17), today = LocalDate.of(2026, 9, 17), holidays = listOf("Leap Day"))
}

/** Year Day 2026 = Gregorian Thursday, December 31, 2026. */
@Preview(name = "Year Day", showBackground = true)
@Composable
internal fun DayDetailYearDayPreview() {
    DayPreview(
        day = LocalDate.of(2026, 12, 31),
        today = LocalDate.of(2026, 9, 17),
        holidays = listOf("Year Day", "New Year’s Eve"),
    )
}

@Composable
private fun DayPreview(
    day: LocalDate,
    today: LocalDate,
    holidays: List<String>,
) {
    IfcTheme(dynamicColor = false) {
        val formatter = rememberIfcDateFormatter()
        DayDetail(state = buildDayUiState(day, today, formatter, holidays), onClose = {})
    }
}
