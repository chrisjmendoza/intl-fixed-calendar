package io.github.chrisjmendoza.yearal.widget.today

import android.content.Context
import android.content.Intent

/**
 * The explicit intent the widget's tap action opens (FEATURES S5): the app's own launcher activity,
 * resolved by [android.content.pm.PackageManager] rather than by naming `MainActivity` directly.
 *
 * `:widget` is depended on only by `:app` (docs/ARCHITECTURE.md §2 "Dependency direction"), so it
 * cannot import an `:app` class at compile time. `getLaunchIntentForPackage` asks the platform to
 * resolve this package's `MAIN`/`LAUNCHER` activity and returns an intent whose component is already
 * set to it — that is what makes the result **explicit** (`docs/security-and-privacy.md` §6.4) without
 * this module knowing the activity's class name. `IntentRouter` (ROADMAP M3 T5) does not exist yet, so
 * no extras are added here; the result opens the app on its normal start destination (Today), which is
 * itself the surface this widget mirrors.
 *
 * @return an explicit intent with no extras, or `null` only if the package manager cannot resolve a
 *   launcher activity for this app's own package — a state the widget cannot recover from, so the
 *   caller should treat it as "there is nothing to launch" rather than substitute a guessed intent.
 */
fun launchAppIntent(context: Context): Intent? =
    context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        // The launcher activity is `singleTask` (app/src/main/AndroidManifest.xml); FLAG_ACTIVITY_NEW_TASK
        // is required to start an activity from outside an activity context (the widget's own process).
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
