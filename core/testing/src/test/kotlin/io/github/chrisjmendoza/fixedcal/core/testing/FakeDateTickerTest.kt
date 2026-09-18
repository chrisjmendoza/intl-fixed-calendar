package io.github.chrisjmendoza.fixedcal.core.testing

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FakeDateTickerTest {
    @Test
    fun `emits the initial date, then each date passed to set`() =
        runTest {
            val ticker = FakeDateTicker(LocalDate.of(2026, 9, 17))
            val emissions = mutableListOf<LocalDate>()
            val collector = launch { ticker.today.toList(emissions) }
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2026, 9, 17))

            ticker.set(LocalDate.of(2026, 9, 18))
            runCurrent()
            emissions shouldBe listOf(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 18))

            ticker.set(LocalDate.of(2026, 12, 31))
            runCurrent()
            emissions shouldBe
                listOf(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 18), LocalDate.of(2026, 12, 31))

            collector.cancel()
        }
}
