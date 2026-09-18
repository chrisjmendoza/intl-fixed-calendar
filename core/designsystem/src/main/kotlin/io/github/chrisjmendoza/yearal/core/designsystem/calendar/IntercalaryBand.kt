package io.github.chrisjmendoza.yearal.core.designsystem.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.R
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter

private val BandVerticalPadding = 8.dp
private val BandHorizontalPadding = 16.dp
private val BandContentSpacing = 12.dp
private val BandGap = 4.dp
private const val SUBTITLE_MAX_LINES = 2

/**
 * The full-width row beneath the fourth week for Leap Day (June, leap years) and Year Day
 * (December): spec §7.2 option C, docs/ARCHITECTURE.md §4 "Intercalary days in a 7-column grid",
 * FEATURES C3. Spanning all seven columns makes "belongs to no week" visible; no weekday header
 * aligns with it.
 *
 * It is a first-class selectable day: the same today ring and selected fill as [DayCell], the same
 * marks, `Role.Button`, the `selected` state and a content description from
 * `IfcDateFormatter.dayDescription` (`Year Day, no IFC weekday. Gregorian Thursday, December 31,
 * 2026.`). It shows the tertiary-container colour plus an icon, the label ("Leap Day" / "Year Day"),
 * the Gregorian date and the real weekday with the "no IFC weekday" note (spec §4.1 item 5).
 *
 * The band is at least [intercalarySlotHeight] tall, the same minimum as [IntercalaryPlaceholder],
 * so the month pager never changes height between months.
 *
 * @param day [IfcDate.LeapDay] or [IfcDate.YearDay].
 * @param isToday whether the day is the real today, matched on its Gregorian date.
 * @param isSelected whether the band is the current selection.
 * @param eventCount the day's number of events, `0` or more.
 * @param holidayName the holiday on this day, or `null`.
 * @param onClick invoked when the band is tapped.
 * @param modifier applied to the band; it fills the available width itself.
 * @throws IllegalArgumentException if [day] is an [IfcDate.Regular] day.
 */
@Composable
fun IntercalaryBand(
    day: IfcDate,
    isToday: Boolean,
    isSelected: Boolean,
    eventCount: Int,
    holidayName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(day.isIntercalary) { "IntercalaryBand takes Leap Day or Year Day, not $day" }
    val formatter = rememberIfcDateFormatter()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val containerColor = if (isSelected) colors.primaryContainer else colors.tertiaryContainer
    val contentColor = if (isSelected) colors.onPrimaryContainer else colors.onTertiaryContainer
    val description = formatter.dayDescription(day, isToday, eventCount, holidayName)

    Box(
        modifier =
            modifier
                .padding(vertical = BandGap)
                .fillMaxWidth()
                .heightIn(min = intercalarySlotHeight())
                .clip(shape)
                .background(containerColor)
                .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(MonthGridTestTags.INTERCALARY_BAND),
        contentAlignment = Alignment.CenterStart,
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
        Row(
            modifier = Modifier.padding(horizontal = BandHorizontalPadding, vertical = BandVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(BandContentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_intercalary),
                contentDescription = null,
                tint = contentColor,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatter.formatDay(day),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isToday) FontWeight.Bold else null,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatter.intercalarySubtitle(day),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = SUBTITLE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DayMarks(eventCount = eventCount, hasHoliday = holidayName != null)
        }
    }
}

/**
 * What the band slot shows in the eleven months (and June in common years) that have no intercalary
 * day: the month's Gregorian span, `Jun 18 – Jul 15`, from [IfcYearMonth.gregorianRange]. It
 * reserves exactly the band's vertical space, [intercalarySlotHeight], so paging between months never
 * moves the grid (spec §7.2). Not interactive and not a day; it has no button semantics.
 *
 * @param month the month whose span is shown.
 * @param modifier applied to the placeholder; it fills the available width itself.
 */
@Composable
fun IntercalaryPlaceholder(
    month: IfcYearMonth,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberIfcDateFormatter()
    Box(
        modifier =
            modifier
                .padding(vertical = BandGap)
                .fillMaxWidth()
                .heightIn(min = intercalarySlotHeight())
                .testTag(MonthGridTestTags.INTERCALARY_PLACEHOLDER),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatter.gregorianSpan(month.gregorianRange),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = BandHorizontalPadding, vertical = BandVerticalPadding),
        )
    }
}

/**
 * The reserved height of the band slot: the band's vertical padding plus one `titleMedium` line and
 * [SUBTITLE_MAX_LINES] `bodySmall` lines, as they actually lay out in the current density, font scale
 * and font — measured with a [TextMeasurer], not derived from the styles' nominal line heights, which
 * the platform's non-linear font scaling and glyph metrics both exceed. The band never needs more
 * than this (its label is one line, its subtitle at most two), so band and placeholder are the same
 * height in every month and at every font scale (docs/ARCHITECTURE.md §4, font scale 2.0).
 */
@Composable
fun intercalarySlotHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    return remember(measurer, typography, density) {
        val title = measurer.measure(ONE_LINE_SAMPLE, typography.titleMedium, maxLines = 1).size.height
        val subtitle =
            measurer
                .measure(
                    TWO_LINE_SAMPLE,
                    typography.bodySmall,
                    maxLines = SUBTITLE_MAX_LINES,
                ).size.height
        with(density) { (title + subtitle).toDp() } + BandVerticalPadding * 2
    }
}

// Placeholder text for measuring line heights only; never shown. Any glyph gives the same line height.
private const val ONE_LINE_SAMPLE = "X"
private const val TWO_LINE_SAMPLE = "X\nX"
