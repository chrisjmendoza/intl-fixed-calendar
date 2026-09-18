package io.github.chrisjmendoza.yearal.core.designsystem.picker

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.chrisjmendoza.yearal.core.designsystem.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The Material 3 date picker dialog for a **Gregorian** date, limited to the years of
 * [DatePickerRange] (1583..9999; docs/ARCHITECTURE.md "Reconciled decisions" 6). The counterpart of
 * [IfcDatePicker] wherever a date can be picked in either calendar (docs/FEATURES.md D1, E2).
 *
 * Material's picker speaks UTC epoch milliseconds; this wrapper keeps that inside and exchanges
 * [LocalDate]s only, converting through `java.time` with no millisecond arithmetic (CLAUDE.md rule 2).
 * Its weekday columns and first day of the week are the real, locale-dependent ones — it is a
 * Gregorian grid (spec §7.2). **Trap:** Material marks "today" from the system clock, which this app
 * cannot inject; nothing the app computes depends on that mark.
 *
 * @param initialDate the date selected when the dialog opens. A date outside [DatePickerRange] opens
 * the dialog with nothing selected, on the nearest month in range.
 * @param onConfirm receives the chosen date, always inside [DatePickerRange]; the caller closes the dialog.
 * @param onDismiss invoked by Cancel, the scrim and the back gesture; the caller closes the dialog.
 * @param modifier applied to the dialog.
 */
@Composable
fun GregorianDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initialDate.takeIf(DatePickerRange::contains)?.toUtcStartOfDayMillis(),
            initialDisplayedMonthMillis = nearestInRange(initialDate).toUtcStartOfDayMillis(),
            yearRange = DatePickerRange.years,
        )
    val selected = state.selectedDateMillis?.let(::utcMillisToLocalDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { selected?.let(onConfirm) }, enabled = selected != null) {
                Text(stringResource(R.string.picker_confirm))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/** [date], or the end of [DatePickerRange] it lies beyond. */
private fun nearestInRange(date: LocalDate): LocalDate =
    when {
        date.isBefore(DatePickerRange.firstDate) -> DatePickerRange.firstDate
        date.isAfter(DatePickerRange.lastDate) -> DatePickerRange.lastDate
        else -> date
    }

/**
 * The start of this date in UTC as epoch milliseconds — the unit Material's `DatePickerState` takes.
 * An adapter for that API only; dates are never stored or computed in milliseconds (CLAUDE.md rules 2, 4).
 */
internal fun LocalDate.toUtcStartOfDayMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The UTC calendar date of [millis], the inverse of [toUtcStartOfDayMillis] for the values Material returns. */
internal fun utcMillisToLocalDate(millis: Long): LocalDate =
    Instant
        .ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
