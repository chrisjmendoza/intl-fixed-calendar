# Feature Catalog

What the app must do to be the IFC app people actually want, prioritised from the evidence in
[competitive-analysis.md](competitive-analysis.md). [ROADMAP.md](ROADMAP.md) sequences the work;
[ARCHITECTURE.md](ARCHITECTURE.md) says how it is built.

## Priority legend

| Colour | Priority | Release | Meaning |
|:---:|---|---|---|
| 🔴 | **P0 — Must have** | 1.0 | Users are explicitly asking for it in competitor reviews, or the app is not credible without it. Launch blocker. |
| 🟠 | **P1 — Should have** | 1.0 | Completes 1.0 and makes it polished and trustworthy. Cut only under real schedule pressure. |
| 🟡 | **P2 — Fast follow** | 1.1 – 1.3 | Planned right after launch. The architecture must not block it. |
| 🔵 | **P3 — Later** | unscheduled | Good idea; revisit with real user feedback. |
| ⚪ | **Won't do** (for now) | — | Deliberately out of scope; reason given. |

---

## Part 1 — What makes this app desirable

### The market in one paragraph

On Google Play there are two IFC apps. The incumbent (erkantr, 10K+ downloads, 3.0★) is a single
screen showing the year with today highlighted; the other has about ten downloads and ads. Neither
has a widget, a converter, or a way to tap a date. The only IFC widget on any store is on iOS, and
its reviews say it does not update and that the date is off by one. **The bar is low, the demand is
documented, and the two things that destroy trust are a wrong date and a stale widget.**

### What users are asking for → what we build

Counts are from the 51 incumbent reviews retrieved, plus the iOS app's review feed.

| Pri | What users say | Evidence | Our answer |
|:---:|---|---|---|
| 🔴 | "I was hoping for a fully functional calendar app… what I got was one screen." **No events, reminders, or notes.** | ~10 mentions; the most-upvoted review (22 helpful) | Events with reminders (E1–E4), plus yearly recurrence on an IFC date — something no Gregorian calendar can express (E5) |
| 🔴 | "Just do an image search and you have all this app gives you… can't zoom in on a month." | ~7 mentions | A real month grid you can swipe and tap (C1, C5), with the year overview as just one screen of many (C6) |
| 🔴 | "This app would be PERFECT if it had a widget." | 6 requests; developer replied "Sure" a year ago, nothing shipped. No IFC widget exists on Play | Today widget and month widget (S1, S2) that **reliably** roll over at midnight (S3) |
| 🔴 | "I can't select a specific date to see the translation, like my birthday." | 4 requests | Tap any date for its Gregorian equivalent (C5); two-way converter (D1); "find my IFC birthday" (D3) |
| 🔴 | "You are ahead a few days." / "The date is one day behind." | Wrong-date reviews on **both** the Android incumbent and the iOS app | One exhaustively tested conversion core behind every date in the app, and a "how is this calculated?" explainer (Q1, L2) |
| 🔴 | "The widget just stays on whatever date it was when added." | iOS app's defining complaint; same failure reported for other date widgets on OEM devices | Layered refresh: midnight alarm + time/zone/boot broadcasts + periodic backstop + recompute on every render (S3) |
| 🟠 | "Latest update doesn't show day of the week anymore… the 7th day column is missing… needs to adjust with font size." | 5 mentions | Weekday headers (both kinds), layouts tested at 200% font scale, TalkBack labels (C2, Q4) |
| 🟠 | Confusion: "year should start in April", "shouldn't months follow the moon?", Sol vs Leap Day | ~8 mentions on Android, repeated on iOS; a steady source of 1–3★ "this is wrong" reviews | First-run intro and a Learn/FAQ screen that addresses exactly these beliefs (L1, L2); an accurate Play listing |
| 🟠 | "I would rather buy this. Hate the ads." | Incumbent shares device IDs; the other Play app has ads. The best-loved adjacent calendar apps are all ad-free | No ads, no tracking, no account, no permissions at install, works offline (P1, P2) |
| 🟠 | Nobody displays or explains Year Day / Leap Day well | Gap across every competitor | First-class floating days in the grid, pickers, converter, widgets, and events (C3, D1, E3, S1) |
| 🟡 | "Wish I could have it on my Galaxy Calendar." | 1–2 mentions; the feature that turns a curiosity into a daily driver | Read-only overlay of device calendars on the IFC grid (I1); "add to device calendar" (I2) |
| 🟡 | "Allow users to change the names of the months." | 3 requests (one with 13 helpful votes) | Custom month names and optional numeric months (W4) |
| 🟡 | Incumbent ships 10 languages; a Spanish-language review exists | Table stakes to match the incumbent; Play ranks localised listings | Localisation-ready from day one, translations in 1.x (W5) |
| 🔵 | Moon phases | 3 mentions; shipped by two niche competitors | Optional, off by default, with an explainer — it risks reinforcing the "IFC is lunar" confusion (C12) |
| ⚪ | April/spring year start; weekdays shifted to match the real week | ~5 and 1–2 mentions | Not the IFC. Variants multiply test surface and muddy "correct". Addressed in the FAQ instead (W8) |

