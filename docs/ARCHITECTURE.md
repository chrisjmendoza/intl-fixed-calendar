# Architecture

Status: **current as of M0 and M1 complete, M2 in progress** (2026-09-17). The toolchain is settled by
[adr/0001-toolchain.md](adr/0001-toolchain.md); [`gradle/libs.versions.toml`](../gradle/libs.versions.toml)
is the authority for versions and §1 explains the choices. Progress per task is in [ROADMAP.md](ROADMAP.md).

## How the planning docs fit together

| Doc | Authoritative for |
|---|---|
| [calendar-spec.md](calendar-spec.md) | IFC rules, conversion algorithms, the `IfcDate` type model, formatting, date-arithmetic semantics, test vectors |
| [FEATURES.md](FEATURES.md) | What the app does and in which release tier |
| **ARCHITECTURE.md** (this file) | Tech stack, module boundaries, data model, UI/widget architecture, testing, CI/CD |
| [holidays-and-import.md](holidays-and-import.md) | Holiday rule engine and data format, holiday data licensing, device-calendar overlay, `.ics` import/export |
| [security-and-privacy.md](security-and-privacy.md) | Threat model, permissions, backup rules, Play policy, repo hygiene |
| [competitive-analysis.md](competitive-analysis.md) | Evidence behind priorities; naming collisions |
| [ROADMAP.md](ROADMAP.md) | Milestones, task breakdown for parallel agents, open questions |
| [WORKFLOW.md](WORKFLOW.md) | How work is done: the gate, Definition of Done, KDoc standard, anti-drift rules, rules for LLM agents |
| `gradle/libs.versions.toml` | The dependency versions the build actually uses (§1 explains the choices) |
| [adr/](adr/) | Decisions made after this baseline |

When two docs disagree, the doc that is authoritative for that topic wins, and the other gets fixed.

## Decisions at a glance

| Topic | Decision |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 with dynamic colour, single activity |
| SDK levels | minSdk 26 (native `java.time`), targetSdk 36, compileSdk 37 |
| Correctness keystone | `:core:calendar` — pure Kotlin/JVM, zero Android dependencies, exhaustively tested; **no date is ever computed outside it** |
| Storage | Room 3 + bundled SQLite driver for events; DataStore for settings. Dates are stored Gregorian (epoch day); IFC is a view. The only IFC data at rest is IFC-anchored recurrence rules |
| Time | Injectable `Clock`; `LocalDate.now()` is banned outside the `Clock` binding; no epoch-millisecond arithmetic |
| DI / navigation | Hilt; Navigation 3 |
| Widgets | Jetpack Glance; one exact alarm per day at local midnight + system-broadcast receivers + `updatePeriodMillis` backstop |
| Holidays | Own pure-Kotlin rule engine, bundled JSON rule packs, computed per year, never stored |
| Network | None in 1.0 — no `INTERNET` permission. No analytics, ads, or accounts, ever |
| Tests | JVM-first: JUnit 6 + Kotest property tests for pure modules; Robolectric + Roborazzi for UI; emulator only for nightly smoke tests |
| CI | GitHub Actions: format check, lint, all JVM tests, screenshot verification, debug assemble on every push to `main` (no pull requests; see WORKFLOW.md) |

## Reconciled decisions

The planning docs were written in parallel and disagreed in a few places. These are the rulings.

1. **`IfcDate` type model** — the sketch in calendar-spec §4 is the one to implement: a sealed
   interface with `Regular`, `LeapDay`, and `YearDay`, the `monthNumber` / `dayOfMonth = 29`
   pseudo-fields, and **no bare `dayOfWeek`** (only `nominalDayOfWeek?` and `actualDayOfWeek`).
   §3.1 below adds `IfcYearMonth` on top of it for grid layout.
2. **Canonical numeric form** — `IFC YYYY-MM-DD` with months `01`–`13`, Leap Day `06-29`, Year Day
   `13-29` (calendar-spec §7.3). It sorts correctly and keeps a plain int triple. The `IFC ` prefix is
   mandatory in anything a user can see.
3. **Month arithmetic from an intercalary day** — defined, clamping like `java.time`
   (Leap Day + 1 month = Sol 28), per calendar-spec §7.7.
4. **Yearly recurrence on Leap Day in common years** — per-event policy `JUNE_28 | SKIP | SOL_1`.
   User-created events default to `JUNE_28` (IFC June 28 is Gregorian June 17 in common years, so the
   Gregorian date never moves). The built-in Leap Day holiday is simply absent in common years.
5. **Exhaustive test range** — every day of years 1–9999 (≈3.65 M days; seconds on the JVM), matching
   the reference script that produced the spec's vectors, rather than a narrower window.
6. **UI year range** — pickers and the converter accept 1583–9999 with a proleptic-calendar note at the
   low end; the library accepts 1–9999 (calendar-spec §7.1).
7. **Weekday display default** — `BOTH`: nominal IFC weekday headers with the actual weekdays in a
   second header row. Everything tied to real life (today highlight, events, reminders) uses the actual
   weekday. To be validated with testers during internal testing.
8. **Holiday rule model** — the rule types and JSON format in holidays-and-import.md supersede the
   shorter list in §3.3 below. Lunisolar holidays come from tables generated at development time, so
   `:core:holidays` stays pure JVM.
9. **`.ics` parsing** — not decided here. A hand-rolled VEVENT parser and the `biweekly` library are
   both on the table; parsing untrusted files favours a maintained library behind our own interface.
   Settled by an ADR when the import milestone starts. RRULE expansion uses `lib-recur` either way.
10. **Release scope** — 1.0 ships without any calendar permission and without network access.
    Device-calendar overlay is 1.1, `.ics` import/export and file backup 1.2, URL subscriptions 1.3.
    The agenda widget (the first widget to show event text) ships in 1.x together with the widget
    privacy mode.
11. **Exact alarms** — reminders are the justification for `USE_EXACT_ALARM`; the midnight widget
    rollover reuses it when present and must work without it (windowed alarm + system broadcasts +
    `updatePeriodMillis`). No Play build declares the permission before reminders ship. See §5.
12. **Release signing** — the upload key stays offline and the 1.0 release bundle is signed on the
    owner's machine; CI proves the tag builds but does not hold the key. CI signing is an optional
    later convenience. Play is the only binary distribution channel at 1.0. See §7.
13. **No database encryption** — SQLCipher is rejected (§8). Sensitive-data exposure is handled where
    it actually happens: widgets, lock-screen notifications, exports, and backups.

## Development environment

Found on the development machine on 2026-09-17:

- Android Studio 2026.1.2 (Quail 2) with bundled JBR (OpenJDK 21.0.10) at
  `C:\Program Files\Android\Android Studio\jbr`. Quail 2 supports AGP up to 9.3.
- SDK at `%LOCALAPPDATA%\Android\Sdk` with platforms 35, 36 and 37 (37 was installed by AGP itself during
  the M0 spike — the licence is accepted on the machine, so a missing platform is fetched on first build).
  `cmdline-tools` are not installed and the build does not need them.
