package io.github.chrisjmendoza.yearal.widget.preview

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * [WidgetPreviewUpdater] is the version/locale-guarded gate in front of the API 35+ system preview call
 * (ROADMAP M5 T5; docs/ARCHITECTURE.md §5 "Picker previews": "Call it on app start, only when the
 * (versionCode, locale) pair changes", "rate-limited to about two per hour"). [PreviewRegistrar] is
 * faked so these tests never touch the real, API-35-only `GlanceAppWidgetManager.setWidgetPreviews` call.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewUpdaterTest {
    private class RecordingRegistrar(
        private val succeeds: Boolean = true,
    ) : PreviewRegistrar {
        var callCount: Int = 0
            private set

        override suspend fun registerPreviews(): Boolean {
            callCount++
            return succeeds
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPersistedState() {
        // Every test starts from "never registered", regardless of what an earlier test in this class
        // (or a shared Robolectric sandbox) left behind.
        context
            .getSharedPreferences(WidgetPreviewUpdater.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        Locale.setDefault(Locale.US)
    }

    @Test
    @Config(sdk = [30])
    fun `below API 35, the registrar is never called`() =
        runTest {
            val registrar = RecordingRegistrar()
            val updater = WidgetPreviewUpdater(context, registrar)

            updater.updateIfNeeded()

            registrar.callCount shouldBe 0
        }

    @Test
    @Config(sdk = [35])
    fun `on API 35+, the first call for a new version-locale pair calls the registrar once`() =
        runTest {
            val registrar = RecordingRegistrar()
            val updater = WidgetPreviewUpdater(context, registrar)

            updater.updateIfNeeded()

            registrar.callCount shouldBe 1
        }

    @Test
    @Config(sdk = [35])
    fun `a second call for the same version-locale pair does not call the registrar again`() =
        runTest {
            val registrar = RecordingRegistrar()
            val updater = WidgetPreviewUpdater(context, registrar)

            updater.updateIfNeeded()
            updater.updateIfNeeded()

            registrar.callCount shouldBe 1
        }

    @Test
    @Config(sdk = [35])
    fun `a locale change after a successful call is treated as a new pair`() =
        runTest {
            val registrar = RecordingRegistrar()
            val updater = WidgetPreviewUpdater(context, registrar)

            updater.updateIfNeeded()
            Locale.setDefault(Locale.GERMANY)
            updater.updateIfNeeded()

            registrar.callCount shouldBe 2
        }

    @Test
    @Config(sdk = [35])
    fun `a rate-limited result is not persisted, so the next start retries`() =
        runTest {
            val registrar = RecordingRegistrar(succeeds = false)
            val updater = WidgetPreviewUpdater(context, registrar)

            updater.updateIfNeeded()
            updater.updateIfNeeded()

            registrar.callCount shouldBe 2
        }
}