### The 1.0 pitch

> The 13-month calendar, done properly: today's date at a glance, a widget that is always right,
> tap any day to see the regular date, convert any date in history, and keep events that repeat on
> *Sol 13* or *Year Day*. No ads, no tracking, works offline.

### Product principles (tie-breakers)

1. **Correct before clever.** Every date shown anywhere comes from the one tested conversion core.
2. **Both calendars, always.** Every IFC date is at most one tap from its Gregorian equivalent, and
   the nominal IFC weekday is never shown without making the real-world weekday clear.
3. **Zero friction, zero creepiness.** No account, ads, or tracking. Permissions are requested in
   context, and the app works fully when they are denied.
4. **The widget is a first-class product.** For many users it *is* the app. It updates silently
   (a toast on refresh was a top complaint against an adjacent app) and it is never stale.
5. **Floating days are a feature, not an edge case.**

---

## Part 2 — Full catalog

### 1. Today & date display

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🔴 | T1 | Today screen: large IFC date with the Gregorian equivalent beneath | App opens here. Renders Year Day / Leap Day as "today" correctly. |
| 🔴 | T2 | Nominal (IFC) weekday vs actual weekday, clearly labelled | calendar-spec §4.1. Reading "Sunday" on a real Thursday must be impossible to do by accident. |
| 🔴 | T6 | Live update at midnight, on time change, and on time-zone change | "Today" is never cached. |
| 🟠 | T3 | Day of year, week (1–52), quarter, year-progress bar | Cheap once the core exists; makes the screen feel alive. |
| 🟠 | T4 | Countdown to the next Year Day / Leap Day | Teaches the calendar's shape. |
| 🟠 | T5 | Today's events and holidays summary | |
| 🟠 | T8 | Share today's date as text | The enthusiast audience evangelises; free marketing. |
| 🔵 | T7 | "On this IFC date" fact / history snippet | Needs curated content. |

