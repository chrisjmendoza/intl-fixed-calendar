# CLAUDE.md

Android app for the International Fixed Calendar (IFC; the owner also says "FC"): 13 months × 28 days,
the month Sol, plus Year Day and Leap Day. Solo developer working with AI agents.

**Status: planning complete, no code yet.** The next step is milestone M0 in `docs/ROADMAP.md`.
Update this file when the scaffold lands (real build commands, module list).

## Read before working

| Working on | Read first |
|---|---|
| Anything involving dates | `docs/calendar-spec.md` (rules §2, algorithms §3, type model §4, semantics §7, vectors §6) |
| Structure, stack, data model, widgets, tests, CI | `docs/ARCHITECTURE.md` — start with "Reconciled decisions" |
| What to build and its priority | `docs/FEATURES.md` |
| What to build next | `docs/ROADMAP.md` |
| Holidays, device calendars, `.ics` | `docs/holidays-and-import.md` |
| Permissions, backups, intents, exports, releases | `docs/security-and-privacy.md` |

Each doc is authoritative for its own topic (table at the top of ARCHITECTURE.md). If two docs
disagree, the authoritative one wins — fix the other in the same change.

## Rules that must not be broken

1. **No date is computed outside `:core:calendar`.** Every IFC date in the app, widgets, and
   notifications comes from that module. It is pure Kotlin/JVM with zero Android dependencies.
2. **Never call `LocalDate.now()`, `Instant.now()`, or `System.currentTimeMillis()`** outside the
   injected `Clock` binding. No epoch-millisecond date arithmetic.
3. **There is no bare `dayOfWeek` on IFC types.** Use `nominalDayOfWeek` (IFC, null on floating days)
   or `actualDayOfWeek` (real world). Never derive one from the other. Anything tied to real life —
   today highlight, events, reminders — uses the actual weekday.
4. **Dates are stored Gregorian** (epoch day / ISO). IFC is a view. The only IFC data at rest is
   IFC-anchored recurrence rules.
5. **IFC numeric dates always carry the `IFC` prefix** in anything a user can see, and are never
   formatted locale-style (`10/08/2026`). IFC month numbers 8–13 do not match Gregorian ones.
6. **Year Day and Leap Day must be handled in every `when`, picker, formatter, widget, and test.**
   Leap Day exists only in leap years.
7. **No `INTERNET` permission, analytics, ads, crash SDKs, Firebase, or Play Services.** No new
   permission without updating `docs/security-and-privacy.md`.
8. **No event content in logs.** Intent extras and `PendingIntent`s carry IDs only.
9. **All user-visible strings live in resources**, including "Sol".
10. **Module boundaries:** features depend on `:core:domain` interfaces, never on `:core:data` or on
    other features. Cross-feature navigation goes through `:core:navigation` keys.

## API generations — easy to get wrong

This project is on newer library lines than most training data:

- Room is **Room 3**: package `androidx.room3`, KSP-only, coroutines-only, `SQLiteDriver`. Not `androidx.room`.
- Navigation is **Navigation 3** (`androidx.navigation3`), not Navigation Compose.
- AGP 9 with the new DSL and built-in Kotlin: Android modules do **not** apply `org.jetbrains.kotlin.android`.
- Pinned versions live in `gradle/libs.versions.toml` (from ARCHITECTURE.md §1). Do not bump AGP,
  Kotlin, or KSP casually; the M0 spike result is in `docs/adr/0001-toolchain.md`.

## Environment (Windows, PowerShell)

`java` is not on PATH. Before any Gradle command in a fresh shell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Expected gate once the scaffold exists: `./gradlew spotlessCheck lint test verifyRoborazziDebug :app:assembleDebug`.
Fast loop for the pure modules: `./gradlew :core:calendar:test :core:domain:test :core:holidays:test`.
Screenshot goldens are recorded only in CI (Linux); locally use `compareRoborazziDebug`.

## Working conventions

- One task = one PR = one module owner. Parallel agents never share a module; use a git worktree each.
- Contract-first: interfaces and fakes (`:core:testing`) land before implementations, and frozen
  contracts are documented in `docs/contracts/`.
- Tests use hand-written fakes, not a mocking library. Inject a fake `Clock`; include a
  midnight-crossing case for anything that shows "today".
- The calendar core's implementation and its brute-force test oracle are written by different
  agents on purpose, so they do not share bugs.
- Decisions that change the architecture get a short ADR in `docs/adr/`.
