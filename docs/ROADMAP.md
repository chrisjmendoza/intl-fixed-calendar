# Roadmap

Status: **current as of M0 and M1 complete; M2, M3, M4 and M5 in progress** (2026-09-18). Milestones sequence the work described in
[FEATURES.md](FEATURES.md) using the structure in [ARCHITECTURE.md](ARCHITECTURE.md).

## Release map

| Release | Contents | Milestones |
|---|---|---|
| 0.1 (Play internal track) | Read-only calendar: Today, month grid, day detail, settings | M0 – M2 |
| **1.0** | Converter, year view, Learn, events with IFC recurrence, Today + Month widgets, reminders, built-in holidays. **No calendar permission, no network.** | M3 – M6, M8 |
| 1.1 | Read-only device-calendar overlay (`READ_CALENDAR`, opt-in); agenda widget + widget privacy mode; Quick Settings tile; app shortcuts | M7a |
| 1.2 | `.ics` import/export, file backup/restore (optionally passphrase-encrypted), app lock | M7b |
| 1.3 | `.ics` subscription by URL — first use of `INTERNET`; Data safety re-audit | M7c |
| Later | More holiday packs, translations, date math, Wear OS, F-Droid, KMP library extraction | — |

Owner, 2026-09-18: there are no testers for a while, so `main` is built feature by feature and 0.1 is cut from whatever `main` holds when a tester build is wanted; features that are not finished end to end say so in the UI (the event editor labels reminders as arriving later, until M6).

## Progress ledger

Updated in the push that finishes each item ([WORKFLOW.md](WORKFLOW.md) §4.2).

