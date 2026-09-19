package io.github.chrisjmendoza.yearal.feature.calendar.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.designsystem.format.IfcDateFormatter
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.event.DayAgenda
import io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.feature.calendar.agenda.toAgendaItemUi
import io.github.chrisjmendoza.yearal.feature.calendar.holiday.HolidayCatalog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * State holder for the Today screen (docs/ARCHITECTURE.md §4 "State management"; FEATURES T5).
 * "Today" comes only from [DateTicker] (CLAUDE.md rule 2), so the state rolls over at local midnight
 * and on the time and zone changes the ticker relays (FEATURES T6); it is never cached here. Today's
 * agenda and holidays follow [observeAgenda] and `UserSettings.enabledHolidaySets` live, the same
 * single evaluation path the Month pager and Day detail use (docs/ARCHITECTURE.md §3.4).
 *
 * Stops collecting five seconds after the last subscriber leaves, so a rotation does not restart it
 * while a backgrounded app does not keep it alive.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        dateTicker: DateTicker,
        formatter: IfcDateFormatter,
        settingsRepository: SettingsRepository,
        catalog: HolidayCatalog,
        observeAgenda: ObserveAgendaUseCase,
    ) : ViewModel() {
        private val agendaByDay: Flow<Map<LocalDate, DayAgenda>> =
            dateTicker.today.flatMapLatest { today -> observeAgenda(today..today) }

        /** [TodayUiState.Loading] until the first tick (immediate), then a [TodayUiState.Loaded] per day. */
        val uiState: StateFlow<TodayUiState> =
            combine(dateTicker.today, settingsRepository.settings, agendaByDay) { today, settings, agendas ->
                val holidays = catalog.labels(settings.enabledHolidaySets, today..today)[today].orEmpty()
                val nextHoliday = catalog.nextHoliday(settings.enabledHolidaySets, today)
                val agenda = agendas[today]?.entries.orEmpty().map { it.toAgendaItemUi() }
                buildTodayUiState(today, formatter, holidays, nextHoliday, agenda)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
