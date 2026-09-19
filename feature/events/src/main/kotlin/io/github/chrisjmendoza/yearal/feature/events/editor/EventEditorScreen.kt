package io.github.chrisjmendoza.yearal.feature.events.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.format.rememberIfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.picker.GregorianDatePickerDialog
import io.github.chrisjmendoza.yearal.core.designsystem.picker.IfcDatePicker
import io.github.chrisjmendoza.yearal.core.designsystem.picker.rememberIfcDatePickerState
import io.github.chrisjmendoza.yearal.core.designsystem.theme.IfcTheme
import io.github.chrisjmendoza.yearal.core.domain.event.LeapDayPolicy
import io.github.chrisjmendoza.yearal.core.navigation.EventEditorKey
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.feature.events.R
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

private val ScreenPadding = 16.dp
private val SectionSpacing = 16.dp
private val FieldSpacing = 8.dp
private val MinTouchTarget = 48.dp
private val ChipSpacing = 8.dp

/**
 * The event editor (`EventEditorKey`, `docs/ROADMAP.md` M4 T4): collects
 * [EventEditorViewModel.uiState] and renders it through the stateless [EventEditorScreen]. Reacts to
 * [EventEditorViewModel.editorEvents] by leaving the screen ([navigator.goBack]) and to the system back
 * gesture by asking the ViewModel first, so an unsaved draft can show its guard.
 *
 * @param key which event to edit, or none for a new one.
 * @param navigator receives the "leave the editor" action.
 */
@Composable
fun EventEditorRoute(
    key: EventEditorKey,
    navigator: Navigator,
    modifier: Modifier = Modifier,
    viewModel: EventEditorViewModel =
        hiltViewModel<EventEditorViewModel, EventEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(key) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.editorEvents.collectLatest { navigator.goBack() }
    }
    BackHandler(onBack = viewModel::requestBack)
    EventEditorScreen(
        state = state,
        callbacks =
            EventEditorCallbacks(
                onTitleChange = viewModel::setTitle,
                onDescriptionChange = viewModel::setDescription,
                onLocationChange = viewModel::setLocation,
                onAllDayChange = viewModel::setAllDay,
                onStartDateChange = viewModel::setStartDate,
                onAllDayEndDateChange = viewModel::setAllDayEndDate,
                onStartMinuteChange = viewModel::setStartMinuteOfDay,
                onEndMinuteChange = viewModel::setEndMinuteOfDay,
                onZoneChoiceChange = viewModel::setZoneChoice,
                onRecurrenceKindChange = viewModel::setRecurrenceKind,
                onLeapDayPolicyChange = viewModel::setLeapDayPolicy,
                onRecurrenceEndKindChange = viewModel::setRecurrenceEndKind,
                onUntilDateChange = viewModel::setUntilDate,
                onCountChange = viewModel::setCount,
                onToggleReminder = viewModel::toggleReminder,
                onSave = viewModel::save,
                onRequestDelete = viewModel::requestDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onCancelDelete = viewModel::cancelDelete,
                onBack = viewModel::requestBack,
                onConfirmDiscard = viewModel::confirmDiscard,
                onCancelDiscard = viewModel::cancelDiscard,
            ),
        modifier = modifier,
    )
}

/** Every intent the stateless [EventEditorScreen] reports (grouped to keep the composable's signature short). */
data class EventEditorCallbacks(
    val onTitleChange: (String) -> Unit,
    val onDescriptionChange: (String) -> Unit,
    val onLocationChange: (String) -> Unit,
    val onAllDayChange: (Boolean) -> Unit,
    val onStartDateChange: (LocalDate) -> Unit,
    val onAllDayEndDateChange: (LocalDate) -> Unit,
    val onStartMinuteChange: (Int) -> Unit,
    val onEndMinuteChange: (Int) -> Unit,
    val onZoneChoiceChange: (ZoneChoice) -> Unit,
    val onRecurrenceKindChange: (RecurrenceKind) -> Unit,
    val onLeapDayPolicyChange: (LeapDayPolicy) -> Unit,
    val onRecurrenceEndKindChange: (RecurrenceEndKind) -> Unit,
    val onUntilDateChange: (LocalDate) -> Unit,
    val onCountChange: (Int) -> Unit,
    val onToggleReminder: (Int) -> Unit,
    val onSave: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onBack: () -> Unit,
    val onConfirmDiscard: () -> Unit,
    val onCancelDiscard: () -> Unit,
)