| Milestone | Task | State |
|---|---|---|
| M0 | T1 repo, `.gitignore`, Gradle wrapper 9.7.1 | ✅ done |
| M0 | T1 owner setup: `JAVA_HOME`, 2FA, repo security settings | ⬜ owner (SDK Platform 37 was installed by AGP during T3; cmdline-tools are not needed by the build) |
| M0 | T2 `settings.gradle.kts`, version catalog, `build-logic` — all convention plugins | ✅ done: `ifc.jvm.library`, `ifc.android.library`, `ifc.android.compose`, `ifc.hilt`, `ifc.android.application`, `ifc.android.feature`, plus `ifc.kotlin.serialization` and `ifc.room` |
| M0 | T3 toolchain spike + ADR 0001 | ✅ done ([adr/0001-toolchain.md](adr/0001-toolchain.md)); the Kotlin 2.4 bump is a follow-up task |
| M0 | T4 module stubs plus the dependency rule check | 🟡 partial: `:app`, `:core:designsystem`, `:core:navigation`, `:core:scheduling`, `:feature:calendar`, `:feature:converter`, `:feature:events`, `:widget` exist; the rule check is in `ifc.android.feature`; remaining modules are created by the task that first needs them |
| M0 | T5 `:app` shell (Hilt application, MainActivity, edge-to-edge, Nav3 with 5 tabs) | ✅ done: per-tab back stacks, `NavigationSuiteScaffold`, debug APK builds |
| M0 | T6 Spotless/ktlint, `.editorconfig`, Android Lint config | ✅ done (lint `warningsAsErrors`, version-nag checks off) |
| M0 | T7 `ci.yml` (SHA-pinned, read-only token, wrapper validation), Dependabot | ✅ done: builds and uploads the debug APK; `scripts/check_manifest_permissions.py` checks the merged debug manifest against the allow-list table in [security-and-privacy.md](security-and-privacy.md) §5 |
| M0 | T8 `CLAUDE.md`, workflow rules, ADR template, CHANGELOG | ✅ done; `SECURITY.md` and LICENSE pending owner decisions |
| M1 | T1 `IfcMonth`, `IfcDate`, conversion | ✅ done |
| M1 | T2 independent definitional oracle (years 1–9999) | ✅ done |
| M1 | T3 `IfcYearMonth` layout, ranges, `actualDayOfWeek(column)` | ✅ done |
| M1 | T4 parse and canonical numeric text | ✅ done |
| M1 | T5 spec-driven vector test, validation and property tests | ✅ done |
| M1 | T6 KDoc gate | ✅ done; the public API is frozen and documented in [contracts/Calendar.md](contracts/Calendar.md) |
| M1 | T6b date arithmetic on `IfcDate` (plus/minus days, weeks, months, years; calendar-spec §7.7) | ✅ done |
| M1 | T7 `Clock`/`ZoneProvider`/`DateTicker` interfaces plus fakes (`:core:domain`, `:core:testing`) | ✅ done |
| M1 | T8 holiday rule engine (`:core:domain`) | ✅ done ([adr/0003-holiday-rule-model.md](adr/0003-holiday-rule-model.md)); reviewed independently with published-table oracles (Easter 1900–2100, OPM 2020–2030) |
| M1 | T9 holiday JSON packs and loader (`:core:holidays`: IFC, US, Easter family) | ✅ done ([adr/0004-holiday-pack-format.md](adr/0004-holiday-pack-format.md)); lunisolar `calendar` rules are H4 |
| M2 | T1 `:core:designsystem` theme | ✅ done: `IfcTheme` with dynamic colour (API 31+, user-switchable) and the brand palette (teal / cream / amber) as the fallback |
| M2 | T3 `:core:data`: settings (`UserSettings` in a typed DataStore + `SettingsRepository`) | ✅ done |
| M2 | T4 `MonthGrid`, `DayCell`, `IntercalaryBand`, dual headers, previews | ✅ done; goldens are recorded by T10 |
| M2 | T2 `:core:navigation` keys, `Navigator`, tab back stacks in `:app` | ✅ done |
| M2 | T5 `IfcDateFormatter` and string resources | ✅ done |
| M2 | T6 Today | 🟡 partial: hero date, both weekdays, day/week/quarter, year progress, countdown; agenda and next-holiday follow M4/M6 |
| M2 | T7 Month pager (`:feature:calendar`) | ✅ done: swipeable 1583–9999, holiday marks from the enabled packs, Today action; title→Year zoom is M3 T2 |
| M2 | T8 Day detail | ✅ done as a compact bottom sheet (both dates, both weekdays, day/week/quarter, holidays); the expanded-width pane is M3 T4, "Add event"/"Open in converter" arrive with M4/M3 |
| M2 | T9 `:feature:settings` (weekday display, theme, dynamic colour, holiday packs) and the More hub | ✅ done |
| M2 | T10 `record-screenshots.yml` plus the first goldens | 🟡 partial: the workflow exists (`workflow_dispatch`, records on `ubuntu-latest`, uploads the goldens as an artifact for a signed local commit — never a bot commit). No screenshot test exists yet, so there are no goldens and no `verifyRoborazziDebug` gate; the first screenshot task also points `roborazzi.outputDir` at a tracked directory ([ARCHITECTURE.md](ARCHITECTURE.md) §6 "Goldens") |
| M2 | T13 visual polish pass (Today, Month, Year, Day detail, widgets) | ⬜ after the foundation. Owner notes from device testing (2026-09-18/19): (1) the dark theme reads as plain black and white and is uninviting — bring the brand palette (teal / cream / amber) and colour into the surfaces; (2) the month grid floats mid-screen with dead space above and below — anchor it and use the space; (3) the Month screen shows the month and year twice (app bar and grid heading) — keep one; (4) nothing signals that the app-bar title opens the Year view — add an affordance (chevron, chip or a dedicated action); (5) the Year view's mini-months are blank squares — consider day numbers or at least weekday structure, and check the top edge where a row is clipped under the app bar; (6) the Month widget's today highlight is a filled pill, not the app's ring. Design reference for later: FEATURES C10, a Gregorian month grid with the IFC date inset (the reverse of our grid), as an optional view. Deliberately sequenced after events, converter and widgets The external review's UI notes are accepted into this task with verdicts in [reviews/2026-09-19-astra-analysis-response.md](reviews/2026-09-19-astra-analysis-response.md): secondary Gregorian text in cells, a selected-date summary under the grid, a dedicated Year Day / Leap Day treatment and accent, tighter Year tiles, large widgets that show more instead of whitespace, the "actual" vs "Gregorian" weekday label question. Acceptance test for the pass: unfamiliar users can answer "what Gregorian date is this?" and "when will this event happen?" |
| M3 | T1 `:feature:converter` (D1, D2, D4) | ✅ done: two-way converter with a direction switch; Material date picker and the reusable `IfcDatePicker` in `:core:designsystem` (1583–9999, Year Day always, Leap Day only in leap years, clamps to June 28); proleptic note up to 1923; copy / share with the IFC marker; round-trips on property-generated dates in the ViewModel and through the screen. "Open in converter" from Day detail is T5 |
| M3 | T2 Year overview (`:feature:calendar`) | ✅ done: 13 mini-months (one `Canvas` each, one TalkBack node per tile) plus Year Day in `LazyVerticalGrid(Adaptive(160dp))`, Leap Day marked inside June in leap years, one `ObserveAgendaUseCase.presence` query per year, previous/next/Today clamped to 1583–9999. Also: Month title → Year, jump to date in either calendar (C7), Day detail "Open in converter" (the UI half of T5), tappable agenda rows on Today |
| M3 | T3 Learn/About (`:feature:settings`, L2) | ✅ done: six sections and an expandable FAQ answering the documented confusions; every worked-example date is computed through `:core:calendar` (`LearnFacts`), never a literal. First-run intro (L1) and contextual info icons (L3) are still to come |
| M2 | T12 privacy policy, in-app Privacy screen, Data safety form | 🟡 partial: the in-app Privacy screen is done (every claim sourced from [security-and-privacy.md](security-and-privacy.md) and the merged manifest; no policy URL yet). The hosted policy and the Play Data safety form remain, with T11 |
| M4 | T1 Events contract: domain models, `EventRepository` / `RecurrenceExpander` / `ObserveAgendaUseCase` interfaces, IFC rule text, fakes | ✅ done: frozen in [contracts/Events.md](contracts/Events.md) ([adr/0005-events-contract.md](adr/0005-events-contract.md)); the expander implementation is T3. T2–T5 are unblocked and parallel |
| M4 | T2 `:core:data`: Room 3 schema, DAOs, mappers and JVM tests | ✅ done: schema v1 exported (`core/data/schemas/`) per ARCHITECTURE §3.2; `RoomEventRepository` passes the same 36-case contract suite as `FakeEventRepository`; FK cascade, rollback, file-backed reopen and the M4 benchmark (about 1 ms for the month query with 1,000 events) have their own tests. Room's query context must be a real dispatcher, never a `TestDispatcher` (it deadlocks; see the KDoc of `RoomEventRepositoryContractTest`) |
| M4 | T3 `:core:domain`: `RecurrenceExpander` implementation | ✅ done: `DefaultRecurrenceExpander` — IFC rules by direct construction, `lib-recur` 0.17.1 for `RRULE`, a 10,000-occurrence work cap; rulings in [adr/0005-events-contract.md](adr/0005-events-contract.md) Amendment 1. **T3b** the independent oracle/property suite (`RecurrenceExpanderOracleTest`, written without reading the implementation) ✅ done, no discrepancy found |
| M4 | T4 `:feature:events` editor | ✅ done for 1.0 scope: either-calendar date pickers, all-day/timed, device or fixed zone, yearly IFC / yearly Gregorian / monthly IFC / weekly rules, Leap Day policy, end condition, reminder chips (stored only), process-death survival, unsaved-changes guard. Not in it: multi-day *timed* events, an interval control, a full zone picker, a calendar chooser. The editor's app-bar icon buttons could not be measured at 48dp under Robolectric — check on a device in M8 T1 |
| M4 | T5 `:feature:events` list and search | ✅ done: both dates and the `IFC` numeric form on every row, recurrence summaries, hidden-calendar flag, in-memory search over title, notes and location |
| M4 | T6 real repository + agenda use case wiring | ✅ done: `RoomEventRepository`, `DefaultRecurrenceExpander`, the UID generator and `DefaultObserveAgendaUseCase` are bound in `:app`; the use case combines the repository candidates, the expander, `HolidayEngine` and the new domain-level `HolidaySetProvider` (real implementation `PackHolidaySetProvider` in `:feature:calendar`, fake in `:core:testing`). `ReminderScheduler` is the real one since M6 T1. Follow-ups: `HolidayCatalog` still evaluates holidays for its labels (a second evaluation path to fold into `DayAgenda.holidays`), and the use case reads the zone fresh on each recomputation rather than re-emitting on a zone change by itself |
| M4 | T7 month-grid dots, Day detail agenda, Today agenda | ✅ done: event dots and spoken counts on the month grid (through the existing `DayMarks`), Day detail lists the day's agenda (all-day first, tap → editor, "Add event"), Today shows today's agenda, today's holidays and the next holiday, live across midnight. Today's agenda rows are not tappable yet (`TodayRoute` has no `Navigator`) |
| M4 | T8 exdates ("delete this occurrence") and the edit-all flow | ✅ done: Day detail offers "Delete this occurrence" (long-press or a TalkBack custom action, confirm, undo) keyed on the occurrence's own start date, and plain delete for one-off events; the editor states that saving or deleting acts on the whole series, shows the count of individually deleted occurrences and can restore them. Per-occurrence edits stay out of 1.0 |
| M4 | T9 encrypted-only Auto Backup rules and "Delete all data" | ✅ done: both backup XML files already matched [security-and-privacy.md](security-and-privacy.md); `BackupRulesTest` pins them to the real database and settings file names. Settings has a two-step "Delete all data" that clears events and calendars (which also cancels the reminder alarm and refreshes the widgets) and resets settings |
| M5 | T1 `:widget` Today widget | ✅ done (Glance 1.2.0): IFC date, Gregorian equivalent and actual weekday at three responsive sizes, dynamic colour with the brand fallback, "today" recomputed from `Clock`/`ZoneProvider` on every render, refreshed by the M5 T2 rollover hook plus a 4-hour `updatePeriodMillis` backstop, tap opens the app. `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` (merged by WorkManager) are stripped; `WAKE_LOCK` is allow-listed. **Not yet checked on a device** — see the M5 T8 matrix |
| M5 | T3 Month-grid widget | ✅ done: the current IFC month as a 4×7 grid with today highlighted (filled pill + bold; Glance has no border modifier), the Leap Day / Year Day band, two responsive sizes (the larger adds the nominal header row and the Gregorian span), one merged content description, the same refresh path as the Today widget (`WidgetRolloverListener`, renamed). Event dots on the widget are T6 |
| M5 | T5 widget-picker previews | ✅ done: real static `previewLayout` mock-ups (API 31–34), a vector `previewImage` below that, and `providePreview` / `setWidgetPreview` on API 35+ behind a (version, locale) guard, registered at app start. **The API 35+ path is not yet checked on a device** |
| M5 | T6 `WidgetUpdater`: widgets follow event changes | ✅ done: `WidgetUpdater` (`:core:domain`) is called by `RoomEventRepository` after every write and debounced (1 s) in `:widget`; the Month widget marks days with events from a bounded, fail-safe `ObserveAgendaUseCase.presence` snapshot — presence only, never titles |
| M5 | T8 manual device test matrix | 🟡 started: [device-test-matrix.md](device-test-matrix.md) collects the on-device checks each task could not automate; none has been run yet |
| M6 | T1 `ReminderScheduler` (next-alarm pattern), notification channel, re-arm hooks | ✅ done: `AlarmReminderScheduler` in `:core:scheduling` — one alarm for the earliest upcoming reminder, recomputed on every event write, every `DayRolloverTrigger`, each delivery and at process start (`AppStartup`); pure `ReminderPlanner` (all-day at 09:00 device zone, work bounded per event); `VISIBILITY_PRIVATE` with a redacted public version; posting skipped but re-armed when `POST_NOTIFICATIONS` is denied; late reminders fire within 15 minutes and are dropped after. The in-context permission request lives in the editor. Not in it: tap routing to the event (`IntentRouter`), snooze, a configurable all-day time, a user-facing hint when exact alarms are revoked |
| M6 | T3 exact-alarm permissions, Play declaration, the `canScheduleExactAlarms()` branch | ✅ done: `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`) declared and allow-listed; one shared `armWakeup` gives the rollover and the reminder alarm the exact branch where allowed and the 10-minute window otherwise; `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` re-arms both. **Owner action before the first upload:** paste [security-and-privacy.md](security-and-privacy.md) §5.4 into Play Console → App content → Exact alarm permission |
| M5 | T2 `:core:scheduling`: `DayRolloverScheduler`, alarm + system-event receivers, `DayRolloverListener` hook | ✅ done: one windowed (10 min) `RTC_WAKEUP` alarm at next local midnight + 1 s, re-armed at process start and by non-exported manifest receivers (TIME_SET, TIMEZONE_CHANGED, LOCALE_CHANGED, BOOT_COMPLETED, MY_PACKAGE_REPLACED); listeners are a Hilt `Set<DayRolloverListener>` (empty until M5 T1 / M6 T1); adds `RECEIVE_BOOT_COMPLETED`; the exact-alarm branch lands with the permission in M6 T3 |

