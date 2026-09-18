package io.github.chrisjmendoza.yearal.feature.settings.settings

import app.cash.turbine.test
import io.github.chrisjmendoza.yearal.core.domain.settings.ThemeMode
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.github.chrisjmendoza.yearal.core.holidays.HolidayPackLoader
import io.github.chrisjmendoza.yearal.core.testing.FakeSettingsRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * [SettingsViewModel] against [FakeSettingsRepository] and the real [HolidayPackLoader]: the state is
 * whatever the store holds plus the bundled pack catalogue, and every intent goes through the store
 * and comes back as a new state (docs/ARCHITECTURE.md §4 "State management"; FEATURES W1, W2, H5).
 *
 * A [StandardTestDispatcher] is used on purpose: with an unconfined one the first value is mapped
 * synchronously on subscription and `stateIn` conflates the initial [SettingsUiState.Loading] away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val loader = HolidayPackLoader()
    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Pack names and region names are resolved for the default locale; pin it so the expected
        // strings below are the same on every machine.
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repository: FakeSettingsRepository,
        dynamicColorSupported: Boolean = true,
    ) = SettingsViewModel(repository, loader, dynamicColorSupported)

    @Test
    fun `starts loading, then mirrors the stored settings and lists the bundled packs`() =
        runTest(dispatcher) {
            val stored =
                UserSettings(
                    weekdayDisplay = WeekdayDisplay.NOMINAL,
                    themeMode = ThemeMode.DARK,
                    dynamicColor = false,
                    enabledHolidaySets = setOf("us"),
                )
            val viewModel = viewModel(FakeSettingsRepository(stored))
            viewModel.uiState.value shouldBe SettingsUiState.Loading
            viewModel.uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>()
                loaded.settings shouldBe stored
                loaded.dynamicColorSupported shouldBe true
                loaded.packs shouldContainExactly
                    listOf(
                        HolidayPackItem(id = "ifc", name = "International Fixed Calendar", region = null),
                        HolidayPackItem(id = "us", name = "United States", region = "United States"),
                        HolidayPackItem(id = "religious-christian", name = "Christian (Easter family)", region = null),
                    )
            }
        }

    @Test
    fun `a fresh install shows the defaults - BOTH, system theme, dynamic colour, IFC and US packs`() =
        runTest(dispatcher) {
            viewModel(FakeSettingsRepository()).uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                val loaded = awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>()
                loaded.settings.weekdayDisplay shouldBe WeekdayDisplay.BOTH
                loaded.settings.themeMode shouldBe ThemeMode.SYSTEM
                loaded.settings.dynamicColor shouldBe true
                loaded.settings.enabledHolidaySets shouldBe setOf("ifc", "us")
            }
        }

    @Test
    fun `dynamicColorSupported reflects the injected flag`() =
        runTest(dispatcher) {
            viewModel(FakeSettingsRepository(), dynamicColorSupported = false).uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().dynamicColorSupported shouldBe false
            }
        }

    @Test
    fun `setWeekdayDisplay updates the repository and the state`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.weekdayDisplay shouldBe
                    WeekdayDisplay.BOTH

                viewModel.setWeekdayDisplay(WeekdayDisplay.ACTUAL)

                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.weekdayDisplay shouldBe
                    WeekdayDisplay.ACTUAL
                repository.current.weekdayDisplay shouldBe WeekdayDisplay.ACTUAL
                // Everything else is untouched by a read-modify-write.
                repository.current shouldBe UserSettings.DEFAULT.copy(weekdayDisplay = WeekdayDisplay.ACTUAL)
            }
        }

    @Test
    fun `setThemeMode updates the repository and the state`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.themeMode shouldBe ThemeMode.SYSTEM

                viewModel.setThemeMode(ThemeMode.DARK)

                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.themeMode shouldBe ThemeMode.DARK
                repository.current shouldBe UserSettings.DEFAULT.copy(themeMode = ThemeMode.DARK)
            }
        }

    @Test
    fun `setDynamicColor updates the repository and the state`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.dynamicColor shouldBe true

                viewModel.setDynamicColor(false)

                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.dynamicColor shouldBe false
                repository.current shouldBe UserSettings.DEFAULT.copy(dynamicColor = false)
            }
        }

    @Test
    fun `toggling a holiday set off then on round-trips`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.enabledHolidaySets shouldBe
                    setOf("ifc", "us")

                viewModel.setHolidaySetEnabled("us", false)
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.enabledHolidaySets shouldBe
                    setOf("ifc")
                repository.current.enabledHolidaySets shouldBe setOf("ifc")

                viewModel.setHolidaySetEnabled("us", true)
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.enabledHolidaySets shouldBe
                    setOf("ifc", "us")
                repository.current shouldBe UserSettings.DEFAULT
            }
        }

    @Test
    fun `the IFC set can be switched off like any other and a new set switched on`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                awaitItem()

                viewModel.setHolidaySetEnabled("ifc", false)
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.enabledHolidaySets shouldBe
                    setOf("us")

                viewModel.setHolidaySetEnabled("religious-christian", true)
                awaitItem().shouldBeInstanceOf<SettingsUiState.Loaded>().settings.enabledHolidaySets shouldBe
                    setOf("us", "religious-christian")
            }
        }

    @Test
    fun `enabling an already enabled set or disabling an absent one stores the same value`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val viewModel = viewModel(repository)
            viewModel.uiState.test {
                awaitItem() shouldBe SettingsUiState.Loading
                awaitItem()

                viewModel.setHolidaySetEnabled("ifc", true)
                viewModel.setHolidaySetEnabled("religious-christian", false)
                dispatcher.scheduler.advanceUntilIdle()

                repository.current shouldBe UserSettings.DEFAULT
                // StateFlow conflates equal values, so no new state was emitted.
                expectNoEvents()
            }
        }

    @Test
    fun `pack items resolve the name for the locale and the region as a country name`() {
        val us = loader.loadBundled("US")
        us.toItem(Locale.US) shouldBe HolidayPackItem(id = "us", name = "United States", region = "United States")
        us.toItem(Locale.GERMANY) shouldBe
            HolidayPackItem(id = "us", name = "United States", region = "Vereinigte Staaten")

        val ifc = loader.loadBundled("ifc")
        ifc.toItem(Locale.FRANCE) shouldBe
            HolidayPackItem(id = "ifc", name = "International Fixed Calendar", region = null)
    }
}
