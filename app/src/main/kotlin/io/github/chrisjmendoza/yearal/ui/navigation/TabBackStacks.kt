package io.github.chrisjmendoza.yearal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import io.github.chrisjmendoza.yearal.core.navigation.Navigator
import io.github.chrisjmendoza.yearal.ui.TopLevelDestination

/**
 * One back stack per top-level tab, plus the rule that the system back button leaves any other tab
 * for Today before it exits the app — the Navigation 3 "top-level back stack" recipe
 * (docs/ARCHITECTURE.md §4 "Screens and navigation"). The selected tab and every stack survive
 * configuration changes and process death.
 */
@Stable
class TabBackStacks internal constructor(
    selectedState: MutableState<TopLevelDestination>,
    private val stacks: Map<TopLevelDestination, NavBackStack<NavKey>>,
) : Navigator {
    /** The tab currently shown. */
    var selected: TopLevelDestination by selectedState
        private set

    /**
     * What [androidx.navigation3.ui.NavDisplay] renders: the selected tab's stack, prefixed by the
     * Today root when another tab is selected so that popping past a tab root lands on Today.
     */
    val backStack: List<NavKey>
        get() {
            val current = stacks.getValue(selected)
            return if (selected == TopLevelDestination.TODAY) {
                current.toList()
            } else {
                stacks.getValue(TopLevelDestination.TODAY).take(1) + current
            }
        }

    /**
     * Shows [destination], seeding its stack with [root] the first time it is opened. Re-selecting the
     * current tab pops it to its root.
     */
    fun select(
        destination: TopLevelDestination,
        root: NavKey,
    ) {
        val stack = stacks.getValue(destination)
        if (stack.isEmpty()) {
            stack.add(root)
        } else if (destination == selected) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
        selected = destination
    }

    override fun navigate(key: NavKey) {
        stacks.getValue(selected).add(key)
    }

    override fun goBack() {
        val stack = stacks.getValue(selected)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            selected != TopLevelDestination.TODAY -> selected = TopLevelDestination.TODAY
        }
    }
}

/** Creates the per-tab back stacks, remembered and saved with the composition. */
@Composable
fun rememberTabBackStacks(): TabBackStacks {
    val selected = rememberSaveable { mutableStateOf(TopLevelDestination.TODAY) }
    val stacks =
        TopLevelDestination.entries.associateWith { destination ->
            val root = destination.rootKey
            if (root == null) rememberNavBackStack() else rememberNavBackStack(root)
        }
    return remember(selected, stacks) { TabBackStacks(selected, stacks) }
}
