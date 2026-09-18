package io.github.chrisjmendoza.yearal.core.designsystem.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import java.time.LocalDate

/** Rows of an IFC month grid: four whole weeks (spec §2.2). */
const val GRID_ROWS: Int = IfcMonth.DAYS_PER_MONTH / GRID_COLUMNS

private val TitleBottomPadding = 8.dp

/**
 * One IFC month as the perpetual 4 × 7 grid (FEATURES C1–C4; spec §7.2; docs/ARCHITECTURE.md §4):
 * the month title as a heading (`Sol 2028`), the [WeekdayHeaders], exactly [GRID_ROWS] rows of
 * [GRID_COLUMNS] [DayCell]s, then the band slot — an [IntercalaryBand] for June in leap years and for
 * December, otherwise an [IntercalaryPlaceholder] of the same height, so a pager of months never
 * jumps.
 *
 * Today and the selection are matched by **Gregorian** date (CLAUDE.md rule 4): a `today` in another
 * month simply marks nothing. Event counts and holidays are likewise keyed by the Gregorian date of
 * each day, which is what the agenda query returns (ARCHITECTURE §3.4). Every date shown is built by
 * `:core:calendar`; the grid computes none.
 *
 * The grid is a semantics traversal group; the cells are a plain `Column` of `Row`s, which keeps
 * screenshot and semantics tests simple.
 *
 * @param month the month to show.
 * @param today the real today as a Gregorian date, or `null` to mark no cell.
 * @param selected the selected day as a Gregorian date, or `null` for no selection.
 * @param weekdayDisplay which header rows to show (the user setting).
 * @param onDayClick invoked with the tapped day — a [IfcDate.Regular] cell, or the band's
 * [IfcDate.LeapDay] / [IfcDate.YearDay].
 * @param modifier applied to the grid; it fills the available width itself.
 * @param eventCounts number of events per Gregorian date; absent dates have none.
 * @param holidays holiday name per Gregorian date; absent dates have none.
 */
@Composable
fun MonthGrid(
    month: IfcYearMonth,
    today: LocalDate?,
    selected: LocalDate?,
    weekdayDisplay: WeekdayDisplay,
    onDayClick: (IfcDate) -> Unit,
    modifier: Modifier = Modifier,
    eventCounts: Map<LocalDate, Int> = emptyMap(),
    holidays: Map<LocalDate, String> = emptyMap(),
) {
    val formatter = rememberIfcDateFormatter()
    Column(modifier = modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
        Text(
            text = formatter.monthTitle(month),
            style = MaterialTheme.typography.titleLarge,
            modifier =
                Modifier
                    .padding(bottom = TitleBottomPadding)
                    .semantics { heading() },
        )
        WeekdayHeaders(month = month, display = weekdayDisplay)
        for (row in 0 until GRID_ROWS) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until GRID_COLUMNS) {
                    val date = IfcDate.Regular(month.year, month.month, row * GRID_COLUMNS + column + 1)
                    val gregorian = date.toLocalDate()
                    DayCell(
                        date = date,
                        gregorian = gregorian,
                        isToday = gregorian == today,
                        isSelected = gregorian == selected,
                        eventCount = eventCounts[gregorian] ?: 0,
                        holidayName = holidays[gregorian],
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        when (val intercalary = month.trailingIntercalary) {
            null -> {
                IntercalaryPlaceholder(month = month)
            }

            else -> {
                val gregorian = intercalary.toLocalDate()
                IntercalaryBand(
                    day = intercalary,
                    isToday = gregorian == today,
                    isSelected = gregorian == selected,
                    eventCount = eventCounts[gregorian] ?: 0,
                    holidayName = holidays[gregorian],
                    onClick = { onDayClick(intercalary) },
                )
            }
        }
    }
}
