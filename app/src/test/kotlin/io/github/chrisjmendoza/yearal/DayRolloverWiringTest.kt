package io.github.chrisjmendoza.yearal

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.core.scheduling.DayRolloverAlarmReceiver
import io.github.chrisjmendoza.yearal.core.scheduling.DayRolloverScheduler
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

// The production Hilt graph, not fakes: Robolectric starts the real IfcApplication from the merged
// manifest. Proves that the day rollover is armed at process start, that the manifest receivers of
// :core:scheduling resolve their entry point from this application, and that the graph is valid while
// the DayRolloverListener multibinding is still empty (docs/ARCHITECTURE.md §5).
@RunWith(AndroidJUnit4::class)
class DayRolloverWiringTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val alarms: ShadowAlarmManager = shadowOf(application.getSystemService(AlarmManager::class.java))

    private fun assertOneRolloverAlarm() {
        val alarm = alarms.scheduledAlarms.shouldHaveSize(1).single()
        alarm.getType() shouldBe AlarmManager.RTC_WAKEUP
        alarm.windowLengthMs shouldBe DayRolloverScheduler.WINDOW.toMillis()
    }

    @Test
    fun `process start arms exactly one rollover alarm`() {
        application.shouldBeInstanceOf<IfcApplication>()
        assertOneRolloverAlarm()
    }

    @Test
    fun `a system event reaches the manifest receiver and re-arms through the real graph`() {
        // As after a reboot: the alarm from process start is gone, so the one found below is a new one.
        val armedAtStart =
            PendingIntent.getBroadcast(
                application,
                0,
                Intent(
                    application,
                    DayRolloverAlarmReceiver::class.java,
                ).setAction(DayRolloverScheduler.ACTION_DAY_ROLLOVER),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
        application.getSystemService(AlarmManager::class.java).cancel(armedAtStart.shouldNotBeNull())
        alarms.scheduledAlarms.shouldBeEmpty()

        application.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()

        assertOneRolloverAlarm()
    }

    @Test
    fun `the alarm itself reaches the manifest receiver and arms the next one`() {
        alarms.fireAlarm(alarms.scheduledAlarms.shouldHaveSize(1).single())
        shadowOf(Looper.getMainLooper()).idle()

        assertOneRolloverAlarm()
    }
}
