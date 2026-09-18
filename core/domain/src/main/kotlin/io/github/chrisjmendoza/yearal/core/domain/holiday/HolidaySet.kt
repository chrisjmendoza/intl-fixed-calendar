package io.github.chrisjmendoza.yearal.core.domain.holiday

/**
 * A named, independently toggleable collection of holidays: a country pack, a religious set, or the
 * IFC set. Users enable sets, not individual rules (`docs/holidays-and-import.md` §2.4).
 *
 * Sets are values: two sets with the same fields are equal. [HolidayEngine] memoises per [id], so an
 * id must denote one pack; a changed pack with the same id is detected and re-evaluated, but two
 * different packs sharing an id at the same time would thrash the cache.
 *
 * Spec: `docs/holidays-and-import.md` §2.4; `docs/ARCHITECTURE.md` §3.3.
 *
 * @property id stable identifier, e.g. `us` or `ifc`. Persisted as the user's "enabled sets" choice.
 * @property region ISO 3166-1 alpha-2 country code when the set is regional, or `null` for
 *   region-independent sets (religious, IFC).
 * @property name display name per BCP-47 language tag; must contain `"en"`.
 * @property sources where the rules came from, for the licences and data-sources screen.
 * @property holidays the holidays, in pack order. Ids must be unique within the set.
 * @throws IllegalArgumentException if [id] is blank, [name] lacks `"en"`, or two holidays share an id.
 */
public data class HolidaySet(
    val id: String,
    val region: String?,
    val name: Map<String, String>,
    val sources: List<String>,
    val holidays: List<HolidayDefinition>,
) {
    init {
        require(id.isNotBlank()) { "Holiday set id must not be blank" }
        require(HolidayDefinition.FALLBACK_LANGUAGE in name) {
            "Holiday set '$id' has no \"${HolidayDefinition.FALLBACK_LANGUAGE}\" name"
        }
        val seen = HashSet<String>()
        for (holiday in holidays) {
            require(seen.add(holiday.id)) { "Holiday set '$id' has a duplicate holiday id: ${holiday.id}" }
        }
    }

    /**
     * Returns the name for [languageTag], falling back to the `"en"` name. Exact tag match, as in
     * [HolidayDefinition.nameFor].
     */
    public fun nameFor(languageTag: String): String =
        name[languageTag] ?: name.getValue(HolidayDefinition.FALLBACK_LANGUAGE)
}
