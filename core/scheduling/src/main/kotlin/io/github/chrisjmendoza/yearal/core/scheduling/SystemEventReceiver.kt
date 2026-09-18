package io.github.chrisjmendoza.yearal.core.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.chrisjmendoza.yearal.core.domain.rollover.DayRolloverTrigger

/**
 * Manifest-declared receiver for the system events after which "today" or the armed alarm may be
 * wrong: layer 2 of `docs/ARCHITECTURE.md` §5 "Midnight rollover (layered)". For each it re-arms
 * [DayRolloverScheduler] and notifies the listeners with the matching [DayRolloverTrigger].
 *
 * | Action | Why a manifest receiver may get it at target 26+ | Trigger |
 * |---|---|---|
 * | `ACTION_TIME_CHANGED` (`android.intent.action.TIME_SET`) | implicit-broadcast exemption list | [DayRolloverTrigger.TIME_CHANGED] |
 * | `ACTION_TIMEZONE_CHANGED` | exemption list | [DayRolloverTrigger.ZONE_CHANGED] |
 * | `ACTION_LOCALE_CHANGED` | exemption list | [DayRolloverTrigger.LOCALE_CHANGED] |
 * | `ACTION_BOOT_COMPLETED` | exemption list; needs `RECEIVE_BOOT_COMPLETED` | [DayRolloverTrigger.BOOT_COMPLETED] |
 * | `ACTION_MY_PACKAGE_REPLACED` | not implicit: sent to this package only | [DayRolloverTrigger.APP_UPDATED] |
 *
 * `ACTION_DATE_CHANGED` is **not** exempt and is deliberately absent; the running app gets it through
 * the context-registered receiver behind `DateTicker`. The app is not Direct Boot aware, so
 * `BOOT_COMPLETED` arrives after the first unlock (`docs/security-and-privacy.md` §2.3).
 *
 * Not exported: all five are protected broadcasts that only the system can send, and the system
 * reaches non-exported receivers (`docs/security-and-privacy.md` §6.3). The action is checked against
 * exactly this set and nothing else is read from the intent; any other action is ignored.
 */
public class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val trigger = triggerFor(intent.action) ?: return
        dispatchRollover(context, trigger)
    }

    internal companion object {
        /** The trigger for a system broadcast [action], or `null` for an action this receiver ignores. */
        fun triggerFor(action: String?): DayRolloverTrigger? =
            when (action) {
                Intent.ACTION_TIME_CHANGED -> DayRolloverTrigger.TIME_CHANGED
                Intent.ACTION_TIMEZONE_CHANGED -> DayRolloverTrigger.ZONE_CHANGED
                Intent.ACTION_LOCALE_CHANGED -> DayRolloverTrigger.LOCALE_CHANGED
                Intent.ACTION_BOOT_COMPLETED -> DayRolloverTrigger.BOOT_COMPLETED
                Intent.ACTION_MY_PACKAGE_REPLACED -> DayRolloverTrigger.APP_UPDATED
                else -> null
            }
    }
}