/** Which date field a picker dialog is currently editing. */
private enum class PickerTarget { START, ALL_DAY_END, UNTIL }

/**
 * The stateless event editor — the unit for previews and Compose tests. Title/notes/location, all-day
 * vs timed with start/end date and time (either calendar — FEATURES E2), device vs fixed zone, the
 * recurrence chooser with the Leap Day policy and end condition, reminder chips, and save/delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(
    state: EventEditorUiState,
    callbacks: EventEditorCallbacks,
    modifier: Modifier = Modifier,
    formatter: IfcDateFormatter = rememberIfcDateFormatter(),
) {
    var chooserTarget by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    var gregorianPickerTarget by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    var ifcPickerTarget by rememberSaveable { mutableStateOf<PickerTarget?>(null) }
    var timePickerIsStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val loaded = state as? EventEditorUiState.Loaded

    fun dateFor(
        target: PickerTarget,
        current: EventEditorUiState.Loaded,
    ): LocalDate =
        when (target) {
            PickerTarget.START -> current.startDate
            PickerTarget.ALL_DAY_END -> current.allDayEndDate
            PickerTarget.UNTIL -> current.untilDate
        }

    fun onDatePicked(
        target: PickerTarget,
        date: LocalDate,
    ) {
        when (target) {
            PickerTarget.START -> callbacks.onStartDateChange(date)
            PickerTarget.ALL_DAY_END -> callbacks.onAllDayEndDateChange(date)
            PickerTarget.UNTIL -> callbacks.onUntilDateChange(date)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            EditorTopBar(state = state, callbacks = callbacks)
        },
    ) { padding ->
        when (state) {
            EventEditorUiState.Loading -> {
                Unit
            }

            EventEditorUiState.NotFound -> {
                Text(
                    text = stringResource(R.string.events_editor_not_found),
                    modifier = Modifier.padding(padding).padding(ScreenPadding),
                )
            }

            is EventEditorUiState.Loaded -> {
                EditorBody(
                    state = state,
                    callbacks = callbacks,
                    formatter = formatter,
                    modifier = Modifier.padding(padding),
                    onPickStart = { chooserTarget = PickerTarget.START },
                    onPickAllDayEnd = { chooserTarget = PickerTarget.ALL_DAY_END },
                    onPickUntil = { chooserTarget = PickerTarget.UNTIL },
                    onPickStartTime = { timePickerIsStart = true },
                    onPickEndTime = { timePickerIsStart = false },
                )
                if (state.showDeleteConfirm) {
                    DeleteConfirmDialog(onConfirm = callbacks.onConfirmDelete, onDismiss = callbacks.onCancelDelete)
                }
                if (state.showDiscardConfirm) {
                    DiscardConfirmDialog(onConfirm = callbacks.onConfirmDiscard, onDismiss = callbacks.onCancelDiscard)
                }
            }
        }
    }

    if (loaded != null) {
        chooserTarget?.let { target ->
            CalendarChooserDialog(
                onPickGregorian = {
                    chooserTarget = null
                    gregorianPickerTarget = target
                },
                onPickIfc = {
                    chooserTarget = null
                    ifcPickerTarget = target
                },
                onDismiss = { chooserTarget = null },
            )
        }
        gregorianPickerTarget?.let { target ->
            GregorianDatePickerDialog(
                initialDate = dateFor(target, loaded),
                onConfirm = { date ->
                    gregorianPickerTarget = null
                    onDatePicked(target, date)
                },
                onDismiss = { gregorianPickerTarget = null },
            )
        }
        ifcPickerTarget?.let { target ->
            IfcDateChooserDialog(
                initialDate = dateFor(target, loaded),
                onConfirm = { date ->
                    ifcPickerTarget = null
                    onDatePicked(target, date)
                },
                onDismiss = { ifcPickerTarget = null },
            )
        }
        timePickerIsStart?.let { isStart ->
            EditorTimePickerDialog(
                initialMinuteOfDay = if (isStart) loaded.startMinuteOfDay else loaded.endMinuteOfDay,
                onConfirm = { minute ->
                    timePickerIsStart = null
                    if (isStart) callbacks.onStartMinuteChange(minute) else callbacks.onEndMinuteChange(minute)
                },
                onDismiss = { timePickerIsStart = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    state: EventEditorUiState,
    callbacks: EventEditorCallbacks,
) {
    val loaded = state as? EventEditorUiState.Loaded
    TopAppBar(
        title = {
            Text(
                stringResource(
                    if (loaded?.isNew != false) R.string.events_editor_title_new else R.string.events_editor_title_edit,
                ),
            )
        },
        navigationIcon = {
            IconButton(onClick = callbacks.onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.events_editor_back),
                )
            }
        },
        actions = {
            if (loaded != null && !loaded.isNew) {
                IconButton(onClick = callbacks.onRequestDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.events_editor_delete))
                }
            }
            IconButton(onClick = callbacks.onSave, enabled = loaded?.canSave == true) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.events_editor_save))
            }
        },
    )
}

@Composable
private fun EditorBody(
    state: EventEditorUiState.Loaded,
    callbacks: EventEditorCallbacks,
    formatter: IfcDateFormatter,
    modifier: Modifier,
    onPickStart: () -> Unit,
    onPickAllDayEnd: () -> Unit,
    onPickUntil: () -> Unit,
    onPickStartTime: () -> Unit,
    onPickEndTime: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        if (state.saveFailed) {
            Text(
                text = stringResource(R.string.events_editor_save_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = callbacks.onTitleChange,
            label = { Text(stringResource(R.string.events_editor_title_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = callbacks.onDescriptionChange,
            label = { Text(stringResource(R.string.events_editor_notes_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.location,
            onValueChange = callbacks.onLocationChange,
            label = { Text(stringResource(R.string.events_editor_location_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.events_editor_all_day_label), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = state.isAllDay, onCheckedChange = callbacks.onAllDayChange)
        }

        DateField(
            label = stringResource(R.string.events_editor_start_label),
            ifcLabel = state.startIfcLabel,
            gregorianLabel = state.startGregorianLabel,
            onClick = onPickStart,
        )
        if (state.isAllDay) {
            DateField(
                label = stringResource(R.string.events_editor_end_date_label),
                ifcLabel = null,
                gregorianLabel = formatter.formatGregorianLong(state.allDayEndDate),
                onClick = onPickAllDayEnd,
            )
            if (state.allDayEndBeforeStart) ErrorText(stringResource(R.string.events_editor_end_before_start))
        } else {
            TimeRow(
                startMinute = state.startMinuteOfDay,
                endMinute = state.endMinuteOfDay,
                onPickStartTime = onPickStartTime,
                onPickEndTime = onPickEndTime,
            )
            if (state.endBeforeStart) ErrorText(stringResource(R.string.events_editor_end_before_start))
            ZoneChoiceRow(
                zoneChoice = state.zoneChoice,
                fixedZoneId = state.fixedZoneId.id,
                onChange = callbacks.onZoneChoiceChange,
            )
        }

        RecurrenceSection(state = state, callbacks = callbacks, formatter = formatter, onPickUntil = onPickUntil)

        ReminderSection(reminders = state.reminders, onToggle = callbacks.onToggleReminder)
    }
}

@Composable
private fun DateField(
    label: String,
    ifcLabel: String?,
    gregorianLabel: String,
    onClick: () -> Unit,
) {
    val description =
        if (ifcLabel != null) {
            stringResource(R.string.events_editor_date_description, label, ifcLabel, gregorianLabel)
        } else {
            stringResource(R.string.events_editor_date_description_gregorian_only, label, gregorianLabel)
        }
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing / 2)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = onClick,
            modifier =
                Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).semantics {
                    contentDescription =
                        description
                },
        ) {
            Text(
                if (ifcLabel != null) {
                    stringResource(R.string.events_editor_date_pair, ifcLabel, gregorianLabel)
                } else {
                    gregorianLabel
                },
            )
        }
    }
}

@Composable
private fun TimeRow(
    startMinute: Int,
    endMinute: Int,
    onPickStartTime: () -> Unit,
    onPickEndTime: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        OutlinedButton(onClick = onPickStartTime, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget)) {
            Text(formatMinuteOfDayLabel(startMinute))
        }
        OutlinedButton(onClick = onPickEndTime, modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget)) {
            Text(formatMinuteOfDayLabel(endMinute))
        }
    }
}

@Composable
private fun ZoneChoiceRow(
    zoneChoice: ZoneChoice,
    fixedZoneId: String,
    onChange: (ZoneChoice) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = zoneChoice == ZoneChoice.FLOATING,
            onClick = { onChange(ZoneChoice.FLOATING) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            modifier = Modifier.heightIn(min = MinTouchTarget),
        ) { Text(stringResource(R.string.events_editor_zone_floating)) }
        SegmentedButton(
            selected = zoneChoice == ZoneChoice.FIXED,
            onClick = { onChange(ZoneChoice.FIXED) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            modifier = Modifier.heightIn(min = MinTouchTarget),
        ) { Text(stringResource(R.string.events_editor_zone_fixed, fixedZoneId)) }
    }
}

@Composable
private fun RecurrenceSection(
    state: EventEditorUiState.Loaded,
    callbacks: EventEditorCallbacks,
    formatter: IfcDateFormatter,
    onPickUntil: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        Text(stringResource(R.string.events_editor_recurrence_heading), style = MaterialTheme.typography.titleSmall)
        Column(modifier = Modifier.selectableGroup()) {
            RecurrenceOption(
                label = stringResource(R.string.events_recurrence_option_none),
                selected = state.recurrenceKind == RecurrenceKind.NONE,
                onClick = { callbacks.onRecurrenceKindChange(RecurrenceKind.NONE) },
            )
            RecurrenceOption(
                label = stringResource(R.string.events_recurrence_option_yearly_ifc, state.startIfcDayLabel),
                selected = state.recurrenceKind == RecurrenceKind.YEARLY_IFC,
                onClick = { callbacks.onRecurrenceKindChange(RecurrenceKind.YEARLY_IFC) },
            )
            RecurrenceOption(
                label =
                    stringResource(
                        R.string.events_recurrence_option_yearly_gregorian,
                        state.startGregorianDayLabel,
                    ),
                selected = state.recurrenceKind == RecurrenceKind.YEARLY_GREGORIAN,
                onClick = { callbacks.onRecurrenceKindChange(RecurrenceKind.YEARLY_GREGORIAN) },
            )
            if (state.monthlyIfcAvailable) {
                RecurrenceOption(
                    label = stringResource(R.string.events_recurrence_option_monthly_ifc),
                    selected = state.recurrenceKind == RecurrenceKind.MONTHLY_IFC,
                    onClick = { callbacks.onRecurrenceKindChange(RecurrenceKind.MONTHLY_IFC) },
                )
            }
            RecurrenceOption(
                label = stringResource(R.string.events_recurrence_option_weekly),
                selected = state.recurrenceKind == RecurrenceKind.WEEKLY,
                onClick = { callbacks.onRecurrenceKindChange(RecurrenceKind.WEEKLY) },
            )
        }
        if (state.recurrenceKind != RecurrenceKind.NONE) {
            if (state.recurrenceKind == RecurrenceKind.YEARLY_IFC && state.isLeapDayAnchor) {
                LeapDayPolicySection(policy = state.leapDayPolicy, onChange = callbacks.onLeapDayPolicyChange)
            }
            RecurrenceEndSection(state = state, callbacks = callbacks, formatter = formatter, onPickUntil = onPickUntil)
        }
    }
}

@Composable
private fun LeapDayPolicySection(
    policy: LeapDayPolicy,
    onChange: (LeapDayPolicy) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing / 2)) {
        Text(
            stringResource(R.string.events_editor_leap_day_policy_heading),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.events_editor_leap_day_policy_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.selectableGroup()) {
            RecurrenceOption(
                label = stringResource(R.string.events_editor_leap_day_policy_june28),
                selected = policy == LeapDayPolicy.JUNE_28,
                onClick = { onChange(LeapDayPolicy.JUNE_28) },
            )
            RecurrenceOption(
                label = stringResource(R.string.events_editor_leap_day_policy_skip),
                selected = policy == LeapDayPolicy.SKIP,
                onClick = { onChange(LeapDayPolicy.SKIP) },
            )
            RecurrenceOption(
                label = stringResource(R.string.events_editor_leap_day_policy_sol1),
                selected = policy == LeapDayPolicy.SOL_1,
                onClick = { onChange(LeapDayPolicy.SOL_1) },
            )
        }
    }
}

@Composable
private fun RecurrenceEndSection(
    state: EventEditorUiState.Loaded,
    callbacks: EventEditorCallbacks,
    formatter: IfcDateFormatter,
    onPickUntil: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing / 2)) {
        Text(stringResource(R.string.events_editor_end_heading), style = MaterialTheme.typography.labelLarge)
        Column(modifier = Modifier.selectableGroup()) {
            RecurrenceOption(
                label = stringResource(R.string.events_editor_end_never),
                selected = state.recurrenceEndKind == RecurrenceEndKind.NEVER,
                onClick = { callbacks.onRecurrenceEndKindChange(RecurrenceEndKind.NEVER) },
            )
            RecurrenceOption(
                label = stringResource(R.string.events_editor_end_until),
                selected = state.recurrenceEndKind == RecurrenceEndKind.UNTIL,
                onClick = { callbacks.onRecurrenceEndKindChange(RecurrenceEndKind.UNTIL) },
            )
            RecurrenceOption(
                label = stringResource(R.string.events_editor_end_count),
                selected = state.recurrenceEndKind == RecurrenceEndKind.COUNT,
                onClick = { callbacks.onRecurrenceEndKindChange(RecurrenceEndKind.COUNT) },
            )
        }
        when (state.recurrenceEndKind) {
            RecurrenceEndKind.NEVER -> {
                Unit
            }

            RecurrenceEndKind.UNTIL -> {
                OutlinedButton(
                    onClick = onPickUntil,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                ) { Text(formatter.formatGregorianLong(state.untilDate)) }
                if (state.untilBeforeStart) ErrorText(stringResource(R.string.events_editor_until_before_start))
            }

            RecurrenceEndKind.COUNT -> {
                OutlinedTextField(
                    value = state.count.toString(),
                    onValueChange = { text -> text.toIntOrNull()?.let(callbacks.onCountChange) },
                    label = { Text(stringResource(R.string.events_editor_count_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}

/**
 * Reminder chips plus an honesty note: chips are stored on the draft, but nothing in the app delivers a
 * notification yet — `ReminderScheduler` is a no-op until `docs/ROADMAP.md` M6 T1. Remove the note
 * ([R.string.events_editor_reminders_not_yet_delivered]) once that lands.
 */
