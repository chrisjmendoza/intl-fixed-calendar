package io.github.chrisjmendoza.yearal.core.designsystem.calendar

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import java.time.LocalDate

// The screenshot matrix of docs/ARCHITECTURE.md §6: {light, dark} x {font 1.0, 2.0} x
// {compact 360dp, expanded 840dp} x {LTR, RTL}, applied to each of the three month shapes below.
// Roborazzi's preview scanner captures every @Preview once M2 T10 records the goldens; dynamic
// colour is off so the brand palette is what is captured. "ar" has no translation — it exercises
// the right-to-left layout and locale digits only.

private const val COMPACT = 360
private const val EXPANDED = 840
private const val LARGE_FONT = 2f
private const val DARK = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
private const val RTL = "ar"

/** The MonthGrid screenshot matrix (docs/ARCHITECTURE.md §6); applied to the three previews below. */
@Preview(name = "light 1.0 compact ltr", widthDp = COMPACT)
@Preview(name = "light 1.0 compact rtl", widthDp = COMPACT, locale = RTL)
@Preview(name = "light 1.0 expanded ltr", widthDp = EXPANDED)
@Preview(name = "light 1.0 expanded rtl", widthDp = EXPANDED, locale = RTL)
@Preview(name = "light 2.0 compact ltr", widthDp = COMPACT, fontScale = LARGE_FONT)
@Preview(name = "light 2.0 compact rtl", widthDp = COMPACT, fontScale = LARGE_FONT, locale = RTL)
@Preview(name = "light 2.0 expanded ltr", widthDp = EXPANDED, fontScale = LARGE_FONT)
@Preview(name = "light 2.0 expanded rtl", widthDp = EXPANDED, fontScale = LARGE_FONT, locale = RTL)
@Preview(name = "dark 1.0 compact ltr", widthDp = COMPACT, uiMode = DARK)
@Preview(name = "dark 1.0 compact rtl", widthDp = COMPACT, uiMode = DARK, locale = RTL)
@Preview(name = "dark 1.0 expanded ltr", widthDp = EXPANDED, uiMode = DARK)
@Preview(name = "dark 1.0 expanded rtl", widthDp = EXPANDED, uiMode = DARK, locale = RTL)
@Preview(name = "dark 2.0 compact ltr", widthDp = COMPACT, uiMode = DARK, fontScale = LARGE_FONT)
@Preview(name = "dark 2.0 compact rtl", widthDp = COMPACT, uiMode = DARK, fontScale = LARGE_FONT, locale = RTL)
@Preview(name = "dark 2.0 expanded ltr", widthDp = EXPANDED, uiMode = DARK, fontScale = LARGE_FONT)
@Preview(name = "dark 2.0 expanded rtl", widthDp = EXPANDED, uiMode = DARK, fontScale = LARGE_FONT, locale = RTL)
internal annotation class MonthGridMatrix

/** Sol 2026, a normal month: today on Sol 8 (June 25), Sol 3 selected, events and a holiday. */
@MonthGridMatrix
@Composable
internal fun MonthGridSolPreview() {
    val month = IfcYearMonth(2026, IfcMonth.SOL)
    MonthGridPreview(
        month = month,
        today = LocalDate.of(2026, 6, 25),
        selected = LocalDate.of(2026, 6, 20),
        eventCounts = mapOf(LocalDate.of(2026, 6, 25) to 2, LocalDate.of(2026, 7, 4) to 5),
        holidays = mapOf(LocalDate.of(2026, 7, 4) to "Independence Day"),
    )
}

/** June 2028, a leap year: the Leap Day band, which is today (June 17, 2028). */
@MonthGridMatrix
@Composable
internal fun MonthGridJuneLeapPreview() {
    MonthGridPreview(
        month = IfcYearMonth(2028, IfcMonth.JUNE),
        today = LocalDate.of(2028, 6, 17),
        selected = LocalDate.of(2028, 6, 17),
        eventCounts = mapOf(LocalDate.of(2028, 6, 17) to 1),
        holidays = mapOf(LocalDate.of(2028, 6, 17) to "Leap Day"),
    )
}

/** December 2026: the Year Day band, with December 25 (Gregorian) a holiday and no today. */
@MonthGridMatrix
@Composable
internal fun MonthGridDecemberPreview() {
    MonthGridPreview(
        month = IfcYearMonth(2026, IfcMonth.DECEMBER),
        today = null,
        selected = LocalDate.of(2026, 12, 31),
        eventCounts = mapOf(LocalDate.of(2026, 12, 31) to 3),
        holidays = mapOf(LocalDate.of(2026, 12, 25) to "Christmas Day", LocalDate.of(2026, 12, 31) to "Year Day"),
    )
}

/** Wraps [MonthGrid] in [IfcTheme] (dynamic colour off) and a [Surface] for the previews above. */
@Composable
private fun MonthGridPreview(
    month: IfcYearMonth,
    today: LocalDate?,
    selected: LocalDate?,
    eventCounts: Map<LocalDate, Int>,
    holidays: Map<LocalDate, String>,
) {
    IfcTheme(dynamicColor = false) {
        Surface {
            MonthGrid(
                month = month,
                today = today,
                selected = selected,
                weekdayDisplay = WeekdayDisplay.BOTH,
                eventCounts = eventCounts,
                holidays = holidays,
                onDayClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
