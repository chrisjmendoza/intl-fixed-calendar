package io.github.chrisjmendoza.yearal.feature.calendar.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.calendar.MonthGrid
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange
import io.github.chrisjmendoza.yearal.core.designsystem.picker.GregorianDatePickerDialog
import io.github.chrisjmendoza.yearal.core.designsystem.picker.IfcDatePicker
import io.github.chrisjmendoza.yearal.core.designsystem.picker.rememberIfcDatePickerState
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.navigation.DayKey
import io.github.chrisjmendoza.yearal.core.navigation.MonthKey
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.core.navigation.YearKey
import io.github.chrisjmendoza.yearal.feature.calendar.R
import kotlinx.coroutines.launch
import java.time.LocalDate

private val PageHorizontalPadding = 16.dp
private val JumpChooserOptionSpacing = 8.dp
private val JumpChooserMinTouchTarget = 48.dp

/**
 * The Calendar tab's Month pager (docs/FEATURES.md C1, C3, C5, C7): collects [MonthViewModel.uiState]
 * with the lifecycle and renders it through the stateless [MonthScreen]. This is the composable
 * `:app` places behind [MonthKey].
 *
 * The ViewModel is created for [key]'s month through [MonthViewModel.Factory] (the month is clamped
 * by [MonthPages.monthOf]); a tap on a day selects it and pushes [DayKey] with the day's Gregorian
 * epoch day (CLAUDE.md rule 4). Tapping the month heading zooms out to [YearKey] (ARCHITECTURE §4);
 * the jump-to-date action lets the user pick either calendar and pushes the [MonthKey] of the chosen
 * date (FEATURES C7).
 *
 * @param key the month to open on.
 * @param navigator where day taps, the title tap and the jump-to-date action navigate.
 * @param modifier applied to the screen's root.
 */
@Composable
fun MonthRoute(
    key: MonthKey,
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: MonthViewModel =
        hiltViewModel<MonthViewModel, MonthViewModel.Factory>(
            creationCallback = { factory -> factory.create(MonthPages.monthOf(key)) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MonthScreen(
        state = state,
        onPageChanged = viewModel::showPage,
        onDayClick = { date ->
            val gregorian = date.toLocalDate()
            viewModel.select(gregorian)
            navigator.navigate(DayKey(gregorian.toEpochDay()))
        },
        onTitleClick = { year -> navigator.navigate(YearKey(year)) },
        onJumpToDate = { date ->
            val month = IfcYearMonth.from(IfcDate.from(date))
            navigator.navigate(MonthKey(month.year, month.month.number))
        },
        modifier = modifier,
    )
}

/**
 * The stateless Month screen — the unit for previews, screenshot and Compose tests
 * (docs/ARCHITECTURE.md §4 "State management").
 *
 * A [HorizontalPager] over every month of [MonthPages] (spec §7.1: 1583..9999), one [MonthGrid] per
 * page, keeping one page warm on each side (ARCHITECTURE §3.4). The grid carries the month heading;
 * the app bar above shows the currently visible month as its own tappable title — tapping it invokes
 * [onTitleClick] with the year, the zoom-out to the Year view (docs/ARCHITECTURE.md §4;
 * docs/ROADMAP.md M3 T2) — a jump-to-date action (FEATURES C7) and the "Today" action, shown only
 * while the pager is away from today's month, which animates the pager to [MonthUiState.todayPage].
 * Leap Day and Year Day are the grid's band and reach [onDayClick] like any cell (spec §7.2).
 *
 * Opts in to the Material 3 experimental marker only because `TopAppBar`'s default arguments still
 * carry it.
 *
 * @param state what to show.
 * @param onPageChanged invoked with the page the pager settles towards, immediately on first
 * composition and on every change; the ViewModel evaluates holidays around it.
 * @param onDayClick invoked with the tapped day — a regular cell or the intercalary band.
 * @param modifier applied to the screen's root.
 * @param onTitleClick invoked with the currently visible page's IFC year when the app bar title is
 * tapped.
 * @param onJumpToDate invoked with the date chosen from the jump-to-date action, in either calendar.
 * @param pagerState the pager's state; defaults to a remembered state opened on
 * [MonthUiState.currentPage]. Overridable so tests can drive the pager directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    state: MonthUiState,
    onPageChanged: (Int) -> Unit,
    onDayClick: (IfcDate) -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (Int) -> Unit = {},
    onJumpToDate: (LocalDate) -> Unit = {},
    pagerState: PagerState = rememberPagerState(initialPage = state.currentPage) { MonthPages.COUNT },
) {
    val currentOnPageChanged by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> currentOnPageChanged(page) }
    }
    val scope = rememberCoroutineScope()
    val todayPage = state.todayPage
    val formatter = rememberIfcDateFormatter()
    var jumpChooserVisible by rememberSaveable { mutableStateOf(false) }
    var jumpGregorianVisible by rememberSaveable { mutableStateOf(false) }
    var jumpIfcVisible by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val visibleMonth = MonthPages.monthAt(pagerState.currentPage)
                    Text(
                        text = formatter.monthTitle(visibleMonth),
                        modifier =
                            Modifier.selectable(
                                selected = false,
                                role = Role.Button,
                                onClick = { onTitleClick(visibleMonth.year) },
                            ),
                    )
                },
                actions = {
                    IconButton(onClick = { jumpChooserVisible = true }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.month_jump_to_date),
                        )
                    }
                    if (todayPage != null && todayPage != pagerState.currentPage) {
                        TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(todayPage) } }) {
                            Text(stringResource(R.string.month_today))
                        }
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> page },
        ) { page ->
            val month = MonthPages.monthAt(page)
            MonthGrid(
                month = month,
                today = state.today,
                selected = state.selected,
                weekdayDisplay = state.weekdayDisplay,
                onDayClick = onDayClick,
                eventCounts = state.eventCountsByMonth[month].orEmpty(),
                holidays = state.holidaysByMonth[month].orEmpty(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PageHorizontalPadding),
            )
        }
    }

    // Jump to date (FEATURES C7): a small chooser, then the picker for the chosen calendar, mirroring
    // the event editor's "either calendar" pattern (docs/ARCHITECTURE.md §4 "Converter") without
    // depending on :feature:events (CLAUDE.md rule 10 — features never depend on other features).
    val jumpInitialDate = state.today ?: DatePickerRange.firstDate
    if (jumpChooserVisible) {
        JumpToDateChooserDialog(
            onPickGregorian = {
                jumpChooserVisible = false
                jumpGregorianVisible = true
            },
            onPickIfc = {
                jumpChooserVisible = false
                jumpIfcVisible = true
            },
            onDismiss = { jumpChooserVisible = false },
        )
    }
    if (jumpGregorianVisible) {
        GregorianDatePickerDialog(
            initialDate = jumpInitialDate,
            onConfirm = { date ->
                jumpGregorianVisible = false
                onJumpToDate(date)
            },
            onDismiss = { jumpGregorianVisible = false },
        )
    }
    if (jumpIfcVisible) {
        JumpToDateIfcDialog(
            initialDate = jumpInitialDate,
            onConfirm = { date ->
                jumpIfcVisible = false
                onJumpToDate(date)
            },
            onDismiss = { jumpIfcVisible = false },
        )
    }
}

/** Offers a choice of calendar before the jump-to-date pickers open (FEATURES C7). */
@Composable
private fun JumpToDateChooserDialog(
    onPickGregorian: () -> Unit,
    onPickIfc: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.month_jump_choose_calendar_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(JumpChooserOptionSpacing)) {
                TextButton(
                    onClick = onPickGregorian,
                    modifier = Modifier.fillMaxWidth().heightIn(min = JumpChooserMinTouchTarget),
                ) {
                    Text(stringResource(R.string.month_jump_pick_gregorian))
                }
                TextButton(
                    onClick = onPickIfc,
                    modifier = Modifier.fillMaxWidth().heightIn(min = JumpChooserMinTouchTarget),
                ) {
                    Text(stringResource(R.string.month_jump_pick_ifc))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.month_jump_cancel)) } },
    )
}

