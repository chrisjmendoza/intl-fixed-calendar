package io.github.chrisjmendoza.yearal.widget.preview

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.intSetOf
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.chrisjmendoza.yearal.widget.MonthWidgetReceiver
import io.github.chrisjmendoza.yearal.widget.TodayWidgetReceiver
import java.util.Locale
import javax.inject.Inject

/**
 * The seam between [WidgetPreviewUpdater] and the real `GlanceAppWidgetManager.setWidgetPreviews` call
 * (ROADMAP M5 T5), so the version/locale guard and the retry-on-rate-limit logic can be unit-tested with
 * a fake instead of a real, API-35-only system call -- the same reason
 * [io.github.chrisjmendoza.yearal.widget.WidgetRefresher] exists as a seam over `GlanceAppWidget.updateAll`.
 */
internal fun interface PreviewRegistrar {
    /**
     * Registers the picker preview for both widgets. Returns `true` only if **both** calls report
     * [GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS]; `false` if either is rate-limited
     * (or any other non-success result), which tells the caller not to persist the (versionCode,
     * locale) pair, so the next process start retries.
     */
    suspend fun registerPreviews(): Boolean
}

/**
 * [PreviewRegistrar] backed by the real [GlanceAppWidgetManager]. Calls both widgets' receivers with
 * the home-screen category -- the only category either widget declares (`docs/security-and-privacy.md`
 * §3.2; neither widget declares `keyguard`).
 *
 * The class itself carries no [RequiresApi]: its constructor does nothing platform-version-specific, so
 * Hilt can construct one unconditionally (it does, as soon as anything needs a [PreviewRegistrar]) with
 * no risk. Only [registerPreviews] is annotated, since that is the one place the real API-35-only call
 * happens.
 */
internal class GlancePreviewRegistrar
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PreviewRegistrar {
        /**
         * [RequiresApi] documents the real floor of `GlanceAppWidgetManager.setWidgetPreviews` (API 35)
         * to Android Lint's `NewApi` check. [WidgetPreviewUpdater.updateIfNeeded] is the only caller,
         * and it already returns before reaching this point on an older device (lint recognises that
         * `SDK_INT` early-return guard, even though the call there goes through the [PreviewRegistrar]
         * interface rather than this class directly).
         */
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        override suspend fun registerPreviews(): Boolean {
            val manager = GlanceAppWidgetManager(context)
            val homeScreen = intSetOf(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            val todayResult = manager.setWidgetPreviews(TodayWidgetReceiver::class, homeScreen)
            val monthResult = manager.setWidgetPreviews(MonthWidgetReceiver::class, homeScreen)
            return todayResult == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS &&
                monthResult == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
        }
    }

/**
 * Registers the API 35+ system widget-picker preview (docs/ARCHITECTURE.md §5 "Picker previews";
 * ROADMAP M5 T5). Below API 35 this does nothing -- launchers there show `previewLayout` (API 31-34) or
 * `previewImage` (API 26-30) instead, both static XML this class never touches.
 *
 * **Must be called once per process start** (ARCHITECTURE §5: "Call it on app start, only when the
 * (versionCode, locale) pair changes"). This class only implements the check-and-call logic; wiring the
 * actual call into app start is outside `:widget`'s ownership (nothing in `:widget` runs at process
 * start on its own) -- see the ROADMAP M5 T6 completion report for the exact line to add to
 * `IfcApplication.onCreate`.
 *
 * @param context the application context, used only to read the package's version; never a source of
 *   event content.
 * @param registrar the seam over the real system call (see [PreviewRegistrar]'s KDoc).
 */
class WidgetPreviewUpdater
    @Inject
    internal constructor(
        @param:ApplicationContext private val context: Context,
        private val registrar: PreviewRegistrar,
    ) {
        /**
         * Calls [PreviewRegistrar.registerPreviews], but only when [Build.VERSION.SDK_INT] is at least
         * 35 and the (versionCode, locale) pair differs from the one persisted in [PREFS_NAME] -- the
         * app-level throttle the architecture doc asks for, on top of (not instead of) the
         * roughly-two-per-hour system throttle the real call enforces.
         *
         * Idempotent and safe to call every process start: a repeat call for the same pair is a single
         * cheap [SharedPreferences] read and nothing else. If [PreviewRegistrar.registerPreviews]
         * reports rate-limiting (or any other failure) for either widget, the persisted pair is **not**
         * updated, so the next process start retries rather than silently giving up for the rest of
         * this app version's life.
         */
        suspend fun updateIfNeeded() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

            val versionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            val locale = Locale.getDefault().toLanguageTag()
            val current = "$versionCode|$locale"

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_LAST_UPDATED, null) == current) return

            if (registrar.registerPreviews()) {
                prefs.edit { putString(KEY_LAST_UPDATED, current) }
            }
        }

        /** `internal` so a test can clear the exact same preferences file between cases. */
        internal companion object {
            const val PREFS_NAME = "widget_preview_prefs"
            const val KEY_LAST_UPDATED = "last_updated_version_locale"
        }
    }
