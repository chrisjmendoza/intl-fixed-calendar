package io.github.chrisjmendoza.yearal.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// docs/ARCHITECTURE.md §5 "Midnight rollover (layered)" layer 1 and "Reconciled decisions" 11;
// docs/security-and-privacy.md §6.4 for the PendingIntent.
@RunWith(AndroidJUnit4::class)
class DayRolloverSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms: ShadowAlarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))

    // 11:00 EDT on 18 Sep 2026; the next New York midnight is 2026-09-19T04:00:00Z.
    private val clock = MutableClock(Instant.parse("2026-09-18T15:00:00Z"))
    private val zones = FakeZoneProvider(ZoneId.of("America/New_York"))
    private val scheduler = DayRolloverScheduler(context, clock, zones)

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    private fun onlyAlarm(): ShadowAlarmManager.ScheduledAlarm = alarms.scheduledAlarms.shouldHaveSize(1).single()

    @Test
    fun `nothing is armed until arm is called`() {
        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `arm sets one wakeup alarm one second after the next local midnight`() {
        scheduler.arm()

        val alarm = onlyAlarm()
        alarm.getType() shouldBe AlarmManager.RTC_WAKEUP
        Instant.ofEpochMilli(alarm.triggerAtMs) shouldBe Instant.parse("2026-09-19T04:00:01Z")
        alarm.intervalMs shouldBe 0L
    }

    // Since ROADMAP M6 T3 the exact-alarm permissions that reminders justify are declared, so the
    // rollover reuses the capability: exact where it exists, the 10-minute window where it does not.
    // Checked on both sides of API 31, where canScheduleExactAlarms() and the 10-minute minimum window
    // appeared, and on the oldest and newest supported releases.
    @Test
    @Config(sdk = [31, 33, 36])
    fun `without exact-alarm capability the alarm is a 10-minute window`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.arm()

        val alarm = onlyAlarm()
        alarm.windowLengthMs shouldBe Duration.ofMinutes(10).toMillis()
        alarm.isAllowWhileIdle shouldBe false
        Instant.ofEpochMilli(alarm.triggerAtMs) shouldBe Instant.parse("2026-09-19T04:00:01Z")
    }

    @Test
    @Config(sdk = [31, 33, 36])
    fun `with exact-alarm capability the alarm is exact and allowed while idle`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.arm()

        val alarm = onlyAlarm()
        alarm.isAllowWhileIdle shouldBe true
        alarm.getType() shouldBe AlarmManager.RTC_WAKEUP
        Instant.ofEpochMilli(alarm.triggerAtMs) shouldBe Instant.parse("2026-09-19T04:00:01Z")
    }

    @Test
    @Config(sdk = [26, 30])
    fun `below API 31 the alarm is exact, because an exact alarm needs no permission there`() {
        // canScheduleExactAlarms() does not exist below API 31 and must not be consulted: whatever the
        // shadow reports, the alarm is exact.
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.arm()

        val alarm = onlyAlarm()
        alarm.isAllowWhileIdle shouldBe true
        Instant.ofEpochMilli(alarm.triggerAtMs) shouldBe Instant.parse("2026-09-19T04:00:01Z")
    }

    @Test
    fun `arming again replaces the alarm instead of adding one`() {
        repeat(3) { scheduler.arm() }
        onlyAlarm()

        // A second scheduler instance (a new process) still targets the same alarm.
        DayRolloverScheduler(context, clock, zones).arm()
        onlyAlarm()
    }

    @Test
    fun `arming after the clock moved re-targets the single alarm`() {
        scheduler.arm()
        clock.set(Instant.parse("2026-09-21T12:00:00Z")) // e.g. TIME_SET three days ahead

        scheduler.arm()

        Instant.ofEpochMilli(onlyAlarm().triggerAtMs) shouldBe Instant.parse("2026-09-22T04:00:01Z")
    }

    @Test
    fun `arming after a zone change re-targets the single alarm to the midnight of the new zone`() {
        scheduler.arm()
        // 15:00Z is already 03:00 on the 19th in Auckland (UTC+12); the 20th starts at 12:00Z on the 19th.
        zones.set(ZoneId.of("Pacific/Auckland"))

        scheduler.arm()

        Instant.ofEpochMilli(onlyAlarm().triggerAtMs) shouldBe Instant.parse("2026-09-19T12:00:01Z")
    }

    @Test
    fun `the zone is read from the ZoneProvider, not from the Clock`() {
        // The clock claims Tokyo; the provider says New York, and the provider wins.
        val tokyoClock = MutableClock(Instant.parse("2026-09-18T15:00:00Z"), ZoneId.of("Asia/Tokyo"))

        DayRolloverScheduler(context, tokyoClock, zones).arm()

        Instant.ofEpochMilli(onlyAlarm().triggerAtMs) shouldBe Instant.parse("2026-09-19T04:00:01Z")
    }

    @Test
    fun `a midnight that does not exist is armed for the first instant of the day`() {
        // Sao Paulo, 4 Nov 2018: 00:00 was skipped, the day began at 01:00-02:00 = 03:00Z.
        clock.set(Instant.parse("2018-11-03T15:00:00Z"))
        zones.set(ZoneId.of("America/Sao_Paulo"))

        scheduler.arm()

        Instant.ofEpochMilli(onlyAlarm().triggerAtMs) shouldBe Instant.parse("2018-11-04T03:00:01Z")
    }

    @Test
    @Config(sdk = [26, 30, 31, 36])
    fun `the PendingIntent is an immutable explicit broadcast that carries no data`() {
        // FLAG_NO_CREATE returns the PendingIntent arm() registered only if one exists for exactly this
        // explicit intent and request code, and null otherwise.
        val expectedIntent =
            Intent(context, DayRolloverAlarmReceiver::class.java).setAction(DayRolloverScheduler.ACTION_DAY_ROLLOVER)
        val lookupFlags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        PendingIntent.getBroadcast(context, 0, expectedIntent, lookupFlags).shouldBeNull()

        scheduler.arm()

        val registered = PendingIntent.getBroadcast(context, 0, expectedIntent, lookupFlags).shouldNotBeNull()
        val operation = shadowOf(registered)
        operation.isBroadcast shouldBe true
        (operation.flags and PendingIntent.FLAG_IMMUTABLE) shouldBe PendingIntent.FLAG_IMMUTABLE
        (operation.flags and PendingIntent.FLAG_MUTABLE) shouldBe 0
        val intent = operation.savedIntent
        intent.component shouldBe ComponentName(context, DayRolloverAlarmReceiver::class.java)
        intent.action shouldBe DayRolloverScheduler.ACTION_DAY_ROLLOVER
        intent.extras.shouldBeNull()
        intent.data.shouldBeNull()
    }
}
