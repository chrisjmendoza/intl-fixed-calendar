package io.github.chrisjmendoza.yearal.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.chrisjmendoza.yearal.BuildConfig
import io.github.chrisjmendoza.yearal.MainViewModel
import io.github.chrisjmendoza.yearal.R
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.calendar.toIfcDate
import io.github.chrisjmendoza.yearal.core.navigation.ConverterKey
import io.github.chrisjmendoza.yearal.core.navigation.DayKey
import io.github.chrisjmendoza.yearal.core.navigation.EventEditorKey
import io.github.chrisjmendoza.yearal.core.navigation.EventListKey
import io.github.chrisjmendoza.yearal.core.navigation.MonthKey
import io.github.chrisjmendoza.yearal.core.navigation.MoreKey
import io.github.chrisjmendoza.yearal.core.navigation.SettingsKey
import io.github.chrisjmendoza.yearal.core.navigation.TodayKey
import io.github.chrisjmendoza.yearal.feature.calendar.day.DayRoute
import io.github.chrisjmendoza.yearal.feature.calendar.month.MonthRoute
import io.github.chrisjmendoza.yearal.feature.calendar.today.TodayRoute
import io.github.chrisjmendoza.yearal.feature.converter.ConverterRoute
import io.github.chrisjmendoza.yearal.feature.events.editor.EventEditorRoute
import io.github.chrisjmendoza.yearal.feature.events.list.EventListRoute
import io.github.chrisjmendoza.yearal.feature.settings.more.MoreRoute
import io.github.chrisjmendoza.yearal.feature.settings.settings.SettingsRoute
import io.github.chrisjmendoza.yearal.ui.navigation.rememberTabBackStacks

/**
 * The app shell: the five top-level tabs in a [NavigationSuiteScaffold] (bar on compact widths, rail
 * from medium up) and one [NavDisplay] over the selected tab's back stack
 * (docs/ARCHITECTURE.md §4 "Screens and navigation").
 */
@Composable
fun IfcApp(viewModel: MainViewModel = hiltViewModel()) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    val tabs = rememberTabBackStacks()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = tabs.selected == destination,
                    onClick = {
                        val root =
                            destination.rootKey
                                ?: today?.let { date ->
                                    val month = IfcYearMonth.from(date.toIfcDate())
                                    MonthKey(month.year, month.month.number)
                                }
                        if (root != null) tabs.select(destination, root)
                    },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = tabs.backStack,
            onBack = { tabs.goBack() },
            // The ViewModel decorator scopes each entry's @HiltViewModel to that entry, so a screen's
            // state is cleared when it is popped rather than living as long as the activity.
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<TodayKey> { TodayRoute() }
                    entry<MonthKey> { key -> MonthRoute(key = key, navigator = tabs) }
                    entry<DayKey> { key -> DayRoute(key = key, navigator = tabs) }
                    entry<EventListKey> { EventListRoute(navigator = tabs) }
                    entry<EventEditorKey> { key -> EventEditorRoute(key = key, navigator = tabs) }
                    entry<ConverterKey> { key -> ConverterRoute(key = key, navigator = tabs) }
                    entry<MoreKey> {
                        MoreRoute(
                            navigator = tabs,
                            appName = stringResource(R.string.app_name),
                            versionName = BuildConfig.VERSION_NAME,
                        )
                    }
                    entry<SettingsKey> { SettingsRoute(navigator = tabs) }
                },
        )
    }
}