### Review-driven fixes (from [reviews/2026-09-19-astra-analysis-response.md](reviews/2026-09-19-astra-analysis-response.md))

These come before new features: they are trust problems in code that already ships on `main`.

| Task | What | State |
|---|---|---|
| R1 | Android `DateTicker` layer in `:app` (re-emit on resume and on context-registered `TIME_SET` / `TIMEZONE_CHANGED` / `DATE_CHANGED`, as ARCHITECTURE §4 specifies) and an observable zone so `DefaultObserveAgendaUseCase` re-emits on a zone change; test a zone change that crosses a date boundary with a screen open | ⬜ |
| R2 | Event editor: disable Save while persisting, and keep one stable UID per draft so a repeated save cannot create a second event | ⬜ |
| R3 | Event editor: when the start moves to Year Day or Leap Day under a monthly-IFC rule, do not silently save a one-off — reset the choice visibly and say why | ⬜ |
| R4 | One-line explanations at the recurrence choice ("weekly" is seven real days and ignores intercalary days; yearly on the IFC date vs on the Gregorian date) | ⬜ |
| R5 | Convert tab icon: swap / opposing arrows instead of the refresh-like arrow | ⬜ |
| R6 | First screenshot baseline (the rest of M2 T10): tracked `roborazzi` output directory, preview-scanner captures for `:core:designsystem` and the main screens, goldens recorded in CI, `verifyRoborazziDebug` in the gate | ⬜ |

