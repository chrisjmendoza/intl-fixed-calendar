package io.github.chrisjmendoza.yearal.core.navigation

import androidx.navigation3.runtime.NavKey

/**
 * What a feature may ask of navigation. Implemented by `:app`, which owns the per-tab back stacks
 * (docs/ARCHITECTURE.md §4 "Screens and navigation"); features only ever see this interface.
 */
interface Navigator {
    /** Pushes [key] onto the current tab's back stack. */
    fun navigate(key: NavKey)

    /** Pops the current tab's back stack, or does nothing at a tab root. */
    fun goBack()
}
