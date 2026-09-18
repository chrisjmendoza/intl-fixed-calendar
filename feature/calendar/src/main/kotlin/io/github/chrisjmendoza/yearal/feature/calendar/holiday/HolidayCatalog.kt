package io.github.chrisjmendoza.yearal.feature.calendar.holiday

import android.content.Context
import android.content.res.Resources
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayEngine
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidayOccurrence
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySet
import io.github.chrisjmendoza.yearal.core.holidays.BundledHolidayPacks
import io.github.chrisjmendoza.yearal.core.holidays.HolidayPackLoader
import io.github.chrisjmendoza.yearal.feature.calendar.R
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bundled holiday packs as the calendar shows them: which of them the user enabled, and the
 * display label of every occurrence in a Gregorian date range (docs/ARCHITECTURE.md §3.3, §3.4 step
 * 2; FEATURES C5, H2, H3).
 *
 * Packs are loaded once, on first use, and kept for the life of the process; evaluation is the
 * memoised [HolidayEngine], so calling this per month render costs a few map lookups. Names are
 * resolved for [Locale.getDefault] at call time — the exact language tag when the pack has it, else
 * the bare language, else English — and an observed entry (a Saturday holiday moved to the Friday) is
 * labelled `<name> (observed)`.
 *
 * Dates in and out are Gregorian (CLAUDE.md rule 4); nothing here converts a date.
 */
@Singleton
class HolidayCatalog
    @Inject
    constructor(
        private val engine: HolidayEngine,
        private val loader: HolidayPackLoader,
        @ApplicationContext context: Context,
    ) {
        private val resources: Resources = context.resources

        // Loaded lazily so a ViewModel that never shows a holiday (every pack disabled) never parses one.
        private val bundled: List<HolidaySet> by lazy {
            BundledHolidayPacks.all.map(loader::loadBundled)
        }

        /**
         * Every holiday from the sets whose [HolidaySet.id] is in [enabledSetIds], on each date of
         * [range] (both ends inclusive), as display labels in [HolidayEngine] order: by date, then set
         * id, then holiday id. Dates without a holiday are absent. An unknown id in [enabledSetIds] is
         * ignored; an empty [range] yields an empty map.
         */
        fun labels(
            enabledSetIds: Set<String>,
            range: ClosedRange<LocalDate>,
        ): Map<LocalDate, List<String>> {
            val sets = bundled.filter { it.id in enabledSetIds }
            if (sets.isEmpty()) return emptyMap()
            val locale = Locale.getDefault()
            return engine.occurrences(sets, range).groupBy({ it.date }, { label(it, locale) })
        }

        /**
         * [labels] collapsed to one line per date for the month grid, the names joined by the
         * `holiday_list_separator` resource (`Year Day, New Year’s Eve`).
         */
        fun gridLabels(
            enabledSetIds: Set<String>,
            range: ClosedRange<LocalDate>,
        ): Map<LocalDate, String> {
            val separator = resources.getString(R.string.holiday_list_separator)
            return labels(enabledSetIds, range).mapValues { (_, names) -> names.joinToString(separator) }
        }

        private fun label(
            occurrence: HolidayOccurrence,
            locale: Locale,
        ): String {
            val names = occurrence.holiday.name
            val tag = locale.toLanguageTag()
            // HolidayDefinition.nameFor matches the tag exactly; stripping the region is the UI's job.
            val name = occurrence.holiday.nameFor(if (tag in names) tag else locale.language)
            return if (occurrence.observed) resources.getString(R.string.holiday_observed, name) else name
        }
    }
