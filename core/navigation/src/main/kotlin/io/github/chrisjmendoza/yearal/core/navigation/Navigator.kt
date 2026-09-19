package io.github.chrisjmendoza.yearal.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * What a feature may ask of navigation. Implemented by `:app`, which owns the per-tab back stacks
 * (docs/ARCHITECTURE.md §4 "Screens and navigation"); features only ever see this interface.
 */
interface Navigator {
    /** Pushes [key] onto the current tab's back stack. */
    fun navigate(key: NavKey)

    /**
     * Pops the current tab's back stack by one entry.
     *
     * **At a tab's own root this does not simply no-op**: it switches to the Today tab, unless Today
     * is already the selected tab, in which case it does nothing and the system handles back (e.g.
     * exiting the app) — the per-tab back stack rule in `docs/ARCHITECTURE.md` §4 "Screens and
     * navigation". A caller at a tab root should expect the current tab to change.
     */
    fun goBack()
}