@Composable
private fun ReminderSection(
    reminders: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing / 2)) {
        Text(stringResource(R.string.events_editor_reminders_heading), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.events_editor_reminders_not_yet_delivered),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
            for (minutes in EventDraft.REMINDER_PRESETS) {
                FilterChip(
                    selected = minutes in reminders,
                    onClick = { onToggle(minutes) },
                    label = { Text(reminderLabel(minutes)) },
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                )
            }
        }
    }
}

@Composable
private fun reminderLabel(minutes: Int): String =
    when (minutes) {
        0 -> stringResource(R.string.events_reminder_0)
        10 -> stringResource(R.string.events_reminder_10)
        30 -> stringResource(R.string.events_reminder_30)
        60 -> stringResource(R.string.events_reminder_60)
        1440 -> stringResource(R.string.events_reminder_1440)
        else -> pluralStringResource(R.plurals.events_reminder_minutes_before, minutes, minutes)
    }

@Composable
private fun RecurrenceOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Selectable Surface (as IfcDatePicker's Option), not a plain Row + Modifier.selectable: Material3's
    // RadioButton drops its own 48dp touch target when its onClick is null (it hands that job to the
    // ancestor that owns the click), so the enclosing container must be the one that reserves 48dp.
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).semantics { role = Role.RadioButton },
        color = Color.Transparent,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).padding(vertical = FieldSpacing / 2),
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(label, modifier = Modifier.padding(start = FieldSpacing))
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.events_editor_delete_confirm_title)) },
        text = { Text(stringResource(R.string.events_editor_delete_confirm_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.events_editor_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.events_editor_cancel)) } },
    )
}

