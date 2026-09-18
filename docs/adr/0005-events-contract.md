# 0005. Events contract: what the docs left open

- Status: accepted
- Date: 2026-09-18
- Owning doc updated: [ARCHITECTURE.md](../ARCHITECTURE.md) §3.2 and §3.4; [holidays-and-import.md](../holidays-and-import.md) §5.2 (pointer); the frozen API is [contracts/Events.md](../contracts/Events.md)

## Context

ROADMAP M4 T1 freezes the Events contract — domain models, `EventRepository`, `RecurrenceExpander`,
`ObserveAgendaUseCase` and their fakes — so that four later tasks (Room storage, the expander, the editor
and the list) can be built in parallel without talking to each other. [ARCHITECTURE.md](../ARCHITECTURE.md)
§3.2 and §3.4 fix the schema and the query shape, [calendar-spec.md](../calendar-spec.md) §7.7 and §7.9 fix
the recurrence semantics, and [holidays-and-import.md](../holidays-and-import.md) §5 fixes the IFC-native
model. They leave a number of points open, and disagree on two. Per [WORKFLOW.md](../WORKFLOW.md) §5 they are
decided here; each decision is checkable by a test or a reviewer.

## Decision

1. **IFC rule text.** ARCHITECTURE owns the data model, so its spelling wins over the `RSCALE=X-IFC;…;SKIP=`
   sketch and the `recurrence_basis` / `recurrence_rule` columns of holidays-and-import §5.2: the columns are
   `recurrence_type`, `rrule`, `ifc_rule`, and the text is `IFC;FREQ=…`. The Leap Day policy keeps the names
   of reconciled decision 4 (`JUNE_28 | SKIP | SOL_1`, which are `BACKWARD | OMIT | FORWARD` of §5.3). The
   grammar is fixed-order and strict (full grammar in [contracts/Events.md](../contracts/Events.md)):
   `IFC;FREQ=YEARLY;MONTH=m;DAY=d`, `IFC;FREQ=YEARLY;INTERCALARY=YEAR_DAY`,
   `IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=<policy>`, `IFC;FREQ=MONTHLY;DAY=d`, then optionally
   `;INTERVAL=n`, then optionally one of `;UNTIL=YYYYMMDD` (Gregorian, inclusive) or `;COUNT=n`.
   - `INTERVAL` defaults to 1 and the canonical form omits it. An explicit `INTERVAL=1` (as one example in
     ARCHITECTURE §3.2 used to show) is the only non-canonical text the parser accepts.
   - `COMMONYEAR` is **required** with `LEAP_DAY` and forbidden otherwise, so data at rest never depends on a
     default policy — the docs themselves disagree on that default (decision 9).
   - Unknown, repeated or reordered keys, lower case, whitespace, signs, leading zeros, non-ASCII digits,
     out-of-range values and text over 128 characters are rejected. `format` never uses locale-sensitive
     formatting, so digits are always ASCII.
   - `.ics` export (1.2) writes this text as `X-IFC-RRULE` next to the Gregorian fallback `RRULE` of
     holidays-and-import §5.5. "Never exported verbatim" in §5.2 meant "never as the `RRULE`"; an `X-`
     property is ignored by other clients, which is the point.
2. **The event's start is occurrence 1 of an IFC rule.** An `Event` with an `IfcRecurrence` must start on a
   position the rule names (same IFC month and day; same day of any month; Year Day; a real Leap Day) and an
   `UNTIL` may not precede the start; construction fails otherwise. The editor derives the rule from the
   picked date (`IfcRecurrence.yearlyOn` / `monthlyOn`), so this costs the UI nothing, and it removes the
   RFC 5545 grey area of a `DTSTART` that does not match its rule. Consequences: `interval` counts from the
   anchor's IFC year (or IFC month index `year × 13 + month − 1`); `COUNT` counts from the anchor, counts
   excluded occurrences (as RFC 5545 does) and does not count rule years that yield nothing (`SKIP` in a common
   year); `UNTIL` is inclusive and compared with the occurrence's own start date. A Leap Day rule is anchored
   only on a real Leap Day — the picker does not offer Leap Day in common years (calendar-spec §7.10) — and
   the June 28 / Sol 1 fallbacks are only ever produced, never chosen as anchors. `monthlyOn` is `null` for
   the intercalary days, which belong to no month (holidays-and-import §5.2).
3. **Durations are nominal wall-clock minutes; ends are exclusive; DST follows RFC 5545 §3.3.5.**
   `duration_minutes` for a timed event is the wall-clock distance from start to end, so a 09:00–10:00 event
   ends at 10:00 on every occurrence and `end_epoch_day` needs no zone rules. An occurrence carries *nominal*
   `startLocal` / `endLocal`; resolution happens in `Occurrence.start/end(deviceZone)` as
   `ZonedDateTime.ofLocal(local, zone, null)`: a wall time in a spring-forward gap happens later by the
   length of the gap (02:30 → 03:30), a wall time in a fall-back overlap is the earlier instant. If
   resolution would put the end before the start, the end is the start. An event that ends exactly at 00:00
   does not touch the next day; a zero-length event is on its start date.
