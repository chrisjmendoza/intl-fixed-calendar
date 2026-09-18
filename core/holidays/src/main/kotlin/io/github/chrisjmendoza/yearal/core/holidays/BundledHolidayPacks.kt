package io.github.chrisjmendoza.yearal.core.holidays

/**
 * The names of the holiday packs bundled in this module, for [HolidayPackLoader.loadBundled]. Each
 * name is a file `src/main/resources/holidays/<name>.json`; the pack's own `id` (the
 * `HolidaySet.id` users enable) is inside the file and may differ in case (`"US"` → `us`).
 *
 * Spec: `docs/holidays-and-import.md` §2.4 (file split), §2.5 (contents); FEATURES H2, H3.
 */
public object BundledHolidayPacks {
    /**
     * The IFC-native set (FEATURES H3): Year Day, Leap Day and Sol 1, category `IFC`. Leap Day is
     * absent in common years (`docs/holidays-and-import.md` §5.3, OMIT).
     */
    public const val IFC: String = "ifc"

    /**
     * United States civil holidays (FEATURES H2): the federal holidays of 5 U.S.C. § 6103 as `PUBLIC`
     * with the `us_federal` observed rule, plus Inauguration Day and the common observances of
     * `docs/holidays-and-import.md` §2.5 as `OBSERVANCE`. Tax Day is deferred (§2.5).
     */
    public const val US: String = "US"

    /**
     * The Easter family, Western and Orthodox (`docs/holidays-and-import.md` §2.5 "religious /
     * cultural sets"): Mardi Gras, Ash Wednesday, Palm Sunday, Good Friday, Easter, Orthodox Easter.
     * Region-independent; category `RELIGIOUS`. The Hebrew, Islamic, Chinese and Hindu entries of that
     * table need the `calendar` type or pre-generated tables and arrive with FEATURES H4.
     */
    public const val RELIGIOUS_CHRISTIAN: String = "religious-christian"

    /** Every bundled pack name, in the order the app should list them. */
    public val all: List<String> = listOf(IFC, US, RELIGIOUS_CHRISTIAN)
}
