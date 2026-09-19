package io.github.chrisjmendoza.yearal.widget.month

import io.github.chrisjmendoza.yearal.widget.today.todayDate
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

/**
 * [MonthGlanceWidget.PREVIEW_CLOCK], [MonthGlanceWidget.PREVIEW_ZONE_PROVIDER] and
 * [MonthGlanceWidget.PREVIEW_EVENT_DATES] (ROADMAP M5 T5) must match the exact sample
 * `res/layout/month_widget_preview.xml`'s hard-coded strings claim (September 2026, today = IFC day 8
 * = Gregorian September 17, with illustrative dots on days 8 and 21), so the static and the dynamic
 * (API 35+) previews can never silently drift apart.
 */
class MonthGlanceWidgetPreviewTest {
    @Test
    fun `the preview clock resolves to the exact sample date named in the static preview layout`() {
        val date = todayDate(MonthGlanceWidget.PREVIEW_CLOCK, MonthGlanceWidget.PREVIEW_ZONE_PROVIDER)

        date.gregorianDate shouldBe LocalDate.of(2026, 9, 17)
    }

    @Test
    fun `the preview event dates match the illustrative dots in the static preview layout`() {
        MonthGlanceWidget.PREVIEW_EVENT_DATES shouldBe
            setOf(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 30))
    }
}
