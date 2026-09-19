package io.github.chrisjmendoza.yearal.feature.converter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange
import io.github.chrisjmendoza.yearal.core.designsystem.picker.IfcDatePickerValue
import io.github.chrisjmendoza.yearal.core.designsystem.picker.IfcDaySelection
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.navigation.ConverterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * State holder for the converter (docs/ARCHITECTURE.md §4 "State management"; FEATURES D1, D2).
 *
 * **Inputs.** There is one chosen day, picked in either calendar, and the IFC picker's text as typed.
 * Until something is chosen the inputs follow "today", which comes only from [DateTicker] (CLAUDE.md
 * rule 2) and so rolls over at local midnight. `ConverterKey.prefillEpochDay` counts as a choice when
 * it lies in 1583..9999; anything else is ignored (fail soft — docs/security-and-privacy.md §6.3),
 * because a key can be synthesized from an intent.
 *
 * **Process death.** Every input is written through to the [SavedStateHandle] as primitives (the day
 * as a Gregorian epoch day — CLAUDE.md rule 4; the IFC selection as its numeric pseudo-fields) and
 * re-validated when read back. Restored input wins over the key's prefill.
 *
 * **Direction switch.** [setDirection] only changes which input is active; it never discards the other
 * input, so switching back and forth never loses or alters the chosen day (see
 * [ConverterUiState.Loaded.direction]). [uiState] rebuilds on every call to [setDirection],
 * [setGregorianDate], [setIfcInput] or [resetToToday], and on every [DateTicker] tick — a tick changes
 * the result only while no day is chosen, since [DateTicker] supplies the default, never overrides one.
 *
 * **Round trip.** Converting a chosen day to the other calendar and back always lands on the same
 * physical day; `ConverterViewModelTest` property-tests this against generated dates across 1583..9999
 * (docs/ROADMAP.md M3 exit).
 *
 * Every conversion goes through `:core:calendar` (rule 1); invalid input becomes
 * [ConversionResult.Invalid], never an exception. Stops collecting the ticker five seconds after the
 * last subscriber leaves.
 *
 * @param key the entry's key, assisted-injected (see [Factory]); tests use the constructor.
 */
