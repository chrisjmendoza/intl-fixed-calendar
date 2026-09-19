# Holidays and Event Import Strategy

Status: current as of M4 T6 (2026-09-18). The rule engine (§2.2) exists in `:core:domain` and the packs and loader (§2.4, §2.5) in `:core:holidays`, unchanged since M1; see [adr/0003-holiday-rule-model.md](adr/0003-holiday-rule-model.md) and [adr/0004-holiday-pack-format.md](adr/0004-holiday-pack-format.md) for the decisions this document left open. Since M1, the engine is also consumed through the domain-level `HolidaySetProvider` seam (`:core:domain`, implemented by `PackHolidaySetProvider` in `:feature:calendar`) so `ObserveAgendaUseCase` can fold holidays into the agenda — see [ARCHITECTURE.md](ARCHITECTURE.md) §3.3. The `calendar` rule type (§2.3) is not implemented yet (FEATURES H4), and none of §4 (device calendars, `.ics` import/export, URL subscriptions) has shipped yet — those remain v1.1–v1.3 as phased in §7.
Scope: how the IFC app gets holidays (built-in vs. imported), how it imports/exports events, and how "IFC-native" recurrences work.

Fixed context assumed throughout: Kotlin, Jetpack Compose, minSdk 26, Room, offline-first, no accounts/backend, pure-Kotlin calendar core on `java.time`. All events and holidays are anchored to Gregorian `LocalDate` and *displayed* in IFC.

Claims marked **(not verified)** could not be confirmed from a primary source during this research pass and need a follow-up check or an on-device test.

---

## 1. TL;DR — decisions

1. **Build our own small holiday rule engine** (pure Kotlin, in the calendar core) with holiday definitions bundled as per-region JSON. Do not take a runtime dependency on any holiday library — none fits Android + offline + permissive licensing well.
2. **Hand-author the US set** from primary sources (5 U.S.C. § 6103 etc.). It is ~11 federal + ~30 observances and only needs six rule types.
3. **Lunisolar holidays come from bundled pre-computed date tables**, generated at dev time with ICU4J (Chinese, Hebrew, Islamic Umm al-Qura) plus a curated table for Diwali/Holi. Use Android's `android.icu.util` calendars only as a runtime fallback outside the table range. Always label Islamic dates "approximate — subject to moon sighting" and offer a per-user ±1/±2 day adjustment.
4. **International coverage arrives first via the device calendar**, not via our data: a read-only `CalendarContract` overlay (opt-in, `READ_CALENDAR`) shows the user's Google Calendar events *and* whatever holiday calendars they already subscribe to, for any country, with zero licensing exposure for us.
5. **ICS import/export uses biweekly** (BSD-2-Clause) — or a hand-rolled writer for export. ical4j is the heavier alternative; lib-recur (Apache-2.0) is the fallback RRULE expander.
6. **Never bundle or scrape Google's holiday ICS feeds, Calendarific, or Nager.Date API output.** Users may paste any ICS URL themselves.
7. **IFC-native recurrence is a first-class recurrence basis** (`basis = IFC`), stored separately from Gregorian RRULEs, with an explicit skip policy for Leap Day in common years (modelled on RFC 7529 `SKIP=OMIT|BACKWARD|FORWARD`).

