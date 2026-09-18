# Events contract

Status: **frozen as of M4 T1** (2026-09-18). This is the API that ROADMAP M4 T2–T10, M5 T6 and M6 T1 build
against. Changing anything here needs an ADR and an update to this file ([WORKFLOW.md](../WORKFLOW.md) §4.2
rule 9). The KDoc in the source is the normative statement of each guarantee; this file is the map, the
rule-text grammar, and the guide to the fakes.

- Code: [`core/domain/…/event/`](../../core/domain/src/main/kotlin/io/github/chrisjmendoza/yearal/core/domain/event)
  (package `io.github.chrisjmendoza.yearal.core.domain.event`).
- Fakes and fixtures: [`core/testing/…/testing/`](../../core/testing/src/main/kotlin/io/github/chrisjmendoza/yearal/core/testing).
- Schema and query shape: [ARCHITECTURE.md](../ARCHITECTURE.md) §3.2 and §3.4.
- Decisions the docs left open: [adr/0005-events-contract.md](../adr/0005-events-contract.md).
- Features: E1, E3, E5–E10 and W6 in [FEATURES.md](../FEATURES.md).

## 1. Ground rules

1. **Dates are Gregorian** (`LocalDate`, `LocalDateTime`). The only IFC data is inside `IfcRecurrence`
   (CLAUDE.md rule 4). Features convert with `IfcDate.from` for display and never compute a date (rule 1).
2. **Year Day and Leap Day are ordinary dates here**: December 31, and June 17 of a leap year. Every `when`
   over `Recurrence`, `IfcRecurrence` or `IntercalaryDay` handles the intercalary case (rule 6).
3. Everything tied to real life uses Gregorian values and the actual weekday; nothing in this contract knows
   a nominal IFC weekday (rule 3).
4. No `now()`: the repository gets a `Clock`, the agenda use case a `ZoneProvider` (rule 2).
5. **No event content in logs.** `Event.toString()` is redacted; exception messages carry ids, dates and
   lengths only (rule 8).
6. Construction validates. Every model throws `IllegalArgumentException` from `init` for an impossible value,
   and `copy` re-validates. `IfcRecurrence.yearlyOn` / `monthlyOn` throw `DateTimeException` for a date outside
   years 1..9999, like `:core:calendar`.
7. Pure JVM, Java 8 `java.*` APIs only (rule 11). No RRULE library is part of the contract (§6).

## 2. Models

