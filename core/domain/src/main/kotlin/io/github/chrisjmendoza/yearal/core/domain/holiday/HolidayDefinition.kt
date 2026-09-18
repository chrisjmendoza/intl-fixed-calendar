package io.github.chrisjmendoza.yearal.core.domain.holiday

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One holiday: a [rule] plus the modifiers that restrict or expand it.
 *
 * This is the in-memory model that the bundled JSON packs (`:core:holidays`) map onto; nothing here
 * is serialisable on its own. Names are user-visible text supplied by the pack, keyed by BCP-47
 * language tag with `"en"` as the mandatory fallback (`docs/holidays-and-import.md` §2.4).
 *
 * Spec: `docs/holidays-and-import.md` §2.2 (modifier table).
 *
 * @property id stable identifier, unique within its [HolidaySet], e.g. `us.new_year`. Never shown to
 *   users; used for hiding, adjustments and de-duplication.
 * @property name display name per language tag; must contain `"en"`. Use [nameFor] to resolve.
 * @property rule how the anchor date is found each year.
 * @property since first year (inclusive) the holiday exists, or `null` for no lower bound.
 * @property until last year (inclusive) the holiday exists, or `null` for no upper bound.
 * @property yearFilter restricts the holiday to years matching `year % mod == eq`, or `null` for every
 *   year (Inauguration Day is `YearFilter(4, 1)`).
 * @property observed the policy that adds an "(observed)" occurrence when the anchor falls on a
 *   weekend; [ObservedPolicy.NONE] by default.
 * @property durationDays number of consecutive days, at least 1 (Kwanzaa is 7, Hanukkah 8).
 * @property startsEveBefore `true` when the day begins at sundown the previous evening (Hebrew and
 *   Islamic holidays). A UI hint only: the occurrence dates are not shifted.
 * @property approximate `true` when the date may differ by a day or two from the official
 *   announcement (moon-sighting calendars). A UI hint only.
 * @property category what kind of holiday this is; drives filtering and styling.
 * @throws IllegalArgumentException if [id] is blank, [name] lacks `"en"`, [durationDays] is less
 *   than 1, or [since] is after [until].
 */
public data class HolidayDefinition(
    val id: String,
    val name: Map<String, String>,
    val rule: HolidayRule,
    val since: Int? = null,
    val until: Int? = null,
    val yearFilter: YearFilter? = null,
    val observed: ObservedPolicy = ObservedPolicy.NONE,
    val durationDays: Int = 1,
    val startsEveBefore: Boolean = false,
    val approximate: Boolean = false,
    val category: HolidayCategory,
) {
    init {
        require(id.isNotBlank()) { "Holiday id must not be blank" }
        require(FALLBACK_LANGUAGE in name) { "Holiday '$id' has no \"$FALLBACK_LANGUAGE\" name" }
        require(durationDays >= 1) { "Holiday '$id': durationDays must be at least 1: $durationDays" }
        if (since != null && until != null) {
            require(since <= until) { "Holiday '$id': since ($since) is after until ($until)" }
        }
    }

    /**
     * Returns the name for [languageTag], falling back to the `"en"` name.
     *
     * Matching is exact on the tag as stored; region-stripping (`"de-AT"` → `"de"`) is the caller's
     * job because locale negotiation is a UI concern.
     */
    public fun nameFor(languageTag: String): String = name[languageTag] ?: name.getValue(FALLBACK_LANGUAGE)

    /**
     * `true` if this holiday exists in [year] according to [since], [until] and [yearFilter]. Says
     * nothing about whether [rule] yields a date that year.
     */
    public fun existsIn(year: Int): Boolean =
        (since == null || year >= since) &&
            (until == null || year <= until) &&
            (yearFilter == null || yearFilter.matches(year))

    /** Constants for [HolidayDefinition]. */
    public companion object {
        /** The language tag every [name] map must contain. */
        public const val FALLBACK_LANGUAGE: String = "en"
    }
}

/**
 * Every-N-years filter: a year matches when `year % mod == eq` (floor modulus, so it is well defined
 * for every year in range).
 *
 * Spec: `docs/holidays-and-import.md` §2.2 (`yearFilter`).
 *
 * @property mod the cycle length, at least 1.
 * @property eq the required remainder, `0..mod-1`.
 * @throws IllegalArgumentException if [mod] is less than 1 or [eq] is outside `0..mod-1`.
 */
public data class YearFilter(
    val mod: Int,
    val eq: Int,
) {
    init {
        require(mod >= 1) { "YearFilter mod must be at least 1: $mod" }
        require(eq in 0 until mod) { "YearFilter eq must be in 0..${mod - 1}: $eq" }
    }

    /** `true` if [year] satisfies `year % mod == eq`. */
    public fun matches(year: Int): Boolean = Math.floorMod(year, mod) == eq
}

/**
 * Named weekend-shifting policies, coded once and referenced from data.
 *
 * A policy never moves the actual occurrence; it adds a second occurrence flagged
 * [HolidayOccurrence.observed] on the shifted date, and adds nothing when the anchor is not shifted.
 * Weekdays here are **Gregorian civil weekdays** of the anchor date (CLAUDE.md rule 3).
 *
 * Spec: `docs/holidays-and-import.md` §2.2 (observed-date policies). `uk_substitute` is deferred.
 */
public enum class ObservedPolicy {
    /** No shifting; the default. */
    NONE,

    /**
     * United States federal rule (5 U.S.C. § 6103(b)): Saturday → the preceding Friday, Sunday → the
     * following Monday. **Jan 1 on a Saturday is observed on Dec 31 of the previous year.**
     */
    US_FEDERAL,

    /** Saturday or Sunday → the following Monday (common Commonwealth rule). */
    NEXT_MONDAY,

    /** Sunday → the following Monday; Saturday is left alone (Inauguration Day). */
    SUNDAY_TO_MONDAY,
    ;

    /**
     * Returns the observed date for an anchor on [date], or `null` if this policy does not shift it.
     * [date] is a Gregorian date, so its `dayOfWeek` is the real weekday.
     */
    public fun observedDate(date: LocalDate): LocalDate? =
        when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> {
                when (this) {
                    NONE, SUNDAY_TO_MONDAY -> null
                    US_FEDERAL -> date.minusDays(1)
                    NEXT_MONDAY -> date.plusDays(2)
                }
            }

            DayOfWeek.SUNDAY -> {
                when (this) {
                    NONE -> null
                    US_FEDERAL, NEXT_MONDAY, SUNDAY_TO_MONDAY -> date.plusDays(1)
                }
            }

            else -> {
                null
            }
        }
}

/**
 * What kind of holiday a [HolidayDefinition] is. Drives filtering and styling only; the engine treats
 * every category alike.
 *
 * Spec: `docs/holidays-and-import.md` §2.2 (`category`).
 */
public enum class HolidayCategory {
    /** A statutory public holiday (US federal holidays). */
    PUBLIC,

    /** A bank holiday that is not a general public holiday. */
    BANK,

    /** A widely observed day that is not a day off (Halloween, Mother's Day). */
    OBSERVANCE,

    /** A religious holiday. */
    RELIGIOUS,

    /** An IFC-native day: Year Day, Leap Day, Sol 1. */
    IFC,
}