@Composable
private fun DiscardConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.events_editor_discard_confirm_title)) },
        text = { Text(stringResource(R.string.events_editor_discard_confirm_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.events_editor_discard)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.events_editor_cancel)) } },
    )
}

@Composable
private fun CalendarChooserDialog(
    onPickGregorian: () -> Unit,
    onPickIfc: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.events_editor_pick_calendar_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
                TextButton(
                    onClick = onPickGregorian,
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                ) {
                    Text(stringResource(R.string.events_editor_pick_gregorian))
                }
                TextButton(onClick = onPickIfc, modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget)) {
                    Text(stringResource(R.string.events_editor_pick_ifc))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.events_editor_cancel)) } },
    )
}

@Composable
private fun IfcDateChooserDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberIfcDatePickerState(initialDate = IfcDate.from(initialDate))
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { IfcDatePicker(state = pickerState) },
        confirmButton = {
            TextButton(
                onClick = { pickerState.date?.let { onConfirm(it.toLocalDate()) } },
                enabled =
                    pickerState.date != null,
            ) {
                Text(stringResource(R.string.events_editor_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.events_editor_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTimePickerDialog(
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialMinuteOfDay / MINUTES_PER_HOUR,
            initialMinute =
                initialMinuteOfDay % MINUTES_PER_HOUR,
        )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * MINUTES_PER_HOUR + state.minute) }) {
                Text(stringResource(R.string.events_editor_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.events_editor_cancel)) } },
        title = { Text(stringResource(R.string.events_editor_pick_time_title)) },
    ) {
        TimePicker(state = state)
    }
}

