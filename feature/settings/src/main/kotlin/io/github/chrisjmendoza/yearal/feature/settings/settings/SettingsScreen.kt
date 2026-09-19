package io.github.chrisjmendoza.yearal.feature.settings.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.domain.settings.ThemeMode
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.feature.settings.R

/**
 * The Settings screen (docs/FEATURES.md W1, W2, H5): collects [SettingsViewModel.uiState] with the
 * lifecycle and renders it through the stateless [SettingsScreen]. This is the composable `:app`
 * places behind `SettingsKey`; the back arrow pops through [navigator].
 *
 * @param modifier applied to the screen's root [Scaffold].
 */
@Composable
fun SettingsRoute(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = navigator::goBack,
        onWeekdayDisplaySelected = viewModel::setWeekdayDisplay,
        onThemeModeSelected = viewModel::setThemeMode,
        onDynamicColorChanged = viewModel::setDynamicColor,
        onHolidaySetEnabledChanged = viewModel::setHolidaySetEnabled,
        onRequestDeleteAllData = viewModel::requestDeleteAllData,
        onContinueDeleteAllData = viewModel::continueDeleteAllData,
        onCancelDeleteAllData = viewModel::cancelDeleteAllData,
        onConfirmDeleteAllData = viewModel::confirmDeleteAllData,
        onDismissDeleteAllDataDone = viewModel::dismissDeleteAllDataDone,
        modifier = modifier,
    )
}

/**
 * The stateless Settings screen — the unit for previews, screenshot and Compose tests
 * (docs/ARCHITECTURE.md §4 "State management"). Four sections: the weekday-header mode as a radio
 * group with a reminder that IFC weekdays are not real ones (calendar-spec §4.1), the theme as a radio
 * group plus the dynamic-colour switch (disabled below API 31), one switch per bundled holiday pack,
 * and "Delete all data" (FEATURES W6) behind a two-step destructive confirmation. Every control
 * reflects [SettingsUiState.Loaded.settings] (or [SettingsUiState.Loaded.deleteAllDataStep]) and
 * reports a change through its callback; nothing is stored locally.
 *
 * Opts in to the Material 3 experimental marker only because `TopAppBar`'s default arguments
 * (`TopAppBarDefaults`) still carry it.
 *
 * @param onBack the top app bar's back arrow.
 * @param onHolidaySetEnabledChanged receives the pack id (`HolidaySet.id`) and the new state.
 * @param onRequestDeleteAllData opens the first "Delete all data" confirmation.
 * @param onContinueDeleteAllData moves from the first confirmation to the final one.
 * @param onCancelDeleteAllData backs out of either confirmation.
 * @param onConfirmDeleteAllData accepted the final confirmation; erases everything.
 * @param onDismissDeleteAllDataDone dismisses the completion notice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onWeekdayDisplaySelected: (WeekdayDisplay) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onHolidaySetEnabledChanged: (id: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onRequestDeleteAllData: () -> Unit = {},
    onContinueDeleteAllData: () -> Unit = {},
    onCancelDeleteAllData: () -> Unit = {},
    onConfirmDeleteAllData: () -> Unit = {},
    onDismissDeleteAllDataDone: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            SettingsUiState.Loading -> {
                LoadingContent(Modifier.padding(padding))
            }

            is SettingsUiState.Loaded -> {
                LoadedContent(
                    state = state,
                    onWeekdayDisplaySelected = onWeekdayDisplaySelected,
                    onThemeModeSelected = onThemeModeSelected,
                    onDynamicColorChanged = onDynamicColorChanged,
                    onHolidaySetEnabledChanged = onHolidaySetEnabledChanged,
                    onRequestDeleteAllData = onRequestDeleteAllData,
                    modifier = Modifier.padding(padding),
                )
                when (state.deleteAllDataStep) {
                    DeleteAllDataStep.NONE -> {
                        Unit
                    }

                    DeleteAllDataStep.CONFIRM_FIRST -> {
                        DeleteAllDataFirstDialog(onContinue = onContinueDeleteAllData, onCancel = onCancelDeleteAllData)
                    }

                    DeleteAllDataStep.CONFIRM_SECOND -> {
                        DeleteAllDataFinalDialog(onConfirm = onConfirmDeleteAllData, onCancel = onCancelDeleteAllData)
                    }

                    DeleteAllDataStep.DONE -> {
                        DeleteAllDataDoneDialog(onDismiss = onDismissDeleteAllDataDone)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    val loading = stringResource(R.string.settings_loading)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loading })
    }
}

@Composable
private fun LoadedContent(
    state: SettingsUiState.Loaded,
    onWeekdayDisplaySelected: (WeekdayDisplay) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onHolidaySetEnabledChanged: (id: String, enabled: Boolean) -> Unit,
    onRequestDeleteAllData: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
    ) {
        WeekdaySection(state.settings.weekdayDisplay, onWeekdayDisplaySelected)
        HorizontalDivider()
        ThemeSection(state, onThemeModeSelected, onDynamicColorChanged)
        HorizontalDivider()
        HolidaySection(state, onHolidaySetEnabledChanged)
        HorizontalDivider()
        DataSection(onRequestDeleteAllData)
    }
}

/**
 * FEATURES W6: "Delete all data", the honest answer to "how do I erase my data"
 * (`docs/security-and-privacy.md` §2.4). Destructive by wording and icon, not colour alone — the row
 * itself opens the first of two confirmations, never erasing anything by a single tap.
 */
