package io.github.chrisjmendoza.yearal.widget.month

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.chrisjmendoza.yearal.widget.R
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Proves the values in `res/xml/month_widget_info.xml` (ROADMAP M5 T3; docs/ARCHITECTURE.md §5
 * "Configuration"): the 4-hour self-healing backstop, both resize directions, `home_screen` only (not
 * `keyguard`, `docs/security-and-privacy.md` §3.2), and the min/target size matching
 * [MonthGlanceWidget.COMPACT] (about 4x3 home-screen cells).
 */
@RunWith(AndroidJUnit4::class)
class MonthWidgetInfoTest {
    private fun rootAttributes(): Map<String, String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.month_widget_info)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.START_TAG) {
            eventType = parser.next()
        }
        return (0 until parser.attributeCount).associate { index ->
            parser.getAttributeName(index) to parser.getAttributeValue(index)
        }
    }

    /** A dimension attribute like `250dp` may come back as `250dp` or a coerced `250.0dip`. */
    private fun magnitudeOf(value: String): Float = Regex("""[0-9.]+""").find(value)!!.value.toFloat()

    @Test
    fun `updatePeriodMillis is the four hour self-healing backstop, same as the Today widget`() {
        val fourHoursMillis = 4 * 60 * 60 * 1000
        rootAttributes()["updatePeriodMillis"]!!.toInt() shouldBe fourHoursMillis
    }

    @Test
    fun `resize mode allows both directions`() {
        val mode = rootAttributes().getValue("resizeMode")
        mode shouldBe "0x3"
    }

    @Test
    fun `widget category is home screen only, never keyguard`() {
        val category = rootAttributes().getValue("widgetCategory")
        category shouldBe "0x1"
    }

    @Test
    fun `min size matches about 4 by 3 home-screen cells`() {
        val attrs = rootAttributes()
        magnitudeOf(attrs.getValue("minWidth")) shouldBe 250f
        magnitudeOf(attrs.getValue("minHeight")) shouldBe 180f
    }

    @Test
    fun `max resize size matches the full responsive breakpoint`() {
        val attrs = rootAttributes()
        magnitudeOf(attrs.getValue("maxResizeWidth")) shouldBe 320f
        magnitudeOf(attrs.getValue("maxResizeHeight")) shouldBe 320f
    }

    @Test
    fun `target cell size is about 4 by 3`() {
        val attrs = rootAttributes()
        attrs.getValue("targetCellWidth").toInt() shouldBe 4
        attrs.getValue("targetCellHeight").toInt() shouldBe 3
    }

    @Test
    fun `initial and preview layouts, and the description, are declared`() {
        val attrs = rootAttributes()
        attrs.getValue("initialLayout") shouldBe "@${R.layout.month_widget_loading}"
        // ROADMAP M5 T5: a real static mock-up, not the loading placeholder.
        attrs.getValue("previewLayout") shouldBe "@${R.layout.month_widget_preview}"
        attrs.getValue("description") shouldBe "@${R.string.month_widget_description}"
    }

    /** No `android:configure`: the config activity is ROADMAP M5 T4, out of scope here. */
    @Test
    fun `has no configuration activity`() {
        rootAttributes().containsKey("configure") shouldBe false
    }

    /**
     * `previewImage` (ROADMAP M5 T5) is valid since API 11 (verified against the SDK's own
     * `api-versions.xml`), so it lives in the base file rather than needing its own resource-qualifier
     * split, and stays declared at every API level this app supports.
     */
    @Test
    @Config(sdk = [26])
    fun `below API 31, previewImage is the static vector fallback`() {
        rootAttributes().getValue("previewImage") shouldBe "@${R.drawable.widget_preview_image}"
    }
}