| Type | What it is | Invariants worth knowing |
|---|---|---|
| `Event` | The aggregate: the `events` row plus its exdates and reminders | `id ≥ 0` (`0` = not stored), `calendarId > 0`, `uid` not blank; title ≤ 500, description ≤ 10 000, location ≤ 500 characters (title may be blank); start and `endDate` within years 1..9999; no exdates when not recurring, none before the start; an `IfcRecurrence` is anchored on the start and its `UNTIL` is not before it; `updatedAt ≥ createdAt`. `createdAt` / `updatedAt` are owned by the repository. `category` is a label and is not cross-validated. |
| `EventTiming` | `AllDay(startDate, days)` or `Timed(startDate, startMinuteOfDay, durationMinutes, zone?)` | Makes "`start_minute_of_day` is NULL iff all-day" and "all-day is always floating" unrepresentable. `days` 1..`MAX_DAYS`; minute 0..1439; duration ≥ 0, **nominal wall-clock minutes**. `zone == null` = floating. `EventTiming.timed(start, end, zone)` builds one from two pickers. |
| `EventCategory` | `EVENT`, `OBSERVANCE` (user holiday), `BIRTHDAY` | |
| `Reminder` | `minutesBefore ≥ 0`; a `Set` on the event | Timed: before the resolved start. **All-day: before the all-day reminder time (09:00 by default) on the first date, device zone.** No id: a reminder is (event id, occurrence date, minutesBefore). |
| `EventCalendar` | `id`, `name`, `colorArgb`, `source` (`LOCAL`/`ICS`), `visible` | The built-in calendar `DEFAULT_ID = 1` always exists, has a **blank name** until renamed (UI shows a localized label for blank), brand-teal colour, and stays `LOCAL`. |
| `Recurrence` | `None`, `Gregorian(rrule)`, or an `IfcRecurrence` | `Gregorian` carries the RFC 5545 `RRULE` *value*, unparsed: not blank, no control characters, no `RRULE:` prefix. |
| `IfcRecurrence` | `YearlyOnDate(month, day)`, `YearlyOnIntercalary(day)`, `MonthlyOnDay(day)`, each with `interval ≥ 1` and `end` | `day` 1..28; `month` is an `IfcMonth` (**July is 8**). `isAnchoredOn(date)`, `toRuleText()`, builders `yearlyOn(date, …)` and `monthlyOn(date, …)` (`null` on Year Day and Leap Day). |
| `IntercalaryDay` | `YearDay` or `LeapDay(commonYearPolicy)` | The policy exists only where it means something. |
| `LeapDayPolicy` | `JUNE_28` (default), `SKIP`, `SOL_1` | FEATURES E6; ARCHITECTURE reconciled decision 4. |
| `RecurrenceEnd` | `Never`, `Until(date)` (Gregorian, inclusive, years 1..9999), `Count(n ≥ 1)` | |
| `Occurrence` | `eventId`, nominal `startLocal`, exclusive `endLocal`, `zone?`, `allDay` | `occurrenceDate` (the exdate key), `lastDate`, and the resolution functions `start(deviceZone)`, `end(deviceZone)`, `dates(deviceZone)` — §4. |
| `AgendaEntry` | `event` + `occurrence` + resolved `colorArgb` + `deviceZone` | `start`, `end`, `firstDate`, `lastDate`, `isAllDay`. |
| `DayAgenda` | `date`, `entries`, `holidays` for one device-zone date | Constructor checks membership and order; `DayAgenda.of` sorts. `ENTRY_ORDER`: all-day first, then resolved start instant, then event id, then nominal start. Holidays in `HolidayOccurrence` order. |

### IFC recurrence semantics (what T3 implements and its oracle tests check)

- **The event's start is occurrence 1**, and it is always a position of the rule (validated by `Event`).
  Every occurrence keeps the anchor's time of day, nominal length, zone and all-day flag.
- The rule is evaluated on the event's **own wall-clock dates** (its zone, or floating), never on device
  dates.
- `YearlyOnDate(m, d)`: rule years `anchorYear + k × interval`; the occurrence is
  `IfcDate.Regular(year, m, d).toLocalDate()`. Inside IFC March 4 – June 28 that is one Gregorian day
  earlier in leap years ([holidays-and-import.md](../holidays-and-import.md) §5.1).
- `YearlyOnIntercalary(YearDay)`: December 31 of every rule year.
- `YearlyOnIntercalary(LeapDay(policy))`: in a leap rule year, Leap Day (June 17). In a common rule year —
  including 2100, 2200, 2300 — `JUNE_28` gives IFC June 28 (Gregorian June 17), `SKIP` gives nothing,
  `SOL_1` gives Sol 1 (June 18).
- `MonthlyOnDay(d)`: IFC month index `year × 13 + (monthNumber − 1)` advances by `interval` from the
  anchor's; the occurrence is day `d` of that month. 13 a year at interval 1; **never** Year Day or Leap Day.
- `Until(date)`: inclusive, compared with `occurrenceDate`. `Count(n)`: n occurrences from the anchor;
  excluded occurrences count, rule years that yield nothing do not.
- Occurrences are built by direct construction in `:core:calendar`, O(1) per year. Nothing exists after
  year 9999.

## 3. IFC rule text (`ifc_rule` column, `X-IFC-RRULE` property)

Implemented once, in `IfcRuleText` (`format`, `parse`, `parseOrNull`, `ICS_PROPERTY`, `MAX_LENGTH`).
Storage and export must use it and nothing else.

```text
rule      = "IFC" ";" position [ ";INTERVAL=" number ] [ ";UNTIL=" date / ";COUNT=" number ]
position  = "FREQ=YEARLY;MONTH=" month ";DAY=" day
          / "FREQ=YEARLY;INTERCALARY=YEAR_DAY"
          / "FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=" ( "JUNE_28" / "SKIP" / "SOL_1" )
          / "FREQ=MONTHLY;DAY=" day
month     = 1..13        ; IFC month number, Sol = 7, July = 8. NOT a Gregorian month number.
day       = 1..28
number    = 1..2147483647
date      = 8DIGIT       ; Gregorian YYYYMMDD, years 0001..9999, inclusive
```

