package io.github.chrisjmendoza.yearal.core.scheduling

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.github.chrisjmendoza.yearal.core.testing.RecordingDayRolloverListener
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
import java.time.LocalDate
import java.time.ZoneId

// docs/ARCHITECTURE.md §5 "Midnight rollover (layered)" layers 1 and 2, end to end: the receivers are
// the ones Robolectric registers from this module's manifest, reached by real broadcasts, and they find
// their collaborators through the real Hilt entry-point lookup (see FakeSchedulingApplication).
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = FakeSchedulingApplication::class)
class RolloverReceiversTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms: ShadowAlarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))

    // 23:59:30 EDT on 18 Sep 2026; New York midnight is 04:00:00Z.
    private val clock = MutableClock(Instant.parse("2026-09-19T03:59:30Z"))
    private val zones = FakeZoneProvider(ZoneId.of("America/New_York"))
    private val scheduler = DayRolloverScheduler(context, clock, zones)

    // What a real listener must do: recompute "today" from the Clock and the ZoneProvider when called.
    private val datesSeen = mutableListOf<LocalDate>()
    private val widgets =
        RecordingDayRolloverListener { datesSeen += clock.instant().atZone(zones.currentZone()).toLocalDate() }
    private val reminders = RecordingDayRolloverListener()

    @Before
    fun installGraph() {
        val handler =
            RolloverBroadcastHandler(
                scheduler = scheduler,
                notifier = DayRolloverNotifier(setOf(widgets, reminders)),
                scope = CoroutineScope(UnconfinedTestDispatcher(TestCoroutineScheduler())),
            )
        (context as FakeSchedulingApplication).component = FakeSchedulingComponent(handler)
    }

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    private fun onlyAlarmTime(): Instant =
        Instant.ofEpochMilli(
            alarms.scheduledAlarms
                .shouldHaveSize(1)
                .single()
                .triggerAtMs,
        )

    private fun fireTheAlarm() {
        alarms.fireAlarm(alarms.scheduledAlarms.shouldHaveSize(1).single())
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sendSystemBroadcast(action: String) {
        context.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `crossing midnight - each listener is notified once, sees the new date, and the next midnight is armed`() {
        scheduler.arm()
        onlyAlarmTime() shouldBe Instant.parse("2026-09-19T04:00:01Z")

        clock.set(Instant.parse("2026-09-19T04:00:01Z"))
        fireTheAlarm()

        widgets.triggers.shouldContainExactly(DayRolloverTrigger.MIDNIGHT)
        reminders.triggers.shouldContainExactly(DayRolloverTrigger.MIDNIGHT)
        datesSeen.shouldContainExactly(LocalDate.of(2026, 9, 19))
        onlyAlarmTime() shouldBe Instant.parse("2026-09-20T04:00:01Z")
    }

    @Test
    fun `an alarm delivered at the end of its window still yields the right date and the right next alarm`() {
        scheduler.arm()

        clock.set(Instant.parse("2026-09-19T04:10:00Z"))
        fireTheAlarm()

        datesSeen.shouldContainExactly(LocalDate.of(2026, 9, 19))
        onlyAlarmTime() shouldBe Instant.parse("2026-09-20T04:00:01Z")
    }

    @Test
    fun `an alarm delivered while the clock is still before midnight re-arms for that same midnight`() {
        scheduler.arm()

        // The wall clock was set back before delivery: nothing may assume the date advanced.
        clock.set(Instant.parse("2026-09-19T03:00:00Z"))
        fireTheAlarm()

        datesSeen.shouldContainExactly(LocalDate.of(2026, 9, 18))
        onlyAlarmTime() shouldBe Instant.parse("2026-09-19T04:00:01Z")
    }

    @Test
    fun `days keep rolling - each firing arms the following midnight, across Year Day into the new year`() {
        clock.set(Instant.parse("2026-12-31T04:59:30Z")) // 23:59:30 EST on 30 December
        scheduler.arm()

        clock.set(Instant.parse("2026-12-31T05:00:01Z"))
        fireTheAlarm()
        clock.set(Instant.parse("2027-01-01T05:00:01Z"))
        fireTheAlarm()

        datesSeen.shouldContainExactly(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1))
        widgets.triggers.shouldContainExactly(DayRolloverTrigger.MIDNIGHT, DayRolloverTrigger.MIDNIGHT)
        onlyAlarmTime() shouldBe Instant.parse("2027-01-02T05:00:01Z")
    }

    @Test
    fun `each system event notifies the listeners with its trigger and re-arms`() {
        val expected =
            listOf(
                Intent.ACTION_TIME_CHANGED to DayRolloverTrigger.TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED to DayRolloverTrigger.ZONE_CHANGED,
                Intent.ACTION_LOCALE_CHANGED to DayRolloverTrigger.LOCALE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED to DayRolloverTrigger.BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED to DayRolloverTrigger.APP_UPDATED,
            )
        expected.forEachIndexed { index, (action, trigger) ->
            // As after a reboot or an update: no alarm is left, so the one found below is a new one.
            context.getSystemService(AlarmManager::class.java).cancel(DayRolloverScheduler.rolloverOperation(context))
            alarms.scheduledAlarms.shouldBeEmpty()

            sendSystemBroadcast(action)

            widgets.triggers.shouldHaveSize(index + 1).last() shouldBe trigger
            reminders.triggers.shouldHaveSize(index + 1).last() shouldBe trigger
            onlyAlarmTime() shouldBe Instant.parse("2026-09-19T04:00:01Z")
        }
    }

    @Test
    fun `a zone change re-arms the single alarm and the listeners see the date in the new zone`() {
        scheduler.arm()

        // 03:59:30Z is 15:59:30 on the 19th in Auckland (UTC+12); its next midnight is 12:00Z on the 19th.
        zones.set(ZoneId.of("Pacific/Auckland"))
        sendSystemBroadcast(Intent.ACTION_TIMEZONE_CHANGED)

        datesSeen.shouldContainExactly(LocalDate.of(2026, 9, 19))
        onlyAlarmTime() shouldBe Instant.parse("2026-09-19T12:00:01Z")
    }

    @Test
    fun `a system event while an alarm is armed does not add a second alarm`() {
        scheduler.arm()

        sendSystemBroadcast(Intent.ACTION_TIME_CHANGED)
        sendSystemBroadcast(Intent.ACTION_LOCALE_CHANGED)

        alarms.scheduledAlarms.shouldHaveSize(1)
    }

    @Test
    fun `the system receiver ignores every action outside its set`() {
        val receiver = SystemEventReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_DATE_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_PACKAGE_REPLACED))
        receiver.onReceive(context, Intent(DayRolloverScheduler.ACTION_DAY_ROLLOVER))
        receiver.onReceive(context, Intent())

        widgets.triggers.shouldBeEmpty()
        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `the alarm receiver ignores every action but its own`() {
        val receiver = DayRolloverAlarmReceiver()

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        receiver.onReceive(context, Intent(context, DayRolloverAlarmReceiver::class.java))

        widgets.triggers.shouldBeEmpty()
        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `trigger mapping covers exactly the five manifest actions`() {
        SystemEventReceiver.triggerFor("android.intent.action.TIME_SET") shouldBe DayRolloverTrigger.TIME_CHANGED
        SystemEventReceiver.triggerFor("android.intent.action.TIMEZONE_CHANGED") shouldBe
            DayRolloverTrigger.ZONE_CHANGED
        SystemEventReceiver.triggerFor("android.intent.action.LOCALE_CHANGED") shouldBe
            DayRolloverTrigger.LOCALE_CHANGED
        SystemEventReceiver.triggerFor("android.intent.action.BOOT_COMPLETED") shouldBe
            DayRolloverTrigger.BOOT_COMPLETED
        SystemEventReceiver.triggerFor("android.intent.action.MY_PACKAGE_REPLACED") shouldBe
            DayRolloverTrigger.APP_UPDATED
        SystemEventReceiver.triggerFor("android.intent.action.DATE_CHANGED") shouldBe null
        SystemEventReceiver.triggerFor(null) shouldBe null
    }

    @Test
    fun `both receivers are declared in the manifest and neither is exported`() {
        val packageManager = context.packageManager
        listOf(DayRolloverAlarmReceiver::class.java, SystemEventReceiver::class.java).forEach { receiver ->
            packageManager.getReceiverInfo(ComponentName(context, receiver), 0).exported shouldBe false
        }
    }

    @Test
    fun `the manifest filter of the system receiver is exactly the five actions`() {
        val actions =
            listOf(
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_LOCALE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_PACKAGE_REPLACED,
                DayRolloverScheduler.ACTION_DAY_ROLLOVER,
            )
        val matched =
            actions.filter { action ->
                context.packageManager
                    .queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
                    .any { it.activityInfo.name == SystemEventReceiver::class.java.name }
            }
        matched.shouldContainExactlyInAnyOrder(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
