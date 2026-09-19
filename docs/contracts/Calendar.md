# Calendar contract

Status: **frozen as of M1 T6** (2026-09-18). This is the public API of `:core:calendar` that every other
module builds on — `:core:domain` (events, recurrence, holidays), `:core:designsystem` (formatting, grids,
pickers) and every feature convert through it and never compute a date themselves (CLAUDE.md rule 1).
Changing a public signature, a validation rule, an exception type, or an arithmetic/clamping rule here needs
an ADR and an update to this file ([WORKFLOW.md](../WORKFLOW.md) §4.2 rule 9). The KDoc in the source is the
normative statement of each guarantee; this file is the map and the guide to what "frozen" covers.

- Code: [`core/calendar/…/core/calendar/`](../../core/calendar/src/main/kotlin/io/github/chrisjmendoza/yearal/core/calendar)
  (package `io.github.chrisjmendoza.yearal.core.calendar`): `IfcMonth.kt`, `IfcDate.kt`, `IfcYearMonth.kt`.
- Spec: [calendar-spec.md](../calendar-spec.md) — rules §2, conversion algorithms §3, type model §4, month
  boundary tables §5, test vectors §6, arithmetic semantics §7.7.
- Decisions this contract relies on: [adr/0002-ifc-date-arithmetic.md](../adr/0002-ifc-date-arithmetic.md)
  (overflow and exception-type rules).
- Consumers: `:core:domain` (`IfcRecurrence`, the holiday rule engine), `:core:designsystem`
  (`IfcDateFormatter`, `MonthGrid`, the date pickers), every feature module.

## 1. Ground rules

1. **Pure JVM, `java.time` only, Java 8 API surface** (CLAUDE.md rule 11) — no Android imports, no
   desugaring assumptions. The module is unit-testable on a plain JVM with no Robolectric.
2. **No localization or display formatting lives here.** Month/weekday names, locale-aware numeral
   formatting and the `IFC ` marker in UI copy belong to `:core:designsystem` (`IfcDateFormatter`). This
   module's own `toPrefixedString()`/`toNumericString()` are the canonical machine-readable forms, not
   display strings.
3. **Immutable value types**, totally ordered. `IfcDate` orders by `(year, dayOfYear)`; `IfcYearMonth` by
   `(year, month)`.
4. **No bare `dayOfWeek`.** `IfcDate.nominalDayOfWeek` (nullable — `null` on the two intercalary days) is
   the IFC-assigned weekday; `IfcDate.actualDayOfWeek` (never null) is the real-world weekday from
   `toLocalDate().dayOfWeek`. **Neither is ever derived from the other** — each is computed independently
   from `dayOfMonth` or from the Gregorian date, per calendar-spec §4.1 (CLAUDE.md rule 3). Everything tied
   to real life (today highlighting, events, reminders) must use `actualDayOfWeek`.
5. **The numeric text form always uses ASCII digits**, regardless of the default locale, so
   `IfcDate.parse` can always read back what `toNumericString()` produced (calendar-spec §7.6). **It is
   visually indistinguishable from an ISO-8601 Gregorian date** (`2026-10-08` could be either calendar) —
   anything a user can see MUST call `toPrefixedString()` instead, which prepends the mandatory
   `"IFC "` marker (CLAUDE.md rule 5).
6. **Construction and every factory validate**, and every failure — an invalid field, a `LeapDay` in a
   common year, an out-of-range year, parse syntax, or arithmetic overflow — surfaces as
   `java.time.DateTimeException` (or `DateTimeParseException`, a subtype, from `parse`). **Never**
   `IllegalArgumentException` or a bare `ArithmeticException`: [adr/0002](../adr/0002-ifc-date-arithmetic.md)
   specifically requires arithmetic overflow to be caught and rethrown as `DateTimeException` so that every
   `IfcDate` method has exactly one exception type a caller needs to catch.
7. **Years 1..9999** (`IfcDate.MIN_YEAR`/`MAX_YEAR`), proleptic Gregorian, matching `java.time.LocalDate`'s
   own supported range assumptions (calendar-spec §7.1). UI pickers may offer a narrower range
   (1583–9999, ARCHITECTURE.md reconciled decision 6) with a proleptic-calendar note; the library itself
   accepts the full 1..9999.

## 2. `IfcMonth`

