package io.github.chrisjmendoza.yearal.feature.calendar.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
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
import io.github.chrisjmendoza.yearal.core.navigation.EventEditorKey
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.feature.calendar.R
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.AgendaItemUi
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Today tab (docs/FEATURES.md T1–T4, T6): collects [TodayViewModel.uiState] with the lifecycle
 * and renders it through the stateless [TodayScreen]. This is the composable `:app` places behind
 * `TodayKey`. Tapping an agenda row pushes [EventEditorKey] with the event's id only (CLAUDE.md rule
 * 8); holidays stay non-tappable (docs/ROADMAP.md, left over from M4 T7).
 *
 * @param navigator where an agenda row tap navigates.
 * @param modifier applied to the screen's root; the screen adds its own safe-drawing insets.
 */
@Composable
fun TodayRoute(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        onAgendaItemClick = { eventId -> navigator.navigate(EventEditorKey(eventId = eventId)) },
        modifier = modifier,
    )
}

/**
 * The stateless Today screen — the unit for previews, screenshot and Compose tests
 * (docs/ARCHITECTURE.md §4 "State management").
 *
 * Shows the hero IFC date, its numeric form with the `IFC` marker, the Gregorian equivalent, both
 * weekdays explicitly labelled (spec §4.1; on Leap Day and Year Day the IFC line reads "no IFC
 * weekday"), day/week/quarter, a year-progress bar and the countdown to the next intercalary day.
 *
 * @param onAgendaItemClick invoked with an agenda row's event id (FEATURES T5); holidays are plain text.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    onAgendaItemClick: (Long) -> Unit = {},
) {
    when (state) {
        TodayUiState.Loading -> LoadingContent(modifier)
        is TodayUiState.Loaded -> LoadedContent(state, modifier, onAgendaItemClick)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    val loading = stringResource(R.string.today_loading)
    Box(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loading })
    }
}

@Composable
private fun LoadedContent(
    state: TodayUiState.Loaded,
    modifier: Modifier,
    onAgendaItemClick: (Long) -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = state.heroDate,
            // displaySmall keeps "September 28, 2026" on one line at 360 dp; displayMedium wrapped the year.
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = state.numericDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.today_gregorian, state.gregorianLongDate),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(16.dp))

        WeekdayBlock(state)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.today_day_week_quarter, state.dayAndWeek, state.quarter),
            style = MaterialTheme.typography.bodyLarge,
        )
        LinearProgressIndicator(
            progress = { state.yearProgress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = state.yearProgressLabel },
        )
        Text(
            text = state.yearProgressLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.countdown?.let { countdown ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = countdown, style = MaterialTheme.typography.bodyLarge)
        }

        val nextHolidayDays = state.nextHolidayDays
        val nextHolidayName = state.nextHolidayName
        if (nextHolidayDays != null && nextHolidayName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    pluralStringResource(
                        R.plurals.today_next_holiday,
                        nextHolidayDays,
                        nextHolidayDays,
                        nextHolidayName,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (state.holidays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.today_holidays),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            for (holiday in state.holidays) {
                Text(text = holiday, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (state.agenda.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.today_agenda_heading),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (item in state.agenda) {
                    TodayAgendaRow(item, onClick = { onAgendaItemClick(item.eventId) })
                }
            }
        }
    }
}

/**
 * One row of today's agenda (FEATURES T5): a coloured dot (never colour alone — the title and time
 * carry the same information in text), the title with a localized placeholder when blank, and "All
 * day" or the locale-formatted time range. Tapping the row invokes [onClick] with nothing but the
 * event id already bound by the caller (CLAUDE.md rule 8), same as the Day detail's agenda row;
 * holidays elsewhere on this screen stay plain text, since they have nothing to open.
 */
@Composable
private fun TodayAgendaRow(
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
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "$title, $timeLabel"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(12.dp).background(Color(item.colorArgb), CircleShape))
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

/**
 * Both weekdays, each line labelled by the formatter (spec §4.1 item 4) and merged into one spoken
 * description, "IFC Sunday, actual Thursday" (§4.1 item 7), so neither can be mistaken for the other.
 */
@Composable
private fun WeekdayBlock(state: TodayUiState.Loaded) {
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = state.nominalWeekday, style = MaterialTheme.typography.bodyLarge)
            Text(text = state.actualWeekday, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// Previews — one per date shape (CLAUDE.md rule 6). Roborazzi's preview scanner captures every
// @Preview once docs/ROADMAP.md M2 T10 records the goldens; dynamic colour is off for determinism.

/** IFC September 8, 2026 = Gregorian Thursday, September 17, 2026 (the spec §4.1 worked example). */
@Preview(name = "Regular day", showBackground = true)
@Composable
internal fun TodayScreenRegularPreview() {
    TodayPreview(LocalDate.of(2026, 9, 17))
}

/** Leap Day 2028 = Gregorian Saturday, June 17, 2028. */
@Preview(name = "Leap Day", showBackground = true)
@Composable
internal fun TodayScreenLeapDayPreview() {
    TodayPreview(LocalDate.of(2028, 6, 17))
}

/** Year Day 2026 = Gregorian Thursday, December 31, 2026. */
@Preview(name = "Year Day", showBackground = true)
@Composable
internal fun TodayScreenYearDayPreview() {
    TodayPreview(LocalDate.of(2026, 12, 31))
}

@Composable
private fun TodayPreview(today: LocalDate) {
    IfcTheme(dynamicColor = false) {
        val formatter = rememberIfcDateFormatter()
        TodayScreen(state = buildTodayUiState(today, formatter))
    }
}
