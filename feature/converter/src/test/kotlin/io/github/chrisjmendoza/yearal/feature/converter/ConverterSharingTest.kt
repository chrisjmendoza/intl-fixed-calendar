package io.github.chrisjmendoza.yearal.feature.converter

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Copy and share (docs/FEATURES.md D4; docs/security-and-privacy.md §6.3): the share is an implicit
 * `ACTION_SEND` of `text/plain` behind the system chooser, carrying the text and nothing else — no
 * URI, no stream, no grant flags — and the copy is a plain-text clip.
 */
@RunWith(AndroidJUnit4::class)
class ConverterSharingTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val text = "IFC September 8, 2026 (IFC 2026-10-08) = Gregorian Thursday, September 17, 2026"

    @Test
    fun `sharing starts a chooser around a plain-text send intent`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        shareConversion(activity, text) shouldBe true

        val chooser = shadowOf(application).nextStartedActivity.shouldNotBeNull()
        chooser.action shouldBe Intent.ACTION_CHOOSER
        // From an Activity there is no new task.
        (chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe 0
        val send = chooser.extraIntent().shouldNotBeNull()
        send.action shouldBe Intent.ACTION_SEND
        send.type shouldBe "text/plain"
        send.getStringExtra(Intent.EXTRA_TEXT) shouldBe text
        send.extras?.keySet() shouldBe setOf(Intent.EXTRA_TEXT)
        send.data.shouldBeNull()
        send.component.shouldBeNull()
        (send.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) shouldBe 0
    }

    @Test
    fun `sharing from a non-activity context adds the new-task flag`() {
        shareConversion(application, text) shouldBe true

        val chooser = shadowOf(application).nextStartedActivity.shouldNotBeNull()
        (chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBeGreaterThan 0
        chooser.extraIntent()?.getStringExtra(Intent.EXTRA_TEXT) shouldBe text
    }

    @Test
    fun `copying puts the text on the clipboard as plain text`() {
        copyConversion(application, "Converted date", text) shouldBe true

        val clip = application.getSystemService(ClipboardManager::class.java).primaryClip.shouldNotBeNull()
        clip.itemCount shouldBe 1
        clip.getItemAt(0).text.toString() shouldBe text
        clip.description.label.toString() shouldBe "Converted date"
        clip.description.getMimeType(0) shouldBe "text/plain"
    }

    // The typed overload is API 33+; these tests run on the SDK pinned in robolectric.properties (36).
    private fun Intent.extraIntent(): Intent? = getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
}
