package io.github.chrisjmendoza.yearal.feature.converter

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

/*
 * Copy and share of a conversion (docs/FEATURES.md D4). Both need no permission. The text handed in is
 * always `ConverterScreen`'s `ConvertedResult`, built from `R.string.converter_share_text` as literally
 * `IFC {ifcLong} ({numeric}) = Gregorian {gregorianLong}` — e.g. "IFC September 8, 2026
 * (IFC 2026-10-08) = Gregorian Thursday, September 17, 2026" — so it always carries the "IFC" marker
 * (via {numeric}'s mandatory prefix, CLAUDE.md rule 5) and the Gregorian date (docs/calendar-spec.md
 * §7.3, §7.9). This file only moves that text to the clipboard or the share sheet; it neither builds nor
 * reformats it, and it is not event content (CLAUDE.md rule 8). Nothing is logged.
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
