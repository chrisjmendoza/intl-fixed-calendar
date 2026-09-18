package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.settings.ThemeMode
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeSettingsRepositoryTest {
    @Test
    fun `starts at the defaults and emits every update`() =
        runTest {
            val repo = FakeSettingsRepository()
            repo.settings.first() shouldBe UserSettings.DEFAULT
            UserSettings.DEFAULT.weekdayDisplay shouldBe WeekdayDisplay.BOTH
            UserSettings.DEFAULT.enabledHolidaySets shouldContainExactly setOf("ifc", "us")

            // The collector must be subscribed before each update, or the StateFlow conflates them.
            val collected = mutableListOf<UserSettings>()
            val job = launch { repo.settings.take(3).toList(collected) }
            runCurrent()
            repo.update { it.copy(themeMode = ThemeMode.DARK) }
            runCurrent()
            repo.update { it.copy(weekdayDisplay = WeekdayDisplay.ACTUAL) }
            job.join()
            collected.map { it.themeMode to it.weekdayDisplay } shouldContainExactly
                listOf(
                    ThemeMode.SYSTEM to WeekdayDisplay.BOTH,
                    ThemeMode.DARK to WeekdayDisplay.BOTH,
                    ThemeMode.DARK to WeekdayDisplay.ACTUAL,
                )
        }

    @Test
    fun `concurrent read-modify-write updates are all applied`() =
        runTest {
            val repo = FakeSettingsRepository()
            (1..50)
                .map { n ->
                    async { repo.update { it.copy(enabledHolidaySets = it.enabledHolidaySets + "set$n") } }
                }.awaitAll()
            repo.current.enabledHolidaySets.size shouldBe 52
        }
}
