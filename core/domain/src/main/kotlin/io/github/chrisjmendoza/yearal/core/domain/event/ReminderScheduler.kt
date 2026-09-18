package io.github.chrisjmendoza.yearal.core.domain.event

/**
 * The hook through which event writes keep the reminder alarm current. Reminders use a single
 * next-alarm pattern: only the earliest upcoming reminder is scheduled, and it is recomputed on
 * every event write, on boot, on time or zone changes and after an app update
 * (`docs/ARCHITECTURE.md` §3.2 "Reminders"). The production [EventRepository] calls [reschedule]
 * after each successful write; the implementation lives in `:core:scheduling` (ROADMAP M6 T1).
 *
 * Only this hook is part of the Events contract; how the next alarm is found is not
 * ([EventRepository.getReminderCandidates] and [RecurrenceExpander.nextOccurrence] are its inputs).
 */
public fun interface ReminderScheduler {
    /**
     * Recomputes the earliest upcoming reminder and replaces the scheduled alarm with it, or cancels
     * the alarm when there is none. Idempotent, main-safe, and never throws for an empty store.
     * Intents it creates carry ids only, never event content (CLAUDE.md rule 8).
     */
    public suspend fun reschedule()
}
