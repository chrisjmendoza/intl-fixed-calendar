package io.github.chrisjmendoza.yearal.feature.calendar.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.calendar.IfcDate
import io.github.chrisjmendoza.yearal.core.calendar.IfcYearMonth
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.feature.calendar.holiday.HolidayCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * State holder for the Month pager (docs/ARCHITECTURE.md §4 "State management"; FEATURES C1, C3,
 * C5, C7). "Today" comes only from [DateTicker] (CLAUDE.md rule 2), so the today ring and the
 * "Today" target roll over at local midnight; the grid headers follow
 * [UserSettings.weekdayDisplay] and the holiday marks [UserSettings.enabledHolidaySets], both live.
 *
 * Holidays are evaluated for the current page and its two neighbours — the three pages the pager
 * keeps warm (docs/ARCHITECTURE.md §3.4) — through the memoised [HolidayCatalog], so paging costs a
 * few map lookups.
 *
 * The starting month is assisted-injected from the `MonthKey` of the entry (see [Factory]); the
 * ViewModel has no other dependency on navigation, so tests build it with the constructor. Stops
 * collecting five seconds after the last subscriber leaves.
 *
 * @param initialMonth the month the pager opens on; its page is the state's first `currentPage`.
 */
@HiltViewModel(assistedFactory = MonthViewModel.Factory::class)
class MonthViewModel
    @AssistedInject
    constructor(
        @Assisted initialMonth: IfcYearMonth,
        dateTicker: DateTicker,
        settingsRepository: SettingsRepository,
        private val catalog: HolidayCatalog,
    ) : ViewModel() {
        /** Creates a [MonthViewModel] for the entry's month; used by `hiltViewModel(creationCallback)`. */
        @AssistedFactory
        interface Factory {
            /** @param initialMonth the month to open on, already clamped by `MonthPages.monthOf`. */
            fun create(initialMonth: IfcYearMonth): MonthViewModel
        }

        private val page = MutableStateFlow(MonthPages.pageOf(initialMonth))
        private val selected = MutableStateFlow<LocalDate?>(null)

        /**
         * The current [MonthUiState]. Starts with the initial page, no today and default settings;
         * the first tick and the stored settings arrive on subscription.
         */
        val uiState: StateFlow<MonthUiState> =
            combine(dateTicker.today, settingsRepository.settings, page, selected) { today, settings, page, selected ->
                MonthUiState(
                    currentPage = page,
                    today = today,
                    todayPage = MonthPages.pageOf(IfcYearMonth.from(IfcDate.from(today))),
                    selected = selected,
                    weekdayDisplay = settings.weekdayDisplay,
                    holidaysByMonth = holidaysAround(page, settings.enabledHolidaySets),
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                MonthUiState(currentPage = page.value, today = null, todayPage = null, selected = null),
            )

        /**
         * Records that the pager now shows [page] (FEATURES C1), so holidays are evaluated around it.
         * Out-of-range values are clamped to `0..MonthPages.LAST_PAGE`.
         */
        fun showPage(page: Int) {
            this.page.value = page.coerceIn(0, MonthPages.LAST_PAGE)
        }

        /** Marks [date] as the selected day (FEATURES C5); the grid fills its cell or band. */
        fun select(date: LocalDate) {
            selected.value = date
        }

        private fun holidaysAround(
            page: Int,
            enabledSetIds: Set<String>,
        ): Map<IfcYearMonth, Map<LocalDate, String>> =
            ((page - 1)..(page + 1))
                .filter { it in 0..MonthPages.LAST_PAGE }
                .map(MonthPages::monthAt)
                .associateWith { month -> catalog.gridLabels(enabledSetIds, month.gregorianRange) }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
