package io.github.chrisjmendoza.yearal.core.designsystem.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter
import java.time.LocalDate

/** Minimum touch target of a day cell (docs/ARCHITECTURE.md §4 "Accessibility"). */
val DayCellMinSize = 48.dp

/** Width of the border drawn around today's cell or band; shared by [DayCell] and `IntercalaryBand`. */
internal val TodayRingWidth = 2.dp

// 1dp, not more: seven columns at 360dp give 51dp each (docs/ARCHITECTURE.md §4), and the touch
// target inside the gap must stay at DayCellMinSize.
private val CellGap = 1.dp
private val CellPadding = 4.dp

/**
 * One regular day of the month grid (FEATURES C1, C4): the IFC day number large, the Gregorian day of
 * month small in the top corner (the "dual date in every cell" pattern), then the holiday marker and
 * up to [MAX_EVENT_DOTS] event dots. Today is marked by a ring, selection by a filled container —
 * shape and colour, never colour alone.
 *
 * Semantics: one merged node with `Role.Button`, the `selected` state and the content description
 * from `IfcDateFormatter.dayDescription` (`Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026.
 * 2 events. Holiday: …`, docs/ARCHITECTURE.md §4 "Accessibility").
 *
 * @param date the day; its nominal weekday comes from the date itself, never from the column.
 * @param gregorian the same physical day, `date.toLocalDate()`; passed in because the caller already
 * has it as the key of its event and holiday maps (CLAUDE.md rule 4). **Trap:** passing any other
 * date puts the corner number and the spoken Gregorian date out of step.
 * @param isToday whether [gregorian] is the real today — matched on the Gregorian date, so it uses
 * the actual weekday cycle (spec §4.1 item 2).
 * @param isSelected whether the cell is the current selection.
 * @param eventCount the day's number of events, `0` or more; the cell shows at most three dots and
 * speaks the exact count.
 * @param holidayName the holiday on this day, or `null`; shown as a diamond and spoken by name.
 * @param onClick invoked when the cell is tapped.
 * @param modifier applied to the cell; the grid passes a row weight. The cell enforces
 * [DayCellMinSize] itself.
 */
@Composable
fun DayCell(
    date: IfcDate.Regular,
    gregorian: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    eventCount: Int,
    holidayName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberIfcDateFormatter()
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val containerColor = if (isSelected) colors.primaryContainer else Color.Transparent
    val contentColor = if (isSelected) colors.onPrimaryContainer else colors.onSurface
    val secondaryColor = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
    val description = formatter.dayDescription(date, isToday, eventCount, holidayName)

    Box(
        modifier =
            modifier
                .padding(CellGap)
                .defaultMinSize(minWidth = DayCellMinSize, minHeight = DayCellMinSize)
                .clip(shape)
                .background(containerColor)
                .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
    ) {
        if (isToday) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .border(TodayRingWidth, colors.primary, shape)
                        .testTag(MonthGridTestTags.TODAY_RING),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(CellPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatter.formatNumber(gregorian.dayOfMonth),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
                maxLines = 1,
                modifier = Modifier.align(Alignment.End),
            )
            Text(
                text = formatter.formatNumber(date.dayOfMonth),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else null,
                color = contentColor,
                maxLines = 1,
            )
            DayMarks(eventCount = eventCount, hasHoliday = holidayName != null)
        }
    }
}
