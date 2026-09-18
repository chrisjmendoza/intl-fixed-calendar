package io.github.chrisjmendoza.yearal.core.designsystem.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.R
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter.WeekdayNameStyle
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay

/** Columns of an IFC month grid: the nominal Sunday column is 0, the nominal Saturday column is 6. */
const val GRID_COLUMNS: Int = 7

private val HeaderVerticalPadding = 4.dp

/**
 * The column headers of an IFC month grid (FEATURES C2; spec §4.1 item 3; docs/ARCHITECTURE.md §4).
 * The week always starts on Sunday whatever the device's first-day-of-week setting (spec §7.2).
 *
 * - [WeekdayDisplay.NOMINAL]: one row of the perpetual IFC weekday names, Sunday to Saturday, the
 *   same for every month.
 * - [WeekdayDisplay.ACTUAL]: one row of the real weekdays of this month's seven columns,
 *   [IfcYearMonth.actualDayOfWeek].
 * - [WeekdayDisplay.BOTH]: the nominal row with the actual row beneath it in a smaller, secondary
 *   style — the default (ARCHITECTURE "Reconciled decisions" #7).
 *
 * Each row is labelled for screen readers ("IFC weekdays" / "Actual weekdays"), so a spoken header
 * can never be mistaken for the other kind. Nominal names come from the days' own
 * [IfcDate.nominalDayOfWeek] and actual names from [IfcYearMonth.actualDayOfWeek]; **neither row is
 * derived from the other** (spec §4.1).
 *
 * @param month the month the headers belong to; only [WeekdayDisplay.ACTUAL] and
 * [WeekdayDisplay.BOTH] depend on it.
 * @param display which rows to show.
 * @param modifier applied to the header block; it fills the available width itself.
 */
@Composable
fun WeekdayHeaders(
    month: IfcYearMonth,
    display: WeekdayDisplay,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberIfcDateFormatter()
    val typography = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        when (display) {
            WeekdayDisplay.NOMINAL -> {
                NominalRow(month, formatter, typography.labelLarge, colors.onSurface)
            }

            WeekdayDisplay.ACTUAL -> {
                ActualRow(month, formatter, typography.labelLarge, colors.onSurface)
            }

            WeekdayDisplay.BOTH -> {
                NominalRow(month, formatter, typography.labelLarge, colors.onSurface)
                ActualRow(month, formatter, typography.labelMedium, colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NominalRow(
    month: IfcYearMonth,
    formatter: IfcDateFormatter,
    style: TextStyle,
    color: Color,
) {
    HeaderRow(
        label = stringResource(R.string.weekday_header_nominal),
        names =
            List(GRID_COLUMNS) { column ->
                // The 1st..7th of any month are the nominal Sunday..Saturday (spec §2.3).
                formatter.weekdayName(
                    IfcDate.Regular(month.year, month.month, column + 1).nominalDayOfWeek,
                    WeekdayNameStyle.SHORT,
                )
            },
        style = style,
        color = color,
    )
}

@Composable
private fun ActualRow(
    month: IfcYearMonth,
    formatter: IfcDateFormatter,
    style: TextStyle,
    color: Color,
) {
    HeaderRow(
        label = stringResource(R.string.weekday_header_actual),
        names =
            List(
                GRID_COLUMNS,
            ) { column -> formatter.weekdayName(month.actualDayOfWeek(column), WeekdayNameStyle.SHORT) },
        style = style,
        color = color,
    )
}

// The row carries the label as its own description and leaves the seven names as separate nodes,
// so a screen reader announces "IFC weekdays" and then each name in column order.
@Composable
private fun HeaderRow(
    label: String,
    names: List<String>,
    style: TextStyle,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = HeaderVerticalPadding)
                .semantics { contentDescription = label },
    ) {
        names.forEach { name ->
            Text(
                text = name,
                style = style,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