private const val MINUTES_PER_HOUR = 60

private fun formatMinuteOfDayLabel(minuteOfDay: Int): String {
    val hour = minuteOfDay / MINUTES_PER_HOUR
    val minute = minuteOfDay % MINUTES_PER_HOUR
    return "%02d:%02d".format(hour, minute)
}

// Previews — a new all-day event and an existing yearly-IFC one on Leap Day, plus 200% font
// (docs/ARCHITECTURE.md §4 "Accessibility"). Dynamic colour is off for determinism.

private fun previewLoaded(
    isNew: Boolean,
    recurrenceKind: RecurrenceKind,
    startDate: LocalDate,
    isLeapDayAnchor: Boolean = false,
): EventEditorUiState.Loaded =
    EventEditorUiState.Loaded(
        isNew = isNew,
        title = if (isNew) "" else "Leap Day birthday",
        description = "",
        location = "",
        isAllDay = true,
        startDate = startDate,
        startIfcLabel = if (isLeapDayAnchor) "Leap Day, 2024" else "Sol 13, 2026",
        startIfcDayLabel = if (isLeapDayAnchor) "Leap Day" else "Sol 13",
        startGregorianLabel = "Monday, June 17, 2024",
        startGregorianDayLabel = "Jun 17",
        allDayEndDate = startDate,
        allDayEndBeforeStart = false,
        startMinuteOfDay = EventDraft.DEFAULT_START_MINUTE,
        endMinuteOfDay = EventDraft.DEFAULT_START_MINUTE + EventDraft.DEFAULT_DURATION_MINUTES,
        endBeforeStart = false,
        zoneChoice = ZoneChoice.FLOATING,
        fixedZoneId = java.time.ZoneId.of("UTC"),
        recurrenceKind = recurrenceKind,
        monthlyIfcAvailable = !isLeapDayAnchor,
        isLeapDayAnchor = isLeapDayAnchor,
        leapDayPolicy = LeapDayPolicy.JUNE_28,
        recurrenceEndKind = RecurrenceEndKind.NEVER,
        untilDate = startDate.plusYears(1),
        untilBeforeStart = false,
        count = 1,
        reminders = setOf(0, 1440),
        canSave = true,
        isDirty = !isNew,
        saveFailed = false,
        showDeleteConfirm = false,
        showDiscardConfirm = false,
    )

