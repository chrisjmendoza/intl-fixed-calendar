package io.github.chrisjmendoza.yearal.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.chrisjmendoza.yearal.core.navigation.ConverterKey
import io.github.chrisjmendoza.yearal.core.navigation.DayKey
import io.github.chrisjmendoza.yearal.core.navigation.EventListKey
import io.github.chrisjmendoza.yearal.core.navigation.MonthKey
import io.github.chrisjmendoza.yearal.core.navigation.MoreKey
import io.github.chrisjmendoza.yearal.core.navigation.TodayKey
import io.github.chrisjmendoza.yearal.ui.TopLevelDestination
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

// The per-tab back stack rules of docs/ARCHITECTURE.md §4 "Screens and navigation", checked without a
// composition: NavBackStack and mutableStateOf work on the plain JVM.
class TabBackStacksTest {
    private fun tabs(): TabBackStacks {
        val stacks =
            TopLevelDestination.entries.associateWith { destination ->
                val root = destination.rootKey
                if (root == null) NavBackStack<NavKey>() else NavBackStack<NavKey>(root)
            }
        return TabBackStacks(mutableStateOf(TopLevelDestination.TODAY), stacks)
    }

    @Test
    fun `starts on Today with only its root`() {
        val tabs = tabs()
        tabs.selected shouldBe TopLevelDestination.TODAY
        tabs.backStack shouldContainExactly listOf(TodayKey)
    }

    @Test
    fun `another tab is shown above the Today root and back returns to Today`() {
        val tabs = tabs()
        tabs.select(TopLevelDestination.EVENTS, EventListKey)
        tabs.backStack shouldContainExactly listOf(TodayKey, EventListKey)
        tabs.goBack()
        tabs.selected shouldBe TopLevelDestination.TODAY
        tabs.backStack shouldContainExactly listOf(TodayKey)
    }

    @Test
    fun `each tab keeps its own stack while another tab is shown`() {
        val tabs = tabs()
        val month = MonthKey(2026, 10)
        tabs.select(TopLevelDestination.CALENDAR, month)
        tabs.navigate(DayKey(20_713))
        tabs.select(TopLevelDestination.CONVERT, ConverterKey())
        tabs.backStack shouldContainExactly listOf(TodayKey, ConverterKey())
        tabs.select(TopLevelDestination.CALENDAR, MonthKey(2027, 1))
        // The root given on a later selection is ignored: the tab already has a stack.
        tabs.backStack shouldContainExactly listOf(TodayKey, month, DayKey(20_713))
    }

    @Test
    fun `back pops within a tab before leaving it`() {
        val tabs = tabs()
        tabs.select(TopLevelDestination.CALENDAR, MonthKey(2026, 10))
        tabs.navigate(DayKey(20_713))
        tabs.goBack()
        tabs.selected shouldBe TopLevelDestination.CALENDAR
        tabs.backStack shouldContainExactly listOf(TodayKey, MonthKey(2026, 10))
        tabs.goBack()
        tabs.selected shouldBe TopLevelDestination.TODAY
    }

    @Test
    fun `re-selecting the current tab pops it to its root`() {
        val tabs = tabs()
        tabs.select(TopLevelDestination.MORE, MoreKey)
        tabs.navigate(DayKey(1))
        tabs.navigate(DayKey(2))
        tabs.select(TopLevelDestination.MORE, MoreKey)
        tabs.backStack shouldContainExactly listOf(TodayKey, MoreKey)
    }

    @Test
    fun `back at the Today root does nothing`() {
        val tabs = tabs()
        tabs.goBack()
        tabs.selected shouldBe TopLevelDestination.TODAY
        tabs.backStack shouldContainExactly listOf(TodayKey)
    }
}