@HiltViewModel(assistedFactory = ConverterViewModel.Factory::class)
class ConverterViewModel
    @AssistedInject
    constructor(
        @Assisted key: ConverterKey,
        private val savedStateHandle: SavedStateHandle,
        dateTicker: DateTicker,
        formatter: IfcDateFormatter,
    ) : ViewModel() {
        /** Creates a [ConverterViewModel] for the entry's key; used by `hiltViewModel(creationCallback)`. */
        @AssistedFactory
        interface Factory {
            /** @param key the converter entry's key, with its optional prefill. */
            fun create(key: ConverterKey): ConverterViewModel
        }

        private val input = MutableStateFlow(restoreInput(savedStateHandle) ?: initialInput(key))

        /** [ConverterUiState.Loading] until the first tick (immediate), then a [ConverterUiState.Loaded] per change. */
        val uiState: StateFlow<ConverterUiState> =
            combine(dateTicker.today, input) { today, current -> buildConverterUiState(today, current, formatter) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConverterUiState.Loading)

        /** Switches which input is active. Both inputs are kept, so switching back loses nothing. */
        fun setDirection(direction: ConversionDirection) {
            update { it.copy(direction = direction) }
        }

        /**
         * Chooses [date] in the Gregorian calendar; the IFC input follows it. A date outside 1583..9999
         * is kept as the input and converts to [ConversionResult.Invalid].
         */
        fun setGregorianDate(date: LocalDate) {
            update { it.copy(chosen = date, ifcDraft = null) }
        }

        /**
         * Takes the IFC picker's new [value]. When it holds a date, that day becomes the chosen day and
         * the Gregorian input follows it; while it does not (the year is being typed), the chosen day
         * stays and the IFC → Gregorian result is [ConversionResult.Invalid].
         */
        fun setIfcInput(value: IfcDatePickerValue) {
            update { it.copy(chosen = value.date?.toLocalDate() ?: it.chosen, ifcDraft = value) }
        }

        /** Forgets the chosen day: both inputs follow the [DateTicker] again. The direction is kept. */
        fun resetToToday() {
            update { it.copy(chosen = null, ifcDraft = null) }
        }

        private fun update(transform: (ConverterInput) -> ConverterInput) {
            val next = transform(input.value)
            input.value = next
            next.saveTo(savedStateHandle)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * The converter's input, the part of its state that is saved.
 *
 * @property direction which input is active; carried into [ConverterUiState.Loaded.direction] unchanged.
 * @property chosen the chosen day, or `null` to follow today.
 * @property ifcDraft the IFC picker's value once the user has edited it, or `null` to derive it from
 * the chosen day. When it holds a date, that date is [chosen].
 */
internal data class ConverterInput(
    val direction: ConversionDirection,
    val chosen: LocalDate?,
    val ifcDraft: IfcDatePickerValue?,
)

/**
 * Builds the [ConverterUiState.Loaded] for [input] when the real today is [today]. Gregorian → IFC
 * converts with [IfcDate.from]; IFC → Gregorian converts the picker's own [IfcDate] with
 * [IfcDate.toLocalDate] — each direction really runs its own conversion in `:core:calendar`.
 */
internal fun buildConverterUiState(
    today: LocalDate,
    input: ConverterInput,
    formatter: IfcDateFormatter,
): ConverterUiState.Loaded {
    val gregorian = input.chosen ?: today
    val ifcInput = input.ifcDraft ?: pickerValueFor(gregorian, today)
    val result =
        when (input.direction) {
            ConversionDirection.GREGORIAN_TO_IFC -> {
                convertGregorian(gregorian, formatter)
            }

            ConversionDirection.IFC_TO_GREGORIAN -> {
                ifcInput.date?.let { convertIfc(it, formatter) }
                    ?: ConversionResult.Invalid
            }
        }
    return ConverterUiState.Loaded(
        direction = input.direction,
        gregorianInput = gregorian,
        gregorianInputLabel = formatter.formatGregorianLong(gregorian),
        ifcInput = ifcInput,
        followsToday = input.chosen == null && input.ifcDraft == null,
        result = result,
    )
}

// The picker needs some date to show even when the Gregorian input cannot be converted (only possible
// when a caller passed an out-of-range date in): today, or failing that the first day of the range.
private fun pickerValueFor(
    gregorian: LocalDate,
    today: LocalDate,
): IfcDatePickerValue {
    val shown = listOf(gregorian, today).firstOrNull(DatePickerRange::contains)
    val date = shown?.let(IfcDate::from) ?: IfcDate.Regular(DatePickerRange.MIN_YEAR, IfcMonth.JANUARY, 1)
    return IfcDatePickerValue.of(date)
}

private fun initialInput(key: ConverterKey): ConverterInput =
    ConverterInput(
        direction = ConversionDirection.GREGORIAN_TO_IFC,
        chosen = key.prefillEpochDay?.let(::epochDayToDate)?.takeIf(DatePickerRange::contains),
        ifcDraft = null,
    )

private const val KEY_DIRECTION = "converter.direction"
private const val KEY_EPOCH_DAY = "converter.epochDay"
private const val KEY_IFC_YEAR_TEXT = "converter.ifc.yearText"
private const val KEY_IFC_MONTH = "converter.ifc.month"
private const val KEY_IFC_DAY = "converter.ifc.day"
private const val KEY_IFC_CLAMPED = "converter.ifc.clamped"

private fun ConverterInput.saveTo(handle: SavedStateHandle) {
    handle[KEY_DIRECTION] = direction.name
    handle[KEY_EPOCH_DAY] = chosen?.toEpochDay()
    handle[KEY_IFC_YEAR_TEXT] = ifcDraft?.yearText
    handle[KEY_IFC_MONTH] = ifcDraft?.selection?.monthNumber
    handle[KEY_IFC_DAY] = ifcDraft?.selection?.dayOfMonth
    handle[KEY_IFC_CLAMPED] = ifcDraft?.leapDayClamped
}

// Saved state is re-validated rather than trusted: an unknown direction means "nothing saved", and a
// draft that names no IFC day is dropped.
private fun restoreInput(handle: SavedStateHandle): ConverterInput? {
    val direction =
        ConversionDirection.entries.firstOrNull { it.name == handle.get<String>(KEY_DIRECTION) } ?: return null
    val yearText = handle.get<String>(KEY_IFC_YEAR_TEXT)
    val selection =
        handle.get<Int>(KEY_IFC_MONTH)?.let { month ->
            handle.get<Int>(KEY_IFC_DAY)?.let { day -> IfcDaySelection.of(month, day) }
        }
    val draft =
        if (yearText != null && selection != null) {
            IfcDatePickerValue.restore(yearText, selection, handle.get<Boolean>(KEY_IFC_CLAMPED) == true)
        } else {
            null
        }
    // A draft that holds a date is the chosen day; otherwise the saved epoch day is.
    val chosen = draft?.date?.toLocalDate() ?: handle.get<Long>(KEY_EPOCH_DAY)?.let(::epochDayToDate)
    return ConverterInput(direction = direction, chosen = chosen, ifcDraft = draft)
}

// LocalDate.ofEpochDay throws outside LocalDate's own range; a Long from a key or saved state can be anything.
private fun epochDayToDate(epochDay: Long): LocalDate? =
    if (epochDay in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()) LocalDate.ofEpochDay(epochDay) else null
