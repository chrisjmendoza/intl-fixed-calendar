package io.github.chrisjmendoza.yearal.widget.today

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The widget's tap action (FEATURES S5; `docs/security-and-privacy.md` §6.4: every `PendingIntent` is
 * explicit and carries IDs only, never content — here, nothing at all). `:widget` cannot depend on
 * `:app`'s `MainActivity` at compile time (docs/ARCHITECTURE.md §2), so [launchAppIntent] must resolve
 * the launcher through [android.content.pm.PackageManager] rather than naming a class.
 *
 * This module's own test manifest declares no launcher activity (only `:app`'s does), so the fake
 * launcher activity below is registered with Robolectric's shadow package manager purely so
 * `getLaunchIntentForPackage` has something to resolve — exactly what the real `:app` manifest already
 * provides for the production widget.
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetActionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun registerAFakeLauncherActivity() {
        val component = ComponentName(context.packageName, FAKE_MAIN_ACTIVITY)
        val activityInfo =
            ActivityInfo().apply {
                packageName = component.packageName
                name = component.className
                applicationInfo = context.applicationInfo
            }
        val shadowPackageManager = shadowOf(context.packageManager)
        shadowPackageManager.addOrUpdateActivity(activityInfo)
        shadowPackageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    @Test
    fun `the launch intent is explicit -- its component is already resolved`() {
        val intent = launchAppIntent(context).shouldNotBeNull()

        intent.component.shouldNotBeNull()
        intent.component!!.packageName shouldBe context.packageName
        intent.component!!.className shouldBe FAKE_MAIN_ACTIVITY
    }

    @Test
    fun `the launch intent carries no extras`() {
        val intent = launchAppIntent(context).shouldNotBeNull()

        (intent.extras == null || intent.extras!!.isEmpty) shouldBe true
    }

    @Test
    fun `the launch intent starts a new task, since it is not launched from an activity`() {
        val intent = launchAppIntent(context).shouldNotBeNull()

        (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldBe Intent.FLAG_ACTIVITY_NEW_TASK
    }

    private companion object {
        const val FAKE_MAIN_ACTIVITY = "io.github.chrisjmendoza.yearal.MainActivity"
    }
}
