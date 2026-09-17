# 0002. IfcDate arithmetic: overflow surfaces as DateTimeException

- Status: accepted
- Date: 2026-09-17
- Owning doc updated: `docs/calendar-spec.md` §7.7

## Context

M1 T6b adds `plusDays`/`minusDays`, `plusWeeks`/`minusWeeks`, `plusMonths`/`minusMonths` and
`plusYears`/`minusYears` to `IfcDate` (`docs/calendar-spec.md` §7.7). The day/week operations delegate to
`LocalDate.plusDays`; the month/year operations do `Math.addExact`/`floorDiv`/`floorMod` on the pseudo-fields,
mirroring `IfcYearMonth.plusMonths`.

Section §7.7 specifies the arithmetic and the clamping rules in full, but is silent on what happens for
amounts that overflow the underlying `Long` arithmetic (for example `Long.MAX_VALUE` days, or
`Long.MIN_VALUE` months). `java.time` itself is inconsistent here on purpose: `LocalDate.plusDays` throws
`ArithmeticException` from `Math.addExact` on overflow, while every existing `IfcDate` entry point
(`of`, `from`, `ofYearDay`, the constructors — spec §3.3) throws only `DateTimeException` for anything
invalid, including out-of-range years. Letting `ArithmeticException` escape from just the new arithmetic
methods would be a second, undocumented exception type on the same type, which is exactly the kind of
silent divergence WORKFLOW.md §5 asks to resolve rather than carry forward.

## Decision

Every `IfcDate` arithmetic method catches `ArithmeticException` raised by the underlying overflow-checked
`java.time`/`Math` operation (`LocalDate.plusDays`, `Math.multiplyExact`, `Math.addExact`) and rethrows it
as `DateTimeException`. Combined with the existing `requireYear` check (which already rejects a result
outside `IfcDate.MIN_YEAR..MAX_YEAR`), this means **every** `IfcDate` method — arithmetic included — throws
only `DateTimeException`, never `ArithmeticException`, for any input that cannot produce a valid date.

`minusDays`/`minusWeeks`/`minusMonths`/`minusYears` negate their argument by delegating to the matching
`plusX`, using the same `Long.MIN_VALUE` handling as `LocalDate.minusDays` et al.
(`if (n == Long.MIN_VALUE) plusX(Long.MAX_VALUE).plusX(1) else plusX(-n)`) rather than negating directly,
so negating `Long.MIN_VALUE` never overflows by itself; the two-step addition still overflows (and still
throws `DateTimeException`, per the above) once the amount plus one day/week/month/year no longer fits in
the supported year range.

## Consequences

- Callers only ever need to catch one exception type from any `IfcDate` method, matching the existing
  contract documented in spec §3.3.
- Test coverage must include `Long.MIN_VALUE`/`Long.MAX_VALUE` amounts and near-range-limit amounts for
  every arithmetic method (`IfcDateArithmeticTest`), since these are the only inputs that exercise the
  catch clauses.
- If a future `Period`-style difference (§7.7, "if ever needed") is added, it should follow the same rule:
  `DateTimeException` only.
