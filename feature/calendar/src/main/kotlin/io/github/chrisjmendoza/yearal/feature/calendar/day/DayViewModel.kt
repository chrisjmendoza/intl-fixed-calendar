package io.github.chrisjmendoza.yearal.feature.calendar.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.feature.calendar.holiday.HolidayCatalog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * State holder for the Day detail sheet (docs/ARCHITECTURE.md §4 "State management"; FEATURES C5).
 * The day is fixed at creation; whether it is "today" comes only from [DateTicker] (CLAUDE.md rule
 * 2), so the badge rolls over at local midnight, and the holiday list follows
 * `UserSettings.enabledHolidaySets` live.
 *
 * The day is assisted-injected from the `DayKey` of the entry (see [Factory]); tests build the
 * ViewModel with the constructor. Stops collecting five seconds after the last subscriber leaves.
 *
 * @param day the Gregorian date to show (CLAUDE.md rule 4: dates travel Gregorian).
 */
@HiltViewModel(assistedFactory = DayViewModel.Factory::class)
class DayViewModel
    @AssistedInject
    constructor(
        @Assisted day: LocalDate,
        dateTicker: DateTicker,
        settingsRepository: SettingsRepository,
        catalog: HolidayCatalog,
        formatter: IfcDateFormatter,
    ) : ViewModel() {
        /** Creates a [DayViewModel] for the entry's day; used by `hiltViewModel(creationCallback)`. */
        @AssistedFactory
        interface Factory {
            /** @param day the Gregorian date to show. */
            fun create(day: LocalDate): DayViewModel
        }

        /** [DayUiState.Loading] until the first tick (immediate), then a [DayUiState.Loaded] per change. */
        val uiState: StateFlow<DayUiState> =
            combine(dateTicker.today, settingsRepository.settings) { today, settings ->
                val holidays = catalog.labels(settings.enabledHolidaySets, day..day)[day].orEmpty()
                buildDayUiState(day, today, formatter, holidays)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DayUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