4. **Bucketing is by device-zone date, and the zone skew is two days, not one.** A floating occurrence is
   read in the device zone; a zoned one keeps its instant. All-day occurrences name dates and are never
   shifted. `Occurrence.dates(deviceZone)` is the single definition, used by the expander's range test, the
   agenda use case and the fakes. Zone offsets span UTC−12..UTC+14 (26 hours), so a zoned occurrence can be
   shown two dates away from its own date (00:30 on the 3rd in `Pacific/Kiritimati` is 23:30 on the 1st in
   `Pacific/Pago_Pago`). The candidate queries of ARCHITECTURE §3.4 therefore pad by
   `EventRepository.ZONE_SKEW_DAYS = 2` on both sides, for recurring events too (§3.4 padded non-recurring
   events by one day and recurring events not at all).
5. **Exdates are keyed by the occurrence's own start date** (`Occurrence.occurrenceDate`, the event's zone or
   floating), matching `event_exdates(event_id, epoch_day)` — not by the device-zone date it is shown on. An
   exdate removes every occurrence starting that day. A non-recurring event has no exdates ("delete this
   occurrence" of a one-off event is "delete"), and no exdate precedes the start; both are `Event` invariants.
6. **`recurrence_until_epoch_day` is computed by the expander, not the storage layer.** A `COUNT` or a
   Gregorian `UNTIL` cannot be turned into a date without evaluating the rule, so
   `RecurrenceExpander.recurrenceEndDate(event)` defines the value (last date touched by the last occurrence,
   exdates ignored, `null` = unbounded or unsupported) and the repository implementation asks it on every
   write. The DAO takes the value as a parameter.
7. **The repository owns ids and timestamps; uids come from an injected generator.** `id = 0` means "not
   stored"; `upsert` assigns ids and stamps `createdAt` / `updatedAt` from its injected `Clock` (CLAUDE.md
   rule 2), ignoring what the caller passed. `Event.uid` is never blank; new events take it from
   `EventUidGenerator` so tests are deterministic. An upsert with an unknown non-zero id is an error, not an
   insert.
8. **A built-in local calendar always exists, with a blank name.** `EventCalendar.DEFAULT_ID = 1` is created
   with the database, cannot be deleted or turned into an ICS calendar, and is restored by `deleteAllData`.
   Its name is blank until the user renames it; the UI shows a localized label for a blank name (CLAUDE.md
   rule 9), so the data layer needs no string resource and a language change cannot strand an old label. Its
   colour is brand teal `#123F3D`. Hidden calendars (`visible = false`) drop out of agenda **and** reminder
   candidates.
9. **Leap Day default is `JUNE_28`.** calendar-spec §7.7 recommends "skip" as the default while ARCHITECTURE
   reconciled decision 4 rules `JUNE_28` for user-created events. The reconciled decision is the ruling; the
   model default follows it, and because of decision 1 the stored text is explicit either way.
10. **Text limits are enforced by the model.** `Event` rejects a title over 500 and a description over 10 000
    characters — the caps security-and-privacy §6.1 sets for imported text — and a location or calendar name
    over 500 (one line of text, capped like the title; the docs give no number). Importers truncate *before*
    constructing; the editor limits input to the same constants. A blank title is allowed. `Event.toString()`
    is redacted so that an event in a log line or exception message leaks no content (CLAUDE.md rule 8).
11. **`Recurrence.Gregorian` carries its `RRULE` unvalidated, and an unsupported rule never throws.** The
    contract has no RRULE library dependency; `lib-recur` arrives with the expander (M4 T3). Construction
    checks only that the text is safe to store and export (not blank, no control characters, no `RRULE:`
    prefix). `RecurrenceExpander.supports` says whether a rule can be evaluated; one that cannot expands to
    its first occurrence only (holidays-and-import §4.2: "imports as a single occurrence with a visible
    flag").
12. **Search is an in-memory filter in 1.0**, over `EventRepository.observeEvents()`, so the fake and SQLite
    cannot disagree on case folding. No search DAO is part of the contract.

## Consequences

- T2–T5 can proceed in parallel against [contracts/Events.md](../contracts/Events.md) and the fakes in
  `:core:testing`. Changing anything above needs a new ADR and an update to the contract doc
  ([WORKFLOW.md](../WORKFLOW.md) §4.2 rule 9).
- The Room schema needs no column beyond ARCHITECTURE §3.2. `calendars.name` may be empty.
- An IFC rule cannot express "starts on a date the rule does not name". That is deliberate; an importer that
  meets such an `X-IFC-RRULE` must move the start to the first rule date or drop the rule.
- A per-event cap on exdates, reminders or all-day length beyond the `Int` range is not set here; the import
  milestone (1.2) sets import-side caps per security-and-privacy §6.1.
- Nominal durations mean an overnight event across a fall-back night lasts an hour longer in real time. That
  matches what the user typed (an end time), and is what RFC 5545 does for `DTEND`-based events.
