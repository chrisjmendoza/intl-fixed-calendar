package io.github.chrisjmendoza.yearal.feature.calendar.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.calendar.IfcMonth
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.designsystem.picker.DatePickerRange
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.navigation.YearKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * State holder for the Year overview (docs/ARCHITECTURE.md §4 "State management"; FEATURES C6). "Today"
 * comes only from [DateTicker] (CLAUDE.md rule 2), so the today marks roll over at local midnight,
 * including the December 31 → January 1 case where the shown year does not change but which tile is
 * "today" does. The year's event presence comes from **one** range query,
 * [ObserveAgendaUseCase.presence] over the whole IFC year (docs/ARCHITECTURE.md §3.4 last paragraph),
 * re-issued only when [year] changes.
 *
 * The starting year is assisted-injected from the [YearKey] of the entry (see [Factory]) and clamped
 * to [DatePickerRange] on every change, so a key built from foreign input (an intent extra) can never
 * show a year outside what the app's pickers accept. Stops collecting five seconds after the last
 * subscriber leaves.
 *
 * @param initialYear the year to open on; clamped into [DatePickerRange] before it is shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = YearViewModel.Factory::class)
class YearViewModel
    @AssistedInject
    constructor(
        @Assisted initialYear: Int,
        dateTicker: DateTicker,
        private val observeAgenda: ObserveAgendaUseCase,
    ) : ViewModel() {
        /** Creates a [YearViewModel] for the entry's year; used by `hiltViewModel(creationCallback)`. */
        @AssistedFactory
        interface Factory {
            /** @param initialYear the year to open on, clamped into [DatePickerRange]. */
            fun create(initialYear: Int): YearViewModel
        }

        private val year = MutableStateFlow(clampYear(initialYear))

        private val eventDates = year.flatMapLatest { y -> observeAgenda.presence(wholeYearRange(y)) }

        /**
         * The current [YearUiState]. Starts with the clamped initial year and no today or event marks;
         * the first tick and the year's presence bitmap arrive on subscription.
         */
        val uiState: StateFlow<YearUiState> =
            combine(year, dateTicker.today, eventDates) { y, today, dates ->
                YearUiState(year = y, today = today, eventDates = dates)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                YearUiState(year = year.value, today = null, eventDates = emptySet()),
            )

        /** Shows [newYear], clamped into [DatePickerRange]. */
        fun goToYear(newYear: Int) {
            year.value = clampYear(newYear)
        }

        /** Shows the year before the one currently shown (FEATURES C7), clamped at [DatePickerRange.MIN_YEAR]. */
        fun previousYear() = goToYear(year.value - 1)

        /** Shows the year after the one currently shown (FEATURES C7), clamped at [DatePickerRange.MAX_YEAR]. */
        fun nextYear() = goToYear(year.value + 1)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** [year], or the nearest year inside [DatePickerRange] (ARCHITECTURE "Reconciled decisions" 6). */
private fun clampYear(year: Int): Int = year.coerceIn(DatePickerRange.MIN_YEAR, DatePickerRange.MAX_YEAR)

/**
 * The Gregorian range of every date IFC year [year] covers: January 1 through the year's own Year Day
 * (spec §7.2), built from `:core:calendar` and nothing else (CLAUDE.md rule 1). December's
 * [IfcYearMonth.trailingIntercalary] is never `null` (every year has a Year Day), so the `!!` here is
 * total.
 */
private fun wholeYearRange(year: Int): ClosedRange<LocalDate> {
    val firstDay = IfcYearMonth(year, IfcMonth.JANUARY).firstDay.toLocalDate()
    val yearDay = IfcYearMonth(year, IfcMonth.DECEMBER).trailingIntercalary!!.toLocalDate()
    return firstDay..yearDay
}
