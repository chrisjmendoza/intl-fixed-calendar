package io.github.chrisjmendoza.yearal.core.domain.widget

/**
 * The hook through which event and calendar writes keep the home-screen widgets' event markers
 * current (`docs/ARCHITECTURE.md` §5 "Data": "Repository writes call `WidgetUpdater.requestUpdate()`.
 * The interface lives in domain, and the implementation is here as a debounced `updateAll`.";
 * `docs/contracts/Events.md` §5 "Small hooks": "The widget updater is declared separately under
 * `core/domain/…/widget/`."). The production [io.github.chrisjmendoza.yearal.core.domain.event.EventRepository]
 * calls [requestUpdate] after each successful write, exactly like
 * [io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler.reschedule]; the implementation
 * lives in `:widget` (ROADMAP M5 T6).
 *
 * **This is a signal, not a render.** Unlike [io.github.chrisjmendoza.yearal.core.domain.event.ReminderScheduler],
 * [requestUpdate] is deliberately **not** `suspend`: it enqueues a request and returns immediately, so a
 * write never waits on a widget re-render. The implementation **debounces** bursts of calls (for
 * example, several events created in quick succession) into a single re-render, per the architecture
 * doc above, so calling this once per write is always correct and never wasteful.
 *
 * Only this hook is part of the Events contract; how or whether a re-render is scheduled is not.
 */
public fun interface WidgetUpdater {
    /**
     * Signals that event or calendar data changed and any widget that shows it should refresh soon.
     * Idempotent and main-safe: safe to call from inside a write transaction's aftermath, any number of
     * times, from any thread. Carries no event content (CLAUDE.md rule 8) -- it is a plain signal, not a
     * description of what changed.
     */
    public fun requestUpdate()
}
