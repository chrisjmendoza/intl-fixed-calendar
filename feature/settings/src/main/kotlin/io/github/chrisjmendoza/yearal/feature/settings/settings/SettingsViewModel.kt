package io.github.chrisjmendoza.yearal.feature.settings.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySet
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.core.domain.settings.ThemeMode
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.github.chrisjmendoza.yearal.core.holidays.BundledHolidayPacks
import io.github.chrisjmendoza.yearal.core.holidays.HolidayPackLoader
import io.github.chrisjmendoza.yearal.feature.settings.di.DynamicColorSupported
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * State holder for the Settings screen (docs/ARCHITECTURE.md §4 "State management"; FEATURES W1, W2,
 * H5). The state mirrors [SettingsRepository.settings]; every intent is a read-modify-write through
 * [SettingsRepository.update], so the screen never holds a value the store does not.
 *
 * The bundled pack list is built once, when the ViewModel is created: three small JSON resources,
 * parsed in well under a millisecond, named for the default locale at that moment (the same
 * once-per-ViewModel locale policy as the calendar feature's formatter).
 *
 * @param dynamicColorSupported whether the platform offers wallpaper-derived colour (API 31+);
 * injected so tests can cover both cases.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        loader: HolidayPackLoader,
        @DynamicColorSupported dynamicColorSupported: Boolean,
    ) : ViewModel() {
        private val packs: List<HolidayPackItem> =
            BundledHolidayPacks.all.map { packName -> loader.loadBundled(packName).toItem(Locale.getDefault()) }

        /** [SettingsUiState.Loading] until the store answers, then a [SettingsUiState.Loaded] per change. */
        val uiState: StateFlow<SettingsUiState> =
            repository.settings
                .map { settings -> SettingsUiState.Loaded(settings, packs, dynamicColorSupported) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState.Loading)

        /** Stores the grid-header mode (FEATURES W1; `BOTH` is the default, ROADMAP decision #5). */
        fun setWeekdayDisplay(display: WeekdayDisplay) {
            update { it.copy(weekdayDisplay = display) }
        }

        /** Stores the light/dark/system choice (FEATURES W2). */
        fun setThemeMode(mode: ThemeMode) {
            update { it.copy(themeMode = mode) }
        }

        /**
         * Stores the Material You preference (FEATURES W2). The value is stored even on devices that
         * cannot honour it, but the screen disables the control there, so this is only reached on API 31+.
         */
        fun setDynamicColor(enabled: Boolean) {
            update { it.copy(dynamicColor = enabled) }
        }

        /**
         * Adds [id] to or removes it from [UserSettings.enabledHolidaySets] (FEATURES H5). Any pack,
         * the IFC observances included, may be switched off; the defaults are what a fresh install shows.
         */
        fun setHolidaySetEnabled(
            id: String,
            enabled: Boolean,
        ) {
            update {
                val sets = if (enabled) it.enabledHolidaySets + id else it.enabledHolidaySets - id
                it.copy(enabledHolidaySets = sets)
            }
        }

        private fun update(transform: (UserSettings) -> UserSettings) {
            viewModelScope.launch { repository.update(transform) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * The list entry for [set]: its name for [locale]'s language tag (exact match, else English) and its
 * region as a country name in [locale], or the raw code if the JDK has no name for it.
 */
internal fun HolidaySet.toItem(locale: Locale): HolidayPackItem =
    HolidayPackItem(
        id = id,
        name = nameFor(locale.toLanguageTag()),
        region =
            region?.let { code ->
                // "und-US" is a valid tag with no language; forLanguageTag never throws, and an unknown
                // region simply has no display name, so the code itself is shown instead.
                Locale.forLanguageTag("und-$code").getDisplayCountry(locale).ifEmpty { code }
            },
    )
