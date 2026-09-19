package io.github.chrisjmendoza.yearal.core.scheduling.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.domain.event.DefaultRecurrenceExpander
import io.github.chrisjmendoza.yearal.core.domain.event.Event
import io.github.chrisjmendoza.yearal.core.domain.event.EventCalendar
import io.github.chrisjmendoza.yearal.core.domain.event.EventRepository
import io.github.chrisjmendoza.yearal.core.domain.event.Recurrence
import io.github.chrisjmendoza.yearal.core.domain.event.Reminder
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger
import io.github.chrisjmendoza.yearal.core.testing.EventFixtures
import io.github.chrisjmendoza.yearal.core.testing.FakeEventRepository
import io.github.chrisjmendoza.yearal.core.testing.FakeZoneProvider
import io.github.chrisjmendoza.yearal.core.testing.MutableClock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowNotificationManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Provider

/**
 * The alarm and notification behaviour of [AlarmReminderScheduler]: the single next-alarm pattern of
 * `docs/ARCHITECTURE.md` §3.2 "Reminders", the exact-versus-windowed policy of §5 layer 1, the
 * `PendingIntent` rules of `docs/security-and-privacy.md` §6.4 and the lock-screen redaction of §3.3
 * (FEATURES E4, P2, P3, Q2, Q10, Q11).
 *
 * The real [DefaultRecurrenceExpander] and a real [ReminderNotifier] are used; only the clock, the
 * zone and the store are fakes. Instants are the ones worked out by hand in `ReminderPlannerTest`.
 */
@RunWith(AndroidJUnit4::class)
class AlarmReminderSchedulerTest {
    /** Robolectric plus coroutines: fail loudly rather than hanging the build. */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val application: Application = context as Application
    private val alarms: ShadowAlarmManager = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications: ShadowNotificationManager =
        shadowOf(context.getSystemService(NotificationManager::class.java))

    // 08:00 EDT on 29 June 2026 in New York, well before any 09:00 all-day reminder that day.
    private val clock = MutableClock(Instant.parse("2026-06-29T12:00:00Z"))
    private val zones = FakeZoneProvider(ZoneId.of("America/New_York"))
    private val repository = FakeEventRepository(clock)
    private val scheduler =
        AlarmReminderScheduler(
            context = context,
            repository = Provider { repository },
            expander = DefaultRecurrenceExpander(),
            clock = clock,
            zoneProvider = zones,
            notifier = ReminderNotifier(context),
        )

    @After
    fun tearDown() {
        ShadowAlarmManager.reset()
    }

    private fun grantNotifications() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun reschedule() = runBlocking { scheduler.reschedule() }

    private fun onlyAlarm(): ShadowAlarmManager.ScheduledAlarm = alarms.scheduledAlarms.shouldHaveSize(1).single()

    private fun onlyAlarmTime(): Instant = Instant.ofEpochMilli(onlyAlarm().triggerAtMs)

    /** An all-day event on [date] with a reminder [minutesBefore] minutes before 09:00 local. */
    private fun seedAllDay(
        date: LocalDate,
        minutesBefore: Int = 0,
        title: String = "All-day event",
        calendarId: Long = EventCalendar.DEFAULT_ID,
    ): Event =
        runBlocking {
            val stored =
                repository.upsertEvent(
                    EventFixtures.allDay(
                        date = date,
                        title = title,
                        calendarId = calendarId,
                        reminders = setOf(Reminder(minutesBefore)),
                    ),
                )
            checkNotNull(repository.getEvent(stored))
        }