Owner setup still open: set `JAVA_HOME` / `ANDROID_HOME` at user level (ARCHITECTURE.md → Development
environment) and ideally update Android Studio to Quail 4. Turning on 2FA/passkeys for the GitHub
and Google Play accounts is the one security task that should happen now.

**Conventions:** each task is one agent session and names the module it owns, so parallel agents never share a module. Local sessions work on a `local/<task>` branch and merge to `main` only once the owner says so; cloud/scheduled sessions work on a `cloud/<task>` branch and open a pull request instead ([WORKFLOW.md](WORKFLOW.md) §1). Tasks sharing a group letter run in parallel. Use git worktrees, one per agent. Effort is in focused days for the owner plus agents, not calendar dates.

## M0 Scaffold and toolchain (about 2 to 3 days). Blocks everything except M1-A.

**Goal:** an empty app launches, CI is green, and every module exists as a stub.

**Tasks:**

- **T1 (serial):** machine setup.
  - Set `JAVA_HOME` and `ANDROID_HOME`.
  - Install `platforms;android-37` and cmdline-tools.
  - ~~`git init`, `.gitignore`, public GitHub repo~~ (done during planning).
  - Bootstrap the Gradle wrapper (9.7.1).
  - Enable 2FA/passkeys on the GitHub and Google accounts; turn on secret scanning with push protection, private vulnerability reporting, and and (optionally) a `main` branch rule that blocks force-pushes and deletion.
