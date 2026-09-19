package io.github.chrisjmendoza.yearal.feature.calendar.year

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.calendar.IntercalaryBand
import io.github.chrisjmendoza.yearal.core.designsystem.calendar.YearMiniMonthTile
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.navigation.MonthKey
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.core.navigation.YearKey
import io.github.chrisjmendoza.yearal.feature.calendar.R
import java.time.LocalDate

private val TileMinWidth = 160.dp
private val GridSpacing = 12.dp
private val GridPadding = 16.dp

/** Test tag of the 13+1 tile grid, so a test can scope a node count to it and exclude the app bar. */
const val YEAR_GRID_TEST_TAG: String = "ifc:yearGrid"

/** Test tag of the Year Day tile, the grid's 14th item. */
const val YEAR_DAY_TILE_TEST_TAG: String = "ifc:yearDayTile"

/**
 * The Year overview (docs/FEATURES.md C6): collects [YearViewModel.uiState] with the lifecycle and
 * renders it through the stateless [YearScreen]. This is the composable `:app` places behind [YearKey].
 *
 * The ViewModel is created for [key]'s year through [YearViewModel.Factory]. Tapping a mini-month or
 * the Year Day tile pushes [MonthKey] for that month — Year Day belongs to December (`docs/calendar-spec.md`
 * §2.4 R8), so it opens the same December page the Month screen's own band would (CLAUDE.md rule 4:
 * only Gregorian epoch data and IFC year/month numbers travel through the key, never a computed date).
 *
 * @param key the year to open on.
 * @param navigator where a tile tap navigates.
 * @param modifier applied to the screen's root.
 */
@Composable
fun YearRoute(
    key: YearKey,
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: YearViewModel =
        hiltViewModel<YearViewModel, YearViewModel.Factory>(
            creationCallback = { factory -> factory.create(key.year) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    YearScreen(
        state = state,
        onPreviousYear = viewModel::previousYear,
        onNextYear = viewModel::nextYear,
        onGoToYear = viewModel::goToYear,
        onMonthClick = { month -> navigator.navigate(MonthKey(month.year, month.month.number)) },
        onYearDayClick = { navigator.navigate(MonthKey(state.year, IfcMonth.DECEMBER.number)) },
        modifier = modifier,
    )
}

/**
 * The stateless Year screen — the unit for previews, screenshot and Compose tests
 * (docs/ARCHITECTURE.md §4 "State management").
 *
 * Thirteen [YearMiniMonthTile]s (Sol between June and July) plus a fourteenth [IntercalaryBand] tile
 * for Year Day, in a `LazyVerticalGrid(GridCells.Adaptive(160.dp))` — two columns of seven rows on a
 * typical phone, exactly matching docs/ARCHITECTURE.md §4's "Year Day takes the 14th slot". Neither
 * tile builds 28 heavyweight day cells; see [YearMiniMonthTile]'s KDoc for why. The app bar holds the
 * year with previous/next actions (FEATURES C7), each disabled at the ends of
 * [io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange], and a "Today" action shown
 * only while another year is on screen.
 *
 * @param state what to show.
 * @param onPreviousYear invoked by the previous-year action.
 * @param onNextYear invoked by the next-year action.
 * @param onGoToYear invoked by the "Today" action with the real today's year.
 * @param onMonthClick invoked with the tapped mini-month.
 * @param onYearDayClick invoked when the Year Day tile is tapped.
 * @param modifier applied to the screen's root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearScreen(
    state: YearUiState,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onGoToYear: (Int) -> Unit,
    onMonthClick: (IfcYearMonth) -> Unit,
    onYearDayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberIfcDateFormatter()
    val todayYear = state.today?.year
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(formatter.formatNumber(state.year)) },
                actions = {
                    IconButton(onClick = onPreviousYear, enabled = state.canGoPrevious) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.year_previous),
                        )
                    }
                    IconButton(onClick = onNextYear, enabled = state.canGoNext) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.year_next),
                        )
                    }
                    if (todayYear != null && todayYear != state.year) {
                        TextButton(onClick = { onGoToYear(todayYear) }) {
                            Text(stringResource(R.string.month_today))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TileMinWidth),
            modifier = Modifier.padding(padding).fillMaxSize().testTag(YEAR_GRID_TEST_TAG),
            contentPadding = PaddingValues(GridPadding),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            items(state.months, key = { month -> month.month.number }) { month ->
                YearMiniMonthTile(
                    month = month,
                    today = state.today,
                    eventDates = state.eventDates,
                    onClick = { onMonthClick(month) },
                    formatter = formatter,
                )
            }
            item(key = YEAR_DAY_ITEM_KEY) {
                val yearDay = IfcDate.YearDay(state.year)
                val yearDayGregorian = yearDay.toLocalDate()
                IntercalaryBand(
                    day = yearDay,
                    isToday = state.today == yearDayGregorian,
                    isSelected = false,
                    eventCount = if (yearDayGregorian in state.eventDates) 1 else 0,
                    holidayName = null,
                    onClick = onYearDayClick,
                    modifier = Modifier.testTag(YEAR_DAY_TILE_TEST_TAG),
                )
            }
        }
    }
}

private const val YEAR_DAY_ITEM_KEY = "yearDay"

// Previews — a common year and a leap year (CLAUDE.md rule 6). Roborazzi's preview scanner captures
// every @Preview once docs/ROADMAP.md M2 T10 records the goldens; dynamic colour is off for determinism.

/** 2026: a common year, no Leap Day tile. */
@Preview(name = "2026 (common year)", showBackground = true)
@Composable
internal fun YearScreenCommonYearPreview() {
    YearPreview(year = 2026, today = LocalDate.of(2026, 9, 17))
}

/** 2028: a leap year, so June's tile shows the Leap Day indicator. */
@Preview(name = "2028 (leap year)", showBackground = true)
@Composable
internal fun YearScreenLeapYearPreview() {
    YearPreview(year = 2028, today = LocalDate.of(2028, 6, 17))
}

@Composable
private fun YearPreview(
    year: Int,
    today: LocalDate,
) {
    IfcTheme(dynamicColor = false) {
        YearScreen(
            state = YearUiState(year = year, today = today),
            onPreviousYear = {},
            onNextYear = {},
            onGoToYear = {},
            onMonthClick = {},
            onYearDayClick = {},
        )
    }
}
