package io.github.chrisjmendoza.yearal.core.scheduling.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.scheduling.DayRolloverNotifier
import io.github.chrisjmendoza.yearal.core.scheduling.DayRolloverScheduler
import io.github.chrisjmendoza.yearal.core.scheduling.FakeSchedulingApplication
import io.github.chrisjmendoza.yearal.core.scheduling.FakeSchedulingComponent
import io.github.chrisjmendoza.yearal.core.scheduling.RolloverBroadcastHandler
import io.github.chrisjmendoza.yearal.core.scheduling.SystemEventReceiver
import io.github.chrisjmendoza.yearal.core.testing.FakeReminderScheduler
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.github.chrisjmendoza.yearal.core.testing.RecordingDayRolloverListener
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.ZoneId

/**
 * The reminder half of the broadcast wiring, end to end through the receivers Robolectric registers
 * from this module's manifest and the real Hilt entry-point lookup: the reminder alarm recomputes the
 * schedule, and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` re-arms **both** alarms under
 * the new capability (ROADMAP M6 T1 and T3; `docs/security-and-privacy.md` §5.1, §6.3).
 *
 * The scheduler itself is a counting fake here; its behaviour is [AlarmReminderSchedulerTest]'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = FakeSchedulingApplication::class)
class ReminderReceiversTest {
    // No JUnit Timeout rule here, unlike AlarmReminderSchedulerTest: that rule runs the test body on
    // a second thread, and Robolectric's paused main looper may only be driven from the test thread
    // ("Main looper can only be controlled from its thread in PAUSED mode"). Nothing here can block —
    // the listener scope is an UnconfinedTestDispatcher and the schedulers are fakes.
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms: ShadowAlarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))

    // 23:59:30 EDT on 18 Sep 2026; the next New York midnight is 2026-09-19T04:00:01Z with the margin.
    private val clock = MutableClock(Instant.parse("2026-09-19T03:59:30Z"))
    private val zones = FakeZoneProvider(ZoneId.of("America/New_York"))
    private val rolloverScheduler = DayRolloverScheduler(context, clock, zones)
    private val reminders = FakeReminderScheduler()
    private val listener = RecordingDayRolloverListener()

    @Before
    fun installGraph() {
        val scope = CoroutineScope(UnconfinedTestDispatcher(TestCoroutineScheduler()))
        (context as FakeSchedulingApplication).component =
            FakeSchedulingComponent(
                handler =
                    RolloverBroadcastHandler(
                        scheduler = rolloverScheduler,
                        notifier = DayRolloverNotifier(setOf(listener)),
                        scope = scope,
                    ),
                reminderHandler = ReminderBroadcastHandler(reminders, rolloverScheduler, scope),
            )
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    private fun sendBroadcast(action: String) {
        context.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun fireReminderAlarm() {
        context.sendBroadcast(
            Intent(context, ReminderAlarmReceiver::class.java).setAction(AlarmReminderScheduler.ACTION_REMINDER),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `the reminder alarm makes the scheduler recompute`() {
        fireReminderAlarm()

        reminders.rescheduleCount shouldBe 1
    }

    @Test
    fun `the reminder alarm does not touch the rollover alarm`() {
        fireReminderAlarm()

        // The rollover chain has its own receiver; this broadcast must not arm anything itself.
        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `the reminder receiver ignores every action but its own`() {
        val receiver = ReminderAlarmReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        receiver.onReceive(context, Intent(DayRolloverScheduler.ACTION_DAY_ROLLOVER))
        receiver.onReceive(context, Intent(context, ReminderAlarmReceiver::class.java))
        receiver.onReceive(context, Intent())

        reminders.rescheduleCount shouldBe 0
    }

    @Test
    fun `an exact-alarm permission change re-arms the rollover alarm and recomputes the reminders`() {
        sendBroadcast(SystemEventReceiver.ACTION_EXACT_ALARM_PERMISSION_CHANGED)

        reminders.rescheduleCount shouldBe 1
        alarms.scheduledAlarms.shouldHaveSize(1)
        Instant.ofEpochMilli(alarms.scheduledAlarms.single().triggerAtMs) shouldBe
            Instant.parse("2026-09-19T04:00:01Z")
    }

    @Test
    fun `an exact-alarm permission change notifies no day-rollover listener`() {
        // It is not a date change: nothing that shows "today" needs to hear about it, and a widget
        // re-render would be wasted work.
        sendBroadcast(SystemEventReceiver.ACTION_EXACT_ALARM_PERMISSION_CHANGED)

        listener.triggers.shouldBeEmpty()
        reminders.rescheduleCount shouldBe 1
    }

    @Test
    fun `a date-related system broadcast still goes down the rollover path only`() {
        sendBroadcast(Intent.ACTION_TIMEZONE_CHANGED)

        listener.triggers.shouldHaveSize(1)
        // The reminder scheduler hears about it through the listener set in production, not here:
        // this test's notifier holds only the recording listener.
        reminders.rescheduleCount shouldBe 0
    }

    @Test
    fun `posting is never attempted by the receiver itself`() {
        fireReminderAlarm()

        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.shouldBeEmpty()
    }
}
