package io.github.chrisjmendoza.yearal.feature.converter

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

/*
 * Copy and share of a conversion (docs/FEATURES.md D4). Both need no permission. The text is the
 * converted date the user asked to send — it is not event content (CLAUDE.md rule 8) — and it always
 * carries the "IFC" marker and the Gregorian date (docs/calendar-spec.md §7.3, §7.9). Nothing is logged.
 */

/**
 * Puts [text] on the clipboard as plain text under [label].
 *
 * @return `false` when the device has no clipboard service.
 */
internal fun copyConversion(
    context: Context,
    label: String,
    text: String,
): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    return true
}

/**
 * Offers [text] to other apps with an `ACTION_SEND` `text/plain` intent behind the system chooser. The
 * intent is implicit on purpose and carries only [text]; no URI, no file, no grant flags.
 *
 * @return `false` when nothing on the device can show the chooser.
 */
internal fun shareConversion(
    context: Context,
    text: String,
): Boolean {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(send, null)
    // Only an Activity context may start an activity without a new task.
    if (context.findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