- `JAVA_HOME` and `ANDROID_HOME` are not set and `java` is not on `PATH`.

Command-line Gradle needs a JDK to launch the wrapper. Either set the variables once at user level:

```powershell
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"
```

or per shell session (what agents should do):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Gradle runs on JDK 21 and compiles to a Java 17 bytecode target in every module. The daemon JDK is
pinned by `gradle/gradle-daemon-jvm.properties` (Java 21, any vendor; generated by Android Studio via
`updateDaemonJvm`). Locally the JBR satisfies it and in CI Temurin 21 does, so nothing is downloaded in
practice; the foojay URLs in that file are only a fallback for a machine with no JDK 21. Modules do not
declare Java toolchains of their own, so compilation never triggers JDK provisioning.

## 1. Tech stack (version-catalog-ready)

### Build and platform

| Key | Artifact / plugin | Version | Rationale |
|---|---|---|---|
| gradle | Gradle wrapper | 9.7.1 | Latest stable (2026-08-19) and already cached locally. AGP 9.3 needs 9.5.0 or later. |
| agp | com.android.application / com.android.library | 9.3.3 | Latest patch of the newest line that the installed Studio Quail 2 supports. Move to 9.4.x only after updating Studio to Quail 4. |
| kotlin | Kotlin (jvm plugin, compose plugin, serialization plugin) | 2.3.21 | Pinned below the latest stable (2.4.20) on purpose: KSP has no verified Kotlin 2.4 line (issue google/ksp#2965 is open). Room and Hilt both depend on KSP. |
| ksp | com.google.devtools.ksp | 2.3.12 | Latest (2026-09-09). |
| compileSdk / targetSdk / minSdk | | 37 / 36 / 26 | Compose 1.12 forces compileSdk 37. Play has required target 36 for new apps since 2026-08-31. minSdk 26 gives `java.time` without desugaring. |

### UI and AndroidX

| Key | Artifact | Version | Rationale |
|---|---|---|---|
| composeBom | androidx.compose:compose-bom | 2026.09.00 | Resolves to Compose 1.12.1, material3 1.4.0 and adaptive 1.3.0. |
| material3 | androidx.compose.material3:material3 | 1.4.0 (via BOM) | Dynamic color on API 31 and later, brand fallback palette below that. |
| adaptive | material3.adaptive, adaptive-layout, adaptive-navigation, material3-adaptive-navigation-suite | 1.3.0 (via BOM) | NavigationSuiteScaffold and list-detail layouts. |
| glance | androidx.glance:glance-appwidget, glance-material3, glance-appwidget-testing, glance-preview, glance-appwidget-preview | 1.2.0 | Stable (2026-08-26). Adds `providePreview` and `setWidgetPreview`. |
| room3 | androidx.room3:room3-runtime, room3-compiler, plugin `androidx.room3` | 3.0.3 | See the decision below. |
| sqlite | androidx.sqlite:sqlite-bundled | 2.7.1 | The bundled driver gives the same SQLite on every device and allows plain JVM DAO tests. |
| datastore | androidx.datastore:datastore | 1.2.1 | Typed `DataStore<UserSettings>` with a kotlinx-serialization JSON serializer. No protobuf toolchain. |
| nav3 | androidx.navigation3:navigation3-runtime, navigation3-ui | 1.1.7 | See the decision below. |
| lifecycle | lifecycle-runtime-compose, lifecycle-viewmodel-compose, lifecycle-viewmodel-navigation3 | 2.11.0 | Needs compileSdk 37, which is fine here. |
| activity | activity-compose | 1.13.0 | |
| coreKtx / splashscreen / window / profileinstaller | | 1.19.0 / 1.2.0 / 1.5.1 / 1.4.1 | |
| work | androidx.work:work-runtime | 2.11.2 | Transitive through Glance. Used only lightly; see section 5. |

### Dependency injection, Kotlin libraries, tests and lint

| Key | Artifact | Version | Rationale |
|---|---|---|---|
| hilt | com.google.dagger:hilt-android, hilt-compiler (KSP), plugin | 2.60.1 | See the decision below. Needs AGP 9 and Gradle 9.1 or later, which are both met. |
| androidxHilt | androidx.hilt:hilt-lifecycle-viewmodel-compose, hilt-work | 1.4.0 | |
| coroutines | kotlinx-coroutines-core, -android, -test | 1.11.0 | |
| serialization | kotlinx-serialization-json | 1.11.0 | Nav keys, the settings serializer, and holiday-set JSON. |
| junit6 | org.junit:junit-bom (Jupiter) | 6.1.3 | Pure-JVM modules only. Parameterized and dynamic tests suit golden vectors. |
| junit4 | junit:junit | 4.13.2 | Android modules. Robolectric, the Compose test rule and Roborazzi are all JUnit4. |
| kotest | kotest-property, kotest-assertions-core | 6.2.5 | Used as libraries under JUnit, not as the Kotest runner. |
| turbine | app.cash.turbine:turbine | 1.2.1 | |
| robolectric | org.robolectric:robolectric | 4.17 | First release with SDK 37 support (2026-09-10). Very fresh. |
| roborazzi | io.github.takahirom.roborazzi (plugin, core, compose, junit-rule, compose-preview-scanner-support) | 1.74.0 | See the screenshot-testing decision below. |
| androidxTest | core / runner / rules / ext-junit / espresso | 1.7.0 / 1.7.0 / 1.7.0 / 1.3.0 / 3.7.0 | |
| spotless / ktlint | com.diffplug.spotless / ktlint | 8.10.2 / 1.8.0 | Formatter and style gate. |
| lint | Android Lint | built into AGP | Correctness gate. Set `warningsAsErrors = true` and check in a baseline. |
| recur | org.dmfs:lib-recur | **UNVERIFIED** (about 0.17.x) | RRULE expansion. Small, pure Java, used by Etar and OpenTasks. |

### Stack decisions

- **Hilt over Koin or manual DI.** Compile-time graph validation catches agent mistakes at build time. Each feature module wires itself with `@HiltViewModel` and `@Module`, so `:app` does not become a merge hotspot. Koin fails at runtime, and manual DI funnels all wiring through `:app`.
- **Navigation 3 over Navigation Compose.** Navigation Compose 2.9.8 is in maintenance. Nav3 is stable, and its back stack as a plain list of `@Serializable` keys is trivial to unit-test and to synthesize from widget and notification intents. The 1.2.0-rc01 release adds a deep-link matcher and a `ResultEventBus`; adopt it once it is stable.
- **Room 3 over Room 2.8.5.**
  - Room 3 is KSP-only, coroutines-only and built on SQLiteDriver. Starting on it avoids a forced `androidx.room` to `androidx.room3` migration later.
  - Agents trained on Room 2 will reach for `androidx.room.*`, so state the Room 3 package in CLAUDE.md.
  - If Room 3 blocks M4, Room 2.8.5 plus the bundled driver is a mechanical downgrade.
- **Roborazzi over Paparazzi or Compose Preview Screenshot Testing.**
  - It is the only option that renders full Compose, activities and Glance on the JVM through Robolectric.
  - Compose Preview Screenshot Testing is still alpha (0.0.1-alpha16) and is being re-homed into AGP 9.5 test suites.
  - Paparazzi cannot host activities or Glance.
- **ktlint through Spotless plus Android Lint, no detekt.** detekt 2.0 is alpha only (alpha.6), and detekt 1.23.8 predates Kotlin 2.3. Revisit when 2.0 is stable.
- **No mocking library.** Use hand-written fakes in `:core:testing`. They are more deterministic for agents.
- **Deliberately absent:** Firebase, Play Services, analytics and network libraries. The app is offline-first with no accounts, and that also keeps it eligible for F-Droid.

### Flagged as unverified, to be settled by the M0 spike

**Settled on 2026-09-17 by [adr/0001-toolchain.md](adr/0001-toolchain.md)** — items 1–3 and 5 verified
by a real build (Kotlin 2.4.20 passed the Room/KSP spike but is not adopted yet); item 4's
`lifecycle-viewmodel-navigation3` exists at lifecycle 2.11.0; `adaptive-navigation3`, lib-recur and the
Android 17 behaviour review remain open for M3/M4. The original list is kept for the record:

1. **Kotlin 2.4.20 with KSP 2.3.12, Room 3 and Hilt.** Evidence is mixed. KSP2 is decoupled from the compiler, but its own "Upgrade to Kotlin 2.4.0" issue is open and third parties report lock-out. The plan is to start on 2.3.21, try 2.4.20 on the scaffold, and bump only if it is green.
2. **Kotlin 2.3.21.** The exact patch was inferred from the Dagger 2.60 release notes. 2.3.20 is known good with AGP 9.3 on this machine, so fall back to it if 2.3.21 does not resolve.
3. **Hilt, Room 3 and Roborazzi Gradle plugins under AGP 9 defaults** (`android.newDsl=true`, built-in Kotlin).
   - Dagger 2.59 and later claim AGP 9 support.
   - Opting out (`android.newDsl=false`) sidesteps the question, but a greenfield project should not, because the opt-out is removed in AGP 10.
   - Under built-in Kotlin, Android modules do not apply `org.jetbrains.kotlin.android`. Raise KGP by declaring the `org.jetbrains.kotlin.jvm` plugin version at the root.
4. **Nav3 companion artifacts.**
   - `lifecycle-viewmodel-navigation3`: the docs sample shows 2.12.0-alpha03. Confirm that it ships at lifecycle 2.11.0 stable.
   - `adaptive-navigation3`: the docs sample shows 1.4.0-alpha02. Confirm whether it is in adaptive 1.3.0 stable. If it is not, hand-roll a two-pane Scene in M3 (about 100 lines).
5. **DataStore 1.2.1.** One Google page says 1.2.0 and another says 1.2.1; 1.2.1 is known to resolve.
6. **lib-recur version.**
7. **Release dates.** The summarizer garbled years on the GitHub release pages. Version numbers are reliable; treat dates as approximate.
8. **Android 17 (target 37) behavior changes.** Not reviewed. That is why targetSdk starts at 36 and the bump is a post-launch roadmap item.

## 2. Module structure

```
D:\Dev\intl-fixed-calendar
├─ build-logic/convention        (included build; 6 small plugins)
├─ gradle/libs.versions.toml
├─ app                           Application, MainActivity, Hilt root, Nav3 back stacks + entryProvider assembly, IntentRouter
├─ core/
│   ├─ calendar      [JVM]       IFC model + conversion + month/year layout. Depends on nothing (JDK java.time only).
│   ├─ domain        [JVM]       Event/Reminder/Holiday models, IfcRecurrence + RecurrenceExpander, HolidayRule engine,
│   │                            repository interfaces, use cases (ObserveAgenda), Clock/Zone/DateTicker, WidgetUpdater +
│   │                            ReminderScheduler interfaces. Depends on :core:calendar, coroutines-core, lib-recur
│   ├─ holidays      [JVM]       Bundled holiday-set JSON packs (schema 1, ADR 0004) + strict loader (HolidayPackLoader). Depends on :core:domain
│   ├─ data          [Android]   DataStore UserSettings + SettingsRepository (done); Room3 DB/DAOs/mappers, ICS import/export, Hilt modules
│   ├─ devicecalendar[Android]   CalendarContract read-only overlay (M7), isolated because it owns a permission
│   ├─ scheduling    [Android]   AlarmManager day-rollover + reminder scheduling, receivers, notifications
│   ├─ navigation    [Android-light] all @Serializable NavKeys + Navigator interface (so features never depend on each other)
│   ├─ designsystem  [Android]   Theme + brand palette, MonthGrid, DayCell, IntercalaryBand, WeekdayHeaders, IfcDateFormatter (resources). Depends on :core:domain (api, for WeekdayDisplay)
│   └─ testing       [JVM+Android split if needed] fakes, fixtures, golden vectors, MainDispatcherRule
├─ feature/
│   ├─ calendar                  Today (done), Month, Year, Day detail
│   ├─ settings                  Settings + More hub (done); Learn/About
│   ├─ converter
│   ├─ events                    list + editor
│   ├─ holidays
│   └─ settings                  Settings + Learn/About
├─ widget                        Glance widgets, widget receivers, config activity, WidgetUpdater impl
└─ baselineprofile               (M8)
```

### Dependency direction

Dependencies are strictly one-way: `feature:*` and `widget` depend on `core:designsystem`, `core:navigation`, `core:domain` and the pure-JVM `core:holidays`; `core:designsystem` depends on `core:domain`; `core:domain` depends on `core:calendar`.

- Features depend on `:core:domain` interfaces and never on `:core:data`.
- Only `:app` depends on `:core:data`, `:core:scheduling`, `:core:devicecalendar` and `:widget`. It needs them to put the Hilt bindings on the classpath.
- Features never depend on other features. Cross-feature navigation goes through `:core:navigation` keys.
- Add a Gradle check in the feature convention plugin that fails the build if a `feature` module depends on another `feature` or on `:core:data`.

Three pure-JVM modules hold all the logic that must be correct. They build and test in seconds with no Android toolchain, which makes them ideal for delegated agents.

### Package naming

Base: `io.github.chrisjmendoza.yearal` (the app is **Yearal**, ROADMAP.md decision #1, 2026-09-18). This is also the applicationId, which can never change after the first Play upload.

- A GitHub-derived reverse domain is legitimate, free, and matches a public repo.
- The namespace for each module is `<base>.core.calendar`, `<base>.feature.events`, and so on.
- The applicationId equals the base.

### build-logic

Yes, add it, but keep it minimal. With about 17 modules, copy-pasted `android {}` blocks are the top source of agent drift.

- Plugins, modelled on Now in Android and written against the AGP 9 new-DSL `CommonExtension`
  (shared code in `IfcAndroid.kt`; the API-shape rules are in [adr/0001-toolchain.md](adr/0001-toolchain.md)):
  - `ifc.jvm.library`: Kotlin JVM, Jupiter, Kotest, `explicitApi()`, Dokka KDoc gate.
  - `ifc.android.library`: SDK levels from the catalog, Java 17, lint as an error gate, JUnit4 + Robolectric.
  - `ifc.android.compose`: Compose compiler, BOM dependencies, Roborazzi.
  - `ifc.android.feature`: library, compose and hilt, plus the standard `:core:*` dependencies, Turbine, and the dependency rule check (fails on `:feature:*` → `:feature:*` or `:core:data`).
  - `ifc.android.application`: target SDK and the SemVer `versionCode`.
  - `ifc.hilt`: Hilt + KSP.
  - `ifc.kotlin.serialization` and `ifc.room`: the compiler plugins must be applied from `build-logic`'s classpath (ADR 0001, decision 3).
- Spotless is configured per module by the convention plugins (ktlint from the catalog).
- Do not write custom tasks beyond these.

## 3. Domain model

### 3.1 `:core:calendar`

The rules, algorithms, type model, formatting, and arithmetic semantics are specified in
[calendar-spec.md](calendar-spec.md) (§3, §4, §7). This module implements that spec and nothing else.
In brief: the IFC day-of-year equals the Gregorian day-of-year, so conversion is pure integer
arithmetic, and two invariants are worth remembering — Sol 1 is always Gregorian June 18, and Year Day
is always December 31.

`IfcDate` is the sealed interface from calendar-spec §4 (`Regular`, `LeapDay`, `YearDay`). On top of
it, this module provides the layout type the month grid and range queries are built on:

```kotlin
data class IfcYearMonth(val year: Int, val month: IfcMonth) {
    val firstDay: LocalDate
    val trailingIntercalary: IfcDate?             // LeapDay for June in leap years; YearDay for December; else null
    val gregorianRange: ClosedRange<LocalDate>    // 28 days, or 29 incl. trailing intercalary - ALWAYS contiguous
    fun actualDayOfWeek(column: Int): DayOfWeek   // constant down the column (28 = 4 whole weeks)
    fun plusMonths(n: Long): IfcYearMonth
}
```

**Design decisions:**

- The arithmetic core works on `(year, dayOfYear)` ints and an `epochDay` Long. `java.time` is a thin adapter layer, which keeps a later KMP port cheap.
- Validation happens at construction: day 1 to 28, Leap Day only in leap years, years 1 to 9999 (proleptic Gregorian, same as `java.time`).
- The canonical numeric text form is `IFC YYYY-MM-DD` with Leap Day `06-29` and Year Day `13-29` (calendar-spec §7.3). `parse` accepts it with or without the prefix; formatting for display always includes it.
- Day and week arithmetic delegates to `LocalDate`. Month and year arithmetic works on the IFC fields and clamps the way `java.time` does (calendar-spec §7.7).
- `explicitApi()` is on. The public API is frozen at the end of M1 and documented in `docs/contracts/Calendar.md`.
- There is no Android code, no localization and no display formatting here. Names live in resources in `:core:designsystem`.

**Weekday insight for the UI.** Every month is 28 days, so the actual weekday of grid column k is constant within a month. It is constant across the whole year up to an intercalary day, and it shifts by one after Leap Day.

- In 2026, 1 January is a Thursday, so every IFC "Sunday" column in 2026 is an actual Thursday.
- Column headers can therefore show both weekdays cheaply, for example `Sun` with a small `Thu` under it.
- Code still obtains the actual weekday from `toLocalDate().dayOfWeek` and never derives one weekday from the other (calendar-spec §4.1).

**Golden vectors** live in calendar-spec §6 (105 machine-generated pairs plus negative vectors). They are copied into `:core:testing` as a CSV and are the acceptance test for this module. Spot check: Gregorian 2026-09-17 is IFC September 8, 2026 — nominal Sunday, actual Thursday.

### 3.2 Events

Events are anchored to Gregorian wall-clock time. Instants are derived and never stored.

```
calendars(id PK, name, color_argb, source /*LOCAL|ICS*/, visible)          -- an ICS import becomes its own calendar
events(
  id PK AUTOINCREMENT, uid TEXT UNIQUE /*UUID, ICS round-trip*/, calendar_id FK,
  title, description, location, color_argb NULL, category /*EVENT|OBSERVANCE|BIRTHDAY*/,
  all_day INT,
  start_epoch_day INT NOT NULL,          -- local date in the event's zone
  start_minute_of_day INT NULL,          -- NULL iff all_day
  duration_minutes INT NOT NULL,         -- all-day: N*1440
  end_epoch_day INT NOT NULL,            -- denormalised last local date of first occurrence (range index)
  zone_id TEXT NULL,                     -- NULL = floating (device zone); all-day is always floating
  recurrence_type INT NOT NULL,          -- 0 none | 1 Gregorian RRULE | 2 IFC rule
  rrule TEXT NULL,                       -- RFC 5545 string (lossless ICS)
  ifc_rule TEXT NULL,                    -- e.g. IFC;FREQ=YEARLY;MONTH=7;DAY=13 | IFC;FREQ=YEARLY;INTERCALARY=YEAR_DAY | IFC;FREQ=MONTHLY;DAY=13;INTERVAL=1
  recurrence_until_epoch_day INT NULL,   -- denormalised from UNTIL/COUNT for pruning
  created_at, updated_at)
  INDEX(start_epoch_day), INDEX(end_epoch_day), INDEX(recurrence_type, recurrence_until_epoch_day), INDEX(calendar_id)
event_exdates(event_id FK CASCADE, epoch_day, PK(event_id, epoch_day))     -- "delete this occurrence"
reminders(id PK, event_id FK CASCADE, minutes_before INT, UNIQUE(event_id, minutes_before))
```

- **IFC recurrence** is modelled as `sealed IfcRecurrence { YearlyOnDate(month, day), YearlyOnIntercalary(kind), MonthlyOnDay(day) }`, plus `interval` and `until/count`.
  - `YearlyOnIntercalary(LEAP_DAY)` carries a common-year policy, `JUNE_28 | SKIP | SOL_1`. User-created events default to `JUNE_28`, which keeps the Gregorian date at June 17 every year.
  - IFC rules expand in O(1) per year by direct construction, `IfcDate.Regular(y, m, d).toLocalDate()`, with no iteration.
  - Gregorian rules go through lib-recur with fast-forward.
  - There is no materialised occurrences table. `RecurrenceExpander` in `:core:domain` is pure and property-tested.
- **Scope cuts:**
  - Version 1 supports edit all, delete all, and delete one occurrence (EXDATE).
  - Per-occurrence overrides (an `event_overrides` table) come after 1.0.
  - User-defined holidays are yearly all-day events with `category=OBSERVANCE`, so they need no extra table.
  - ICS export writes `ifc_rule` as `X-IFC-RRULE` plus a Gregorian-approximate fallback.
- **Time zones:**
  - A non-null `zone_id` keeps the wall time fixed in that zone. A null `zone_id` follows the device.
  - Expansion yields `Occurrence(eventId, startLocal: LocalDateTime, zone, …)`.
  - Bucketing by day converts each occurrence to the device zone.
- **Reminders** use a single next-alarm pattern: only the earliest upcoming reminder is scheduled, as one PendingIntent.
  - When it fires, the app posts the notification, then recomputes and reschedules.
  - It also recomputes on event writes, boot, time or zone changes, and app update.
  - This pattern never approaches the 500-alarm cap.
  - All-day reminders fire at a configurable local time, 09:00 by default.
  - `POST_NOTIFICATIONS` is requested in context, the first time the user adds a reminder.

### 3.3 Holidays

Holidays are computed, never stored.

- The rule types, modifiers, and the bundled JSON format are specified in [holidays-and-import.md](holidays-and-import.md): `fixed`, `nthWeekday` (negative n = last), `offset`, `easter` (western and orthodox), `table` (pre-generated lunisolar dates), and `ifc`, with `observed` policies and `since/until` bounds. In code this is a `sealed HolidayRule` hierarchy in `:core:domain`.
- A set is `HolidaySet(id, region, name, sources, holidays)`, loaded from JSON resources in `:core:holidays`
  by `HolidayPackLoader`; the engine is `HolidayEngine` in `:core:domain`. Decisions the spec left open
  (rule year vs anchor year, observed entries, memoisation key, the `SUNDAY_TO_MONDAY` policy) are in
  [adr/0003-holiday-rule-model.md](adr/0003-holiday-rule-model.md); the schema decisions in
  [adr/0004-holiday-pack-format.md](adr/0004-holiday-pack-format.md).
- Evaluation per year takes microseconds and is memoised.
- Enabled set IDs live in DataStore.
- `HolidayEngine` is bound in `:feature:calendar` (`di/HolidayModule`) and `HolidayPackLoader` in `:feature:settings`; both move to `:app` if a third module needs them. The IFC observances set and the US pack are enabled by default; any set, the IFC one included, may be switched off in Settings (`UserSettings.enabledHolidaySets`). Version 1 ships the "IFC observances" set (Year Day, Leap Day, Sol 1) and the US pack (federal holidays plus common observances). Lunisolar tables are a 1.0 stretch goal that can slip to 1.1 without affecting the engine. More sets are data-only PRs.
- Device calendars (M7) are a read-only overlay through `CalendarContract.Instances`, queried on the same Gregorian range. The feature is opt-in and the `READ_CALENDAR` prompt appears in context.

### 3.4 Month-grid query

1. `IfcYearMonth.gregorianRange` gives a contiguous `[start, end]` of 28 or 29 days.
2. `ObserveAgendaUseCase(range): Flow<Map<LocalDate, DayAgenda>>` combines four sources:
   - **Non-recurring events:** `WHERE recurrence_type=0 AND start_epoch_day <= :end+1 AND end_epoch_day >= :start-1`. The one-day padding covers zone skew. Filter exactly in Kotlin.
   - **Recurring candidates:** `WHERE recurrence_type!=0 AND start_epoch_day <= :end AND (recurrence_until_epoch_day IS NULL OR >= :start)`. This is a small set. Expand it in memory and subtract exdates.
   - **Holidays:** `holidays(year, enabledSets)` filtered to the range.
   - **Device instances:** optional.
3. Bucket the results by local date. The UI maps dates to cells through `IfcDate.from`.

The pager keeps three months warm with `beyondViewportPageCount = 1`. The Year view issues one range query for the whole year and returns only a `Set<epochDay>` presence bitmap.

## 4. UI architecture

### Screens and navigation

- **Bottom bar / rail:** the top-level destinations, via `NavigationSuiteScaffold`, are **Today | Calendar | Events | Convert | More**. "More" holds Holidays, Settings and Learn/About.
- **Nav keys** live in `:core:navigation`: `TodayKey`, `MonthKey(year, month)`, `YearKey(year)`, `DayKey(epochDay)`, `ConverterKey(prefillEpochDay?)`, `EventListKey`, `EventEditorKey(eventId?, prefillEpochDay?)`, `MoreKey` (the hub tab), `HolidaysKey`, `SettingsKey`, `LearnKey`.
- **Per-tab back stacks** are `TabBackStacks` in `:app`: one `NavBackStack` per tab, the Today root prefixed when another tab is shown so that back from a tab root returns to Today; the Calendar tab's root `MonthKey` is resolved from `DateTicker` when the tab is first opened.
- **Entry providers:** each feature exposes `fun EntryProviderScope<NavKey>.xEntries(navigator: Navigator)`. `:app` owns the per-tab back stacks (the Nav3 "top-level back stack" recipe) and the `NavDisplay`.
- **Intent routing:** widget and notification taps send explicit intents with extras. `IntentRouter` in `:app` builds the back stack, for example `[MonthKey, DayKey]`. No URI deep links are needed until Nav3 1.2 is stable.
- **Screen behaviors:**
  - **Today:** the hero IFC date, the Gregorian equivalent, both weekdays, year progress, today's agenda, and the next intercalary day or holiday.
  - **Month:** a `HorizontalPager` of months (done: "Calendar" app bar with a Today action; each page's `MonthGrid` carries the month heading; holidays come from `HolidayCatalog`, which evaluates the enabled packs with `HolidayEngine` for the visible page ±1). Tapping the title zooms out to **Year**, which is 13 mini-months in a `LazyVerticalGrid(Adaptive(160.dp))`. On a two-column phone, Year Day takes the 14th slot.
  - **Day detail:** a bottom sheet on compact widths (done: a material3 `ModalBottomSheet` inside the Nav3 entry — Nav3 1.1.7 has no sheet scene, only `DialogSceneStrategy`) and a pane on expanded widths (M3 T4). It shows both dates, both weekdays and the day's events, with "Add event" and "Open in converter" actions.

### State management

Use plain unidirectional data flow with no MVI framework.

- An `@HiltViewModel` exposes one `StateFlow<XUiState>` built with `stateIn(WhileSubscribed(5_000))`, plus public intent functions.
- `XRoute(viewModel)` collects with `collectAsStateWithLifecycle`. It delegates to a stateless `XScreen(state, callbacks)`, which is the unit for previews, Roborazzi and Compose tests.
- "Today" comes from `DateTicker: Flow<LocalDate>` in `:core:domain`:
  - It emits the current date, then delays to the next midnight.
  - The Android implementation also re-emits on resume and on a context-registered `TIME_SET`, `TIMEZONE_CHANGED` and `DATE_CHANGED` receiver.
- Never call `LocalDate.now()` outside the `Clock` binding.

### Intercalary days in a 7-column grid

- The grid is always exactly 4 rows by 7 columns.
- June in leap years and every December gain an **intercalary band** below row 4.
  - It is one full-width pill spanning all seven columns.
  - It shows a label ("Leap Day" or "Year Day"), the Gregorian date and the real weekday.
  - It uses the tertiary-container color plus an icon, and it is tappable like any other day.
  - The slot height is measured from real text (`intercalarySlotHeight()`), because non-linear font scaling breaks any "line height × N" estimate; months without a band show the month's Gregorian span in the placeholder.
- Spanning every column makes "belongs to no week" visible. No weekday header aligns with it.
- The same component serves the Year view as a thin bar, and the widget.
- A `WeekdayDisplay { NOMINAL, ACTUAL, BOTH }` setting drives the headers. `BOTH` is the default: nominal IFC weekdays with the actual weekdays in a second header row.

### Adaptive layouts

- Navigation is a bar on compact widths and a rail on medium widths and up.
- Calendar becomes list-detail on expanded widths, with Month on the left and Day detail on the right. It uses the Nav3 Scenes list-detail strategy if `adaptive-navigation3` is stable, otherwise a small custom two-pane Scene.
- Events list and editor use the same pattern.
- The app is edge-to-edge (enforced at target 36), supports predictive back, and does not lock orientation. Target 36 ignores orientation locks on sw600dp and up anyway.

### Accessibility

- Cells are at least 48dp. Seven columns at 360dp gives 51dp.
- Each cell has a merged description, for example "Sol 13, IFC Friday. Gregorian Tuesday, June 30, 2026. 2 events. Holiday: …", with "Today." appended on the current date.
- Use `selected` and `Role.Button` semantics, a heading on the month title, and traversal groups on the grid.
- Today, holidays and intercalary days are never encoded by color alone. Each also has a shape or icon.
- Screenshot tests run at font scale 2.0. Respect the reduced-motion setting.

### Localization

- All strings are in resources from day one, including the 13 month names with "Sol".
- The IFC date pattern is a positional string resource, `%1$s %2$d, %3$d`, so locales can reorder it
  (`date_long_regular`, `date_long_intercalary`, `date_medium_*` in `:core:designsystem`; `IfcDateFormatter`
  formats them with its own `Locale` via `String.format`, not `Resources.getString(id, args)`, so the
  numerals follow the formatter's locale).
- Gregorian dates use `DateTimeFormatter.ofLocalizedDate`.
- Weekday names come from `DayOfWeek.getDisplayName`.
- Numerals are formatted with the locale.
- RTL comes for free from `Row`.
- Per-app language uses AGP `generateLocaleConfig`.
- Version 1 ships in English only.

## 5. Widget architecture (Glance, in `:widget`)

### Widget types

1. **Today:** sizes from 2x1 to 2x2. It shows the IFC date large, with an optional Gregorian line and weekdays.
2. **Month grid:** 4x3 and larger. It uses the same 4x7 grid plus band and marks today, event dots and holidays. Rows are nested containers, because Glance caps a container at 10 children. Seven columns and at most six rows fit.
3. **Agenda:** the next N occurrences. It ships in 1.x, together with the "hide event details" widget privacy mode, because it is the first widget that shows event text on the home screen.

All widgets use `SizeMode.Responsive` with three sizes, `GlanceTheme` dynamic colors, and tap actions that open the app at a `DayKey`.

### Data

- `provideGlance` pulls repositories through a Hilt `@EntryPoint`.
- It computes "today" from the injected `Clock` at composition time, so any update trigger self-corrects.
- It collects the agenda flow while the Glance session is alive.
- Repository writes call `WidgetUpdater.requestUpdate()`. The interface lives in domain, and the implementation is here as a debounced `updateAll`.

### Midnight rollover (layered)

1. **Primary:** one `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, nextLocalMidnight + about 1s)`. It fires `updateAll` and re-arms itself. One wakeup per day has negligible battery cost.
   - Permissions: declare `USE_EXACT_ALARM` (API 33 and later, auto-granted and not revocable) and `SCHEDULE_EXACT_ALARM` with `maxSdkVersion="32"` (granted by default on API 31 and 32).
   - **Event reminders are what justify the exact-alarm permission** — Play explicitly allows it for calendar apps that show event notifications, and requires a Play Console declaration. The widget rollover merely reuses it. No build uploaded to Play declares `USE_EXACT_ALARM` before reminders ship (M6).
   - This route avoids the Android 14 denied-by-default `SCHEDULE_EXACT_ALARM` flow entirely.
   - Always guard with `canScheduleExactAlarms()` and fall back to `setWindow` with a 10-minute window. Pre-reminder builds, and any build where Play rejects the declaration, run on the windowed alarm plus layers 2 and 3, so the design must be correct without exact alarms.
2. **Manifest receivers**, all on the implicit-broadcast exemption list or package-targeted: `TIME_SET`, `TIMEZONE_CHANGED`, `LOCALE_CHANGED`, `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. Each one updates the widgets and re-arms the rollover and reminder alarms.
   - `ACTION_DATE_CHANGED` is **not** exempt, so it cannot be registered in the manifest at target 26 or later. It is used only context-registered, inside `DateTicker`, while the app is alive.
3. **Self-healing backstop:** `updatePeriodMillis` of 4 hours in the provider XML. It needs no code, recovers from OEM alarm killing or force-stop, and re-arms the alarm.
   - Do not add a periodic WorkManager job. Glance already uses WorkManager internally for sessions.

Never update a widget per minute. The widgets show dates, not clocks.

### Configuration

- Use an optional config activity with `configuration_optional|reconfigurable`.
- Options are: show the Gregorian line, the weekday mode, and background opacity.
- Settings are stored per widget in Glance `PreferencesGlanceStateDefinition`.

### Picker previews

- Override `providePreview` and call `setWidgetPreview` on API 35 and later.
  - The call is rate-limited to about two per hour.
  - Call it on app start, only when the (versionCode, locale) pair changes.
- Provide an XML `previewLayout` for API 31 to 34.
- Provide a `previewImage` PNG below API 31, generated from a Roborazzi capture.

## 6. Testing strategy

| Layer | Tests | Runner |
|---|---|---|
| `:core:calendar` | Exhaustive round trip for every date in years 1 to 9999 (about 3.65 million): `from(d).toLocalDate()==d`, successor monotonicity, 13x28 plus the intercalary counts per year. | JUnit 6 plus kotest-property. Runs in seconds. |
| | An independent enumeration oracle (build each year's date list by brute force) checked against the arithmetic implementation. | |
| | Golden CSV vectors, from section 3.1 plus historical dates. | |
| | kotest-property for arithmetic laws (plus/minus inverses, clamping, ordering). | |
| | Parse/format round trip, invalid construction, and ordering. | |
| `:core:domain` | Property tests for the recurrence expander. For every IFC rule, each occurrence converts back to a matching `IfcDate`, and Leap Day rules fire only in leap years. | JUnit 6 |
| | DST edge cases with fixed zones. | |
| | Holiday rules against published tables: `HolidayOracleTest` (Easter 1900–2100 Western and Orthodox, OPM federal holidays 2020–2030) in `:core:domain`; `UsPackOracleTest` / `BundledPacksTest` (OPM 2024–2028, 2026 observances, Easter family) in `:core:holidays`. Oracle CSVs carry their source URL and fetch date. | |
| | Agenda bucketing. | |
| `:core:data` | Room 3 DAO tests on the JVM with `BundledSQLiteDriver` in memory. No emulator is needed. | JUnit4 (plus Robolectric only where a Context is needed) |
| | Range-query correctness against a naive filter (property test). | |
| | Migration tests from exported schemas (`core/data/schemas/` is committed). | |
| | DataStore serializer round trip and corruption fallback. | |
| | ICS parser fixtures. | |
| ViewModels | Fakes from `:core:testing`, a fake `Clock` and `DateTicker`, Turbine, and `runTest`. Include a test that advances the clock across midnight. | JUnit4 |
| Compose UI | Stateless `XScreen` tests under Robolectric (`@GraphicsMode(NATIVE)` for any test that depends on text metrics — legacy mode fakes every Text at one height). Cover semantics (content descriptions, selection) and the intercalary band in June 2028 and in December. Library modules pin `sdk=36` in `src/test/resources/robolectric.properties`: without a `targetSdk` in the test manifest Robolectric picks its newest SDK, where the Compose test rule's input injection breaks. | JUnit4 plus Robolectric 4.17 |
| Screenshots | Roborazzi. The preview scanner auto-captures every `@Preview` in `:core:designsystem` and the features. | `verifyRoborazziDebug` |
| | Explicit matrices for MonthGrid: {normal, June-leap, December} x {light, dark} x {font 1.0, 2.0} x {compact, expanded} x {LTR, RTL}. | |
| | Glance widgets through glance-appwidget-testing or previews. | |
| Instrumented | Minimal smoke tests. They run nightly and on manual dispatch, not per push: app launch, a widget receiver smoke test, and the alarm re-arm after `TIME_SET` (adb broadcast). | emulator-runner |

### Goldens

Robolectric native-graphics output differs between Windows and Linux, so CI (Linux) is the only recorder.

- A `workflow_dispatch` "record-screenshots" job commits goldens to the branch it is dispatched on (normally `main`).
- Local and agent runs use `compareRoborazziDebug`, which never blocks.

### CI gate per push

`check` (per module: `spotlessCheck`, `lint` on Android modules, `test` — JVM and Robolectric —, the Dokka KDoc
gate on JVM modules) and `:app:assembleDebug`; `verifyRoborazziDebug` joins once the first goldens are recorded (M2 T10).

## 7. CI/CD (GitHub Actions)

- **`ci.yml`**
  - Triggers: pushes to `main`, and pull requests (cloud agents and Dependabot; see WORKFLOW.md §1).
  - Setup: ubuntu-latest, `actions/setup-java` (temurin 21), and `gradle/actions/setup-gradle` with caching.
  - Steps: `./gradlew check :app:assembleDebug` (plus `verifyRoborazziDebug` once goldens exist).
  - Artifacts: the debug APK on every run; test, lint and Roborazzi diff reports on failure.
  - Add concurrency cancellation.
  - Optional: a separate fast job that runs `:core:calendar:test :core:domain:test :core:holidays:test`. It finishes in under a minute and gives agents early feedback.
- **`record-screenshots.yml`:** manual dispatch. See section 6.
- **`nightly.yml`:** instrumented smoke tests on `reactivecircus/android-emulator-runner` (API 26, API 36, and API 37 when images exist), plus a dependency-updates report.
- **`release.yml`:**
  - Trigger: a `v*` tag.
  - Steps: run the full CI gate, build the **unsigned** release bundle to prove the tag builds, and create a GitHub Release with the changelog and the R8 mapping file.
  - For 1.0 the signed AAB is built and uploaded to Play from the owner's machine, because the upload key stays offline (security-and-privacy.md). Signing in CI from GitHub secrets, and automated upload to the internal track, are optional later steps; if adopted, use an upload action rather than Gradle Play Publisher, which has had open AGP 9 compatibility issues.
  - No APKs are attached to GitHub Releases. They would be signed with a different key than the Play build, so users could not cross-update. F-Droid, if pursued, builds from source with its own key.
  - Promotion between tracks happens in the Play Console.
- **Signing:**
  - Use Play App Signing. Google holds the app key and you hold an upload key, which can be reset through Play Console if lost.
  - Keep the keystore out of the repo; `.gitignore` covers keystores, signing properties, and key material.
  - Use the debug keystore for local development.
- **Dependencies and repo hygiene** (details in security-and-privacy.md):
  - Dependabot for the `gradle` and `github-actions` ecosystems with grouped PRs and a cooldown. Review AGP, Kotlin and KSP bumps manually.
  - GitHub Actions pinned by full commit SHA, read-only `GITHUB_TOKEN` by default, secret scanning with push protection, branch protection on `main`, private vulnerability reporting, and a `SECURITY.md`.
  - Repositories restricted with content filtering (`google()`, `mavenCentral()`, Gradle Plugin Portal only); no JitPack, no dynamic versions; Gradle wrapper checksum validated.
  - A CI check compares the merged manifest's permissions against an allow-list, so a dependency cannot silently add `INTERNET` or anything else.
- **Versioning:**
  - Use SemVer in `gradle.properties` (`VERSION_NAME=0.1.0`).
  - `versionCode = major*1_000_000 + minor*10_000 + patch*100 + build`.
  - Tags are `vX.Y.Z`, with a `CHANGELOG.md` in Keep a Changelog style.
- **Play path:** internal track from M2, then closed testing, then production.
  - **Schedule risk:** personal Play developer accounts created after November 2023 must run a closed test with at least 12 testers for 14 continuous days before production access. Start recruiting testers at M5.
- **F-Droid:** there are no proprietary dependencies, so F-Droid is a cheap second channel after 1.0.
- **Privacy policy:** host it on GitHub Pages from this repo and link it in the app. Play requires it, together with the Data safety form, before *any* track including closed testing.

## 8. Security and privacy architecture

[security-and-privacy.md](security-and-privacy.md) holds the threat model and the full decision table. The points that shape the code:

- **Data at rest:** app-private storage only (settings are plain JSON at `filesDir/datastore/user_settings.json`, inside the backup include set), relying on the app sandbox and file-based encryption. **No SQLCipher** — it only helps against root/forensic attackers (out of scope), and an auth-gated key would break widgets, reminders and workers. Caches go in `cacheDir` / `noBackupFilesDir`. Device-calendar data is read live and never copied into Room.
- **No network:** no `INTERNET` permission until URL subscriptions (1.3). This is a verifiable privacy claim and means a compromised dependency cannot exfiltrate anything. No analytics, ads, crash SDK, Firebase or Play Services; Play Console vitals only.
- **Logging:** no event content in release logs, ever. A "Delete all data" action ships in 1.0.
- **Notifications:** reminders use `VISIBILITY_PRIVATE` with a redacted public version. No full-screen intents.
- **Widgets:** any widget that can show event titles ships with a privacy mode (titles / counts only / date only) and `widgetCategory="not_keyguard"` (in an `xml-36` resource folder), because since Android 16 QPR2 widgets are lock-screen eligible by default.
- **App lock (1.x, optional):** `BiometricPrompt` with `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`, no custom PIN. It is a UI gate, not encryption: one gate in `:app` covers launcher entry, widget taps, notification taps, import intents and widget configuration. Enabling it also turns on `FLAG_SECURE`.
- **Backups:** Auto Backup stays on but encrypted-only — `dataExtractionRules` with `disableIfNoEncryptionCapabilities="true"`, plus the legacy `fullBackupContent` for API 30 and below. Verified by a backup → reinstall → restore round trip with `bmgr` before release.
- **Export / import (1.2):** Storage Access Framework only, no storage permissions. Plaintext export warns; optional passphrase encryption uses platform PBKDF2-SHA256 + AES-256-GCM. Restored JSON and imported `.ics` are untrusted input: size/count/length caps, bounded lazy RRULE expansion, imported reminders off by default, plain-text rendering only, transactional import with preview and undo, and a hostile-file test corpus.
- **Components and intents:** minimal exported components; typed, validated intent extras carrying IDs only; immutable explicit `PendingIntent`s; `RECEIVER_NOT_EXPORTED` for context-registered receivers; no deep links or App Links. Android Lint security checks are CI errors.
- **Permissions by release:** 1.0 — `POST_NOTIFICATIONS` (asked when the first reminder is added), `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` (max SDK 32), `RECEIVE_BOOT_COMPLETED`. 1.1 — `READ_CALENDAR`, just-in-time with a rationale. 1.3 — `INTERNET`. Never: `WRITE_CALENDAR`, storage, contacts, location, `AD_ID`.

## 9. Risks

1. **Toolchain freshness.**
   - AGP 9 new DSL and built-in Kotlin with the Hilt, Room 3 and Roborazzi plugins.
   - Robolectric 4.17 is one week old.
   - KSP lags Kotlin 2.4.
   - Mitigation: the M0-T3 spike plus an ADR, and conservative pins (Kotlin 2.3.21, AGP 9.3.3). The fallbacks are Room 2.8.5, or a temporary `android.newDsl=false`.
2. **Play closed-testing gate.** A new personal account needs 12 testers for 14 days, which is the largest calendar-time risk. Confirm your account type now.
3. **`USE_EXACT_ALARM` policy review.** The app should qualify as a calendar app with reminders. If the request is rejected, fall back to the user-granted `SCHEDULE_EXACT_ALARM` flow, with `setWindow` for rollover.
4. **OEM background killers** (Xiaomi, Samsung, Huawei) can defeat alarms. The `updatePeriodMillis` backstop and update-on-app-open limit the damage. Document this in the app's help.
5. **Weekday confusion is the core UX risk.** The IFC "Sunday" is not the real Sunday. The dual headers and the Learn screen mitigate it. Decide the default with real users during internal testing.
6. **Agents hallucinating older APIs** (Room 2 packages, Navigation Compose, `kotlin-android` plugin usage). The CLAUDE.md rules and the compile-time gates cover this.
7. **Screenshot goldens across operating systems.** Windows development against Linux CI is solved by CI-only recording, but that adds a step to UI changes.
8. **Scope creep in events.** Per-occurrence edits, attendees and sync are explicitly out of version 1.

## Sources

Version and platform facts were checked against these pages on 2026-09-17.

- [AGP 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [AGP 9.3 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [AGP 9.0 release notes (built-in Kotlin, new DSL)](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [About AGP: Studio and API-level compatibility](https://developer.android.com/build/releases/about-agp)
- [Android Studio Quail 4 stable](https://androidstudio.googleblog.com/2026/09/android-studio-quail-4-now-available.html)
- [Gradle 9.7.1 release notes](https://docs.gradle.org/current/release-notes.html)
- [What's new in Kotlin 2.4.20](https://kotlinlang.org/docs/whatsnew2420.html)
- [KSP releases](https://github.com/google/ksp/releases)
- [KSP issue 2965: Upgrade to Kotlin 2.4.0](https://github.com/google/ksp/issues/2965)
- [Third-party report of KSP and Kotlin 2.4 lock-out](https://github.com/uny/a2ui-compose/issues/64)
- [Compose August '26 release](https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html)
- [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping)
- [AndroidX all-channel versions](https://developer.android.com/jetpack/androidx/versions/all-channel)
- [AndroidX stable channel](https://developer.android.com/jetpack/androidx/versions/stable-channel)
- [Room 3 releases](https://developer.android.com/jetpack/androidx/releases/room3)
- [Glance releases](https://developer.android.com/jetpack/androidx/releases/glance)
- [Glance widget updates guide](https://developer.android.com/develop/ui/compose/glance/glance-app-widget)
- [Glance generated previews](https://developer.android.com/develop/ui/compose/glance/generated-previews)
- [Navigation 3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3)
- [Navigation 3 get started](https://developer.android.com/guide/navigation/navigation-3/get-started)
- [Dagger and Hilt releases](https://github.com/google/dagger/releases)
- [kotlinx.coroutines 1.11.0](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.11.0)
- [kotlinx.serialization changelog](https://github.com/Kotlin/kotlinx.serialization/blob/master/CHANGELOG.md)
- [JUnit releases](https://github.com/junit-team/junit-framework/releases)
- [Kotest releases](https://github.com/kotest/kotest/releases)
- [Turbine releases](https://github.com/cashapp/turbine/releases)
- [Robolectric releases](https://github.com/robolectric/robolectric/releases)
- [Roborazzi releases](https://github.com/takahirom/roborazzi/releases)
- [Compose Preview Screenshot Testing](https://developer.android.com/studio/preview/compose-screenshot-testing)
- [androidx.test releases](https://developer.android.com/jetpack/androidx/releases/test)
- [detekt releases](https://github.com/detekt/detekt/releases)
- [ktlint releases](https://github.com/pinterest/ktlint/releases)
- [Spotless Gradle plugin](https://plugins.gradle.org/plugin/com.diffplug.spotless)
- [Gradle Play Publisher AGP 9 issue](https://github.com/Triple-T/gradle-play-publisher/issues/1182)
- [Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- [Schedule alarms and exact alarm permissions](https://developer.android.com/develop/background-work/services/alarms/schedule)
