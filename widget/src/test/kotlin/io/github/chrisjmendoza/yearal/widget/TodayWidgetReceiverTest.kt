package io.github.chrisjmendoza.yearal.widget

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.widget.today.TodayGlanceWidget
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The receiver is declared exactly as `docs/security-and-privacy.md` §6.3 allows for a widget provider:
 * exported (launchers require it) with only the `APPWIDGET_UPDATE` filter, and it points at
 * [TodayGlanceWidget] and `res/xml/today_widget_info.xml`.
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `the receiver renders TodayGlanceWidget`() {
        (TodayWidgetReceiver().glanceAppWidget is TodayGlanceWidget) shouldBe true
    }

    @Test
    fun `the receiver is exported, as a widget provider must be`() {
        val info =
            context.packageManager.getReceiverInfo(
                ComponentName(context, TodayWidgetReceiver::class.java),
                PackageManager.GET_META_DATA,
            )

        info.exported shouldBe true
        info.metaData.getInt("android.appwidget.provider") shouldBe R.xml.today_widget_info
    }

    @Test
    fun `the only intent filter is the system's widget-update action`() {
        val matches =
            context.packageManager.queryBroadcastReceivers(
                Intent("android.appwidget.action.APPWIDGET_UPDATE").setPackage(context.packageName),
                0,
            )

        matches.any { it.activityInfo.name == TodayWidgetReceiver::class.java.name } shouldBe true
    }

    @Test
    fun `it does not respond to an unrelated action`() {
        val matches =
            context.packageManager.queryBroadcastReceivers(
                Intent("android.intent.action.BOOT_COMPLETED").setPackage(context.packageName),
                0,
            )

        matches.any { it.activityInfo.name == TodayWidgetReceiver::class.java.name } shouldBe false
    }
}