- Segments appear in **exactly this order**, separated by `;`, upper-case ASCII, no whitespace, no empty
  segment, no trailing `;`. Numbers are ASCII digits with no sign and no leading zero.
- `INTERVAL` defaults to 1; the canonical form omits `INTERVAL=1`. `UNTIL` and `COUNT` exclude each other.
- `COMMONYEAR` is **required** with `LEAP_DAY` and **forbidden** anywhere else.
- `parse(format(rule)) == rule` for every rule. `format(parse(text)) == text` for every accepted text except
  one that spells out `INTERVAL=1`, which is the only non-canonical input accepted.
- Everything else throws `IllegalArgumentException` (`parseOrNull` returns `null`): unknown, repeated or
  reordered keys; a key that does not belong to the position; lower case; out-of-range or malformed numbers;
  an `UNTIL` that is not a real Gregorian date; both `UNTIL` and `COUNT`; text longer than 128 characters.
- The text names positions only. The anchor date, time, zone and exdates live on the `Event`.

| Rule | Text |
|---|---|
| Every Sol 13 | `IFC;FREQ=YEARLY;MONTH=7;DAY=13` |
| Every Year Day | `IFC;FREQ=YEARLY;INTERCALARY=YEAR_DAY` |
| Every Leap Day, June 28 in common years | `IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=JUNE_28` |
| Leap years only, ten times | `IFC;FREQ=YEARLY;INTERCALARY=LEAP_DAY;COMMONYEAR=SKIP;INTERVAL=4;COUNT=10` |
| The 13th of every other IFC month until the end of 2030 | `IFC;FREQ=MONTHLY;DAY=13;INTERVAL=2;UNTIL=20301231` |

`.ics` export (release 1.2) writes the text as `X-IFC-RRULE` together with the Gregorian fallback `RRULE`
of [holidays-and-import.md](../holidays-and-import.md) §5.5.

## 4. Occurrences, time zones and daylight saving

`Occurrence` holds **nominal** wall-clock values exactly as the rule produced them. Its three functions are
the single definition of resolution and bucketing for the expander, the agenda, reminders and the fakes:

- **Floating** (`zone == null`) is read in the device zone passed in. **Zoned** is read in its zone and shown
  in the device zone.
- A wall time in a **gap** happens later by the length of the gap (02:30 in a 02:00→03:00 gap is 03:30). A
  wall time in an **overlap** is the **earlier** instant. (`ZonedDateTime.ofLocal(local, zone, null)`;
  RFC 5545 §3.3.5.)
- `endLocal` is **exclusive**, resolved the same way, and never before the start.
- Durations are **nominal**: 22:00–06:00 over a fall-back night still ends at 06:00.
- `dates(deviceZone)` — **the bucketing rule**: all-day occurrences cover their own dates and are never
  shifted; a timed occurrence covers the date of its resolved start through the date of the last instant
  before its resolved end, so an event ending at 00:00 is not on the next day and a zero-length event is on
  its start date.
- `occurrenceDate` (own start date) is the **exdate key** and the date `UNTIL` is compared with. It can
  differ from the date the occurrence is shown on by up to `EventRepository.ZONE_SKEW_DAYS = 2`.

## 5. Interfaces

### `EventRepository` (implemented in `:core:data`, T2 + T6)

- Calendars: `observeCalendars`, `getCalendar`, `upsertCalendar`, `deleteCalendar` (cascades to events; the
  built-in calendar cannot be deleted or made `ICS`).
- Events: `observeEvents` (all calendars, `Event.LIST_ORDER`), `observeEvent(id)`, `getEvent(id)`,
  `upsertEvent` (whole aggregate; id `0` inserts; an unknown non-zero id, an unknown calendar or a duplicate
  uid throws), `deleteEvent`.
