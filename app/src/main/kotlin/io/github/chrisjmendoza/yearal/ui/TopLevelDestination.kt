package io.github.chrisjmendoza.yearal.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import io.github.chrisjmendoza.yearal.R
import io.github.chrisjmendoza.yearal.core.navigation.ConverterKey
import io.github.chrisjmendoza.yearal.core.navigation.EventListKey
import io.github.chrisjmendoza.yearal.core.navigation.MoreKey
import io.github.chrisjmendoza.yearal.core.navigation.TodayKey

/**
 * The five top-level tabs (docs/ARCHITECTURE.md §4 "Screens and navigation"): Today | Calendar |
 * Events | Convert | More. Each owns its own back stack whose root is [rootKey].
 *
 * The Calendar tab's root is the current month, which depends on the clock, so [CALENDAR] carries no
 * static root; [IfcApp] resolves it when the tab is first opened.
 */
enum class TopLevelDestination(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val rootKey: NavKey?,
) {
    TODAY(R.string.tab_today, Icons.Filled.Home, TodayKey),
    CALENDAR(R.string.tab_calendar, Icons.Filled.DateRange, null),
    EVENTS(R.string.tab_events, Icons.AutoMirrored.Filled.List, EventListKey),
    CONVERT(R.string.tab_convert, Icons.Filled.Refresh, ConverterKey()),
    MORE(R.string.tab_more, Icons.Filled.MoreVert, MoreKey),
}