- **T2 (serial):** `settings.gradle.kts`, `gradle/libs.versions.toml` from the ARCHITECTURE.md §1 tables, and `build-logic` with the 6 plugins.
- **T3 (serial): toolchain spike.**
  - Build a hello-world on AGP 9.3.3 defaults (new DSL, built-in Kotlin) with Hilt (KSP), Room 3 (KSP) and Roborazzi, first on Kotlin 2.3.21.
  - Record the outcome in `docs/adr/0001-toolchain.md`.
  - Try Kotlin 2.4.20 and keep it only if it is green.
  - Resolve the unverified flags from ARCHITECTURE.md §1.
- **[A]:**
  - T4: module stubs plus the dependency rule check.
  - T5: `:app` shell (Hilt application, MainActivity, edge-to-edge, empty Nav3 with 5 tabs).
  - T6: Spotless, Lint config and `.editorconfig`.
  - T7: `ci.yml`, with actions pinned by commit SHA, a read-only `GITHUB_TOKEN`, Gradle wrapper validation, and a merged-manifest permission allow-list check. Dependabot config for `gradle` and `github-actions`.
  - T8: `CLAUDE.md` and `AGENTS.md` (build commands, env vars, module ownership rules, the "Room is `androidx.room3`" note, the "never `LocalDate.now()`" rule), a `docs/contracts/` skeleton, `SECURITY.md`, and the LICENSE once the owner has chosen one.

**Exit:** `./gradlew spotlessCheck lint test assembleDebug` is green locally from PowerShell and in CI, the app launches on an API 26 emulator and an API 36 emulator, and the ADR is written.

## M1 Calendar core (about 2 to 3 days). Group A starts alongside M0 once T2 lands.

**Goal:** `:core:calendar` is complete, exhaustively tested and API-frozen.

**Tasks:**

- **[A]:**
  - T1: `IfcMonth`, `IfcDate` and conversion arithmetic.
  - T2: the independent brute-force oracle plus the golden CSV in `:core:testing`. Give this to a different agent than T1 on purpose, so the two do not share bugs.
- **[B] (after T1):**
  - T3: `IfcYearMonth`, `IfcYear` layout, ranges and `realDayOfWeek`.
  - T4: parse and canonical text.
  - T5: exhaustive and property test suite.
  - T6: KDoc plus `docs/contracts/Calendar.md`.
