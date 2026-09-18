package io.github.chrisjmendoza.yearal.core.domain.settings

/**
 * How weekday headers are shown on IFC grids (`docs/ARCHITECTURE.md` §4 "Intercalary days in a
 * 7-column grid"; FEATURES W1). The IFC week always starts on Sunday, so headers never depend on the
 * device's first-day-of-week setting.
 *
 * **Trap:** the nominal IFC weekday is not the real weekday (calendar-spec §4.1). Anything tied to
 * real life — today highlight, events, reminders — uses the actual weekday whatever this setting says.
 */
public enum class WeekdayDisplay {
    /** Only the perpetual IFC weekday names (Sunday … Saturday, identical for every month). */
    NOMINAL,

    /** Only the real weekdays of that month's seven columns. */
    ACTUAL,

    /** Both: nominal names with the actual weekdays in a second header row. The default. */
    BOTH,
}

/** The app's colour theme (FEATURES W2). */
public enum class ThemeMode {
    /** Follow the system dark/light setting. */
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Every user preference, as one immutable value stored in DataStore (`docs/ARCHITECTURE.md` §1
 * "datastore"; FEATURES W1, W2, H5). Defaults are the values of a fresh install and are also what a
 * corrupt or missing store falls back to.
 *
 * @property weekdayDisplay the grid header mode; default [WeekdayDisplay.BOTH] (ROADMAP decision #5).
 * @property themeMode dark/light/system; default [ThemeMode.SYSTEM].
 * @property dynamicColor use Material You wallpaper colours on API 31+ instead of the brand palette.
 * @property enabledHolidaySets ids of the holiday packs shown on grids and agendas (the pack `id`
 * field, e.g. `"ifc"`, `"us"`), default the IFC observances and the US pack (ROADMAP decision #8).
 */
public data class UserSettings(
    val weekdayDisplay: WeekdayDisplay = WeekdayDisplay.BOTH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val enabledHolidaySets: Set<String> = setOf("ifc", "us"),
) {
    /** Well-known values. */
    public companion object {
        /** The settings of a fresh install. */
        public val DEFAULT: UserSettings = UserSettings()
    }
}
