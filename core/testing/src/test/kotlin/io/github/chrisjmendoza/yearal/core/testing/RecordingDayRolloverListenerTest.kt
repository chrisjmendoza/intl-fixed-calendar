package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RecordingDayRolloverListenerTest {
    @Test
    fun `records every trigger in call order`() {
        runTest {
            val listener = RecordingDayRolloverListener()
            listener.triggers.shouldBeEmpty()

            listener.onDayRollover(DayRolloverTrigger.MIDNIGHT)
            listener.onDayRollover(DayRolloverTrigger.ZONE_CHANGED)
            listener.onDayRollover(DayRolloverTrigger.MIDNIGHT)

            listener.triggers.shouldContainExactly(
                DayRolloverTrigger.MIDNIGHT,
                DayRolloverTrigger.ZONE_CHANGED,
                DayRolloverTrigger.MIDNIGHT,
            )
        }
    }

    @Test
    fun `the onCall hook sees the trigger after it was recorded`() {
        runTest {
            var sizeSeenByHook = -1
            lateinit var listener: RecordingDayRolloverListener
            listener = RecordingDayRolloverListener { sizeSeenByHook = listener.triggers.size }

            listener.onDayRollover(DayRolloverTrigger.BOOT_COMPLETED)

            sizeSeenByHook shouldBe 1
        }
    }

    @Test
    fun `a throwing hook propagates and the call is still recorded`() {
        runTest {
            val listener = RecordingDayRolloverListener { error("listener failed") }

            shouldThrow<IllegalStateException> { listener.onDayRollover(DayRolloverTrigger.APP_UPDATED) }

            listener.triggers.shouldContainExactly(DayRolloverTrigger.APP_UPDATED)
        }
    }
}
