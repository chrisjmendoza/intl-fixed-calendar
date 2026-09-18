package io.github.chrisjmendoza.yearal.core.domain.rollover

/**
 * Something that shows or schedules against "today" outside a running screen and must therefore be
 * told when the local date, or the way it is displayed, may have changed: the home-screen widgets
 * (M5) and the reminder scheduler (M6).
 *
 * The Android day-rollover scheduler (`:core:scheduling`) calls every listener in the app's
 * dependency-injection multibinding (`Set<DayRolloverListener>`, which may be empty) after the
 * midnight alarm and after each system event in [DayRolloverTrigger]. Screens do not use this; they
 * collect [io.github.chrisjmendoza.yearal.core.domain.DateTicker].
 *
 * **Trap: a call is a hint, not a fact.** The alarm behind it may be delivered minutes late (the
 * windowed fallback, Doze) and a [DayRolloverTrigger.TIME_CHANGED] may move the clock backwards, so an
 * implementation must **recompute "today" from the injected [java.time.Clock] and
 * [io.github.chrisjmendoza.yearal.core.domain.ZoneProvider] every time it is called** and never
 * assume from the [DayRolloverTrigger] that the date advanced by one, or changed at all. Being called
 * twice for one real change is harmless and must be treated as such (idempotent).
 *
 * Spec: `docs/ARCHITECTURE.md` §5 "Midnight rollover (layered)"; `docs/FEATURES.md` S3, Q2, Q10, Q11.
 */
public fun interface DayRolloverListener {
    /**
     * Refreshes whatever this listener owns (re-render widgets, recompute the next reminder alarm).
     *
     * Called off the main thread, inside the short lifetime of a broadcast (a few seconds): do the
     * refresh and return; work that is not finished by then is cancelled. A listener that throws
     * does not stop the other listeners or the re-arming of the midnight alarm, but the failure is
     * rethrown afterwards rather than hidden.
     *
     * @param trigger why the call happened. For choosing *what* to refresh only (for example, a
     *   [DayRolloverTrigger.LOCALE_CHANGED] changes texts but not the date) — **never** a source for
     *   the date itself.
     */
    public suspend fun onDayRollover(trigger: DayRolloverTrigger)
}

/**
 * Why a [DayRolloverListener] is being called. Each value corresponds to one layer-1 or layer-2
 * source in `docs/ARCHITECTURE.md` §5 "Midnight rollover (layered)".
 */
public enum class DayRolloverTrigger {
    /** The once-a-day alarm set for the next local midnight was delivered (possibly late). */
    MIDNIGHT,

    /** The user or the network set the wall clock (`ACTION_TIME_SET`); the date may have moved either way. */
    TIME_CHANGED,

    /** The device time zone changed (`ACTION_TIMEZONE_CHANGED`); the local date may differ in the new zone. */
    ZONE_CHANGED,

    /** The device locale changed (`ACTION_LOCALE_CHANGED`); the date is the same but its text is not. */
    LOCALE_CHANGED,

    /**
     * The device finished booting and was unlocked (`ACTION_BOOT_COMPLETED`). Every alarm was lost
     * with the reboot, and any number of days may have passed.
     */
    BOOT_COMPLETED,

    /**
     * A new version of the app was installed over the old one (`ACTION_MY_PACKAGE_REPLACED`), which
     * also cancels the app's alarms.
     */
    APP_UPDATED,
}
