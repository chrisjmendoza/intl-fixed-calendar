package io.github.chrisjmendoza.yearal.core.designsystem.picker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.designsystem.R
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter

/** The smallest touch target of every option (docs/ARCHITECTURE.md §4 "Accessibility"). */
private val MinTouchTarget = 48.dp
private val SectionSpacing = 12.dp
private val OptionSpacing = 8.dp
private val OptionHorizontalPadding = 12.dp
private val OptionVerticalPadding = 8.dp

/** Day numbers get almost the whole 48dp cell, so two digits stay on one line at 200% font scale. */
private val DayHorizontalPadding = 2.dp

/** Columns of the day chooser when seven 48dp cells fit: one per nominal weekday (§2.3 R5). */
private const val WEEK_COLUMNS = 7

/** Columns of the day chooser when they do not fit (a dialog, a very narrow pane): 28 = 7 rows of 4. */
private const val NARROW_COLUMNS = 4

/**
 * A date picker for the International Fixed Calendar (docs/FEATURES.md D1, E2): a validated year
 * field ([DatePickerRange], 1583..9999), all 13 months including Sol, days 1..28, and the intercalary
 * days — **Year Day always, Leap Day only in leap years** (`docs/calendar-spec.md` §7.10; CLAUDE.md
 * rule 6). Stateless: it renders [value] and reports every edit as a new value through
 * [onValueChange]; [IfcDatePickerValue] holds the rules, including what happens to a selected Leap
 * Day when the year becomes a common year (it moves to June 28, and the picker says so in a polite
 * live region).
 *
 * Every option is a radio-button-role selectable at least 48dp high. Months and intercalary days wrap
 * onto as many lines as the font scale needs; the days form the month's 4 × 7 layout when seven 48dp
 * columns fit and 7 × 4 otherwise, so nothing clips at 200% font scale or in a narrow dialog. The
 * selected option is filled and bold, not only tinted.
 *
 * The picker shows no weekdays: a caller that needs them shows both, labelled, next to the picker
 * (spec §4.1), as the converter does.
 *
 * @param value what to show.
 * @param onValueChange receives the value after each edit.
 * @param modifier applied to the picker's root column.
 * @param formatter supplies the month and intercalary-day names (resources; CLAUDE.md rule 9).
 */
@Composable
fun IfcDatePicker(
    value: IfcDatePickerValue,
    onValueChange: (IfcDatePickerValue) -> Unit,
    modifier: Modifier = Modifier,
    formatter: IfcDateFormatter = rememberIfcDateFormatter(),
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        YearField(value = value, onValueChange = onValueChange)
        LeapDayClampedNotice(value = value, formatter = formatter)
        MonthChooser(value = value, onValueChange = onValueChange, formatter = formatter)
        DayChooser(value = value, onValueChange = onValueChange, formatter = formatter)
        IntercalaryChooser(value = value, onValueChange = onValueChange, formatter = formatter)
    }
}

/**
 * [IfcDatePicker] over an [IfcDatePickerState], for callers that keep the picker's state in the
 * composition rather than in a ViewModel.
 *
 * @param state the hoisted state; read [IfcDatePickerState.date] for the result.
 * @param modifier applied to the picker's root column.
 * @param formatter supplies the month and intercalary-day names.
 */
@Composable
fun IfcDatePicker(
    state: IfcDatePickerState,
    modifier: Modifier = Modifier,
    formatter: IfcDateFormatter = rememberIfcDateFormatter(),
) {
    IfcDatePicker(
        value = state.value,
        onValueChange = { state.value = it },
        modifier = modifier,
        formatter = formatter,
    )
}

/**
 * The small state holder of [IfcDatePicker]: an observable [IfcDatePickerValue]. Create it with
 * [rememberIfcDatePickerState], which also saves it across configuration changes and process death.
 *
 * @param initial the value to start from.
 */
@Stable
class IfcDatePickerState(
    initial: IfcDatePickerValue,
) {
    /** What the picker shows; assigning it re-renders the picker. */
    var value: IfcDatePickerValue by mutableStateOf(initial)

    /** The selected date, or `null` while the typed year is not a valid year ([IfcDatePickerValue.date]). */
    val date: IfcDate? get() = value.date

    /** Saving support. */
    companion object {
        /**
         * Saves the typed year, the selection as its numeric pseudo-fields (§7.3) and the clamp notice.
         * Restoring goes through [IfcDatePickerValue.restore], which re-validates the saved parts.
         */
        val Saver: Saver<IfcDatePickerState, Any> =
            listSaver(
                save = { state ->
                    val value = state.value
                    listOf(
                        value.yearText,
                        value.selection.monthNumber,
                        value.selection.dayOfMonth,
                        value.leapDayClamped,
                    )
                },
                restore = { saved ->
                    val selection = IfcDaySelection.of(saved[1] as Int, saved[2] as Int)
                    selection?.let {
                        IfcDatePickerState(IfcDatePickerValue.restore(saved[0] as String, it, saved[3] as Boolean))
                    }
                },
            )
    }
}

/**
 * Creates and remembers an [IfcDatePickerState] that starts at [initialDate] and survives
 * configuration changes and process death. The caller supplies the date — typically "today" from the
 * injected `DateTicker` — because nothing here may read a clock (CLAUDE.md rule 2).
 */
