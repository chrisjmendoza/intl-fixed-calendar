package io.github.chrisjmendoza.yearal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.chrisjmendoza.yearal.core.domain.DateTicker
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * App-level state: the current local date, so the shell can open the Calendar tab on the current
 * month, and the user settings that shape the whole window (theme, dynamic colour). "Today" comes
 * only from [DateTicker] (CLAUDE.md rule 2), so it rolls over at midnight.
 */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        dateTicker: DateTicker,
        settingsRepository: SettingsRepository,
    ) : ViewModel() {
        /** Today's Gregorian date, or `null` until the first tick arrives (which is immediate). */
        val today: StateFlow<LocalDate?> =
            dateTicker.today.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** The current settings; starts at the defaults until the store has been read. */
        val settings: StateFlow<UserSettings> =
            settingsRepository.settings.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                UserSettings.DEFAULT,
            )
    }
