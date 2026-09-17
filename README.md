# International Fixed Calendar for Android

An Android app for the [International Fixed Calendar](https://en.wikipedia.org/wiki/International_Fixed_Calendar)
(IFC): 13 months of exactly 28 days, a month called **Sol** between June and July, and two "floating"
days — **Year Day** and **Leap Day** — that belong to no week.

> **Status: early development.** The design docs are complete and the calendar core (`:core:calendar`,
> pure Kotlin) is implemented and exhaustively tested. The Android app itself has not been started. The
> working name is a placeholder until the app name is chosen.

## The calendar in 30 seconds

- Every month has 28 days, starts on a Sunday, and ends on a Saturday. The 13th is always a Friday.
- Months: January, February, March, April, May, June, **Sol**, July, August, September, October,
  November, December.
- **Year Day** follows December 28 (Gregorian December 31). In leap years **Leap Day** follows June 28
  (Gregorian June 17). Neither has a weekday.
- The year number and January 1 match the Gregorian calendar.

Example: Gregorian Thursday, 17 September 2026 is IFC **September 8, 2026**. Its IFC weekday is
Sunday — which is why the app always makes clear which weekday is which.

## What the app will do

- Show today's date in the IFC, with its Gregorian equivalent
- A month grid you can swipe and tap: any date shows its regular-calendar equivalent
- Convert any date in either direction, past or future — including "what's my IFC birthday?"
- Events and reminders, with recurrence on IFC dates ("every Sol 13", "every Year Day")
- Home-screen widgets that are always right
- Built-in holidays
- First-class handling of Year Day and Leap Day everywhere
- No ads, no tracking, no account, works offline

See [docs/FEATURES.md](docs/FEATURES.md) for the prioritised feature catalog.

## Documentation

| Doc | What it covers |
|---|---|
| [docs/FEATURES.md](docs/FEATURES.md) | What users are asking for, and every planned feature by priority |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Releases, milestones, task breakdown, open decisions |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Tech stack, modules, data model, UI and widget architecture, testing, CI/CD |
| [docs/calendar-spec.md](docs/calendar-spec.md) | The IFC rules, conversion algorithms, type model, and machine-verified test vectors |
| [docs/holidays-and-import.md](docs/holidays-and-import.md) | Holiday rule engine, data licensing, device-calendar overlay, `.ics` import/export |
| [docs/security-and-privacy.md](docs/security-and-privacy.md) | Threat model, permissions, backups, Play policy, repo hygiene |
| [docs/competitive-analysis.md](docs/competitive-analysis.md) | Existing IFC apps, what their users say, and the gaps |
| [docs/WORKFLOW.md](docs/WORKFLOW.md) | How work is done here: the gate, Definition of Done, documentation and anti-drift rules |

## Building

Requires JDK 21 (Android Studio's bundled JBR works). From the repo root:

```powershell
.\gradlew.bat check          # compile, tests, ktlint, KDoc gate
python scripts\check_docs.py # doc link check
```

## Tech stack (planned)

Kotlin · Jetpack Compose + Material 3 · Jetpack Glance widgets · Room · Hilt · Navigation 3 ·
minSdk 26. The calendar core is a pure Kotlin/JVM module with no Android dependencies, verified by
an exhaustive round-trip test over every day of years 1–9999.

## License

Not yet chosen. Until a license is added, all rights are reserved.
