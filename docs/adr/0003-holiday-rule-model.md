# 0003. Holiday rule model: what the spec left open

- Status: accepted
- Date: 2026-09-17
- Owning doc updated: [holidays-and-import.md](../holidays-and-import.md) §1 status and §2.2 observed policies; [ARCHITECTURE.md](../ARCHITECTURE.md) §3.3

## Context

M1 T8 implements the holiday rule engine of [holidays-and-import.md](../holidays-and-import.md) §2.2 and §5
as `HolidayRule`, `HolidayDefinition`, `HolidaySet`, `HolidayOccurrence` and `HolidayEngine` in
`:core:domain` (package `io.github.chrisjmendoza.yearal.core.domain.holiday`). The spec fixes the rule
taxonomy, the modifiers, the observed policies and the "evaluate Y−1..Y+1" trap, but is silent on a
handful of edge cases that a test must be able to check. Per [WORKFLOW.md](../WORKFLOW.md) §5 they are
decided here rather than left to the implementation.

## Decision

1. **Per-year evaluation is by rule year, not by anchor year.** `HolidayEngine.occurrences(set, year)`
   evaluates every holiday *for* rule year `year` (after `since`/`until`/`yearFilter`). The anchor lies in
   `year` for `Fixed`, `NthWeekday`, `Easter`, `Table` and `Ifc`; `Offset` and `WeekdayRelative` (e.g.
   "Monday on or before Jan 1") may push it into an adjacent year; `durationDays` expansion and the
   observed shift may spill further. The call never returns spill-over *from* other
   rule years. `occurrences(sets, range)` evaluates rule years `range.start.year − 1 .. range.end.year + 1`
   (clamped to 1..9999) and filters by date, which is the §2.2 / §6 rule made concrete. Rules whose result
   drifts more than one year from the rule year (nested offsets of hundreds of days, durations over a
   year) are outside that guarantee and are not validated against; packs must not contain them.
2. **A rule may yield no date in a year, and that is silent.** `Fixed(2, 29)` and a `WeekdayRelative`
   anchored on Feb 29 yield nothing in common years (no move to Feb 28 or Mar 1); an `NthWeekday` ordinal
   the month does not have (5th, −5th) yields nothing; a `Table` year that is absent yields nothing;
   `Ifc.LeapDay` yields nothing in common years (the OMIT policy of §5.3 — the BACKWARD/FORWARD policies
   belong to user recurrences, not to holiday rules); an `Offset` of a rule that yields nothing yields
   nothing.
3. **Observed shift applies to the anchor only.** For a multi-day holiday the extra `observed = true`
   occurrence is computed from day 0 and carries `dayIndex = 0`; later days are never shifted. The actual
   occurrence is always kept (§2.2).
4. **Structural validation throws `IllegalArgumentException` from `init`; year-range errors throw
   `DateTimeException`.** Rule and definition constructors reject impossible fields (month outside 1..12,
   day beyond the month's longest length, `n = 0` or |n| > 5, IFC day outside 1..28, `durationDays < 1`,
   `since > until`, `YearFilter` with `mod < 1` or `eq` outside `0..mod−1`, a `Table` date outside its key
   year, a blank id, a name map without `"en"`, duplicate holiday ids in a set). `occurrences(set, year)`
   throws `DateTimeException` for a year outside `IfcDate.MIN_YEAR..MAX_YEAR`, matching `:core:calendar`,
   for every set — not only when an `ifc` rule happens to be present. The range overload never throws for
   out-of-range dates; it clamps the rule years.
5. **Occurrence dates outside years 1..9999 are dropped** (an observed Jan 1 of year 1 on Dec 31 of year
   0; an offset past Dec 31, 9999), so every returned date converts with `IfcDate.from`.
6. **Ordering is total and documented on `HolidayOccurrence`:** date, then set id, then holiday id, then
   actual before observed, then `dayIndex`. Both engine calls return that order.
7. **Memoisation is per (set id, year) in a `ConcurrentHashMap`, guarded by set equality.** A cache hit
   requires the cached `HolidaySet` to equal the requested one, so a changed pack under the same id is
   re-evaluated rather than served stale. The cache is unbounded and never evicts; `clearCache()` exists
   for tests and pack reloads.
8. **Orthodox Easter shift.** The Julian computus result is built as a proleptic-Gregorian `LocalDate` with
   the same fields and shifted by `⌊Y/100⌋ − ⌊Y/400⌋ − 2` days. This is exact because Julian Easter is
   always after March 1, where the two calendars' month lengths agree until the next century boundary.
9. **Names carry an `"en"` fallback at both levels.** `HolidayDefinition.name` and `HolidaySet.name` must
   contain `"en"`; `nameFor(tag)` does an exact tag lookup with `"en"` fallback and leaves locale
   negotiation (region stripping) to the UI.
10. **The `calendar` rule type is not modelled.** Lunisolar holidays arrive as `Table` rules (FEATURES H4,
    §2.3); adding `calendar` later is a new sealed case and does not change anything above.

## Consequences

- The loader in `:core:holidays` maps JSON onto these constructors and gets validation for free; it must
  surface `IllegalArgumentException` as a pack-load error with the holiday id from the message.
- UI code renders December with a range query, never with `occurrences(set, year)` alone, or it will miss
  the observed New Year (§6). The range overload is the only call the agenda use case should make.
- If a pack ever needs a rule that drifts more than a year (decision 1) or a per-day observed shift
  (decision 3), this ADR is superseded and the engine's neighbour window or expansion changes with it.
- Decision 4 makes `DateTimeException` the only exception a valid pack can raise at evaluation time.
