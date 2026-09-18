package io.github.chrisjmendoza.yearal.feature.calendar.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.feature.calendar.R
import java.time.LocalDate

private val SheetHorizontalPadding = 24.dp
private val SheetBottomPadding = 32.dp
private val BlockSpacing = 16.dp
private val LineSpacing = 4.dp
private val ChipHorizontalPadding = 12.dp
private val ChipVerticalPadding = 4.dp
private val LoadingHeight = 160.dp

/**
 * The Day detail (docs/FEATURES.md C5): collects [DayViewModel.uiState] with the lifecycle and renders
 * it through the stateless [DayScreen]. This is the composable `:app` places behind [DayKey].
 *
 * The ViewModel is created for [key]'s date through [DayViewModel.Factory]; dismissing the sheet pops
 * the entry with [Navigator.goBack].
 *
 * @param key the day to show, as a Gregorian epoch day (CLAUDE.md rule 4).
 * @param navigator popped when the sheet is dismissed.
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
    DayScreen(state = state, onDismiss = navigator::goBack, modifier = modifier)
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    state: DayUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        DayDetail(state = state, onClose = onDismiss)
    }
}

/**
 * The sheet's content: the IFC date as a heading with its numeric form and the `IFC` marker, the
 * Gregorian date, a "Today" badge when the day is today, both weekdays explicitly labelled (spec
 * §4.1; "no IFC weekday" on Leap Day and Year Day), day/week/quarter, and the day's holidays under
 * a "Holidays" heading — omitted when there are none. Events, "Add event" and "Open in converter"
 * arrive with their features (docs/ROADMAP.md M3–M4); nothing here is a dead control.
 *
 * @param state what to show.
 * @param onClose the close button's action.
 * @param modifier applied to the content column.
 */
@Composable
fun DayDetail(
    state: DayUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        DayUiState.Loading -> LoadingContent(modifier)
        is DayUiState.Loaded -> LoadedContent(state, onClose, modifier)
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