@Composable
fun rememberIfcDatePickerState(initialDate: IfcDate): IfcDatePickerState =
    rememberSaveable(saver = IfcDatePickerState.Saver) { IfcDatePickerState(IfcDatePickerValue.of(initialDate)) }

/**
 * The year text field: free-typed digits, an error state while [IfcDatePickerValue.year] is `null`
 * (empty, partial, or outside [DatePickerRange]), and a supporting caption spelling out the range.
 */
@Composable
private fun YearField(
    value: IfcDatePickerValue,
    onValueChange: (IfcDatePickerValue) -> Unit,
) {
    OutlinedTextField(
        value = value.yearText,
        onValueChange = { onValueChange(value.withYearText(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.picker_year_label)) },
        supportingText = {
            Text(stringResource(R.string.picker_year_range, DatePickerRange.MIN_YEAR, DatePickerRange.MAX_YEAR))
        },
        isError = value.year == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
    )
}

/** Says, once, that a selected Leap Day was moved because the new year has none (spec §7.10). */
@Composable
private fun LeapDayClampedNotice(
    value: IfcDatePickerValue,
    formatter: IfcDateFormatter,
) {
    val year = value.year
    if (!value.leapDayClamped || year == null) return
    val june28 = formatter.formatDay(IfcDate.Regular(year, IfcMonth.JUNE, IfcMonth.DAYS_PER_MONTH))
    Text(
        text = stringResource(R.string.picker_leap_day_clamped, formatter.formatNumber(year), june28),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** All 13 months, Sol included, as a wrapping row of single-choice options (§2.2 R4). */
@Composable
private fun MonthChooser(
    value: IfcDatePickerValue,
    onValueChange: (IfcDatePickerValue) -> Unit,
    formatter: IfcDateFormatter,
) {
    val selection = value.selection
    SectionHeading(stringResource(R.string.picker_month_heading))
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(OptionSpacing),
    ) {
        for (month in IfcMonth.entries) {
            Option(
                label = formatter.monthName(month),
                selected = selection is IfcDaySelection.Regular && selection.month == month,
                onClick = { onValueChange(value.withMonth(month)) },
            )
        }
    }
}

/**
 * Days 1..28 of the selected month, laid out [WEEK_COLUMNS] wide (one nominal weekday per column,
 * matching the grid, §2.3 R5) when that fits at [MinTouchTarget], or [NARROW_COLUMNS] wide otherwise
 * so no option shrinks below the touch-target floor in a narrow dialog or at large font scale.
 */
@Composable
private fun DayChooser(
    value: IfcDatePickerValue,
    onValueChange: (IfcDatePickerValue) -> Unit,
    formatter: IfcDateFormatter,
) {
    val selection = value.selection
    SectionHeading(stringResource(R.string.picker_day_heading))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        val columns = if (maxWidth >= MinTouchTarget * WEEK_COLUMNS) WEEK_COLUMNS else NARROW_COLUMNS
        Column {
            for (firstOfRow in 1..IfcMonth.DAYS_PER_MONTH step columns) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (day in firstOfRow until firstOfRow + columns) {
                        Option(
                            label = formatter.formatNumber(day),
                            description = stringResource(R.string.picker_day_description, day),
                            selected = selection is IfcDaySelection.Regular && selection.dayOfMonth == day,
                            onClick = { onValueChange(value.withDayOfMonth(day)) },
                            modifier = Modifier.weight(1f),
                            horizontalPadding = DayHorizontalPadding,
                        )
                    }
                }
            }
        }
    }
}

/** Year Day always; Leap Day only while [IfcDatePickerValue.isLeapDayOffered] (CLAUDE.md rule 6). */
@Composable
private fun IntercalaryChooser(
    value: IfcDatePickerValue,
    onValueChange: (IfcDatePickerValue) -> Unit,
    formatter: IfcDateFormatter,
) {
    SectionHeading(stringResource(R.string.picker_intercalary_heading))
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(OptionSpacing),
    ) {
        if (value.isLeapDayOffered) {
            Option(
                label = stringResource(R.string.intercalary_leap_day),
                selected = value.selection == IfcDaySelection.LeapDay,
                onClick = { onValueChange(value.withSelection(IfcDaySelection.LeapDay)) },
            )
        }
        Option(
            label = stringResource(R.string.intercalary_year_day),
            selected = value.selection == IfcDaySelection.YearDay,
            onClick = { onValueChange(value.withSelection(IfcDaySelection.YearDay)) },
        )
    }
    val year = value.year
    if (year != null && !value.isLeapDayOffered) {
        Text(
            text = stringResource(R.string.picker_leap_day_unavailable, formatter.formatNumber(year)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * One choice of a single-choice group: a selectable [Surface] with the radio-button role, at least
 * 48dp high. Selected = filled and bold; unselected = outlined, so the state is not colour alone.
 */
@Composable
private fun Option(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    horizontalPadding: Dp = OptionHorizontalPadding,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = MinTouchTarget)
                .semantics {
                    role = Role.RadioButton
                    if (description != null) contentDescription = description
                },
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = OptionVerticalPadding),
            )
        }
    }
}