### 2. Calendar views

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🔴 | C1 | Month grid: perpetual 4×7, swipe between the 13 months | Each cell: IFC day large, Gregorian date small (the pattern users praise in the best adjacent apps). |
| 🔴 | C5 | Tap a date → day detail (IFC date, Gregorian date, both weekdays, events, holidays) | The owner's core ask and a direct review request. |
| 🔴 | C3 | Floating days as a full-width, tappable row under June (leap years) / December | Row height reserved in every month so the pager never jumps. |
| 🔴 | C2 | Weekday headers: nominal IFC row plus the **actual** weekdays for that month | Fixes the incumbent's "no weekday labels" complaint — honestly. |
| 🟠 | C6 | Year overview: 13 mini-months + floating days | The incumbent's entire feature set, as one screen. Tap to zoom into a month. |
| 🟠 | C4 | Event dots / holiday markers in cells; today highlighted (never by colour alone) | |
| 🟠 | C7 | Jump to date / jump to today | |
| 🟡 | C8 | Agenda list (upcoming events, both dates on each row) | |
| 🟡 | C10 | Gregorian month grid with IFC dates overlaid (the inverse view) | For planning around Gregorian-world deadlines. Owner-requested as an optional view (2026-09-19): the mirror of C1 — Gregorian day large, IFC date small; design it together with the M2 T13 polish pass. |
| 🟡 | C11 | Tablet / foldable / landscape layouts (month + day side by side; year comparison) | Window size classes from day one, so this is additive. |
| 🔵 | C12 | Optional moon-phase indicator | Off by default, with explainer. |
| ⚪ | C9 | Week view | Every IFC week looks the same; the agenda covers the need. |

### 3. Converter & date tools

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🔴 | D1 | Two-way converter with a direction switch | The IFC picker offers Year Day always and Leap Day only in leap years. |
| 🔴 | D2 | Historical and future lookups (1583–9999) | Proleptic-calendar note at the low end (calendar-spec §7.1). |
| 🟠 | D3 | "Find my IFC birthday" | The first thing users try; doubles as the first-run hook. |
| 🟠 | D4 | Copy / share a converted date as text | Always carries the "IFC" marker so it cannot be misread (calendar-spec §7.3). |
| 🟡 | D5 | Date math: days between dates, add/subtract days/weeks/months | Semantics per calendar-spec §7.7. |
| 🟡 | D6 | Recent conversions / pinned dates | |
| 🔵 | D7 | Share as image card | |
| 🔵 | D8 | Open a shared date / link already converted | All external input validated (security doc). |

### 4. Events & reminders

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🔴 | E1 | Create / edit / delete events: title, notes, all-day or timed, location text | The most-upvoted complaint against the incumbent. Stored Gregorian-anchored. |
| 🔴 | E2 | Pick the date in either calendar | |
| 🔴 | E4 | Reminder notifications | Permission asked when the first reminder is set. Survive reboot. Details hidden on the lock screen by default. |
| 🔴 | E5 | Yearly recurrence anchored to **either** calendar ("every Sol 13" vs "every July 1") | The uniquely IFC capability. |
| 🟠 | E3 | Events on floating days | Falls out of the design; tested explicitly. |
| 🟠 | E6 | Leap Day recurrence policy: June 28 (default) / skip / Sol 1 | June 28 IFC is Gregorian June 17 in common years, so the default never moves the Gregorian date. |
| 🟡 | E7 | Daily / weekly / monthly recurrence, including IFC-monthly ("the 13th of every IFC month") | Weekly always follows the real 7-day week. |
| 🟡 | E8 | Categories / colours | |
| 🟡 | E9 | Search events | |
| 🟡 | E10 | Birthdays and anniversaries as a first-class type (age, "celebrate on my IFC date") | |
| 🔵 | E11 | Multiple reminders per event, snooze; edit a single occurrence | |
| ⚪ | E12 | Attendees, invitations, sync server | Needs accounts and a backend. The device-calendar overlay (I1) covers "see my real calendar". |

### 5. Holidays

Strategy and licensing: [holidays-and-import.md](holidays-and-import.md). No Android IFC app has holidays.

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🟠 | H1 | Built-in rule engine (fixed, nth-weekday, Easter-relative, offset, table, IFC-native) | Pure Kotlin; no runtime library, no network. |
| 🟠 | H2 | US pack: federal holidays + common observances, with observed-date rules | Hand-authored from primary sources. |
| 🟠 | H3 | IFC-native days: Year Day, Leap Day, Sol 1 | |
| 🟠 | H5 | Holiday settings: region pack, category toggles | |
| 🟡 | H4 | Lunisolar holidays from bundled pre-generated tables; Islamic dates flagged approximate with a ± adjustment | 1.0 stretch goal; slips to 1.1 without touching the engine. |
| 🟡 | H6 | More country packs | Data-only additions from permissively licensed sources, cross-checked. |
| 🔵 | H7 | "Friday the 13th" and other fun markers | Every IFC month has one — nominally. Charming or noise? Decide with users. |