/** The IFC picker step of the jump-to-date action, wrapped in a dialog like the event editor's own. */
@Composable
private fun JumpToDateIfcDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberIfcDatePickerState(initialDate = IfcDate.from(initialDate))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { IfcDatePicker(state = pickerState) },
        confirmButton = {
            TextButton(
                onClick = { pickerState.date?.let { onConfirm(it.toLocalDate()) } },
                enabled = pickerState.date != null,
            ) {
                Text(stringResource(R.string.month_jump_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.month_jump_cancel)) } },
    )
}

// Previews — one per band shape (CLAUDE.md rule 6): none, Leap Day, Year Day. Roborazzi's preview
// scanner captures every @Preview once docs/ROADMAP.md M2 T10 records the goldens; dynamic colour is
// off for determinism. Today is the spec §4.1 worked example, Gregorian September 17, 2026.

/** Sol 2026: no intercalary day, so the band slot shows the Gregorian span. */
@Preview(name = "Sol 2026", showBackground = true)
@Composable
internal fun MonthScreenSolPreview() {
    MonthPreview(IfcYearMonth(2026, IfcMonth.SOL))
}

/** June 2028: a leap year, so the Leap Day band follows the fourth week. */
@Preview(name = "June 2028 (Leap Day)", showBackground = true)
@Composable
internal fun MonthScreenLeapDayPreview() {
    MonthPreview(IfcYearMonth(2028, IfcMonth.JUNE))
}

/** December 2026: the Year Day band, with the December holidays of the default packs. */
@Preview(name = "December 2026 (Year Day)", showBackground = true)
@Composable
internal fun MonthScreenYearDayPreview() {
    MonthPreview(
        month = IfcYearMonth(2026, IfcMonth.DECEMBER),
        holidays =
            mapOf(
                LocalDate.of(2026, 12, 24) to "Christmas Eve",
                LocalDate.of(2026, 12, 25) to "Christmas Day",
                LocalDate.of(2026, 12, 31) to "Year Day, New Year’s Eve",
            ),
    )
}

@Composable
private fun MonthPreview(
    month: IfcYearMonth,
    holidays: Map<LocalDate, String> = emptyMap(),
) {
    val today = LocalDate.of(2026, 9, 17)
    IfcTheme(dynamicColor = false) {
        MonthScreen(
            state =
                MonthUiState(
                    currentPage = MonthPages.pageOf(month),
                    today = today,
                    todayPage = MonthPages.pageOf(IfcYearMonth.from(IfcDate.from(today))),
                    selected = null,
                    holidaysByMonth = mapOf(month to holidays),
                ),
            onPageChanged = {},
            onDayClick = {},
        )
    }
}
