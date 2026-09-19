package io.github.chrisjmendoza.yearal.widget.today

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.widget.R
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `res/layout/today_widget_preview.xml` (ROADMAP M5 T5) is a real, RemoteViews-safe static mock-up of
 * the widget: it must inflate cleanly (a widget-picker preview crashing the launcher is a much worse
 * failure than a merely plain one) and show the three fixed sample lines, in the LARGE responsive
 * size's order.
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetPreviewLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `inflates to a vertical layout with the three sample lines, in order`() {
        val root = LayoutInflater.from(context).inflate(R.layout.today_widget_preview, null) as LinearLayout

        val texts = (0 until root.childCount).map { (root.getChildAt(it) as TextView).text.toString() }

        texts shouldBe
            listOf(
                context.getString(R.string.widget_preview_today_ifc_date),
                context.getString(R.string.widget_preview_today_gregorian_date),
                context.getString(R.string.widget_preview_today_actual_weekday),
            )
    }
}