### 6. Import, export, backup

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🟠 | I6 | Android Auto Backup / device transfer, configured deliberately (encrypted-only) | User data survives a phone change. |
| 🟡 | I1 | Read-only overlay of device calendars on the IFC grid (1.1) | Opt-in `READ_CALENDAR`; read live, never copied. Also surfaces holiday calendars the user already subscribes to. |
| 🟡 | I2 | "Add to device calendar" via insert intent | Permission-free write-back. |
| 🟡 | I3 | `.ics` file import and export (1.2) | System file picker; hardened parsing; preview and undo. |
| 🟡 | I4 | Full backup / restore to a user-chosen file, optionally passphrase-encrypted (1.2) | |
| 🟡 | I5 | `.ics` subscription by URL (1.3) | First use of `INTERNET`; HTTPS only. |
| ⚪ | I7 | Write access to device calendars | Broadens the permission grant; I2 and I3 cover the need. |

### 7. Widgets & system surfaces

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🔴 | S1 | "Today" widget: IFC date + Gregorian equivalent, resizable | The #1 unmet ask on Android. |
| 🔴 | S3 | Bulletproof refresh: midnight, time/zone change, reboot, app update; silent | The #1 broken thing on iOS. This is the quality bar for the whole app. |
| 🟠 | S2 | Month-grid widget with today highlighted | The most-praised widget in the closest adjacent app. |
| 🟠 | S4 | Material You colour, light/dark, widget-picker previews | |
| 🟠 | S5 | Tap targets: widget → today / tapped day | |
| 🟡 | S7 | Agenda widget (next events, both dates) | Ships with S6. |
| 🟡 | S6 | Widget privacy mode: titles / counts only / date only | Required by any widget that shows event text; such widgets are also kept off the lock screen. |
| 🟡 | S8 | Widget configuration: style, which date is primary, weekday mode, optional rows | Users of adjacent apps ask to hide rows they do not use. |
| 🟡 | S9 | Quick Settings tile showing today's IFC date | Small effort, high delight. |
| 🟡 | S10 | App shortcuts: Convert a date, New event | |
| 🔵 | S11 | Optional persistent / daily "today is …" notification | Users of an adjacent app rely on this. |
| 🔵 | S12 | Wear OS tile / complication | The iOS app has a Watch version. |
| ⚪ | S13 | Lock-screen / always-on display | Not available to third-party apps on stock Android. |

### 8. Learn & onboarding

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🟠 | L1 | First-run intro (≤3 screens, skippable): what the IFC is, why weekdays differ, where the floating days live | Ends with "find my IFC birthday". |
| 🟠 | L2 | Learn / About: rules, history (Cotsworth, Eastman Kodak), how dates are calculated, FAQ | FAQ answers the documented confusions: not lunar, starts January 1, Sol vs Leap Day, other 13-month variants. Requested in iOS reviews too. |
| 🟠 | L3 | Contextual explainers (info icon on the nominal-weekday header and on floating-day rows) | |
| 🟠 | L5 | Accurate, well-written Play listing naming IFC, Sol, Year Day, Leap Day | The incumbent's listing conflates Sol with Leap Day. A cheap differentiator. |
| 🔵 | L4 | Printable / shareable year calendar (PDF or image) | A web competitor sells one for $9 — demand exists. |