    // ---------------------------------------------------------------------------------------------
    // Arming and cancelling one alarm
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an empty store arms nothing`() {
        reschedule()

        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `an event without reminders arms nothing`() {
        runBlocking { repository.upsertEvent(EventFixtures.sol13Yearly()) }

        reschedule()

        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `the single next reminder is armed, and only that one`() {
        seedAllDay(LocalDate.of(2026, 6, 30)) // 09:00 EDT = 13:00Z
        seedAllDay(LocalDate.of(2026, 7, 5), title = "Later event") // 09:00 EDT on 5 July

        reschedule()

        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
        onlyAlarm().getType() shouldBe AlarmManager.RTC_WAKEUP
    }

    @Test
    fun `when nothing is pending any armed alarm is cancelled`() {
        val event = seedAllDay(LocalDate.of(2026, 6, 30))
        reschedule()
        alarms.scheduledAlarms.shouldHaveSize(1)

        runBlocking { repository.deleteEvent(event.id) }
        reschedule()

        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `rescheduling repeatedly keeps exactly one alarm`() {
        seedAllDay(LocalDate.of(2026, 6, 30))

        repeat(3) { reschedule() }

        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
    }

    @Test
    fun `an event in a hidden calendar is never armed`() {
        val hidden = runBlocking { repository.upsertCalendar(EventCalendar(name = "Hidden", visible = false)) }
        seedAllDay(LocalDate.of(2026, 6, 30), calendarId = hidden)

        reschedule()

        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `the zone comes from the ZoneProvider, so a zone change moves the alarm`() {
        seedAllDay(LocalDate.of(2026, 6, 30))
        reschedule()
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")

        zones.set(ZoneId.of("Asia/Tokyo")) // 09:00 Tokyo on the same date is 00:00Z
        reschedule()

        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T00:00:00Z")
    }

    @Test
    fun `a Leap Day event in a common year is armed on the date its policy gives`() {
        // Every Leap Day, JUNE_28 policy, anchored on Leap Day 2024: in common 2026 that is IFC
        // June 28 = Gregorian 17 June, 09:00 EDT = 13:00Z.
        runBlocking { repository.upsertEvent(EventFixtures.leapDayYearly().copy(reminders = setOf(Reminder(0)))) }
        clock.set(Instant.parse("2026-06-01T12:00:00Z"))

        reschedule()

        onlyAlarmTime() shouldBe Instant.parse("2026-06-17T13:00:00Z")
    }

    // ---------------------------------------------------------------------------------------------
    // The PendingIntent and the exact/windowed branch
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the PendingIntent is an immutable explicit broadcast that carries no data`() {
        val expectedIntent =
            Intent(context, ReminderAlarmReceiver::class.java)
                .setAction(AlarmReminderScheduler.ACTION_REMINDER)
        val lookupFlags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        PendingIntent
            .getBroadcast(context, AlarmReminderScheduler.REQUEST_CODE, expectedIntent, lookupFlags)
            .shouldBeNull()
        seedAllDay(LocalDate.of(2026, 6, 30))

        reschedule()

        val registered =
            PendingIntent
                .getBroadcast(context, AlarmReminderScheduler.REQUEST_CODE, expectedIntent, lookupFlags)
                .shouldNotBeNull()
        val operation = shadowOf(registered)
        operation.isBroadcast shouldBe true
        (operation.flags and PendingIntent.FLAG_IMMUTABLE) shouldBe PendingIntent.FLAG_IMMUTABLE
        (operation.flags and PendingIntent.FLAG_MUTABLE) shouldBe 0
        val intent = operation.savedIntent
        intent.component shouldBe ComponentName(context, ReminderAlarmReceiver::class.java)
        intent.action shouldBe AlarmReminderScheduler.ACTION_REMINDER
        intent.extras.shouldBeNull()
        intent.data.shouldBeNull()
        // The reminder alarm and the rollover alarm must not share a request code, or one would
        // replace the other.
        AlarmReminderScheduler.REQUEST_CODE shouldNotBe 0
    }

    @Test
    @Config(sdk = [26, 30])
    fun `below API 31 the alarm is exact, because no permission is involved`() {
        seedAllDay(LocalDate.of(2026, 6, 30))

        reschedule()

        onlyAlarm().isAllowWhileIdle shouldBe true
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
    }

    @Test
    @Config(sdk = [31, 33, 36])
    fun `with the exact-alarm capability the alarm is exact and allowed while idle`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        seedAllDay(LocalDate.of(2026, 6, 30))

        reschedule()

