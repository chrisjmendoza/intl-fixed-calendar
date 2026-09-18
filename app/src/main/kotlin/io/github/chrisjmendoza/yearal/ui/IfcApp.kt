package io.github.chrisjmendoza.yearal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.chrisjmendoza.yearal.core.navigation.EventListKey
import io.github.chrisjmendoza.yearal.core.navigation.MonthKey
import io.github.chrisjmendoza.yearal.core.navigation.MoreKey
import io.github.chrisjmendoza.yearal.core.navigation.SettingsKey
import io.github.chrisjmendoza.yearal.core.navigation.TodayKey
import io.github.chrisjmendoza.yearal.feature.calendar.today.TodayRoute
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
                    entry<MonthKey> { TabPlaceholder(TopLevelDestination.CALENDAR) }
                    entry<EventListKey> { TabPlaceholder(TopLevelDestination.EVENTS) }
                    entry<ConverterKey> { TabPlaceholder(TopLevelDestination.CONVERT) }
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

/** The empty tab body of the M0 walking skeleton; each is replaced by its feature in M2–M4. */
@Composable
private fun TabPlaceholder(destination: TopLevelDestination) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(destination.labelRes))
    }
}
