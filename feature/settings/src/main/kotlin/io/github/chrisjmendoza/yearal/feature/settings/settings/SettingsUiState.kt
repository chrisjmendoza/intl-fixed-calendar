package io.github.chrisjmendoza.yearal.feature.settings.settings

import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings

/**
 * One bundled holiday pack as the Settings screen lists it (FEATURES H5), already formatted for the
 * current locale so the screen renders text only.
 *
 * @property id the pack's `HolidaySet.id`, the value stored in [UserSettings.enabledHolidaySets]
 * (`"ifc"`, `"us"`, …). **Not** the bundled file name, which may differ in case.
 * @property name the display name for the device language, falling back to English.
 * @property region the localized country name for a regional pack (`"United States"`), or `null` for
 * region-independent sets such as the IFC observances and the Easter family.
 */
data class HolidayPackItem(
    val id: String,
    val name: String,
    val region: String?,
)

/**
 * Where the "Delete all data" action (FEATURES W6) stands: a two-step destructive confirmation, per
 * `docs/security-and-privacy.md` §2.4, so a single mis-tap can never erase everything.
 */
enum class DeleteAllDataStep {
    /** Nothing is happening; the action row is idle. */
    NONE,

    /** The first confirmation is open: what will be erased. */
    CONFIRM_FIRST,

    /** The second, final confirmation is open: this cannot be undone. */
    CONFIRM_SECOND,

    /** The erase finished; a completion notice is shown. */
    DONE,
}

/** What the Settings screen shows (docs/FEATURES.md W1, W2, H5). */
sealed interface SettingsUiState {
    /** Before the stored settings have been read; DataStore answers within milliseconds. */
    data object Loading : SettingsUiState

    /**
     * The current preferences plus the static catalogue they apply to.
     *
     * @property settings the stored value; every control reflects it and nothing else.
     * @property packs the bundled holiday packs in `BundledHolidayPacks.all` order; a pack is switched
     * on when its [HolidayPackItem.id] is in [UserSettings.enabledHolidaySets].
     * @property dynamicColorSupported whether the device can honour [UserSettings.dynamicColor]
     * (API 31+); when `false` the switch is shown disabled and the stored value is left untouched.
     * @property deleteAllDataStep which step of the "Delete all data" confirmation is open, if any
     * (FEATURES W6).
     */
    data class Loaded(
        val settings: UserSettings,
        val packs: List<HolidayPackItem>,
        val dynamicColorSupported: Boolean,
        val deleteAllDataStep: DeleteAllDataStep = DeleteAllDataStep.NONE,
    ) : SettingsUiState
}
