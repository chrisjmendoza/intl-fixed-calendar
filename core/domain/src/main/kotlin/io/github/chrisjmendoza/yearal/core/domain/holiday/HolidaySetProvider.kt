package io.github.chrisjmendoza.yearal.core.domain.holiday

import kotlinx.coroutines.flow.Flow

/**
 * The holiday sets the user currently has enabled, ready for [HolidayEngine] — the one seam
 * `:core:domain` reads "which packs are on" through, so every agenda consumer
 * ([io.github.chrisjmendoza.yearal.core.domain.event.ObserveAgendaUseCase] included) resolves holidays
 * the same way (`docs/ARCHITECTURE.md` §3.3, §3.4).
 *
 * `:core:domain` cannot depend on `:core:holidays` — the dependency runs the other way
 * (`docs/ARCHITECTURE.md` §2 "Dependency direction") — so this interface only ever deals in
 * [HolidaySet] values. The production implementation (loading the bundled JSON packs and reading the
 * user's enabled-set ids from settings) is bound outside `:core:domain`, near its first consumer, the
 * same way [HolidayEngine] and `HolidayPackLoader` already are. Tests use
 * `io.github.chrisjmendoza.yearal.core.testing.FakeHolidaySetProvider`.
 */
public fun interface HolidaySetProvider {
    /**
     * The sets currently enabled, in no particular order — [HolidayEngine] sorts and de-duplicates its
     * own results. Cold: emits the current value on collection and again whenever the enabled ids or
     * the packs themselves change. An empty list is a normal result, not an error.
     */
    public fun enabledSets(): Flow<List<HolidaySet>>
}