@Composable
private fun DataSection(onRequestDeleteAllData: () -> Unit) {
    SectionHeading(stringResource(R.string.settings_section_data))
    ListItem(
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        headlineContent = {
            Text(stringResource(R.string.settings_delete_all_data), color = MaterialTheme.colorScheme.error)
        },
        supportingContent = { Text(stringResource(R.string.settings_delete_all_data_detail)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .clickableRole(onClick = onRequestDeleteAllData),
    )
}

/** A full-width, TalkBack-reachable [Role.Button] click target for a [ListItem] row. */
private fun Modifier.clickableRole(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.selectable(selected = false, role = Role.Button, onClick = onClick),
    )

/** The first "Delete all data" confirmation: what will be erased. */
@Composable
private fun DeleteAllDataFirstDialog(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_delete_all_data_confirm1_title)) },
        text = { Text(stringResource(R.string.settings_delete_all_data_confirm1_text)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.settings_delete_all_data_continue)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** The final, unmistakably destructive "Delete all data" confirmation: this cannot be undone. */
@Composable
private fun DeleteAllDataFinalDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_delete_all_data_confirm2_title)) },
        text = { Text(stringResource(R.string.settings_delete_all_data_confirm2_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.settings_delete_all_data),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** Confirms the erase finished. */
@Composable
private fun DeleteAllDataDoneDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_delete_all_data_done_title)) },
        text = { Text(stringResource(R.string.settings_delete_all_data_done_text)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) } },
    )
}

/** FEATURES W1: `BOTH` / `ACTUAL` / `NOMINAL`, with the §4.1 reminder above the options. */
@Composable
private fun WeekdaySection(
    selected: WeekdayDisplay,
    onSelected: (WeekdayDisplay) -> Unit,
) {
    SectionHeading(stringResource(R.string.settings_section_weekdays))
    SectionInfo(stringResource(R.string.settings_weekday_info))
    Column(modifier = Modifier.selectableGroup()) {
        // Listed in the order of FEATURES W1: both (default) / actual only / nominal IFC only.
        RadioRow(
            title = stringResource(R.string.settings_weekday_both),
            detail = stringResource(R.string.settings_weekday_both_detail),
            selected = selected == WeekdayDisplay.BOTH,
            onClick = { onSelected(WeekdayDisplay.BOTH) },
        )
        RadioRow(
            title = stringResource(R.string.settings_weekday_actual),
            detail = stringResource(R.string.settings_weekday_actual_detail),
            selected = selected == WeekdayDisplay.ACTUAL,
            onClick = { onSelected(WeekdayDisplay.ACTUAL) },
        )
        RadioRow(
            title = stringResource(R.string.settings_weekday_nominal),
            detail = stringResource(R.string.settings_weekday_nominal_detail),
            selected = selected == WeekdayDisplay.NOMINAL,
            onClick = { onSelected(WeekdayDisplay.NOMINAL) },
        )
    }
}