### 9. Settings & personalisation

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🟠 | W1 | Weekday display: both (default) / actual only / nominal IFC only | To be validated with testers. |
| 🟠 | W2 | Theme: system / light / dark; dynamic colour on/off | The incumbent has a single red theme. |
| 🟠 | W6 | "Delete all data" | |
| 🟡 | W3 | Primary-calendar emphasis (IFC-first or Gregorian-first labels) | |
| 🟡 | W4 | Custom month names; optional numeric months ("Month 7") | 3 reviewer requests. |
| 🟡 | W5 | Translations; per-app language (Android 13+) | All strings in resources from day one — "Sol" included. |
| ⚪ | W7 | Monday-first grids | Breaks the "13th is always a Friday" identity. |
| ⚪ | W8 | Other 13-month variants (April start, World Calendar, 13 Moon, real-week-aligned) | Dilutes "correct" and multiplies test surface. The core's design should not preclude it. |

### 10. Privacy & security

Threat model and rationale: [security-and-privacy.md](security-and-privacy.md), which is
authoritative for this section.

| Pri | ID | Feature | Notes |
|:---:|---|---|---|
| 🟠 | P1 | No ads, analytics, crash SDKs, or account; **no `INTERNET` permission** until 1.3 | A verifiable claim and a selling point, stated in the listing. |
| 🟠 | P2 | Just-in-time permission requests; full function when denied | An adjacent app's worst reviews are about demanding permissions at launch. |
| 🟠 | P3 | Reminder notifications redacted on the lock screen | |
| 🟠 | P4 | Encrypted-only Auto Backup rules | Same as I6. |
| 🟠 | P5 | Privacy policy (required by Play even with no collection) + in-app Privacy screen | Needed before any Play track, including closed testing. |
| 🟡 | P6 | Optional app lock (biometric / device credential); also hides the app in Recents | A UI gate, not encryption. |
| 🟡 | P7 | Widget privacy mode; "hide details in notifications" toggle | Same as S6. |
| 🟡 | P8 | Passphrase-encrypted backup export | Ships after plain export (I4). |
| 🟡 | P9 | Hardened `.ics` parsing; HTTPS-only subscriptions | Ships with I3 / I5. |
| ⚪ | P10 | Encrypted database (SQLCipher) | Only helps against forensic attackers, who are out of scope; would break widgets and reminders. |

### 11. Quality bar

Requirements, not features. They apply from the first milestone and are all 🔴 for the release they ship in.

| ID | Area | Requirement |
|---|---|---|
| Q1 | Correctness | Conversion core verified by exhaustive round-trip tests (every day, years 1–9999) and the machine-generated vectors in calendar-spec §6. No date is computed outside the core. |
| Q2 | Time | All "today" logic uses an injectable `Clock`; no epoch-millisecond arithmetic; tested across DST, time-zone changes, and year boundaries. |
| Q3 | Offline | 100% functional with no network, forever. |
| Q4 | Accessibility | TalkBack reads both calendars sensibly; 200% font scale never clips (a documented incumbent failure); colour is never the only signal; touch targets ≥ 48dp. |
| Q5 | Performance | Cold start to Today feels instant on a mid-range device; month swipes never drop frames; baseline profile shipped. |
| Q6 | Size | Small APK; no heavyweight dependency without a written justification. |
| Q7 | Platform | Edge-to-edge, predictive back, dynamic colour, latest required target API at each release. |
| Q8 | Localisation | No hard-coded strings or date formats; RTL-safe layouts. |
| Q9 | Data safety | Room schema exported and every migration tested; user data never lost on upgrade. |
| Q10 | Battery | No polling. Widget refresh is event-driven plus one scheduled rollover per day. |
| Q11 | Reliability | Widgets and reminders survive reboot, app update, and aggressive OEM battery management as far as the platform allows; known OEM limits documented in-app. |
| Q12 | Privacy | No event content in logs, ever. |

### 12. Monetisation

Free, no ads. The niche is small and ratings matter more than revenue; ads are a documented
complaint against the incumbent, and reviewers say they would "rather buy" than see them. If costs
ever need covering: an optional supporter purchase for cosmetic extras (widget themes, PDF export),
never feature gating. Decision deferred until after 1.0.
