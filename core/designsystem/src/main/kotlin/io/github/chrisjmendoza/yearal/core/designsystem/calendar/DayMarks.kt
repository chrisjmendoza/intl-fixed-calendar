package io.github.chrisjmendoza.yearal.core.designsystem.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Semantics test tags of the month grid's parts that have no text of their own. They sit in the
 * **unmerged** semantics tree (cells merge their descendants), so tests read them with
 * `useUnmergedTree = true`. Screen readers never see them; the merged content description carries
 * the same facts in words.
 */
object MonthGridTestTags {
    /** The ring drawn around today's cell or band. Present only when the day is today. */
    const val TODAY_RING: String = "ifc:todayRing"

    /** One event dot. A day shows at most [MAX_EVENT_DOTS] of them. */
    const val EVENT_DOT: String = "ifc:eventDot"

    /** The holiday marker, a diamond so it is distinguishable from the round event dots. */
    const val HOLIDAY_MARKER: String = "ifc:holidayMarker"

    /** The intercalary band (Leap Day or Year Day). */
    const val INTERCALARY_BAND: String = "ifc:intercalaryBand"

    /** The same-height placeholder shown in the band slot of months without an intercalary day. */
    const val INTERCALARY_PLACEHOLDER: String = "ifc:intercalaryPlaceholder"
}

/** The most event dots a cell shows; the spoken description still gives the exact count (FEATURES C4). */
const val MAX_EVENT_DOTS: Int = 3

private val MarkRowHeight = 8.dp
private val MarkSize = 6.dp
private val MarkSpacing = 3.dp

/**
 * The row of marks under a day number: a holiday diamond first, then up to [MAX_EVENT_DOTS] round
 * event dots. Shapes differ so neither mark depends on colour alone (docs/ARCHITECTURE.md §4
 * "Accessibility"). The row keeps its height even when empty so every cell in a week is the same
 * height.
 */
@Composable
internal fun DayMarks(
    eventCount: Int,
    hasHoliday: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(MarkRowHeight),
        horizontalArrangement = Arrangement.spacedBy(MarkSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasHoliday) {
            Spacer(
                modifier =
                    Modifier
                        .size(MarkSize)
                        .rotate(degrees = 45f)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .testTag(MonthGridTestTags.HOLIDAY_MARKER),
            )
        }
        repeat(eventCount.coerceIn(0, MAX_EVENT_DOTS)) {
            Spacer(
                modifier =
                    Modifier
                        .size(MarkSize)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag(MonthGridTestTags.EVENT_DOT),
            )
        }
    }
}
