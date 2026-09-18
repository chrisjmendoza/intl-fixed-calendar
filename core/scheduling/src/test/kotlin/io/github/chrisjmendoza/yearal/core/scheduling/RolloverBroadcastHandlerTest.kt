package io.github.chrisjmendoza.yearal.core.scheduling

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverListener
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.github.chrisjmendoza.yearal.core.testing.RecordingDayRolloverListener
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

// The contract the receivers rely on: re-arm first and synchronously, notify off-thread, and always
// release the broadcast (goAsync's PendingResult.finish) exactly once, inside the listener budget.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RolloverBroadcastHandlerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms: ShadowAlarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val scheduler =
        DayRolloverScheduler(
            context,
            MutableClock(Instant.parse("2026-09-18T15:00:00Z")),
            FakeZoneProvider(ZoneOffset.UTC),
        )

    private val testScheduler = TestCoroutineScheduler()
    private val uncaught = mutableListOf<Throwable>()
    private val scope =
        CoroutineScope(
            StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, failure -> uncaught += failure },
        )
    private var finishedCalls = 0

    private fun handler(vararg listeners: DayRolloverListener) =
        RolloverBroadcastHandler(scheduler, DayRolloverNotifier(linkedSetOf(*listeners)), scope)

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    @Test
    fun `the alarm is re-armed before any listener runs, and listeners run after handle has returned`() {
        val listener = RecordingDayRolloverListener()

        handler(listener).handle(DayRolloverTrigger.BOOT_COMPLETED) { finishedCalls++ }

        // handle() has returned: the alarm is already set, the listener has not started yet.
        alarms.scheduledAlarms.shouldHaveSize(1)
        listener.triggers.shouldBeEmpty()
        finishedCalls shouldBe 0

        testScheduler.advanceUntilIdle()

        listener.triggers.shouldContainExactly(DayRolloverTrigger.BOOT_COMPLETED)
        finishedCalls shouldBe 1
        uncaught.shouldBeEmpty()
    }

    @Test
    fun `with no listeners the broadcast is still released`() {
        handler().handle(DayRolloverTrigger.MIDNIGHT) { finishedCalls++ }
        testScheduler.advanceUntilIdle()

        alarms.scheduledAlarms.shouldHaveSize(1)
        finishedCalls shouldBe 1
    }

    @Test
    fun `a failing listener leaves the alarm armed, releases the broadcast once, and its failure surfaces`() {
        val failing = RecordingDayRolloverListener { error("widget update failed") }

        handler(failing).handle(DayRolloverTrigger.MIDNIGHT) { finishedCalls++ }
        testScheduler.advanceUntilIdle()

        alarms.scheduledAlarms.shouldHaveSize(1)
        finishedCalls shouldBe 1
        uncaught.shouldHaveSize(1).single().shouldBeInstanceOf<IllegalStateException>()
    }

    @Test
    fun `a listener that never returns is cancelled at the budget and the broadcast is released`() {
        var cancelled = false
        val hanging =
            RecordingDayRolloverListener {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }

        handler(hanging).handle(DayRolloverTrigger.MIDNIGHT) { finishedCalls++ }

        testScheduler.advanceTimeBy(RolloverBroadcastHandler.LISTENER_BUDGET.minus(Duration.ofMillis(1)).toMillis())
        testScheduler.runCurrent()
        finishedCalls shouldBe 0

        testScheduler.advanceTimeBy(2)
        testScheduler.runCurrent()
        cancelled shouldBe true
        finishedCalls shouldBe 1
        uncaught.shouldBeEmpty()
    }
}