Phasing: **MVP** = built-in US + IFC-native holidays, no permissions. **v1.1** = device calendar read-only overlay. **v1.2** = `.ics` file import + export. **v1.3** = ICS URL subscriptions (first use of `INTERNET`). **Later** = more country packs, write-back via intents, downloadable rule packs. Details in [section 7](#7-recommended-phased-approach).

---

## 2. Built-in rule engine

### 2.1 Why build instead of depend

- The rule taxonomy is small and well understood (Jollyday, date-holidays and python-holidays all converge on the same ~8 rule kinds).
- Every mature holiday library targets server JVM / .NET / Python / JS. None is a clean Android dependency (see [section 3](#3-existing-libraries-and-datasets)).
- A pure-Kotlin engine on `java.time.LocalDate` is unit-testable on the JVM, has zero APK cost, and keeps holiday data as *data* we can extend or ship as downloadable packs later.
- Holidays are **computed on the fly per visible year and cached in memory**. They are not rows in Room. Room stores only user choices: enabled holiday sets, hidden holidays, and per-holiday day adjustments.

### 2.2 Rule types needed

| # | Rule type | Meaning | Examples |
|---|-----------|---------|----------|
| 1 | `fixed` | Gregorian month/day | Independence Day (Jul 4), Christmas |
| 2 | `nthWeekday` | nth weekday of a Gregorian month; negative n counts from the end | Thanksgiving (4th Thu Nov, n=4); Memorial Day (last Mon May, n=-1) |
| 3 | `weekdayRelative` | first given weekday on/after or on/before a fixed date | Victoria Day CA (Mon before May 25); Swedish Midsummer (Sat in Jun 20–26) |
| 4 | `offset` | N days from another rule | Election Day (first Mon Nov **+1**); Black Friday (Thanksgiving **+1** — *not* "4th Friday", which is wrong when Nov 1 is a Friday) |
| 5 | `easter` | Computus + offset; `western` (Gregorian computus, Meeus/Jones/Butcher) or `orthodox` (Julian computus, then Julian→Gregorian shift of `⌊Y/100⌋ − ⌊Y/400⌋ − 2` days; 13 days for 1900–2099) | Good Friday (−2), Ash Wednesday (−46), Mardi Gras (−47), Pentecost (+49), Orthodox Easter |
| 6 | `calendar` | month/day in another calendar system (`hebrew`, `islamic`, `chinese`, `dangi`, ...) resolved via table first, ICU fallback | Rosh Hashanah (1 Tishrei), Eid al-Fitr (1 Shawwal), Lunar New Year (1/1 Chinese) |
| 7 | `table` | explicit list of Gregorian dates by year | Diwali, Holi, officially announced one-off dates, solstices/equinoxes |
| 8 | `ifc` | IFC-native date: IFC month/day or the specials `YEAR_DAY` / `LEAP_DAY` | Year Day, Leap Day, Sol 1 ("Sol Day") — see [section 5](#5-ifc-native-recurring-holidays-and-events) |

Modifiers applicable to any rule:

| Modifier | Purpose |
|----------|---------|
| `since` / `until` | validity years (Juneteenth since 2021; MLK Day since 1986) |
| `yearFilter` | every-N-years (`{mod:4, eq:1}` Inauguration Day; `{mod:2, eq:0}` federal Election Day) |
| `observed` | named shifting policy producing an additional "(observed)" entry; the actual date is kept |
| `durationDays` | multi-day (Hanukkah 8, Kwanzaa 7) |
| `startsEveBefore` | Hebrew/Islamic days begin at sundown the previous evening — UI hint only |
| `approximate` | date may differ ±1–2 days from official announcement (Islamic, some Hindu) |
| `category` | `public` \| `bank` \| `observance` \| `religious` \| `ifc` — drives filtering and styling |
| `subdivisions` | ISO 3166-2 codes when not nationwide (later) |

**Observed-date policies** are named, coded once, and referenced from data:

- `us_federal`: Saturday → preceding Friday; Sunday → following Monday. Edge case: Jan 1 on a Saturday is observed on **Dec 31 of the previous year**, so rendering December of year Y must also evaluate year Y+1's rules.
- `next_monday`: Sat/Sun → following Monday (many Commonwealth rules).
- `sunday_to_monday`: Sunday → following Monday only; Saturday unchanged (Inauguration Day, 20th Amendment practice).
- `uk_substitute`: like `next_monday` but collision-aware (Christmas + Boxing Day push to Monday/Tuesday). Later.
- `none` (default).

**Weekday semantics.** Rules 2–4 are defined on *Gregorian civil weekdays and Gregorian months*. They are always evaluated in Gregorian and the result is then converted for display. If the app displays IFC perpetual weekdays (every month starts on Sunday, Year Day/Leap Day outside the week), "the 4th Thursday of November" will generally not land on an IFC Thursday or even necessarily in IFC November. That is a display question owned by the calendar-core/UX docs; the engine must never evaluate weekday rules against IFC weekdays.

### 2.3 Lunar and lunisolar holidays: ICU evaluation

Android ships ICU calendars in `android.icu.util` since API 24 (we are minSdk 26): `ChineseCalendar`, `DangiCalendar`, `HebrewCalendar`, `IslamicCalendar`, `IndianCalendar`, `CopticCalendar`, `EthiopicCalendar`, `JapaneseCalendar`, `BuddhistCalendar`, `TaiwanCalendar`. They work fully offline.

| Calendar | ICU class | Fit for holidays | Notes |
|----------|-----------|------------------|-------|
| Hebrew | `HebrewCalendar` | **Exact.** Purely arithmetic calendar. | Watch Adar I/II in leap years (Purim is in Adar II). Israeli civil postponement rules (Yom HaShoah, Yom HaAtzmaut) are extra `observed` policies — not needed for the US set. |
| Chinese / Korean | `ChineseCalendar`, `DangiCalendar` | **Good.** Astronomical, computed for the reference meridian (Beijing / Seoul). | Vietnamese Tết uses UTC+7 and occasionally differs by a day (e.g. 2030 — **not verified**). Leap months via `IS_LEAP_MONTH`. |
| Islamic | `IslamicCalendar` with calculation types `ISLAMIC_CIVIL` (default, tabular), `ISLAMIC_TBLA`, `ISLAMIC_UMALQURA`, `ISLAMIC` (astronomical approximation) | **Approximate by nature.** | ICU's own docs: the religious calendar is "based on the observation of the crescent moon" and ICU's algorithms are "only approximations ... fairly simplistic" ([ICU4J IslamicCalendar](https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/util/IslamicCalendar.html)). Use Umm al-Qura as the default estimate; real observance varies by country and community by ±1–2 days. Range of ICU's Umm al-Qura table: **not verified**. |
| Hindu (Diwali, Holi) | *none* | **Not computable with ICU.** | `IndianCalendar` is the Saka **civil** (solar, Gregorian-synchronised) national calendar, not the lunisolar panchanga ([ICU4J IndianCalendar](https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/util/IndianCalendar.html)). Diwali/Holi need a `table` rule. |
| Coptic / Ethiopic | `CopticCalendar`, `EthiopicCalendar` | Exact (arithmetic). | Useful later for Orthodox Christmas variants, Enkutatash. |

Two problems with calling `android.icu` directly at runtime:

1. **It breaks the pure-Kotlin core.** `android.icu.*` does not exist on the JVM, so core unit tests could not run without Robolectric or a parallel ICU4J dependency.
2. **ICU version = OS version.** The platform ICU is updated with Android releases, so astronomical results (Chinese, Islamic) could in principle differ between devices. For a calendar app, two phones disagreeing on a holiday is a bad bug class.

**Decision: tables first, ICU as fallback.**

- A dev-time script (JVM, [ICU4J](https://github.com/unicode-org/icu), Unicode license — permissive) generates Gregorian dates for each lunisolar holiday for roughly 1950–2100 and writes them into bundled JSON tables. Size is trivial (≈15 holidays × 150 years × a few bytes).
- The core sees only `table` data → deterministic on every device, testable on plain JVM, no runtime dependency.
- The core defines an interface (e.g. `ForeignCalendarResolver`); the Android app module provides an `android.icu` implementation used only for years outside the table range, and for any future "show this date in the Hebrew/Islamic calendar" feature.
- Diwali/Holi tables are curated by hand from published almanacs and cross-checked against python-holidays/date-holidays output (as a *verification oracle*, not copied wholesale).
- Islamic entries carry `approximate: true`. UI shows "approximate — subject to moon sighting" and Settings offers a global Hijri adjustment (−2…+2 days), as is conventional in Islamic calendar apps.

### 2.4 Data format

JSON, bundled as classpath resources `/holidays/<name>.json` in `:core:holidays` (pure JVM, so not `assets/`), parsed with kotlinx.serialization (polymorphism on `rule.type`). JSON rather than a Kotlin DSL because data files can later be shipped as downloadable/updatable packs, generated by scripts, and diffed/reviewed by non-Kotlin contributors. Names are embedded per locale with `en` fallback so packs are self-contained.

**Implemented as schema 1** (`HolidayPackLoader`, [adr/0004-holiday-pack-format.md](adr/0004-holiday-pack-format.md)), with these precisions over the example below: the pack has a required `id`; `weekday` is `MON`..`SUN`; `weekdayRelative.direction` is `onOrAfter` | `onOrBefore`; the `ifc` rule takes either `month`/`day` or `special`; `table` is inline `"dates": { "2024": "2024-11-01", … }`; unknown keys are rejected. The example's `"table": "diwali"` file reference and its two `calendar` entries do **not** load in schema 1 — both arrive with FEATURES H4. Shipped packs: `ifc`, `US` (Tax Day omitted as deferred; Columbus Day carries both names in one `en` string; `since` set for Inauguration Day 1937, Patriot Day 2002, Kwanzaa 1966) and `religious-christian` (Easter family, ids `x.*`).

```json
{
  "schema": 1,
  "region": "US",
  "name": { "en": "United States" },
  "sources": ["5 U.S.C. § 6103"],
  "holidays": [
    { "id": "us.new_year", "name": { "en": "New Year's Day" },
      "rule": { "type": "fixed", "month": 1, "day": 1 },
      "observed": "us_federal", "category": "public" },

    { "id": "us.mlk", "name": { "en": "Martin Luther King Jr. Day" },
      "rule": { "type": "nthWeekday", "month": 1, "weekday": "MON", "n": 3 },
      "since": 1986, "category": "public" },

    { "id": "us.memorial", "name": { "en": "Memorial Day" },
      "rule": { "type": "nthWeekday", "month": 5, "weekday": "MON", "n": -1 },
      "since": 1971, "category": "public" },

    { "id": "us.election", "name": { "en": "Election Day" },
      "rule": { "type": "offset", "days": 1,
                "base": { "type": "nthWeekday", "month": 11, "weekday": "MON", "n": 1 } },
      "yearFilter": { "mod": 2, "eq": 0 }, "category": "observance" },

    { "id": "x.good_friday", "name": { "en": "Good Friday" },
      "rule": { "type": "easter", "calendar": "western", "offset": -2 },
      "category": "religious" },

    { "id": "x.hanukkah", "name": { "en": "Hanukkah" },
      "rule": { "type": "calendar", "system": "hebrew", "month": "KISLEV", "day": 25 },
      "durationDays": 8, "startsEveBefore": true, "category": "religious" },

    { "id": "x.eid_al_fitr", "name": { "en": "Eid al-Fitr" },
      "rule": { "type": "calendar", "system": "islamic-umalqura", "month": "SHAWWAL", "day": 1 },
      "approximate": true, "startsEveBefore": true, "category": "religious" },

    { "id": "x.diwali", "name": { "en": "Diwali" },
      "rule": { "type": "table", "table": "diwali" },
      "approximate": true, "category": "religious" },

    { "id": "ifc.year_day", "name": { "en": "Year Day" },
      "rule": { "type": "ifc", "special": "YEAR_DAY" }, "category": "ifc" }
  ]
}
```

Suggested file split: `US.json` (civil), `religious-christian.json`, `religious-jewish.json`, `religious-islamic.json`, `religious-hindu.json`, `cultural-eastasia.json`, `ifc.json`, plus `tables/*.json`. The user enables *sets*; religious/cultural sets are region-independent, which also keeps future country files small.

### 2.5 US starter set

**Federal holidays** (5 U.S.C. § 6103) — all `category: public`, fixed-date ones use `observed: us_federal`:

| Holiday | Rule type | Definition | Notes |
|---------|-----------|------------|-------|
| New Year's Day | `fixed` + observed | Jan 1 | Sat → Dec 31 of previous year |
| Martin Luther King Jr. Day | `nthWeekday` | 3rd Mon Jan | since 1986 |
| Washington's Birthday (Presidents' Day) | `nthWeekday` | 3rd Mon Feb | since 1971 |
| Memorial Day | `nthWeekday` (n=-1) | last Mon May | since 1971 |
| Juneteenth | `fixed` + observed | Jun 19 | since 2021 |
| Independence Day | `fixed` + observed | Jul 4 | |
| Labor Day | `nthWeekday` | 1st Mon Sep | |
| Columbus Day / Indigenous Peoples' Day | `nthWeekday` | 2nd Mon Oct | since 1971; two display names |
| Veterans Day | `fixed` + observed | Nov 11 | (was 4th Mon Oct 1971–1977; ignore unless historical accuracy matters) |
| Thanksgiving | `nthWeekday` | 4th Thu Nov | since 1942 |
| Christmas Day | `fixed` + observed | Dec 25 | |
| Inauguration Day | `fixed` + `yearFilter {mod:4,eq:1}` | Jan 20; Sun → Mon Jan 21 | DC-area only; `category: observance` |

**Common observances** (`category: observance` unless noted):

| Holiday | Rule type | Definition |
|---------|-----------|------------|
| Groundhog Day | `fixed` | Feb 2 |
| Valentine's Day | `fixed` | Feb 14 |
| Daylight Saving Time begins / ends | `nthWeekday` | 2nd Sun Mar / 1st Sun Nov (since 2007) |
| St. Patrick's Day | `fixed` | Mar 17 |
| Tax Day | `fixed` + custom observed | Apr 15, weekend and DC Emancipation Day shifts — defer to v1.x, mark approximate until the policy is coded |
| Earth Day | `fixed` | Apr 22 |
| Cinco de Mayo | `fixed` | May 5 |
| Mother's Day | `nthWeekday` | 2nd Sun May |
| Flag Day | `fixed` | Jun 14 |
| Father's Day | `nthWeekday` | 3rd Sun Jun |
| Patriot Day | `fixed` | Sep 11 |
| Halloween | `fixed` | Oct 31 |
| Election Day | `offset` | (1st Mon Nov) + 1, even years |
| Black Friday | `offset` | Thanksgiving + 1 |
| Pearl Harbor Remembrance Day | `fixed` | Dec 7 |
| Christmas Eve / New Year's Eve | `fixed` | Dec 24 / Dec 31 |
| Kwanzaa | `fixed`, `durationDays: 7` | Dec 26 |

**Religious / cultural sets** (separately toggleable, default on for Easter-family only):

| Holiday | Rule type | Definition |
|---------|-----------|------------|
| Mardi Gras, Ash Wednesday, Palm Sunday, Good Friday, Easter | `easter` western | −47, −46, −7, −2, 0 |
| Orthodox Easter | `easter` orthodox | 0 |
| Passover | `calendar` hebrew | 15 Nisan, 8 days, eve-before |
| Rosh Hashanah | `calendar` hebrew | 1 Tishrei, 2 days, eve-before |
| Yom Kippur | `calendar` hebrew | 10 Tishrei, eve-before |
| Hanukkah | `calendar` hebrew | 25 Kislev, 8 days, eve-before |
| Ramadan begins | `calendar` islamic-umalqura | 1 Ramadan, approximate |
| Eid al-Fitr | `calendar` islamic-umalqura | 1 Shawwal, approximate |
| Eid al-Adha | `calendar` islamic-umalqura | 10 Dhu al-Hijjah, approximate |
| Lunar New Year | `calendar` chinese | month 1 day 1 |
| Diwali, Holi | `table` | curated, approximate |

**IFC set** (enabled by default and switchable like any other set; the app's signature): Year Day, Leap Day, Sol 1, optionally "IFC month begins" markers and Friday the 13th jokes (every IFC month has one). See section 5.

Deferred: solstices/equinoxes (need an astronomical routine or a `table`), state holidays (`subdivisions`), Tax Day policy.

---

## 3. Existing libraries and datasets

| Source | License | Coverage | Offline bundling | Android fit | Verdict |
|--------|---------|----------|------------------|-------------|---------|
| [Jollyday (focus-shift)](https://github.com/focus-shift/jollyday) | Apache-2.0 (code and XML rule data) | 230+ countries, subdivisions; Christian, Islamic, Ethiopian Orthodox rule types; Hebrew/Chinese not mentioned in README | Yes — rule data is in-repo XML | **Poor at runtime**: requires JDK 17, XML binding via JAXB or Jackson-XML (JAXB is absent on Android; Jackson XML/StAX on Android is fragile — **not verified** to work); v2.x actively released (latest on Maven Central ≈ early Sep 2026). The old [jollyday-android](https://github.com/galgtonold/jollyday-android) fork (Apache-2.0) has 9 commits and is effectively dead. | **Do not depend on it. Use as the rule-taxonomy blueprint and as the preferred source for future country packs**: convert its XML to our JSON at build/dev time with attribution (Apache-2.0 NOTICE). |
| [Nager.Date / Nager.Holidays](https://github.com/nager/Nager.Date) | MIT (code). Offline use of the NuGet/Docker builds requires a **sponsor license key** | 200+ countries; public/bank/school/optional types; lunar coverage not documented | **No practical path**: .NET library, rules live in C# code not data; offline is key-gated | None as a library. [Public REST API](https://nagerholidays.com/Api) is free, "no rate limits", CORS enabled; terms on caching/redistribution **not verified** | **API-only → conflicts with offline-first.** At most an optional "fetch public holidays for country X" cache later. Do not bundle its output. |
| [python-holidays (vacanza)](https://github.com/vacanza/holidays) | MIT | ~250 country codes + financial markets; Christian/Hebrew/Islamic/Chinese/Hindu categories; very active | Indirect: rules are Python code with internal lookup tables for lunar dates; run it at dev time to **emit date tables** | None at runtime (Python) | **Best verification oracle** for our engine's tests, and a legitimate (MIT, attribution) generator for `table`-based country packs. Generated tables expire, so pick a 15–20 year horizon. |
| [date-holidays (JS)](https://github.com/commenthol/date-holidays) | Code ISC; **data CC BY-SA 3.0** | 206 countries; YAML rule grammar; Islamic 1970–2080, Hebrew 1970–2100, Chinese; warns Islamic dates depend on moon sighting | Possible, but share-alike + attribution attaches to any derived data file | None at runtime (JS) | **Reference only.** Its grammar is a good second opinion for rule design; avoid converting its data unless we are happy to publish our data files under CC BY-SA. |
| [OpenHolidays API](https://www.openholidaysapi.org/en/) / [data repo](https://github.com/openpotato/openholidaysapi.data) | **ODbL-1.0** (data) | 37 countries, mostly Europe + BR/MX/ZA; **no US**; includes *school* holidays (rare) | Yes, raw data on GitHub; ODbL requires attribution and share-alike of the *derived database* | Data only (date lists, not rules) | **Later / optional.** Only interesting for European school holidays. |
| [Calendarific](https://calendarific.com/pricing) | Commercial API | 230+ countries | Bundling/caching rights **not verified**; free tier = 500 calls/month, attribution required, limited date range | An API key would have to ship inside the APK (we have no backend) → trivially extractable | **Reject.** |
| Google public holiday ICS feeds (`https://calendar.google.com/calendar/ical/en.usa%23holiday%40group.v.calendar.google.com/public/basic.ics`) | No license granted | Every country Google Calendar supports, plus religious sets | **No.** Google states the data comes "from a third-party company that specializes in this data" ([help page](https://support.google.com/calendar/answer/13748345?hl=en)) — Google itself is a licensee. [Google APIs ToS §5.e](https://developers.google.com/terms) forbids scraping, building databases or keeping permanent copies of content (whether the public ICS endpoint falls under the APIs ToS or only the general Google ToS: **not verified**). URLs are undocumented and can change. | Works technically as a user-supplied ICS subscription | **Never bundle, never scrape, do not hardcode as a built-in source.** Fine if the *user* pastes the URL, and better still: read the same calendars via `CalendarContract` where the user already has them. |
| [Unicode CLDR](https://cldr.unicode.org/) / ICU | Unicode license | **No holiday data at all.** A [2005 proposal to add holiday rules](https://unicode.org/mail-arch/unicode-ml/y2005-m10/0122.html) went nowhere. | n/a | ICU is already on-device | **Not a holiday source.** Useful for calendar-system math (section 2.3), localized month/day names, and locale week data (first day of week, weekend days). |

Licensing note (not legal advice): individual holiday dates are facts, but (a) curated compilations can be protected (EU sui generis database right, copyright in selection/arrangement), and (b) API terms of service bind us contractually regardless of copyright. The clean routes are: author rules from primary legal sources; convert permissively licensed rule data (Apache-2.0 / MIT) with attribution; keep share-alike data (CC BY-SA, ODbL) out of the bundle unless we deliberately accept the obligations. Ship an "Open-source licenses & data sources" screen from day one.

---

## 4. Import and export paths

### 4.1 Device calendars via `CalendarContract` (READ_CALENDAR)

**What it gives us.** Every calendar synced to the device's Calendar Provider — Google accounts, Exchange, Samsung, DAVx⁵/CalDAV, ICSx⁵ subscriptions, local calendars — drawn onto the IFC grid. Google's "Holidays in <country>" and religious-holiday calendars are ordinary synced read-only calendars, so users who have them enabled get international holidays in our app with no data from us. (Widely observed in third-party calendar apps; visibility of Google's holiday and Birthdays calendars to third-party readers should be **verified on a real device** before we promise it in the listing.)

**Implementation shape.**

- List calendars from `CalendarContract.Calendars` (display name, account, color, `VISIBLE`, `SYNC_EVENTS`). Let the user tick which to overlay; default to `VISIBLE = 1`. Persist only the chosen calendar IDs.
- Read occurrences from **`CalendarContract.Instances`** for the visible date range. The provider expands recurrences, exceptions and time zones for us — **no RRULE code needed for this path.** Query by month/visible window, narrow projection, `CALENDAR_ID IN (...)`.
- All-day events are stored as **UTC midnight** boundaries: convert `BEGIN`/`END` with `ZoneOffset.UTC` to `LocalDate` (end exclusive). Timed events convert with the device zone. Getting this wrong shifts all-day events by one day west of UTC — the classic bug.
- **Do not copy device events into Room.** Query live, keep in memory, refresh through a `ContentObserver`. This keeps us honest on privacy ("never stored, never leaves the device"), avoids staleness, and avoids a sync engine.
- Use the calendar's own color; visually distinguish device events from app-native events and built-in holidays. De-duplicate obvious doubles (built-in "Thanksgiving" vs. Google's "Thanksgiving Day") by same-date + fuzzy name, or simply let the user turn off the built-in set.
- Work-profile calendars are not reachable from a personal-profile app (and vice versa) — document as a known limitation.

**Permission UX.**

- `READ_CALENDAR` is a *dangerous* runtime permission. The app must be fully useful without it (that is why built-in holidays come first).
- Never ask at first launch. Offer a "Show my device calendars" card/setting; tapping it shows a one-screen rationale ("Events are read on this device only to draw them on the IFC grid. Nothing is uploaded; the app has no server.") and then the system dialog.
- On Android 11+ a second denial means "don't ask again": detect it and deep-link to app settings instead of re-prompting. Handle revocation and the unused-app permission auto-reset gracefully (overlay silently turns off, card reappears).
- Declaring both `READ_CALENDAR` and `WRITE_CALENDAR` makes the system dialog grant the whole Calendar group — so **declare only `READ_CALENDAR`** until a write feature actually ships; the dialog wording and the Play listing then both say "read".

**Google Play policy implications** (checked 2026-09-17):

- Calendar permissions are **not** on the list that requires a Permissions Declaration Form (that list covers SMS, Call Log, background location, all-files access, package visibility, etc.) — [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en). The [preview policy effective 2027-01-27](https://support.google.com/googleplay/android-developer/answer/16909972?hl=en) adds a contacts-picker requirement and a location-button recommendation but **does not mention calendar**.
- The general rule still applies: request only permissions "necessary to implement current features or services in your app that are promoted in your Google Play listing", request in context, and respect denial. → The store listing must describe the device-calendar overlay.
- **A privacy policy URL is mandatory for every app**, even with zero collection ([Data safety help](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)).
- Data safety form: "Calendar events" is a declared data type, **but** data "only processed locally on the user's device and not sent off device does not need to be disclosed". Our overlay qualifies *as long as* nothing leaks: no event titles in crash reports, analytics or logs. Adding any crash/analytics SDK later requires re-auditing this.
- Direction of travel: Google is replacing broad permissions with pickers + declarations (photos, now contacts). Calendar could be next. Keep the overlay optional and isolated behind an interface so a policy change cannot take down the app.

**Read-only vs. write-back.**

| Option | Permission | Pros | Cons |
|--------|-----------|------|------|
| Read-only overlay | `READ_CALENDAR` | Simple, low risk, trustworthy story | Cannot create/edit device events in-app |
| Write via **intent** (`Intent.ACTION_INSERT` on `CalendarContract.Events.CONTENT_URI`, `ACTION_EDIT`/`ACTION_VIEW` on an event URI) | **none** | User's own calendar app does the write; zero policy surface | Leaves our UI; limited prefill; no bulk |
| Direct write | `WRITE_CALENDAR` | Full in-app editing; could publish IFC holidays as a device calendar | Risk of corrupting user data; recurrence-exception editing is hard; sync-adapter semantics (`DIRTY`, account types); larger permission ask; more Play scrutiny |

**Decision:** read-only overlay + intent-based "open/add in calendar app". Revisit `WRITE_CALENDAR` only if users clearly want IFC holidays pushed *into* Google Calendar — and note that ICS export (4.3) already covers that use case without any permission.

### 4.2 `.ics` file import and URL subscription

**File import.** Use the Storage Access Framework (`ACTION_OPEN_DOCUMENT`, MIME `text/calendar`) — no storage permission. Also register an intent filter for `text/calendar` / `.ics` so "Open with" works from mail and file apps. Imported events are copied into Room as app-native events (tagged with a source/import batch ID so an import can be undone).

**URL subscription.** Accept `https://` and `webcal://` (rewrite to `https://`; cleartext HTTP is blocked by default on Android 9+ — keep it blocked). Refresh with WorkManager (daily, unmetered-friendly, conditional GET with ETag/Last-Modified), replace-all per subscription keyed by `UID`. This is the app's **first use of the `INTERNET` permission** — a meaningful change to the "fully offline, no network" positioning, which is why it is phased last. Subscription URLs often embed secrets (Google "secret address in iCal format"): store locally only, never log them.

**Parsing library evaluation.**

| Library | License | State (checked 2026-09) | Android fit | Notes |
|---------|---------|-------------------------|-------------|-------|
| [biweekly](https://github.com/mangstadt/biweekly) | BSD-2-Clause | 0.6.8 (Jan 2024); low-activity single maintainer, last commits Jun 2025 | **Explicitly supports Android**, Java 6+ bytecode, small; deps: `vinnie` + `jackson-core` (only needed for jCal/JSON — excludable) | Parser + writer + built-in recurrence iterator (derived from google-rfc-2445) incl. EXDATE/RDATE handling. API is `java.util.Date`/`TimeZone`-based → needs a thin adapter to `java.time`. |
| [ical4j](https://github.com/ical4j/ical4j) | BSD-3-Clause | 4.3.0 (≈ Jul 2026), actively maintained | Workable but heavier: 4.x needs Java 11 + `java.time` (fine at minSdk 26); deps slf4j, commons-lang3, commons-codec, threeten-extra; Groovy classes and bundled zoneinfo must be excluded/shrunk; [official Android notes](https://www.ical4j.org/android/) only document up to 3.x (API 26 + Java 11); 4.x uses `ServiceLoader`, flagged by the authors as an Android concern — **4.x on Android not verified** | Most complete RFC 5545 model; used by DAVx⁵/ICSx⁵, but their Android glue ([ical4android](https://github.com/bitfireAT/ical4android), archived Jun 2025, superseded by bitfireAT/synctools) is **GPL-3.0** — cannot be reused unless the app is GPL. |
| [lib-recur (dmfs)](https://github.com/dmfs/lib-recur) | Apache-2.0 | 0.17.1, last release ≈ 2024; API declared "not finalized" | Pure Java, Android heritage (dmfs OpenTasks) | **RRULE expansion only**, no ICS parsing. Strict/lax RFC 5545 + RFC 2445 modes, RDATE/EXDATE sets, and **RFC 7529 RSCALE** support. |
| Hand-rolled | — | — | — | Reasonable for **export only** (writing VEVENTs is easy: folding at 75 octets, text escaping, `DTSTART;VALUE=DATE`). Not reasonable for import (time zones, folding, encodings, vendor quirks). |

**Decision:** biweekly for import (and export unless the hand-rolled writer is done first). It is the only candidate that is small, permissive, and explicitly Android-compatible; the ICS format is frozen, so low maintenance activity is tolerable. Wrap it behind our own `IcsParser` interface so ical4j 4.x can be swapped in if biweekly stalls. Keep lib-recur in reserve as the RRULE expander if biweekly's iterator shows correctness problems.

**RRULE handling.**

- Store the original `RRULE`/`RDATE`/`EXDATE` text plus `DTSTART` and TZID in Room; **expand lazily per visible window**, never materialise all instances (infinite rules).
- Holiday-style feeds are overwhelmingly all-day events with either no RRULE or `FREQ=YEARLY` → the MVP-of-import subset is: all-day + timed events, `FREQ=DAILY|WEEKLY|MONTHLY|YEARLY`, `INTERVAL`, `COUNT`, `UNTIL`, `BYDAY`, `BYMONTHDAY`, `BYMONTH`, `BYSETPOS`, `EXDATE`, and `RECURRENCE-ID` overrides. Anything unsupported imports as a single occurrence with a visible "recurrence not supported" flag rather than being dropped silently.
- `VTIMEZONE`: prefer mapping `TZID` to `java.time.ZoneId` (IANA names cover almost all real-world feeds; keep a small alias map for Windows zone names from Outlook) over interpreting embedded VTIMEZONE rules.
- Gregorian RRULEs are always evaluated in Gregorian. The IFC grid is a view; "monthly on the 15th" means Gregorian 15th unless the event's recurrence basis is IFC (section 5).

### 4.3 Export

- **`.ics` export** of app-native events and (optionally) the enabled built-in/IFC holiday sets for a year range. Deliver via `ACTION_CREATE_DOCUMENT` (save) or `FileProvider` + `ACTION_SEND` (share). This is the permission-free way to get "Year Day" and "Sol 1" into Google Calendar/Outlook, and it doubles as the user's backup/portability story (no accounts, no backend → export matters).
- **Single event → device calendar** via `Intent.ACTION_INSERT` (no permission).
- **Full-fidelity backup** (settings, IFC-basis recurrences, hidden holidays) as app-specific JSON; ICS is lossy for IFC-native rules (see 5.4).

---

## 5. IFC-native recurring holidays and events

### 5.1 The mapping facts that drive the design

IFC date ↔ Gregorian date is a function of **day-of-year**. With Leap Day inserted after IFC June 28 (ordinal 168) and Year Day as the last day of the year:

| IFC date | Gregorian date, common year | Gregorian date, leap year |
|----------|----------------------------|---------------------------|
| Jan 1 – Mar 3 (ordinals 1–59) | Jan 1 – Feb 28 | same |
| **Mar 4 – Jun 28 (ordinals 60–168)** | **Mar 1 – Jun 17** | **Feb 29 – Jun 16 (one day earlier)** |
| Leap Day | — (does not exist) | Jun 17 |
| Sol 1 onward | Jun 18 onward | same (Jun 18 onward) |
| Year Day | Dec 31 | Dec 31 |

Consequences:

- **Sol 1 is always June 18. Year Day is always December 31. IFC Leap Day is June 17 of Gregorian leap years.**
- Outside the window IFC Mar 4 – Jun 28, an IFC-anchored yearly event and a Gregorian-anchored yearly event are *identical*. Inside the window they differ by one day in leap years. That window is the only place the recurrence basis matters for yearly events — but it matters visibly, in exactly the app whose point is IFC.
- A Gregorian **Feb 29** birthday is IFC **March 4**, which exists every year (= Mar 1 in common years). IFC elegantly solves the "leapling" problem; worth surfacing in the UI.
- Example: born Gregorian 1992-04-10 (leap year) = IFC April 17. Celebrated on the IFC date, the birthday falls on Gregorian Apr 11 in common years and Apr 10 in leap years.

### 5.2 Model

Every event keeps a Gregorian `LocalDate` anchor (first occurrence) — consistent with the rest of the app. Recurrence carries an explicit **basis**:

```
Recurrence
 ├─ Gregorian(rrule: String)                  // RFC 5545 text, evaluated in Gregorian
 └─ Ifc(freq: YEARLY | MONTHLY,
        position: IfcDay(month 1..13, day 1..28) | YEAR_DAY | LEAP_DAY,
        skip: OMIT | BACKWARD | FORWARD,      // only meaningful for LEAP_DAY
        interval, until/count)
```

> **Superseded in part (2026-09-18).** The storage columns and the rule text below were a sketch. The data
> model is owned by [ARCHITECTURE.md](ARCHITECTURE.md) §3.2 and frozen in
> [contracts/Events.md](contracts/Events.md): columns `recurrence_type` / `rrule` / `ifc_rule`, text
> `IFC;FREQ=…` (`IfcRuleText`), and the Leap Day policy names `SKIP | JUNE_28 | SOL_1` for
> `OMIT | BACKWARD | FORWARD`. The text is exported as `X-IFC-RRULE` next to the §5.5 fallback `RRULE`. See
> [adr/0005-events-contract.md](adr/0005-events-contract.md).

Room columns: `recurrence_basis` (`GREG`/`IFC`) + `recurrence_rule` (text). For the IFC rule text, mirror [RFC 7529](https://www.rfc-editor.org/rfc/rfc7529) (the standard for non-Gregorian recurrence: `RSCALE` + `SKIP`) with a private scale, e.g. `RSCALE=X-IFC;FREQ=YEARLY;BYMONTH=7;BYMONTHDAY=1;SKIP=OMIT`. This is internal storage only — never exported verbatim, because no other client knows `X-IFC`. How Year Day / Leap Day are addressed inside the rule (special tokens vs. the common "June 29 / December 29" convention) must follow whatever the calendar-core doc chooses for `IfcDate`.

Expansion is trivial and lives in the core: for each year, build the `IfcDate`, convert to `LocalDate`, apply skip policy. IFC-`MONTHLY` ("the 1st of every IFC month") yields 13 occurrences per year; Year Day and Leap Day belong to no month and are never produced by a monthly rule.

Built-in holidays use the same machinery through the `ifc` rule type (section 2.2).

### 5.3 Leap Day recurrences in common years

Same problem as Gregorian Feb 29, same answer as RFC 7529 `SKIP`:

| Policy | Behaviour in common years | Use |
|--------|---------------------------|-----|
| `OMIT` | no occurrence | **Default for the built-in "Leap Day" holiday** — the day genuinely does not exist; showing it would be wrong. |
| `BACKWARD` | IFC June 28 (Gregorian Jun 17) | **Default for user birthdays/anniversaries on Leap Day.** Conveniently, this is the same Gregorian date (Jun 17) as the real Leap Day in leap years. |
| `FORWARD` | Sol 1 (Gregorian Jun 18) | User option. |

The UI asks only when the user creates an IFC-yearly event on Leap Day ("In years without a Leap Day: skip / celebrate June 28 / celebrate Sol 1"). Year Day needs no policy — it exists every year.

Century rule: IFC leap years follow Gregorian leap years, so 2100 has no Leap Day. Expansion by conversion handles this automatically; hard-coded "every 4 years" shortcuts do not.

### 5.4 Birthdays: which date do we celebrate?

When the user enters a birthday (always captured as a real Gregorian date of birth, shown with its IFC equivalent), offer: **"Repeat on: Gregorian date (Apr 10) / IFC date (April 17)"**. Default to Gregorian — it matches the rest of the user's life and any imported calendars — with the IFC option presented as the fun, on-brand choice. Imported events (device calendar, ICS) are always Gregorian-basis.

### 5.5 Exporting IFC-basis recurrences to ICS

Because IFC position is a function of day-of-year, IFC-yearly rules map onto standard RFC 5545 rules:

| IFC position | Exported RRULE |
|--------------|----------------|
| Ordinals 1–59 and Sol 1 onward (fixed Gregorian date) | `FREQ=YEARLY;BYMONTH=m;BYMONTHDAY=d` — maximum compatibility |
| Ordinals 60–168 (IFC Mar 4 – Jun 28) | `FREQ=YEARLY;BYYEARDAY=<ordinal>` (day-of-year is identical in leap and common years for this range) |
| Year Day | `FREQ=YEARLY;BYMONTH=12;BYMONTHDAY=31` (equivalently `BYYEARDAY=-1`) |
| Leap Day, `OMIT` | `DTSTART` = Jun 17 of a leap year; `FREQ=YEARLY;INTERVAL=4` plus `EXDATE` for 2100, 2200, 2300 |
| Leap Day, `BACKWARD` | `FREQ=YEARLY;BYMONTH=6;BYMONTHDAY=17` |
| IFC-monthly on day *d* | single rule `FREQ=YEARLY;BYYEARDAY=` list of 13 values: positive ordinals for months 1–6, **negative** (`−(366 − ordinal)`, ordinal in common-year numbering where Sol 1 = 169, so Sol 1 → −197 and IFC Dec 28 → −2) for months 7–13, which is leap-safe |

`BYYEARDAY` support in consumer calendars (Google Calendar, Outlook) is **not verified**; fallback is exporting explicit instances (`RDATE` list or individual VEVENTs) for a bounded horizon (e.g. 20 years). Test before promising.

---

## 6. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Holiday data licensing** — copying from Google feeds, commercial APIs, or share-alike datasets | Takedown / forced relicensing / ToS breach | Author US rules from statutes; convert only Apache-2.0/MIT rule data with attribution; keep CC BY-SA/ODbL out of the bundle; data-sources screen from v1.0 |
| **Lunar correctness vs. official announcements** — Islamic holidays set by moon sighting vary by country/community; Hindu dates vary by regional panchanga; Chinese-derived dates vary by meridian (Tết) | Wrong-by-a-day on culturally important days → bad reviews, lost trust | `approximate` flag + visible caveat; Umm al-Qura as stated basis; user Hijri adjustment; curated tables can be corrected in app updates; device-calendar overlay lets users rely on their own authoritative calendar |
| **ICU version skew across Android releases** | Same app, different dates on different phones | Bundled tables as primary; `android.icu` only as out-of-range fallback |
| **Per-country maintenance burden** — governments add/move holidays yearly (one-off bridge days, royal events, new holidays like Juneteenth 2021) | Stale or wrong data; unbounded workload for a solo dev | Ship US only as first-party; rely on overlay/ICS for the rest; later packs generated from Jollyday/python-holidays with a scripted pipeline; `since`/`until` + `table` for one-offs; consider downloadable packs so fixes do not need an app release (requires `INTERNET`) |
| **Permission friction** (`READ_CALENDAR`) | Users deny → feature invisible; perceived as creepy for a novelty calendar | App complete without it; opt-in, in-context rationale; read-only; "never leaves device" promise backed by no-network build until v1.3 |
| **Play policy drift** — calendar may get the contacts/photos treatment (picker or declaration) | Forced rework or removal | Overlay isolated behind an interface; listing documents the feature; minimal permission set (no `WRITE_CALENDAR`) |
| **Data safety mis-declaration** once network/crash SDKs appear | Policy strike | Never log event content; re-audit form whenever a network-capable SDK is added |
| **All-day/UTC and time-zone bugs** in overlay and ICS import | Events off by one day — fatal for a calendar | Dedicated conversion layer with tests for UTC−/UTC+ zones, DST edges, year boundaries |
| **RRULE edge cases** (BYSETPOS, RECURRENCE-ID overrides, floating times) | Missing/duplicated instances from imports | Library expansion, not home-grown; unsupported → import single instance with flag; test corpus of real feeds (Google, Outlook, Apple, school districts) |
| **biweekly maintenance stall** | Unfixed parser bugs | Wrap behind `IcsParser`; ical4j 4.x and lib-recur as swap-ins |
| **IFC-basis rules are lossy in ICS** | Round-trip surprises | Section 5.5 mapping + native JSON backup as the lossless path |
| **Observed-date cross-year edge** (Jan 1 on Saturday) | Missing "(observed)" entry on Dec 31 | Engine evaluates Y−1…Y+1 when rendering year Y; golden tests (New Year's Day observed on 2021-12-31 and 2027-12-31) |

---

## 7. Recommended phased approach

### MVP (v1.0) — built-in, zero permissions

- Rule engine in the pure-Kotlin core: `fixed`, `nthWeekday`, `offset`, `easter` (western + orthodox), `table`, `ifc`; modifiers `since/until`, `yearFilter`, `observed` (`us_federal`), `durationDays`, `approximate`, `category`.
- Bundled sets: **US federal + observances**, Christian (Easter family), **IFC set** (Year Day, Leap Day, Sol 1). Jewish, Islamic, Lunar New Year, Diwali/Holi ship as **pre-generated tables** (so the `calendar` rule type and the ICU fallback can wait).
- Settings: toggle sets, hide individual holidays.
- If user events are in the MVP: yearly recurrence with **Gregorian or IFC basis** and the Leap Day skip policy — this is the app's unique feature and costs little once the core conversion exists.
- Golden tests against known dates (python-holidays as oracle) for 1970–2100.

*Why:* satisfies the owner's "built in since we know most holidays" with no licensing exposure, no permissions, no network, trivially clean Play review, and it forces the rule/data format to exist before anything depends on it.

### v1.1 — device calendar overlay

- Opt-in `READ_CALENDAR`, `Instances`-based read-only overlay, calendar picker, `ContentObserver` refresh, intent-based "open in calendar app" / "add to calendar".
- Update Play listing text + privacy policy; verify on-device that Google holiday calendars are visible.

*Why second:* highest value per line of code (all user events + worldwide holidays, with recurrence expansion done by the OS) but it introduces the first sensitive permission; shipping it after a clean v1.0 de-risks the initial review and gives the rationale screen something to stand on. If the owner wants real events on the grid at launch, this is the one item that can reasonably be pulled into the MVP.

### v1.2 — `.ics` file import + export

- biweekly behind `IcsParser`; SAF open + `text/calendar` intent filter; import batches undoable; lazy RRULE expansion.
- Export app events and holiday sets to `.ics` (IFC-basis mapping per 5.5); JSON full backup.

### v1.3 — ICS URL subscriptions

- `INTERNET` permission, WorkManager refresh, conditional GET, `webcal://` handling, secret-URL hygiene. Re-audit Data safety form.
- `calendar` rule type + `android.icu` fallback resolver (tables nearing edge cases, "show in Hebrew/Islamic calendar" feature).

### Later

- Country packs (CA, UK, AU, DE, MX, IN ... by user demand) generated from Jollyday XML (Apache-2.0) and cross-checked with python-holidays (MIT); `weekdayRelative`, more `observed` policies, `subdivisions`.
- Downloadable/updatable rule packs (static hosting, signed JSON) so holiday fixes do not need an app release.
- Optional `WRITE_CALENDAR` "publish IFC holidays to a device calendar" — only with demonstrated demand.
- Solstices/equinoxes, Tax Day policy, school holidays (OpenHolidays, ODbL) if ever.

---

## 8. Open questions for the owner

1. Should the device-calendar overlay be in the MVP (more useful at launch, but first release carries `READ_CALENDAR`) or v1.1 (recommended)?
2. Is "no `INTERNET` permission at all" a marketing promise worth keeping through v1.2? It is a strong trust signal next to a calendar permission.
3. Open-source the holiday JSON? If yes, Apache-2.0 keeps Jollyday-derived packs simple; avoid CC BY-SA inputs.
4. Default birthday basis: Gregorian (recommended) or IFC?
5. IFC weekday display (perpetual vs. civil weekdays) affects how weekday-based holidays *look* on the grid — owned by the calendar-core/UX docs, but it should be decided with holidays in mind.

---

## 9. Sources

- Jollyday: <https://github.com/focus-shift/jollyday>, <https://central.sonatype.com/artifact/de.focus-shift/jollyday-core>, <https://github.com/galgtonold/jollyday-android>
- Nager.Date: <https://github.com/nager/Nager.Date>, <https://nagerholidays.com/Api>
- python-holidays: <https://github.com/vacanza/holidays>
- date-holidays: <https://github.com/commenthol/date-holidays>
- OpenHolidays: <https://www.openholidaysapi.org/en/>, <https://github.com/openpotato/openholidaysapi.data>
- Calendarific pricing: <https://calendarific.com/pricing>
- Google Calendar holidays help: <https://support.google.com/calendar/answer/13748345?hl=en>; Google APIs ToS: <https://developers.google.com/terms>
- CLDR: <https://cldr.unicode.org/>; 2005 holiday-rules proposal thread: <https://unicode.org/mail-arch/unicode-ml/y2005-m10/0122.html>
- ICU4J API docs: <https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/util/IslamicCalendar.html>, <https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/util/IndianCalendar.html>; Android: <https://developer.android.com/reference/android/icu/util/package-summary>
- Play policy: <https://support.google.com/googleplay/android-developer/answer/16558241?hl=en>, preview (effective 2027-01-27): <https://support.google.com/googleplay/android-developer/answer/16909972?hl=en>, Data safety: <https://support.google.com/googleplay/android-developer/answer/10787469?hl=en>
- Calendar Provider: <https://developer.android.com/reference/android/provider/CalendarContract.Instances>
- iCalendar libraries: <https://github.com/mangstadt/biweekly>, <https://central.sonatype.com/artifact/net.sf.biweekly/biweekly>, <https://github.com/ical4j/ical4j>, <https://www.ical4j.org/android/>, <https://central.sonatype.com/artifact/org.mnode.ical4j/ical4j>, <https://github.com/dmfs/lib-recur>, <https://github.com/bitfireAT/ical4android>
- RFC 5545 (iCalendar): <https://www.rfc-editor.org/rfc/rfc5545>; RFC 7529 (RSCALE/SKIP): <https://www.rfc-editor.org/rfc/rfc7529>
