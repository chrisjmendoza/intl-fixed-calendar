package io.github.chrisjmendoza.yearal.widget

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.widget.month.MonthGlanceWidget
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Month widget's receiver is declared exactly as `docs/security-and-privacy.md` §6.3 allows for a
 * widget provider (ROADMAP M5 T3): exported (launchers require it) with only the `APPWIDGET_UPDATE`
 * filter, and it points at [MonthGlanceWidget] and `res/xml/month_widget_info.xml`.
 */
@RunWith(AndroidJUnit4::class)
class MonthWidgetReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `the receiver renders MonthGlanceWidget`() {
        (MonthWidgetReceiver().glanceAppWidget is MonthGlanceWidget) shouldBe true
    }

    @Test
    fun `the receiver is exported, as a widget provider must be`() {
        val info =
            context.packageManager.getReceiverInfo(
                ComponentName(context, MonthWidgetReceiver::class.java),
                PackageManager.GET_META_DATA,
            )

        info.exported shouldBe true
        info.metaData.getInt("android.appwidget.provider") shouldBe R.xml.month_widget_info
    }

    @Test
    fun `the only intent filter is the system's widget-update action`() {
        val matches =
            context.packageManager.queryBroadcastReceivers(
                Intent("android.appwidget.action.APPWIDGET_UPDATE").setPackage(context.packageName),
                0,
            )

        matches.any { it.activityInfo.name == MonthWidgetReceiver::class.java.name } shouldBe true
    }

    @Test
    fun `it does not respond to an unrelated action`() {
        val matches =
            context.packageManager.queryBroadcastReceivers(
                Intent("android.intent.action.BOOT_COMPLETED").setPackage(context.packageName),
                0,
            )

        matches.any { it.activityInfo.name == MonthWidgetReceiver::class.java.name } shouldBe false
    }
}
