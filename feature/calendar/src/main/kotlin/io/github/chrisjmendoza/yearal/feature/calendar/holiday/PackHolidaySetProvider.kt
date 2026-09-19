package io.github.chrisjmendoza.yearal.feature.calendar.holiday

import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySet
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySetProvider
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.core.holidays.BundledHolidayPacks
import io.github.chrisjmendoza.yearal.core.holidays.HolidayPackLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The production [HolidaySetProvider]: the bundled packs, filtered to
 * [io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings.enabledHolidaySets] and kept live
 * as settings change. This is the one place the app resolves "which holiday sets are on" from
 * (`docs/ARCHITECTURE.md` §3.3) — [io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase]
 * and [HolidayCatalog] both go through it, so the month grid, Day detail, Today and the event agenda
 * never disagree about which packs are enabled.
 *
 * `:core:domain` cannot see [HolidayPackLoader] (`:core:holidays` depends the other way), so this
 * implementation lives here, in `:feature:calendar` — the same place [HolidayEngine] is already bound
 * (`di/HolidayModule`) — and is injected into `:app`'s agenda wiring across the Hilt component like
 * [HolidayPackLoader] itself already is (that binding lives in `:feature:settings`).
 */
@Singleton
class PackHolidaySetProvider
    @Inject
    constructor(
        private val loader: HolidayPackLoader,
        private val settingsRepository: SettingsRepository,
    ) : HolidaySetProvider {
        // Loaded lazily so a process that never shows a holiday never parses a pack.
        private val bundled: List<HolidaySet> by lazy {
            BundledHolidayPacks.all.map(loader::loadBundled)
        }

        override fun enabledSets(): Flow<List<HolidaySet>> =
            settingsRepository.settings
                .map { settings -> bundled.filter { it.id in settings.enabledHolidaySets } }
                .distinctUntilChanged()
    }