- **[C] (parallel, `:core:domain` and `:core:holidays`):**
  - T7: `Clock`, `ZoneProvider` and `DateTicker` interfaces plus fakes.
  - T8: `HolidayRule` engine and tests.
  - T9: holiday-set JSON schema, plus the IFC and US Federal sets.

**Exit:** 100% branch coverage on conversion, the exhaustive round trip over years 1 to 9999 passes, every vector in calendar-spec §6 passes (including the negative vectors), the public API is reviewed and declared stable with `explicitApi`, and the holiday tables match published data.

**Depends on:** the M0 T2 skeleton only.

## M2 Read-only calendar UI, the walking skeleton (about 4 to 5 days). Needs M0 and M1.

**Tasks:**

- **[A]:**
  - T1 `:core:designsystem`: theme, dynamic color and typography.
  - T2 `:core:navigation`: keys, Navigator and tab back stacks in `:app`.
  - T3 `:core:data`: settings only (DataStore `UserSettings` plus repository).
- **[B] (after T1):**
  - T4: `MonthGrid`, `DayCell`, `IntercalaryBand` and dual headers, with previews and the Roborazzi matrix.
  - T5: `IfcDateFormatter` and string resources.
- **[C] (after B, all in `:feature:calendar`, so one agent or strictly separate files):**
  - T6: Today.
  - T7: Month pager.
  - T8: Day detail sheet.