The 13 IFC months as an `enum class`, in calendar order (`JANUARY`..`JUNE`, `SOL`, `JULY`..`DECEMBER`).

- `number: Int` — 1..13. **Trap:** not interchangeable with `java.time.Month.getValue()` for any month
  from `SOL` onward, because `SOL` (7) has no Gregorian counterpart and shifts every later month's number
  up by one relative to its Gregorian namesake (calendar-spec §2.2).
- `gregorianNamesake: Month?` — the `java.time.Month` of the same *name*, for looking up localized display
  names only; `null` for `SOL`. It says nothing about which Gregorian dates the IFC month actually covers.
- `IfcMonth.of(number)` — `@throws DateTimeException` if `number` is not in 1..13.
- Constants: `DAYS_PER_MONTH = 28`, `MONTHS_PER_YEAR = 13`.

## 3. `IfcDate`

A `sealed interface` with exactly three implementations, so the compiler forces every `when` to handle the
intercalary days (CLAUDE.md rule 6):

| Type | Meaning | Construction throws when |
|---|---|---|
| `Regular(year, month, dayOfMonth)` | One of the 364 ordinary days, inside a month and a week | `year` out of range, or `dayOfMonth` not in 1..28 |
| `LeapDay(year)` | The day after June 28 in leap years (Gregorian June 17); no week, no weekday | `year` out of range, or `year` is not a leap year |
| `YearDay(year)` | The day after December 28, last day of every year (Gregorian December 31); no week, no weekday | `year` out of range |

Common surface (every implementation):

- `year: Int` — always equal to the Gregorian year of the same physical day.
- `dayOfYear: Int` — 1..365/366, always equal to `toLocalDate().dayOfYear`.
- `monthNumber: Int` — 1..13. Pseudo-field for numeric formatting/parsing/sorting/month arithmetic only;
  intercalary days report the month they are attached to by convention (`LeapDay` → 6, `YearDay` → 13),
  they do not "belong" to that month for weekday or week purposes.
- `dayOfMonth: Int` — 1..28 for `Regular`; **29** for both intercalary days ("June 29", "December 29" in
  numeric/pseudo-field contexts only — never a real week day 29).
- `nominalDayOfWeek: DayOfWeek?` — see §1.4. `null` for `LeapDay`/`YearDay`.
- `actualDayOfWeek: DayOfWeek` — see §1.4. Never null.
- `weekOfYear: Int?` — IFC week 1..52 for `Regular`; `null` for intercalary days. **Not** an ISO-8601 week
  number.
- `quarter: Int` — 1..4. Intercalary days count in the quarter they follow (`LeapDay` → 2, `YearDay` → 4);
  this makes Q2 92 days long in leap years and Q4 always 92 days long (calendar-spec §5.3).
- `isIntercalary: Boolean` — `true` only for `LeapDay`/`YearDay`.
- `toLocalDate(): LocalDate` — the same physical day in the Gregorian calendar. Total; never throws for a
  validly-constructed `IfcDate`.
- `toNumericString()` / `toPrefixedString()` — see §1.2 and §1.5.
- `compareTo` — chronological order by `(year, dayOfYear)`.
- `daysUntil(other): Long` — `ChronoUnit.DAYS.between` on the Gregorian equivalents; positive when `other`
  is later. `other == this.plusDays(daysUntil(other))` always holds.

### Factories (`IfcDate` companion)

| Factory | Contract |
|---|---|
| `from(date: LocalDate): IfcDate` | Total over the supported range (calendar-spec §3.1). `@throws DateTimeException` if `date.year` is outside 1..9999. |
| `of(year, monthNumber, dayOfMonth): IfcDate` | From the numeric pseudo-fields (calendar-spec §3.3 rule V5). Day 29 resolves to `LeapDay` only for month 6 in a leap year, or to `YearDay` for month 13 in any year; every other day-29 combination throws. **No lenient/overflow resolution** — an invalid combination is an error, never silently rolled into the next month. |
| `ofYearDay(year, dayOfYear): IfcDate` | The day-of-year number is identical in both calendars. |
| `now(clock: Clock): IfcDate` | **Trap:** "today" is a function of both an instant and a zone (calendar-spec §7.8); this factory exists so the app never has a reason to call `LocalDate.now()` directly outside the injected `Clock` (CLAUDE.md rule 2). |
| `parse(text: CharSequence): IfcDate` | Accepts the canonical numeric form with or without the `IFC ` prefix. `@throws DateTimeParseException` for a syntax mismatch, `DateTimeException` for a syntactically valid but out-of-range/invalid field combination. |

