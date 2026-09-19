package io.github.chrisjmendoza.yearal.widget.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.widget.R
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Proves the values in `res/xml/today_widget_info.xml` (docs/ARCHITECTURE.md §5 "Configuration"):
 * the 4-hour self-healing backstop, both resize directions, `home_screen` only (not `keyguard`,
 * `docs/security-and-privacy.md` §3.2), and the min size matching [TodayGlanceWidget.SMALL].
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetInfoTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun rootAttributes(): Map<String, String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.today_widget_info)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.START_TAG) {
            eventType = parser.next()
        }
        return (0 until parser.attributeCount).associate { index ->
            parser.getAttributeName(index) to parser.getAttributeValue(index)
        }
    }

    /** A dimension attribute like `110dp` may come back as `110dp` or a coerced `110.0dip`; either way
     * the leading magnitude must match. */
    private fun magnitudeOf(value: String): Float = Regex("""[0-9.]+""").find(value)!!.value.toFloat()

    @Test
    fun `updatePeriodMillis is the four hour self-healing backstop`() {
        val fourHoursMillis = 4 * 60 * 60 * 1000
        rootAttributes()["updatePeriodMillis"]!!.toInt() shouldBe fourHoursMillis
    }

    @Test
    fun `resize mode allows both directions`() {
        // A flag attribute compiles to its combined bitmask, printed in hex by the resource parser:
        // AppWidgetProviderInfo.RESIZE_HORIZONTAL (0x1) or RESIZE_VERTICAL (0x2) = 0x3.
        val mode = rootAttributes().getValue("resizeMode")
        mode shouldBe "0x3"
    }

    @Test
    fun `widget category is home screen only, never keyguard`() {
        // AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN. Deliberately not `0x3` (home_screen|keyguard):
        // this date-only widget stays lock-screen eligible on Android 16 QPR2+ by default
        // (docs/security-and-privacy.md §3.2), so it opts into nothing extra here.
        val category = rootAttributes().getValue("widgetCategory")
        category shouldBe "0x1"
    }

    @Test
    fun `min size matches the small responsive breakpoint`() {
        val attrs = rootAttributes()
        magnitudeOf(attrs.getValue("minWidth")) shouldBe 110f
        magnitudeOf(attrs.getValue("minHeight")) shouldBe 40f
    }

    @Test
    fun `max resize size matches the large responsive breakpoint`() {
        val attrs = rootAttributes()
        magnitudeOf(attrs.getValue("maxResizeWidth")) shouldBe 180f
        magnitudeOf(attrs.getValue("maxResizeHeight")) shouldBe 110f
    }

    @Test
    fun `initial and preview layouts, and the description, are declared`() {
        val attrs = rootAttributes()
        attrs.getValue("initialLayout") shouldBe "@${R.layout.today_widget_loading}"
        attrs.getValue("previewLayout") shouldBe "@${R.layout.today_widget_loading}"
        attrs.getValue("description") shouldBe "@${R.string.today_widget_description}"
    }

    /** No `android:configure`: the config activity is ROADMAP M5 T4, out of scope here. */
    @Test
    fun `has no configuration activity`() {
        rootAttributes().containsKey("configure") shouldBe false
    }
}
