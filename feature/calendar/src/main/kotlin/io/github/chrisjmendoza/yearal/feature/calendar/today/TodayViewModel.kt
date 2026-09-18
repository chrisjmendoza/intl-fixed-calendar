package io.github.chrisjmendoza.yearal.feature.calendar.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State holder for the Today screen (docs/ARCHITECTURE.md §4 "State management"). "Today" comes
 * only from [DateTicker] (CLAUDE.md rule 2), so the state rolls over at local midnight and on the
 * time and zone changes the ticker relays (FEATURES T6); it is never cached here.
 *
 * Stops collecting the ticker five seconds after the last subscriber leaves, so a rotation does not
 * restart it while a backgrounded app does not keep it alive.
 */
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        dateTicker: DateTicker,
        formatter: IfcDateFormatter,
    ) : ViewModel() {
        /** [TodayUiState.Loading] until the first tick (immediate), then a [TodayUiState.Loaded] per day. */
        val uiState: StateFlow<TodayUiState> =
            dateTicker.today
                .map { today -> buildTodayUiState(today, formatter) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
