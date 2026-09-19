package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySet
import io.github.chrisjmendoza.yearal.core.domain.holiday.HolidaySetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A hand-driven [HolidaySetProvider] fake: starts with [initial] and changes on [set], so a test can
 * simulate the user toggling a pack mid-test without touching settings or loading a real JSON pack.
 */
public class FakeHolidaySetProvider(
    initial: List<HolidaySet> = emptyList(),
) : HolidaySetProvider {
    private val sets = MutableStateFlow(initial)

    /** Replaces the enabled sets; collectors of [enabledSets] see the change. */
    public fun set(sets: List<HolidaySet>) {
        this.sets.value = sets
    }

    /** The sets last passed to the constructor or [set]. */
    override fun enabledSets(): Flow<List<HolidaySet>> = sets
}
