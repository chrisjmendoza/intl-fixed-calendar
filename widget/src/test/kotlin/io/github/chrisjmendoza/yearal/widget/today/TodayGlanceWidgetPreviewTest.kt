package io.github.chrisjmendoza.yearal.widget.today

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

/**
 * [TodayGlanceWidget.PREVIEW_CLOCK] and [TodayGlanceWidget.PREVIEW_ZONE_PROVIDER] (ROADMAP M5 T5) must
 * resolve to the exact sample date `res/layout/today_widget_preview.xml`'s hard-coded strings claim
 * (IFC Sol 13, 2026 = Gregorian June 30, 2026), so the static and the dynamic (API 35+) previews can
 * never silently drift apart.
 */
class TodayGlanceWidgetPreviewTest {
    @Test
    fun `the preview clock resolves to the exact sample date named in the static preview layout`() {
        val date = todayDate(TodayGlanceWidget.PREVIEW_CLOCK, TodayGlanceWidget.PREVIEW_ZONE_PROVIDER)

        date.gregorianDate shouldBe LocalDate.of(2026, 6, 30)
    }
}
