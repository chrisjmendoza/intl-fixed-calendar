package io.github.chrisjmendoza.yearal.core.domain.event

/**
 * How an [Event] repeats. Exactly one of three shapes, matching the `recurrence_type` column of
 * `docs/ARCHITECTURE.md` §3.2: [None] (0), [Gregorian] (1, an RFC 5545 `RRULE` string) or an
 * [IfcRecurrence] (2, a rule anchored to the International Fixed Calendar).
 *
 * A recurrence is pure data. It is evaluated only by [RecurrenceExpander]; nothing else may derive
 * occurrence dates from it (CLAUDE.md rule 1). Every `when` over this type must handle all three
 * shapes and, inside [IfcRecurrence], the intercalary rule (CLAUDE.md rule 6).
 *
 * **Every recurring event stores which calendar anchors it** (`docs/calendar-spec.md` §7.7): a
 * Gregorian yearly rule and an IFC yearly rule on "the same day" differ by one day in leap years
 * inside the window IFC March 4 – June 28 (`docs/holidays-and-import.md` §5.1).
 *
 * Spec: `docs/ARCHITECTURE.md` §3.2; `docs/contracts/Events.md`; `docs/adr/0005-events-contract.md`.
 */
public sealed interface Recurrence {
    /** The event happens once, on its own start. It has no exdates ([Event.exdates] must be empty). */
    public data object None : Recurrence

    /**
     * A Gregorian recurrence carried as RFC 5545 `RRULE` text, e.g. `FREQ=WEEKLY;BYDAY=MO,WE`. The
     * rule is always evaluated in the Gregorian calendar with the event's start as `DTSTART`, so
     * "monthly on the 15th" is the Gregorian 15th and weekly rules follow the real seven-day week
     * (`docs/holidays-and-import.md` §4.2; FEATURES E7). Any end condition (`UNTIL`, `COUNT`) lives
     * inside the text.
     *
     * **The text is carried, not parsed, by this type.** Construction checks only that it could be
     * stored and exported safely; whether the rule can be evaluated is answered by
     * [RecurrenceExpander.supports], and an unsupported rule expands to the first occurrence only
     * rather than failing (`docs/holidays-and-import.md` §4.2). This keeps the contract free of any
     * RRULE library (`docs/contracts/Events.md` "Out of scope").
     *
     * @property rrule the `RRULE` property *value*: no `RRULE:` property name in front, not blank, no
     *   ISO control characters (so it can never break out of an `.ics` content line).
     * @throws IllegalArgumentException if [rrule] is blank, contains a control character, or starts
     *   with `RRULE:` (any case).
     */
    public data class Gregorian(
        val rrule: String,
    ) : Recurrence {
        init {
            require(rrule.isNotBlank()) { "RRULE text must not be blank" }
            require(rrule.none { it.isISOControl() }) { "RRULE text must not contain control characters" }
            require(!rrule.startsWith(PROPERTY_PREFIX, ignoreCase = true)) {
                "RRULE text is the property value; drop the leading \"$PROPERTY_PREFIX\""
            }
        }

        private companion object {
            const val PROPERTY_PREFIX = "RRULE:"
        }
    }
}