- One occurrence: `addExdate(eventId, occurrenceDate)` / `removeExdate` — `false` when there is nothing to
  do, `IllegalArgumentException` for a non-recurring event or a date before the start.
- Range queries: `observeAgendaCandidates(range)` and `getReminderCandidates(from)` return a **superset**
  from visible calendars, widened by `ZONE_SKEW_DAYS` on both sides; callers decide exactly with the
  expander. The SQL predicates are in the KDoc and in [ARCHITECTURE.md](../ARCHITECTURE.md) §3.4.
- `deleteAllData()` (W6): removes everything and restores `EventCalendar.DEFAULT`. Settings, caches and
  alarms are cleared by the caller of the "Delete all data" action.
- **Threading:** every `suspend` function is main-safe; flows are cold, emit the current value first, never
  complete, may re-emit equal values. **Atomicity:** each write is all-or-nothing and writers are serialised.
  **Errors:** a broken precondition throws `IllegalArgumentException` and changes nothing; a missing delete
  target returns `false`. **Ids** are positive and not reused while the data lives. **Timestamps** come from
  the injected `Clock`; `updatedAt` is never before `createdAt`.
- After each successful write the production implementation calls the widget updater and
  `ReminderScheduler.reschedule()`. Callers never do.
- Search (E9) is an in-memory filter over `observeEvents()` in 1.0; there is no search query.

### `RecurrenceExpander` (implemented in `:core:domain`, T3 — interface only here)

- `expand(event, range, deviceZone)`: every occurrence whose `dates(deviceZone)` **intersects** `range`,
  exdates removed, ordered by `startLocal`. The test is on touched dates, not on the start, so a multi-day
  occurrence that began before the range is included, once.
- `nextOccurrence(event, from)`: the first remaining occurrence with `occurrenceDate ≥ from`, or `null`.
- `recurrenceEndDate(event)`: the `recurrence_until_epoch_day` value — last date touched by the last
  occurrence, exdates ignored; `null` if unbounded or unsupported; `event.endDate` when not recurring.
- `supports(recurrence)`: `false` for an `RRULE` the implementation cannot evaluate. Such an event expands to
  its first occurrence only and never throws.
- Pure, synchronous, thread-safe, no clock, no logging of content; work bounded by the range and the event
  length. Occurrences carry nominal times (a 02:30 daily event is listed at 02:30 on the gap day).
  `Gregorian` rules use the event start as `DTSTART` and are evaluated in the Gregorian calendar on the
  event's own wall clock; weekly means the real seven-day week.

### `ObserveAgendaUseCase` (implemented in T6)

- `invoke(range): Flow<Map<LocalDate, DayAgenda>>` — keys are **only** the dates of `range` that have an
  entry or a holiday, in ascending order; a multi-day occurrence is the same `AgendaEntry` under every date
  it touches; hidden calendars and excluded occurrences never appear; holidays come from the enabled sets.
- `presence(range): Flow<Set<LocalDate>>` — the dates with at least one **event** occurrence (holidays not
  counted): the Year view's bitmap.
- Reads the device zone from `ZoneProvider` at every recomputation; re-emits on event, calendar and
  holiday-set changes; works off the main thread; an empty range gives an empty result.

### Small hooks

- `EventUidGenerator` (`RandomEventUidGenerator` in production) — inject it wherever an event is created.
- `ReminderScheduler.reschedule()` — the hook the repository calls; the implementation is M6 T1. The widget
  updater is declared separately under `core/domain/…/widget/`.

## 6. Out of scope for 1.0

- **Per-occurrence overrides** ("edit this occurrence", an `event_overrides` table, `RECURRENCE-ID`). 1.0 has
  edit all, delete all, and delete one occurrence (FEATURES E11 is later).
- **Attendees, invitations, sync** (E12).
- `RDATE`, multiple `RRULE`s per event, and an "every IFC Friday" nominal-weekday rule
  ([calendar-spec.md](../calendar-spec.md) §7.7).
- **Any RRULE library in the contract.** `Recurrence.Gregorian` carries text; `lib-recur` is added by T3 as
  an implementation detail of the expander, behind `supports`.
