package io.github.chrisjmendoza.yearal.feature.settings.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.core.navigation.SettingsKey
import io.github.chrisjmendoza.yearal.feature.settings.R

/**
 * The More tab's hub (docs/ARCHITECTURE.md §4 "Screens and navigation": More holds Holidays, Settings
 * and Learn/About). This is the composable `:app` places behind `MoreKey`; the Settings row pushes
 * `SettingsKey` through [navigator]. Holidays and Learn rows are added when those screens exist.
 *
 * @param appName the launcher label, e.g. `Yearal`; it lives in `:app`'s resources, so the caller
 * passes it rather than the feature duplicating the string.
 * @param versionName the app's `versionName`, e.g. `0.1.0`; supplied by `:app`, which owns the
 * package information (the feature never reads `PackageManager`).
 * @param modifier applied to the screen's root [Scaffold].
 */
@Composable
fun MoreRoute(
    navigator: Navigator,
    appName: String,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    MoreScreen(
        appName = appName,
        versionName = versionName,
        onSettingsClick = { navigator.navigate(SettingsKey) },
        modifier = modifier,
    )
}

/**
 * The stateless More hub — a Settings row and a non-interactive About row showing [appName] and
 * [versionName] — the unit for previews, screenshot and Compose tests.
 *
 * Opts in to the Material 3 experimental marker only because `TopAppBar`'s default arguments
 * (`TopAppBarDefaults`) still carry it.
 *
 * @param onSettingsClick the Settings row's action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    appName: String,
    versionName: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_settings)) },
                supportingContent = { Text(stringResource(R.string.more_settings_detail)) },
                leadingContent = { Icon(imageVector = Icons.Filled.Settings, contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onSettingsClick),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(appName) },
                supportingContent = { Text(stringResource(R.string.more_version, versionName)) },
                leadingContent = { Icon(imageVector = Icons.Filled.Info, contentDescription = null) },
            )
        }
    }
}

@Preview(name = "More hub", showBackground = true)
@Composable
internal fun MoreScreenPreview() {
    IfcTheme(dynamicColor = false) {
        MoreScreen(appName = "Yearal", versionName = "0.1.0", onSettingsClick = {})
    }
}