private val previewCallbacks =
    EventEditorCallbacks(
        onTitleChange = {},
        onDescriptionChange = {},
        onLocationChange = {},
        onAllDayChange = {},
        onStartDateChange = {},
        onAllDayEndDateChange = {},
        onStartMinuteChange = {},
        onEndMinuteChange = {},
        onZoneChoiceChange = {},
        onRecurrenceKindChange = {},
        onLeapDayPolicyChange = {},
        onRecurrenceEndKindChange = {},
        onUntilDateChange = {},
        onCountChange = {},
        onToggleReminder = {},
        onSave = {},
        onRequestDelete = {},
        onConfirmDelete = {},
        onCancelDelete = {},
        onBack = {},
        onConfirmDiscard = {},
        onCancelDiscard = {},
    )

@Preview(name = "New event", showBackground = true, heightDp = 1400)
@Preview(
    name = "New event, dark",
    showBackground = true,
    heightDp = 1400,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun EventEditorNewPreview() {
    IfcTheme(dynamicColor = false) {
        EventEditorScreen(
            state =
                previewLoaded(
                    isNew = true,
                    recurrenceKind = RecurrenceKind.NONE,
                    startDate = LocalDate.of(2026, 6, 30),
                ),
            callbacks = previewCallbacks,
        )
    }
}

@Preview(name = "Edit, yearly IFC on Leap Day", showBackground = true, heightDp = 1600)
@Preview(name = "Edit, font 2.0", showBackground = true, heightDp = 2200, fontScale = 2f)
@Composable
internal fun EventEditorLeapDayPreview() {
    IfcTheme(dynamicColor = false) {
        EventEditorScreen(
            state =
                previewLoaded(
                    isNew = false,
                    recurrenceKind = RecurrenceKind.YEARLY_IFC,
                    startDate = LocalDate.of(2024, 6, 17),
                    isLeapDayAnchor = true,
                ),
            callbacks = previewCallbacks,
        )
    }
}