- `.ics` import/export, read-only ICS calendars, snooze, a reminder-time setting: the types leave room for
  them, nothing here implements them.

## 7. How to build against the fakes (T2–T5 and later)

All in `:core:testing` (`testImplementation(project(":core:testing"))`; feature modules already have it).

| You need | Use |
|---|---|
| A repository | `FakeEventRepository(clock = MutableClock(…))` — in-memory, flow-backed, ids 1, 2, 3 …, same exceptions as the contract. `seed(EventFixtures.all())` stores the nine fixtures as ids 1..9. `currentEvents` / `currentCalendars` for assertions. Its range queries never prune recurring events (a legal superset). |
| Events | `EventFixtures`: `yearDayYearly`, `leapDayYearly(policy = …)` for each policy, `sol13Yearly`, `thirteenthMonthly`, `timedZoned` (New York, DST day, one reminder), `weeklyGregorian`, `floatingMultiDay` (Dec 30 – Year Day – Jan 1), `allDay(…)` as a base, plus `SOL_13_2026`, `YEAR_DAY_2026`, `LEAP_DAY_2024`, `LEAP_DAY_2028`. |
| An agenda in a ViewModel test | `FakeObserveAgendaUseCase`: `putEntry(EventFixtures.entry(event))` (placed on every date it touches), `putHoliday`, `setAgendas`, `clear`; `requestedRanges` records what the screen asked for. |
| Occurrences without the real expander | `FakeRecurrenceExpander`: `scriptDates(event, dates)` or `script(eventId, occurrences)`; it applies range, exdates, ordering, `nextOccurrence`, `recurrenceEndDate`. **An unscripted recurring event throws**, on purpose. `markUnsupported(recurrence)` stages an unsupported RRULE. It is not an oracle for T3. |
| Uids, the reminder hook | `FakeEventUidGenerator` (`uid-1`, `uid-2`, …), `FakeReminderScheduler.rescheduleCount`. |

Per task:

- **T2 (Room 3, `:core:data`):** columns exactly as ARCHITECTURE §3.2. `ifc_rule` is
  `IfcRuleText.format(rule)` / `IfcRuleText.parse(text)`; `rrule` is `Recurrence.Gregorian.rrule`;
  `recurrence_type` 0/1/2. `start_minute_of_day`, `duration_minutes`, `zone_id` and `all_day` come from
  `EventTiming`; `end_epoch_day` is `Event.endDate`. `recurrence_until_epoch_day` is a **parameter** of the
  write (T6 passes `RecurrenceExpander.recurrenceEndDate(event)`); do not compute it in the mapper. Candidate
  queries pad by `EventRepository.ZONE_SKEW_DAYS`. `calendars.name` may be empty; row 1 is created with the
  database. `calendar_id` deletes cascade. Order lists with
  `ORDER BY start_epoch_day, start_minute_of_day, id`. A stored row that no longer satisfies the model
  invariants is a corrupted row: fail soft (skip it), never crash a flow, never log its text.
- **T3 (expander):** implement `RecurrenceExpander` only; §2 "IFC recurrence semantics", §4 and the KDoc are
  the spec. The oracle tests are written by a different agent from those, not from your code.
- **T4 (editor):** build the `Event` with `EventTiming.timed(start, end, zone)` or `EventTiming.AllDay`;
  derive IFC rules with `IfcRecurrence.yearlyOn(date, leapDayPolicy)` / `monthlyOn(date)` and hide
  "monthly (IFC)" when it returns `null`; ask for the Leap Day policy only when `yearlyOn` returns
  `YearlyOnIntercalary(LeapDay)`; limit text fields with `Event.MAX_*_LENGTH`; take the uid from
  `EventUidGenerator`; when the start or the rule changes, drop exdates before saving. Catch
  `IllegalArgumentException` from `upsertEvent` (deleted meanwhile) and fail soft.
- **T5 (list):** `observeEvents()` plus an in-memory search; join `observeCalendars()` for colour and
  visibility; show a blank title and a blank calendar name with localized placeholders.
- **T6–T8:** "delete this occurrence" passes `entry.occurrence.occurrenceDate` to `addExdate`, never the
  date of the cell that was tapped.
