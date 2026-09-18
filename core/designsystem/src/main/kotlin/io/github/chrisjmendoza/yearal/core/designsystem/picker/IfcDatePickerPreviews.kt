package io.github.chrisjmendoza.yearal.core.designsystem.picker

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme

// One preview per date shape (CLAUDE.md rule 6) plus the states a screenshot should pin down: the
// Leap Day clamp notice, an invalid year, 200% font scale and the narrow 4-column day layout.
// Dynamic colour is off so the brand palette is what is captured.

private const val COMPACT = 360
private const val NARROW = 280
private const val LARGE_FONT = 2f

/** Sol 13, 2026: a regular day of the month no Gregorian picker has. */
@Preview(name = "Regular day (Sol)", widthDp = COMPACT)
@Preview(name = "Regular day (Sol), dark", widthDp = COMPACT, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Regular day (Sol), font 2.0", widthDp = COMPACT, fontScale = LARGE_FONT)
@Preview(name = "Regular day (Sol), narrow", widthDp = NARROW)
@Composable
internal fun IfcDatePickerRegularPreview() {
    PickerPreview(IfcDatePickerValue.of(IfcDate.Regular(2026, IfcMonth.SOL, 13)))
}

/** Leap Day 2028: offered and selected because 2028 is a leap year. */
@Preview(name = "Leap Day", widthDp = COMPACT)
@Composable
internal fun IfcDatePickerLeapDayPreview() {
    PickerPreview(IfcDatePickerValue.of(IfcDate.LeapDay(2028)))
}

/** Year Day 2026, a common year: Leap Day is not offered and the picker says why. */
@Preview(name = "Year Day", widthDp = COMPACT)
@Composable
internal fun IfcDatePickerYearDayPreview() {
    PickerPreview(IfcDatePickerValue.of(IfcDate.YearDay(2026)))
}

/** Leap Day 2028 after the year was changed to 2027: clamped to June 28, with the notice (spec §7.10). */
@Preview(name = "Leap Day clamped", widthDp = COMPACT)
@Composable
internal fun IfcDatePickerClampedPreview() {
    PickerPreview(IfcDatePickerValue.of(IfcDate.LeapDay(2028)).withYearText("2027"))
}

/** A year outside 1583..9999: the field is in its error state and there is no date. */
@Preview(name = "Invalid year", widthDp = COMPACT)
@Composable
internal fun IfcDatePickerInvalidYearPreview() {
    PickerPreview(IfcDatePickerValue.of(IfcDate.Regular(2026, IfcMonth.SEPTEMBER, 8)).withYearText("1582"))
}

@Composable
private fun PickerPreview(value: IfcDatePickerValue) {
    IfcTheme(dynamicColor = false) {
        Surface {
            IfcDatePicker(value = value, onValueChange = {}, modifier = Modifier.padding(12.dp))
        }
    }
}
