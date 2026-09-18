# CLAUDE.md

Android app for the International Fixed Calendar (IFC; the owner also says "FC"): 13 months × 28 days,
the month Sol, plus Year Day and Leap Day. Solo developer working with AI agents.

**Status:** planning complete; Gradle skeleton and `:core:calendar` (pure JVM) exist. No Android
modules yet — those start with the M0 toolchain spike in `docs/ROADMAP.md`, which needs SDK Platform 37
and the SDK command-line tools installed first.

## Workflow — mandatory

**`docs/WORKFLOW.md` is binding.** In short: read the docs below first; tests and KDoc land in the same
change as the code; owning docs are updated in the same push as the behaviour; run the gate and report the
real result; end every task with the completion report from WORKFLOW.md §6. Never weaken a test, edit an
expected value, or bypass a gate to get to green.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat check                 # compile (warnings = errors), tests, ktlint, KDoc gate — all modules
.\gradlew.bat :core:calendar:test   # fast loop for one module
.\gradlew.bat spotlessApply         # fix formatting
python scripts\check_docs.py        # doc link check
```

`java` is not on PATH, so the first line is required in every fresh shell.

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
5. **IFC numeric dates always carry the `IFC` prefix** in anything a user can see
   (`IfcDate.toPrefixedString()`), and are never formatted locale-style (`10/08/2026`). IFC month
   numbers 8–13 do not match Gregorian ones.
6. **Year Day and Leap Day must be handled in every `when`, picker, formatter, widget, and test.**
   Leap Day exists only in leap years.
7. **No `INTERNET` permission, analytics, ads, crash SDKs, Firebase, or Play Services.** No new
   permission without updating `docs/security-and-privacy.md`.
8. **No event content in logs.** Intent extras and `PendingIntent`s carry IDs only.
9. **All user-visible strings live in resources**, including "Sol".
10. **Module boundaries:** features depend on `:core:domain` interfaces, never on `:core:data` or on
    other features. Cross-feature navigation goes through `:core:navigation` keys.
11. **Pure-JVM modules may only use Java 8 APIs from `java.*`.** They ship inside an app with
    minSdk 26, where the JDK classes are Android's, and a JVM build cannot detect a missing method.
    Known traps: `LocalDate.ofInstant` (use `instant.atZone(zone).toLocalDate()`), `Optional.isEmpty`,
    `List.of`/`Map.of`/`Set.of`, `String.isBlank/strip/repeat`, `Stream.toList`, `InstantSource`.
    Kotlin stdlib equivalents are fine. Android Lint's `NewApi` check catches the rest once an Android
    module depends on these.
12. **Spec tables and golden files are inputs.** `SpecVectorsTest` reads `docs/calendar-spec.md` §6;
    never edit those tables to match the code.

## API generations — easy to get wrong

This project is on newer library lines than most training data:

- Room is **Room 3**: package `androidx.room3`, KSP-only, coroutines-only, `SQLiteDriver`. Not `androidx.room`.
- Navigation is **Navigation 3** (`androidx.navigation3`), not Navigation Compose.
- AGP 9 with the new DSL and built-in Kotlin: Android modules do **not** apply `org.jetbrains.kotlin.android`.
- Tests in pure-JVM modules are **JUnit 6** (Jupiter) with Kotest used as a library, not as the runner.
- Versions live in `gradle/libs.versions.toml`. Do not bump Kotlin, AGP, or KSP casually; toolchain
  changes get an ADR (`docs/adr/`).

## Build layout

- `build-logic/convention` — convention plugins. `ifc.jvm.library` = explicit API, warnings as errors,
  JUnit 6 + Kotest, Spotless/ktlint, Dokka with undocumented-public-API as a build failure.
- `core/calendar` — `IfcMonth`, `IfcDate`, `IfcYearMonth`. Base package `io.github.chrisjmendoza.fixedcal`.
- Screenshot goldens (later) are recorded only in CI (Linux); locally use `compareRoborazziDebug`.

## Working conventions

- **Local work: no branches, no pull requests** — commit straight to `main` in small signed commits, and
  run the gate before every push. **Cloud work (scheduled routines): branch `cloud/<task>` and open a
  PR** for the owner's review; never push to `main` from the cloud. One task = one module owner.
  Parallel agents never share a module; use a git worktree each. Local commits are GPG-signed; never
  bypass signing.
- Contract-first: interfaces and fakes (`:core:testing`) land before implementations, and frozen
  contracts are documented in `docs/contracts/`.
- Tests use hand-written fakes, not a mocking library. Inject a fake `Clock`; include a
  midnight-crossing case for anything that shows "today".
- Correctness-critical code and its oracle tests are written by different agents on purpose; the test
  author works from the spec, not the implementation.
- Decisions that change the architecture get a short ADR in `docs/adr/`.
