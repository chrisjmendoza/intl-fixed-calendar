package io.github.chrisjmendoza.yearal.widget.month

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
 * `res/layout/month_widget_preview.xml` (ROADMAP M5 T5) is a real, RemoteViews-safe static mock-up of
 * the Month widget: it must inflate cleanly and show the fixed sample month title, both weekday header
 * rows, a full 28-day grid with today (day 8) marked, and the Gregorian span.
 */
@RunWith(AndroidJUnit4::class)
class MonthWidgetPreviewLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun rowTexts(row: LinearLayout): List<String> =
        (0 until row.childCount).map { (row.getChildAt(it) as TextView).text.toString() }

    @Test
    fun `the title and Gregorian span are the fixed sample values`() {
        val root = LayoutInflater.from(context).inflate(R.layout.month_widget_preview, null) as LinearLayout

        (root.getChildAt(0) as TextView).text.toString() shouldBe
            context.getString(R.string.widget_preview_month_title)
        (root.getChildAt(root.childCount - 1) as TextView).text.toString() shouldBe
            context.getString(R.string.widget_preview_month_gregorian_span)
    }

    @Test
    fun `both weekday header rows have seven cells, nominal Sunday-first and actual Thursday-first`() {
        val root = LayoutInflater.from(context).inflate(R.layout.month_widget_preview, null) as LinearLayout
        val nominalRow = root.getChildAt(1) as LinearLayout
        val actualRow = root.getChildAt(2) as LinearLayout

        rowTexts(nominalRow) shouldBe listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        rowTexts(actualRow) shouldBe listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
    }

    @Test
    fun `the grid has four rows of seven days, 1 through 28`() {
        val root = LayoutInflater.from(context).inflate(R.layout.month_widget_preview, null) as LinearLayout
        val gridRows = (3..6).map { root.getChildAt(it) as LinearLayout }

        val days = gridRows.flatMap { rowTexts(it) }.map { it.trimEnd('•') }

        days shouldBe (1..28).map { it.toString() }
    }

    @Test
    fun `today (day 8) and the illustrative day 21 carry the event-dot glyph`() {
        val root = LayoutInflater.from(context).inflate(R.layout.month_widget_preview, null) as LinearLayout
        val gridRows = (3..6).map { root.getChildAt(it) as LinearLayout }
        val allCellText = gridRows.flatMap { rowTexts(it) }

        allCellText.filter { it.contains('•') } shouldBe listOf("8•", "21•")
    }
}