        onlyAlarm().isAllowWhileIdle shouldBe true
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
    }

    @Test
    @Config(sdk = [31, 33, 36])
    fun `without the exact-alarm capability the alarm falls back to a 10-minute window`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        seedAllDay(LocalDate.of(2026, 6, 30))

        reschedule()

        onlyAlarm().windowLengthMs shouldBe Duration.ofMinutes(10).toMillis()
        onlyAlarm().isAllowWhileIdle shouldBe false
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
    }

    // ---------------------------------------------------------------------------------------------
    // Posting
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `when the reminder instant has come the notification is posted and the next alarm armed`() {
        grantNotifications()
        val event =
            runBlocking {
                val id =
                    repository.upsertEvent(
                        EventFixtures
                            .allDay(date = LocalDate.of(2026, 6, 29), title = "Dentist", reminders = setOf(Reminder(0)))
                            .copy(recurrence = Recurrence.Gregorian("FREQ=DAILY")),
                    )
                checkNotNull(repository.getEvent(id))
            }
        event.title shouldBe "Dentist"

        clock.set(Instant.parse("2026-06-29T13:00:00Z")) // 09:00 EDT, exactly the reminder instant
        reschedule()

        val posted = notifications.allNotifications.shouldHaveSize(1).single()
        posted.extras.getString(Notification.EXTRA_TITLE) shouldBe "Dentist"
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
    }

    @Test
    fun `the notification hides the event on the lock screen and shows a redacted public version`() {
        grantNotifications()
        seedAllDay(LocalDate.of(2026, 6, 29), title = "Therapy")

        clock.set(Instant.parse("2026-06-29T13:00:00Z"))
        reschedule()

        val posted = notifications.allNotifications.shouldHaveSize(1).single()
        posted.visibility shouldBe Notification.VISIBILITY_PRIVATE
        val public = posted.publicVersion.shouldNotBeNull()
        public.extras.getString(Notification.EXTRA_TITLE) shouldBe "Event reminder"
        public.extras.getString(Notification.EXTRA_TITLE) shouldNotBe "Therapy"
        public.extras.getString(Notification.EXTRA_TEXT) shouldBe "All day"
        public.extras.toString() shouldNotContain "Therapy"
    }

    @Test
    fun `a timed reminder shows the start time and an all-day one says All day`() {
        grantNotifications()
        // EventFixtures.timedZoned: 09:30 New York on 8 March 2026 with a 10-minute reminder.
        runBlocking { repository.upsertEvent(EventFixtures.timedZoned()) }

        clock.set(Instant.parse("2026-03-08T13:20:00Z"))
        reschedule()

        val posted = notifications.allNotifications.shouldHaveSize(1).single()
        // The localized short time; recent CLDR data separates it with a narrow no-break space.
        posted.shortTime() shouldBe "9:30 AM"
        posted.publicVersion.shouldNotBeNull().shortTime() shouldBe "9:30 AM"
    }

    /** The notification's text with the narrow no-break space of the localized time normalized. */
    private fun Notification.shortTime(): String? =
        extras.getString(Notification.EXTRA_TEXT)?.replace(' ', ' ')?.replace(' ', ' ')

    @Test
    fun `the channel is created before the first notification and reused afterwards`() {
        grantNotifications()
        seedAllDay(LocalDate.of(2026, 6, 29))
        notifications.notificationChannels.shouldBeEmpty()

        clock.set(Instant.parse("2026-06-29T13:00:00Z"))
        reschedule()
        clock.set(Instant.parse("2026-06-30T13:00:00Z"))
        reschedule()

        notifications.notificationChannels.shouldHaveSize(1)
        context
            .getSystemService(NotificationManager::class.java)
            .getNotificationChannel(ReminderNotifier.CHANNEL_ID)
            .shouldNotBeNull()
            .importance shouldBe NotificationManager.IMPORTANCE_HIGH
    }

    @Test
    fun `two events due at the same instant both post, and each keeps its own notification`() {
        grantNotifications()
        seedAllDay(LocalDate.of(2026, 6, 29), title = "First event")
        seedAllDay(LocalDate.of(2026, 6, 29), title = "Second event")

        clock.set(Instant.parse("2026-06-29T13:00:00Z"))
        reschedule()

        notifications
            .allNotifications
            .map { it.extras.getString(Notification.EXTRA_TITLE) }
            .shouldContainExactlyInAnyOrder("First event", "Second event")
        alarms.scheduledAlarms.shouldBeEmpty() // nothing else is pending
    }

    @Test
    fun `two reminders on one event post separately`() {
        grantNotifications()
        runBlocking {
            repository.upsertEvent(
                EventFixtures.allDay(
                    date = LocalDate.of(2026, 6, 30),
                    title = "Flight",
                    reminders = setOf(Reminder(0), Reminder(1440)),
                ),
            )
        }

        clock.set(Instant.parse("2026-06-29T13:00:00Z")) // the 1440-minute one
        reschedule()
        notifications.allNotifications.shouldHaveSize(1)
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")

        clock.set(Instant.parse("2026-06-30T13:00:00Z")) // the 0-minute one
        reschedule()

        notifications.allNotifications.shouldHaveSize(2)
        alarms.scheduledAlarms.shouldBeEmpty()
    }

    @Test
    fun `a reminder is posted only once, however often the recomputation runs`() {
        grantNotifications()
        seedAllDay(LocalDate.of(2026, 6, 29))

        clock.set(Instant.parse("2026-06-29T13:00:00Z"))
        reschedule()
        reschedule()
        reschedule()

        notifications.allNotifications.shouldHaveSize(1)
    }

    @Test
    @Config(sdk = [33, 36])
    fun `with POST_NOTIFICATIONS denied nothing is posted but the next alarm is still armed`() {
        // No grantNotifications(): on API 33+ Robolectric starts with the runtime permission denied.
        seedAllDay(LocalDate.of(2026, 6, 29))
        seedAllDay(LocalDate.of(2026, 6, 30), title = "Tomorrow")

        clock.set(Instant.parse("2026-06-29T13:00:00Z"))
        reschedule()

        notifications.allNotifications.shouldBeEmpty()
        notifications.notificationChannels.shouldBeEmpty()
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
    }

    @Test
    @Config(sdk = [26, 30])
    fun `below API 33 there is no runtime permission to deny, so the notification is posted`() {
        seedAllDay(LocalDate.of(2026, 6, 29))

        clock.set(Instant.parse("2026-06-29T13:00:00Z"))
        reschedule()

        notifications.allNotifications.shouldHaveSize(1)
    }

    // ---------------------------------------------------------------------------------------------
    // Late reminders and the rollover hook
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a reminder missed inside the grace window is delivered late`() {
        grantNotifications()
        seedAllDay(LocalDate.of(2026, 6, 29))

        // As after a reboot: the alarm was lost and the app is only called back 5 minutes late.
        clock.set(Instant.parse("2026-06-29T13:00:00Z").plus(Duration.ofMinutes(5)))
        reschedule()

        notifications.allNotifications.shouldHaveSize(1)
    }

    @Test
    fun `a reminder missed beyond the grace window is dropped, silently`() {
        grantNotifications()
        seedAllDay(LocalDate.of(2026, 6, 29))
        seedAllDay(LocalDate.of(2026, 6, 30), title = "Tomorrow")

        clock.set(Instant.parse("2026-06-29T13:00:00Z").plus(AlarmReminderScheduler.LATE_GRACE).plusSeconds(1))
        reschedule()

        notifications.allNotifications.shouldBeEmpty()
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
    }

    @Test
    fun `crossing local midnight re-targets nothing and then delivers the new day's reminder`() {
        grantNotifications()
        runBlocking {
            repository.upsertEvent(
                EventFixtures
                    .allDay(date = LocalDate.of(2026, 6, 29), title = "Daily", reminders = setOf(Reminder(0)))
                    .copy(recurrence = Recurrence.Gregorian("FREQ=DAILY")),
            )
        }

        // 23:30 EDT on 29 June: today's 09:00 reminder is long past and dropped, tomorrow's is armed.
        clock.set(Instant.parse("2026-06-30T03:30:00Z"))
        reschedule()
        notifications.allNotifications.shouldBeEmpty()
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")

        // 00:30 EDT on 30 June: the local date has changed, the armed instant has not.
        clock.set(Instant.parse("2026-06-30T04:30:00Z"))
        reschedule()
        onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
        notifications.allNotifications.shouldBeEmpty()

        // 09:00 EDT on 30 June: it fires, and the day after is armed.
        clock.set(Instant.parse("2026-06-30T13:00:00Z"))
        reschedule()
        notifications.allNotifications.shouldHaveSize(1)
        onlyAlarmTime() shouldBe Instant.parse("2026-07-01T13:00:00Z")
    }

    @Test
    fun `every day-rollover trigger recomputes the alarm`() {
        seedAllDay(LocalDate.of(2026, 6, 30))
        val alarmManager = context.getSystemService(AlarmManager::class.java)

        DayRolloverTrigger.entries.forEach { trigger ->
            // As after a reboot or an app update: no alarm is left, so the one found below is new.
            alarmManager.cancel(AlarmReminderScheduler.reminderOperation(context))
            alarms.scheduledAlarms.shouldBeEmpty()

            runBlocking { scheduler.onDayRollover(trigger) }

            onlyAlarmTime() shouldBe Instant.parse("2026-06-30T13:00:00Z")
        }
    }

    @Test
    fun `the candidate query is asked from today, so the padding of the contract applies`() {
        // A zoned event whose own date is a day ahead of the device date already has its reminder
        // instant in the device's today; ZONE_SKEW_DAYS is what keeps it in the candidate set and in
        // the occurrence walk, which start from the device date.
        EventRepository.ZONE_SKEW_DAYS shouldBe 2L
        clock.set(Instant.parse("2026-06-29T09:00:00Z")) // 05:00 EDT on 29 June
        runBlocking {
            repository.upsertEvent(
                Event(
                    uid = "kiritimati",
                    title = "Line Islands call",
                    timing =
                        io.github.chrisjmendoza.yearal.core.domain.event.EventTiming
                            .Timed(LocalDate.of(2026, 6, 30), 30, 30, ZoneId.of("Pacific/Kiritimati")),
                    reminders = setOf(Reminder(0)),
                ),
            )
        }
        // 00:30 on 30 June in Kiritimati (UTC+14) is 10:30Z on 29 June, an hour and a half ahead.
        reschedule()

        onlyAlarmTime() shouldBe Instant.parse("2026-06-29T10:30:00Z")
    }
}