/** FEATURES W2: the theme mode and the dynamic-colour switch. */
@Composable
private fun ThemeSection(
    state: SettingsUiState.Loaded,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
) {
    SectionHeading(stringResource(R.string.settings_section_theme))
    Column(modifier = Modifier.selectableGroup()) {
        RadioRow(
            title = stringResource(R.string.settings_theme_system),
            detail = null,
            selected = state.settings.themeMode == ThemeMode.SYSTEM,
            onClick = { onThemeModeSelected(ThemeMode.SYSTEM) },
        )
        RadioRow(
            title = stringResource(R.string.settings_theme_light),
            detail = null,
            selected = state.settings.themeMode == ThemeMode.LIGHT,
            onClick = { onThemeModeSelected(ThemeMode.LIGHT) },
        )
        RadioRow(
            title = stringResource(R.string.settings_theme_dark),
            detail = null,
            selected = state.settings.themeMode == ThemeMode.DARK,
            onClick = { onThemeModeSelected(ThemeMode.DARK) },
        )
    }
    SwitchRow(
        title = stringResource(R.string.settings_dynamic_color),
        detail =
            stringResource(
                if (state.dynamicColorSupported) {
                    R.string.settings_dynamic_color_detail
                } else {
                    R.string.settings_dynamic_color_unavailable
                },
            ),
        checked = state.settings.dynamicColor,
        enabled = state.dynamicColorSupported,
        onCheckedChange = onDynamicColorChanged,
    )
}

/** FEATURES H5: one switch per bundled pack, in catalogue order. */
@Composable
private fun HolidaySection(
    state: SettingsUiState.Loaded,
    onHolidaySetEnabledChanged: (id: String, enabled: Boolean) -> Unit,
) {
    SectionHeading(stringResource(R.string.settings_section_holidays))
    SectionInfo(stringResource(R.string.settings_holidays_info))
    state.packs.forEach { pack ->
        SwitchRow(
            title = pack.name,
            detail = pack.region?.let { stringResource(R.string.settings_holiday_region, it) },
            checked = pack.id in state.settings.enabledHolidaySets,
            enabled = true,
            onCheckedChange = { enabled -> onHolidaySetEnabledChanged(pack.id, enabled) },
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun SectionInfo(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * One option of a radio group. The whole row is the selectable so the tap target spans its width and
 * TalkBack reads title, detail and state as one item; the [RadioButton] itself is decorative.
 */
@Composable
private fun RadioRow(
    title: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A list row with a trailing [Switch]. The row is the toggleable (one semantics node, full-width tap
 * target); the switch is decorative. A disabled row keeps its stored value visible but dims its text.
 */
@Composable
private fun SwitchRow(
    title: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors =
        if (enabled) {
            ListItemDefaults.colors()
        } else {
            ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA),
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            )
        }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = colors,
        modifier =
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

/** Material's opacity for disabled content. */
private const val DISABLED_ALPHA = 0.38f

// Previews — Roborazzi's preview scanner captures every @Preview once docs/ROADMAP.md M2 T10 records
// the goldens; dynamic colour is off for determinism.

@Preview(name = "Light", showBackground = true)
@Composable
internal fun SettingsScreenLightPreview() {
    SettingsPreview(darkTheme = false)
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SettingsScreenDarkPreview() {
    SettingsPreview(darkTheme = true)
}

@Preview(name = "Font 2.0", showBackground = true, fontScale = 2f)
@Composable
internal fun SettingsScreenLargeFontPreview() {
    SettingsPreview(darkTheme = false)
}

/** A device below API 31: the dynamic-colour row is disabled with its "not available" subtitle. */
@Preview(name = "No dynamic colour", showBackground = true)
@Composable
internal fun SettingsScreenNoDynamicColorPreview() {
    SettingsPreview(darkTheme = false, dynamicColorSupported = false)
}

@Composable
private fun SettingsPreview(
    darkTheme: Boolean,
    dynamicColorSupported: Boolean = true,
) {
    IfcTheme(darkTheme = darkTheme, dynamicColor = false) {
        SettingsScreen(
            state =
                SettingsUiState.Loaded(
                    settings = UserSettings.DEFAULT,
                    packs = previewPacks,
                    dynamicColorSupported = dynamicColorSupported,
                ),
            onBack = {},
            onWeekdayDisplaySelected = {},
            onThemeModeSelected = {},
            onDynamicColorChanged = {},
            onHolidaySetEnabledChanged = { _, _ -> },
        )
    }
}

/** The bundled packs as the loader lists them for an English device (preview data, not resources). */
private val previewPacks =
    listOf(
        HolidayPackItem(id = "ifc", name = "International Fixed Calendar", region = null),
        HolidayPackItem(id = "us", name = "United States", region = "United States"),
        HolidayPackItem(id = "religious-christian", name = "Christian (Easter family)", region = null),
    )