### Arithmetic

Full semantics and the worked-example table are in calendar-spec §7.7; [adr/0002](../adr/0002-ifc-date-arithmetic.md)
settles the exception type. Summary:

- `plusDays`/`minusDays`, `plusWeeks`/`minusWeeks` — real Gregorian day/week arithmetic (`LocalDate.plusDays`
  under the hood). Exact and reversible. **Trap:** a real week does **not** preserve the nominal weekday
  across an intercalary day — `Regular(2024, JUNE, 25).plusWeeks(1)` is `Regular(2024, SOL, 3)` (nominal
  Tuesday, not Wednesday), because users live in the real week.
- `plusMonths`/`minusMonths`, `plusYears`/`minusYears` — computed on the pseudo-fields
  `(year, monthNumber, dayOfMonth)` with `java.time`-style clamping: day 29 clamps to 28 whenever the target
  month has no 29th (every month except June in a leap year, and December). Regular days (1..28) never
  clamp and always keep their nominal weekday. **Trap:** clamped steps are not reversible or associative,
  exactly like `LocalDate.plusMonths` (`Leap Day 2024 + 1 month` = `Sol 28, 2024`, but that result minus one
  month is `June 28, 2024`, not back to Leap Day).
- **Every arithmetic method throws only `DateTimeException`**, for a result year outside 1..9999 or for
  underlying arithmetic overflow (never a bare `ArithmeticException`) — the one exception type callers need
  to catch from any `IfcDate` method, matching the construction/parse contract in §1.6.

## 4. `IfcYearMonth`

One IFC month of one IFC year — the unit the month grid and event range queries are built on
(calendar-spec §2.2–§2.4; ARCHITECTURE.md §3.1).

- `IfcYearMonth(year, month)` — `@throws DateTimeException` if `year` is out of range.
- `firstDay: IfcDate.Regular` — the 1st, always a nominal Sunday.
- `lastRegularDay: IfcDate.Regular` — the 28th, always a nominal Saturday.
- `trailingIntercalary: IfcDate?` — `LeapDay` after June in a leap year, `YearDay` after December,
  otherwise `null`. **Every month has at most one; no month has more than one.**
- `gregorianRange: ClosedRange<LocalDate>` — 28 days, or 29 including `trailingIntercalary`.
  **Always contiguous** — safe to drive a single range-scoped storage query from (this is exactly how
  `:core:domain`'s agenda queries and holiday evaluation scope their range reads).
- `days: List<IfcDate>` — the 28 regular days in order, followed by `trailingIntercalary` if present.
- `actualDayOfWeek(column: Int): DayOfWeek` — the real-world weekday of grid column `column` (0 = the
  nominal-Sunday column, 6 = nominal-Saturday). **Constant down the column**, because a month is exactly
  four whole weeks (calendar-spec §3.1 "Weekday insight for the UI"). `@throws IllegalArgumentException`
  (not `DateTimeException` — this is a UI-facing precondition on an `Int`, not a calendar-validity check) if
  `column` is not in 0..6.
- `plusMonths(months: Long): IfcYearMonth` — rolls over year boundaries. `@throws DateTimeException` if the
  result falls outside 1..9999 or on arithmetic overflow.
- `IfcYearMonth.from(date: IfcDate): IfcYearMonth` — the month whose grid shows `date`. An intercalary day
  belongs to the month it follows (Leap Day → June, Year Day → December), matching `IfcDate.monthNumber`'s
  convention.

## 5. What "frozen" means here

Frozen at the end of M1 means: public class/interface names, function and property signatures, validation
rules (§1.6, §1.7), the exception type contract (`DateTimeException`/`DateTimeParseException` only), the
canonical numeric text format (`YYYY-MM-DD`, `06-29` for Leap Day, `13-29` for Year Day) and the arithmetic
clamping rules in §3 "Arithmetic" do not change without an ADR and an update to this file. Internal
implementation (how `from`/`of` compute their result, private constants) may change freely as long as every
guarantee above still holds — the golden vectors in calendar-spec §6 and the exhaustive round-trip test are
what actually enforce this in CI.