- **[C'] (parallel):** T9 `:feature:settings` (weekday display, theme), and T10 `record-screenshots.yml` plus the first goldens.
- **T11:** `release.yml` (builds the tag, no signing key in CI), the offline upload key, Play App Signing and the Play Console app. Needs the app-name and applicationId decisions.
- **T13:** visual polish pass on Today, Month and Day detail, after the foundation milestones (owner request, 2026-09-18).
- **T12:** privacy policy on GitHub Pages, in-app Privacy screen, and the Data safety form. Play requires these before any track, including internal and closed testing.

**Exit:**

- Opening the app shows today's date.
- Swiping months shows the correct bands for June 2028 and for December.
- Tapping a day shows its Gregorian equivalent.
- A TalkBack pass is done on the grid.
- The screenshot gate is live.
- **v0.1.0 is on the Play internal track.**

## M3 Converter, Year, Learn and adaptive layouts (about 3 to 4 days). Needs M2. All tasks are parallel.

**Tasks:**

- **[A]:**
  - T1 `:feature:converter`: two-way conversion with a Gregorian date picker and an IFC picker (month, day and intercalary chooser), a pre-1582 note, and share and copy.
  - T2: Year overview in `:feature:calendar`.
  - T3: Learn/About in `:feature:settings`. It covers the rules, "every month has a Friday the 13th", and why the weekdays differ.
  - T4: adaptive layouts (rail, list-detail Scene).
  - T5: `IntentRouter` plus the `ConverterKey` prefill from Day detail.

**Exit:** the converter round-trips in the UI on property-generated dates, and the expanded-width screenshots are approved.

## M4 Events (about 6 to 8 days). Needs M1. UI integration needs M2.

Work contract-first. **T1 is serial:** the domain models, `EventRepository` and `ObserveAgendaUseCase` interfaces, and fakes in `:core:testing`. It freezes `docs/contracts/Events.md`.

**Tasks after T1:**

- **[A]:**
  - T2 `:core:data`: Room 3 schema, DAOs, mappers and JVM tests.
  - T3 `:core:domain`: `IfcRecurrence` and `RecurrenceExpander` (IFC rules plus lib-recur for RRULE), with property tests.
  - T4 `:feature:events` editor UI, built against fakes (all-day or timed, zone picker, recurrence picker with an IFC tab, reminder chips).
  - T5 `:feature:events` list and search, built against fakes.
- **[B] (after A):**
  - T6: real repository implementation plus agenda use case wiring.
  - T7: month-grid dots, Day detail agenda and Today agenda.
  - T8: exdates ("delete this occurrence") and the edit-all flow.
  - T9: encrypted-only Auto Backup rules (`dataExtractionRules` plus legacy `fullBackupContent`) and the "Delete all data" action.
  - T10: intent hardening in `IntentRouter`: typed, validated extras carrying IDs only; immutable explicit `PendingIntent`s; no event content in logs.

**Exit:**

- One-off events can be created.
- "Every Sol 13" and "every Year Day" events can be created.
- A weekly Gregorian event can be created.
- All of these survive process death.
- The zone and DST tests are green.
- The month query takes under 5 ms with 1,000 events in a JVM benchmark test.

## M5 Widgets (about 4 to 5 days). Today and Month widgets need only M1 and the M2 design tokens, so they run in parallel with M4. The Agenda widget needs M4.

**Tasks:**

- **[A]:**
  - T1 `:widget` Today widget, with Hilt EntryPoint, Glance theme and tap routing.
  - T2 `:core:scheduling`: `DayRolloverScheduler`, the manifest receivers (TIME_SET, TIMEZONE, LOCALE, BOOT, MY_PACKAGE_REPLACED) with Robolectric ShadowAlarmManager tests. The rollover must be correct on the windowed-alarm fallback alone; the exact-alarm permission is only declared once reminders ship in M6.
- **[B]:**
  - T3: Month-grid widget.
  - T4: config activity plus per-widget state.
  - T5: previews (`providePreview` and `setWidgetPreview` with a version/locale guard, `previewLayout`, `previewImage`).
- **[C] (after M4):**
  - T6: `WidgetUpdater` implementation, debounced on repository writes, so event dots on the month widget stay current.
  - T7: moved to 1.1 (M7a) — the Agenda widget, which ships together with the widget privacy mode.
- **T8:** manual test matrix doc covering midnight, a manual clock change, a zone change, reboot, Doze (`adb shell dumpsys deviceidle force-idle`), a Samsung or Xiaomi device if available, and an app update. Start recruiting closed-test testers at this milestone.

**Exit:** the widget shows the correct date within seconds of midnight, and immediately after a time or zone change, on API 26, 33 and 36, and the picker previews render.

## M6 Reminders and the Holidays UI (about 3 to 4 days). Needs M4 and M5-T2.

**Tasks:**

- **[A]:**
  - T1: `ReminderScheduler` (next-alarm pattern), notification channel, in-context `POST_NOTIFICATIONS` request, and re-arm hooks.
  - T2 `:feature:holidays`: browse sets, toggle sets, a per-year list with both dates, and holidays merged into the agenda and the grid.
  - T3: declare `USE_EXACT_ALARM` (plus `SCHEDULE_EXACT_ALARM` up to SDK 32) and write the Play Console exact-alarm declaration. In the same change, add the `canScheduleExactAlarms()` → `setExactAndAllowWhileIdle` branch to `DayRolloverScheduler.arm()` and handle `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (lint rejects the call before the permission is declared). Reminder notifications use `VISIBILITY_PRIVATE` with a redacted public version; no full-screen intents.

**Exit:** a reminder fires within one minute of its target time in Doze, and holidays show on the grid and the widgets.

## M7 Interop — post-1.0 (releases 1.1 to 1.3). Needs M4.

**M7a (1.1):** T2 below, plus the Agenda widget with widget privacy mode and `not_keyguard`, the Quick Settings tile, and app shortcuts.
**M7b (1.2):** T1 below, plus file backup/restore with optional passphrase encryption, and the optional app lock.
**M7c (1.3):** `.ics` subscriptions by URL — adds `INTERNET`, a network security config, HTTPS-only fetching with redirect/size/timeout limits, and a Data safety re-audit.

**Tasks:**

- **T1:** ICS import (SAF picker, leading to a new `calendars` row) and export in `:core:data`. Parser choice (hand-rolled vs the `biweekly` library behind our own interface) is settled by an ADR first. Either way it covers unfolding, DTSTART, DTEND, RRULE, EXDATE, UID, SUMMARY, DESCRIPTION, LOCATION and `X-IFC-RRULE`, tested with fixture files and a hostile-file corpus (size/count caps, RRULE expansion bombs, malformed input). Import is transactional with preview and undo; imported reminders are off by default. Per [adr/0005-events-contract.md](adr/0005-events-contract.md) Amendment 1, the preview flags any `RRULE` that parses only leniently (lax and strict `lib-recur` modes disagree), and the importer normalises an event whose start does not match its rule (move the start to the first rule date) so the "start is occurrence 1" rule never yields an extra occurrence.
- **T2:** `:core:devicecalendar`, a CalendarContract.Instances overlay with a permission rationale and a per-calendar visibility list.
- **T3:** Settings UI for both.

**Exit:** a Google Calendar `.ics` export imports correctly, including recurring events, and the device overlay can be toggled off cleanly when permission is revoked.

None of this is in 1.0: the first Play review stays free of calendar and network permissions.

## M8 Release hardening, leading to 1.0 (about 4 to 5 days plus the mandatory 14-day closed test)

**Tasks (all parallel):**

- **[A]:**
  - T1: a11y audit (TalkBack script, font 200%, contrast).
  - T2: R8 plus the new `optimization {}` DSL, a `:baselineprofile` module, and a startup check.
  - T3: l10n readiness (pseudolocale `en-XA` and `ar-XB` screenshots).
  - T4: store listing (screenshots from Roborazzi, feature graphic), data-safety form (no data collected) and content rating.
  - T5: migration test for schema v1 frozen.
  - T6: bug reporting without a backend, as a "Send feedback" email intent. Any attached diagnostics contain no event content.
  - T7: security acceptance checklist from security-and-privacy.md, including the `bmgr` backup → reinstall → restore round trip and a final merged-manifest permission review.

**Exit:** the closed test is complete, no P1 bugs are open, and **1.0.0 is in production.**

## Beyond 1.0

- Bump targetSdk to 37 before Play's likely August 2027 deadline.
- Move to Kotlin 2.4.x once KSP catches up, to AGP 9.4 or later once Studio is updated, and to Nav3 1.2 deep links.
- Add per-occurrence edits (`event_overrides`).
- Add translations.
- Add more holiday sets (data-only changes).
- Publish on F-Droid (it builds from source with its own key).
- Build a Wear OS tile or complication.
- Offer CalendarContract write-out or sync so IFC events appear in other calendar apps.
- Extract `:core:calendar` as a published KMP library.
- Add detekt 2.0 when it is stable.

**Critical path:** M0, M1, M2, M4, M6, M8. The work that runs off the critical path is M1-C (holidays engine), M3, M5 groups A and B, and M7 (post-1.0).

---

## Open decisions for the owner

Each has a recommendation and a deadline — the milestone that cannot finish without it. Nothing here
blocks M0 or M1.

| # | Decision | Recommendation | Needed by |
|---|---|---|---|
| 1 | **App name.** ✅ **Decided 2026-09-18: Yearal**, store title "Yearal: 13-Month Calendar". Availability of 20 candidates checked ([competitive-analysis.md](competitive-analysis.md) §8). | Still owed by the owner: register `yearal.com` and `yearal.app`; run a manual USPTO/EUIPO search before the first upload. | M2 (store listing) |
| 2 | **applicationId.** ✅ **Decided 2026-09-18: `io.github.chrisjmendoza.yearal`** (also the code package base). | Permanent once uploaded; if the owner would rather ship under `app.yearal`, change it in `app/build.gradle.kts` before the first upload — never after. | M2 (first Play upload) |
| 3 | **License.** The repo is public with no license yet, which means all rights reserved. | MIT or Apache-2.0 if reuse is welcome; GPL-3.0 if Play-store clones are a worry. Deliberately left unset until decided. | Before accepting outside contributions |
| 4 | **Play developer account type.** Personal accounts created after 2023-11-13 must run a closed test with ≥12 testers for 14 continuous days before production. | Check now; if it applies, start recruiting testers at M5. | M5 |
| 5 | **Weekday display default.** ✅ Shipped as `BOTH` (nominal headers + actual weekdays beneath), user-switchable in Settings. | Validate with internal testers. | M2 |
| 6 | **Intercalary day labelling.** | Named days ("Leap Day", "Year Day") everywhere; `06-29` / `13-29` only in the numeric form. | M2 |
| 7 | **Monday-first option.** | No — it breaks the "13th is always Friday" identity. | M2 |
| 8 | **Holiday scope for 1.0.** | IFC observances + US pack (federal + common observances). Lunisolar tables are a stretch goal; other countries are post-1.0 data-only changes. | M6 |
| 9 | **Android Studio update to Quail 4.** | Yes, before M0 — allows AGP 9.4 from day one and avoids an early bump. | M0 |
| 10 | **Brand colour and icon.** ✅ **Closed 2026-09-18** — the "perfect month" glyph, teal `#123F3D` / cream `#F4ECDA` / accent `#F28C28` ([docs/brand/](brand/README.md)); adaptive + themed layers ship in `:app`. | Done: the Compose fallback palette is seeded from the icon (`core/designsystem/.../theme/Color.kt`). | M2 |
| 11 | **Monetisation.** | Free, no ads, no billing in 1.0. Optional tip jar later (note: Play Billing would complicate F-Droid). | After 1.0 |
| 12 | **Distribution beyond Play.** | Play only at 1.0; F-Droid afterwards (it builds from source with its own key). No APKs on GitHub Releases. | After 1.0 |
