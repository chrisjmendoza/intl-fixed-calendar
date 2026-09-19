package io.github.chrisjmendoza.yearal.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every widget-owned label is a string resource (CLAUDE.md rule 9). This does not catch a hard-coded
 * string used *instead of* a resource elsewhere in the module (that is a review concern), but it does
 * prove the resources this module declares actually exist and are not empty placeholders.
 */
@RunWith(AndroidJUnit4::class)
class WidgetStringsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `every widget string resource is present and non-blank`() {
        context.getString(R.string.today_widget_label).shouldNotBeBlank()
        context.getString(R.string.today_widget_description).shouldNotBeBlank()
        context.getString(R.string.today_widget_loading).shouldNotBeBlank()
        context.getString(R.string.today_widget_tap_hint).shouldNotBeBlank()
    }
}
