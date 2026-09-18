package io.github.chrisjmendoza.yearal.core.scheduling

import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.github.chrisjmendoza.yearal.core.testing.RecordingDayRolloverListener
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

class DayRolloverNotifierTest {
    @Test
    fun `an empty listener set is valid and does nothing`() {
        runTest {
            DayRolloverNotifier(emptySet()).notify(DayRolloverTrigger.MIDNIGHT)
        }
    }

    @Test
    fun `every listener is called exactly once with the trigger`() {
        runTest {
            val widgets = RecordingDayRolloverListener()
            val reminders = RecordingDayRolloverListener()

            DayRolloverNotifier(setOf(widgets, reminders)).notify(DayRolloverTrigger.ZONE_CHANGED)

            widgets.triggers.shouldContainExactly(DayRolloverTrigger.ZONE_CHANGED)
            reminders.triggers.shouldContainExactly(DayRolloverTrigger.ZONE_CHANGED)
        }
    }

    @Test
    fun `a failing listener does not stop the others and its failure is rethrown afterwards`() {
        runTest {
            val failing = RecordingDayRolloverListener { throw IllegalStateException("first") }
            val healthy = RecordingDayRolloverListener()
            val alsoFailing = RecordingDayRolloverListener { throw IllegalArgumentException("second") }

            val thrown =
                shouldThrow<IllegalStateException> {
                    DayRolloverNotifier(linkedSetOf(failing, healthy, alsoFailing)).notify(DayRolloverTrigger.MIDNIGHT)
                }

            healthy.triggers.shouldContainExactly(DayRolloverTrigger.MIDNIGHT)
            alsoFailing.triggers.shouldContainExactly(DayRolloverTrigger.MIDNIGHT)
            thrown.message shouldBe "first"
            thrown.suppressed.map { it.message }.shouldContainExactly("second")
        }
    }

    @Test
    fun `a listener that never finishes does not keep the others from running`() {
        runTest {
            val hanging = RecordingDayRolloverListener { awaitCancellation() }
            val healthy = RecordingDayRolloverListener()

            val finished =
                withTimeoutOrNull(1_000) {
                    DayRolloverNotifier(linkedSetOf(hanging, healthy)).notify(DayRolloverTrigger.MIDNIGHT)
                }

            finished shouldBe null // cancelled by the timeout, as the broadcast handler does
            healthy.triggers.shouldContainExactly(DayRolloverTrigger.MIDNIGHT)
        }
    }
}
