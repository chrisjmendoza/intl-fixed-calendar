# Competitive Analysis — International Fixed Calendar (IFC) Android App

_Research date: 2026-09-17. All store data below was collected on that date and will drift._

## 0. How to read this document

**Honesty rule.** Nothing here is invented. Every factual claim carries a source link. Each competitor is tagged:

- **VERIFIED** — I loaded the primary store page / API / repo myself on 2026-09-17 and read the values directly.
- **PARTIAL** — seen only via a third-party mirror or a search-result snippet; primary page not reachable.
- **NOT VERIFIED** — could not be fetched; stated explicitly.

**Method.**
- Google Play: listing pages fetched directly (`play.google.com/store/apps/details?id=…&hl=en_US&gl=US`); Play search result pages fetched for ~35 keyword queries (US storefront, English); review text pulled from Play's public review endpoint (the same data the "See all reviews" dialog shows). Play search results are personalised/regional, so "not found" means "not found in US/English search on this date", not "does not exist".
- iOS: Apple's public iTunes Search/Lookup API and the public customer-reviews RSS feed.
- F-Droid: `search.f-droid.org` queries.
- GitHub: topic page and individual repos.
- **Reddit could not be accessed at all** (both the search tool and direct requests are blocked for this agent). Everything about Reddit communities is therefore **NOT VERIFIED** and is flagged as a to-do for a human.

Review quotes are verbatim excerpts from public store reviews, identified by star rating and date only; reviewer display names are deliberately omitted. Star ratings on Play differ between the header (all devices) and the "Phone" tab; both are given where seen.

---

## 1. Executive summary

**The field is nearly empty, and what exists is weak.**

- On Google Play (US/English) exactly **two** apps implement the IFC: erkantr's *13 Month Fixed Calendar* (10K+ downloads, 3.0★) and a brand-new clone-named *13 Month Fixed Calendar* by Eizuberg (10+ downloads, launched/updated Sep 2026). A third, *International Fixed Calendar* by "ApplicationStation" (2018), has been **delisted** (Play returns HTTP 404).
- On iOS there is one long-standing IFC app (Darren Maxwell, 3.2★ from 12 ratings, broken-widget complaints) and one new IFC-derived moon/zodiac app with zero ratings.
- F-Droid has **no** IFC app. GitHub has a handful of converter libraries, one tiny Android widget project (4 stars, APK-only), and two active web apps.
- **Nobody on Android ships an IFC home-screen widget**, a tap-a-date converter, or real events/reminders in a maintained, rated app. These are precisely the top requests in the incumbent's reviews — and precisely the owner's planned feature list.
- Despite doing almost nothing, the incumbent has 10K+ installs and ~180 reviews, which shows there is real (if small) organic search demand. Reviews reveal two distinct audiences: calendar-reform/rationalist enthusiasts, and a "natural time" / spiritual audience who arrive via TikTok-style content and often hold non-standard beliefs about the calendar (April new year, lunar alignment). The app needs a good explainer to serve both without being wrong.

---

## 2. Summary table

### 2a. Direct competitors (IFC / 13×28 calendar apps)

| # | Name | Developer | Platform | Rating / installs | Last update | Monetization | Key features | Status |
|---|------|-----------|----------|-------------------|-------------|--------------|--------------|--------|
| 1 | [13 Month Fixed Calendar](https://play.google.com/store/apps/details?id=com.bysoftware.fixedcalendar&hl=en_US) | erkantr (Türkiye) | Android (Play) | 3.0★ / 184 reviews (header); 2.8★ / 179 (Phone tab); 10K+ downloads | May 13, 2025 (v2.2.3) | Free. No "Contains ads" label seen on listing, but a Feb 2026 reviewer complains about ads; Data safety declares sharing device IDs with third parties | Single-screen year view of 13 months, today highlighted, Gregorian date shown, dark mode/theme colour, info screen, 10 languages. **No events, no converter, no widget, no month zoom** | VERIFIED |
| 2 | [13 Month Fixed Calendar](https://play.google.com/store/apps/details?id=eizu.kodaCalendar&hl=en_US) (same name as #1) | Eizuberg (Slovenia) | Android (Play) | No rating shown; 1 review (3★); 10+ downloads | Sep 3, 2026 | Free, **Contains ads** | 13-month calendar + agenda/plans/events, month navigation (per listing text; not hands-on tested) | VERIFIED (listing); features not tested |
| 3 | International Fixed Calendar (`io.github.hidroh.calendar`) | "ApplicationStation" | Android (Play) — **delisted** | 500+ installs per [APKCombo mirror](https://apkcombo.com/international-fixed-calendar/io.github.hidroh.calendar/) | v1.0, May 26, 2018 (per mirror) | Unknown | "Gregorian to International Fixed Calendar that lets you enter events" (mirror text) | PARTIAL — Play URL returns **404** on 2026-09-17; data only from APK mirror |
| 4 | [International Fixed Calendar](https://apps.apple.com/us/app/international-fixed-calendar/id1107327564) | Darren Maxwell | iOS, iPadOS, watchOS, macOS, tvOS, visionOS | 3.2★ (3.17) / 12 ratings | v2.2, 2024-09-12; first released 2016-04-27 | Free, no IAP listed | Today's IFC date, home-screen + StandBy widgets, Apple Watch app + complications | VERIFIED (iTunes API + store page) |
| 5 | [Moon Year Calendar](https://apps.apple.com/us/app/moon-year-calendar/id6761673829) | George Tsipolitis | iOS | 0 ratings | v1.0, 2026-04-08 | Free | IFC-based 13×28 grid, moon phase + zodiac per day, year view, two-way converter, dark mode. **Non-standard**: puts Year Day at 31 March and Leap Day at 16 September | VERIFIED (iTunes API) |
| 6 | [SuperiorTimeSystem](https://github.com/vivian-dai/SuperiorTimeSystem) | vivian-dai | Android (GitHub APK only, not on Play/F-Droid) | 4 GitHub stars | Topic page shows last update Apr 11, 2023 | Free, MIT | Two home-screen widgets: decimal time and IFC date; light/dark | VERIFIED (repo README) |
| 7 | [13 Calendar](https://13calendar.pages.dev/) ([source](https://github.com/jean7rafael/13calendar-public)) | jean7rafael | Web / PWA | 0 GitHub stars | Repo updated Sep 11, 2026 | Free, MIT, no account | Two-way conversion, side-by-side Gregorian/IFC, holidays for 251 countries/territories, moon phases, ICS export, 12 languages, birthday comparison | VERIFIED |
| 8 | [Fixed Calendar (fixedcalendar.org)](https://www.fixedcalendar.org/) ([GitHub org](https://github.com/fixedcalendar)) | "An open initiative" (community) | Web | n/a | Active (2026 content) | Free, open source | Two-way converter, year view, charts, EN/ES, explicit "days outside the week" explanation | VERIFIED (home page) |
| 9 | [13cal.net](https://13cal.net/) | @13Calendar on X | Web | n/a | Active (sells 2026 & 2027 edition) | **Sells a $9 printable PDF calendar** | Interactive calendar, converter, birthday finder, moon tracker, "compare calendars"; nav lists a "Calendar widget" page (web embed presumably; the URL I guessed 404'd — not verified) | VERIFIED (home page) |
| 10 | [JoyTempo](https://joytempo.com/) | unknown | Web | n/a | unknown | None seen | Today in both calendars, two-way converter, mentions holidays | VERIFIED (home page, shallow) |
| 11 | Other small web converters: [Jeremy Aldrich](https://jeremy-aldrich.com/IFC/), [Russell Elliott](https://russellelliott.codehs.me/ifc.html), [freexenon conversion tables](https://www.freexenon.com/_Calendar_Reform/Conversion%20Calendars/Standard.htm) | individuals | Web | n/a | unknown | none | Simple converters / static tables | PARTIAL (search results only, pages not opened) |
| 12 | Libraries: [PyryL/fixedcal](https://github.com/PyryL/fixedcal) (Python, 5★), [k5cents/ifc](https://github.com/k5cents/ifc) (R), [ThisIsMissEm/gregorian-to-ifc-converter](https://github.com/ThisIsMissEm/gregorian-to-ifc-converter) (TS), [comicsads/Gregorian-to-IFC](https://github.com/comicsads/Gregorian-to-IFC) (JS), [gauravnumber/ifc-cli](https://github.com/gauravnumber/ifc-cli), [katemihalikova ion-datetime-picker IFC plugins](https://github.com/katemihalikova/ion-datetime-picker-calendar-fixed) | various | Libraries | 0–5 stars each (per [GitHub topic page](https://github.com/topics/international-fixed-calendar)) | 2017–2026 | OSS | Conversion only | VERIFIED via topic page / search listing; repos not individually audited |

**F-Droid:** searches for "fixed calendar", "13 month", "13 months", "international fixed", "cotsworth" on [search.f-droid.org](https://search.f-droid.org/?q=international+fixed&lang=en) returned **zero** results (2026-09-17). VERIFIED absence (for those queries).

### 2b. Near-neighbours (other 13×28 systems — not IFC, but share the search results page)

| Name | Developer | Platform | Rating / installs | Notes | Status |
|------|-----------|----------|-------------------|-------|--------|
| [Lightbody: 13 Moon Calendar](https://play.google.com/store/apps/details?id=com.light.body.technology.app&hl=en_US) | Sovereign Temples | Android + [iOS](https://apps.apple.com/us/app/lightbody-13-moon-calendar/id6478142281) | Play: 4.2★ / 34 reviews, 5K+; iOS: 5.0★ / 7 | Synodic (true lunar) months, journaling, playlists, cycle tracking, community; IAP. Spiritual positioning. Ranks #2–3 for most "13 month" queries | VERIFIED |
| [DreamSpell: Tzolkin Calendar](https://play.google.com/store/apps/details?id=com.kurbetsoft.dreamspell&hl=en_US) | KurbetSoft | Android | 4.7★ / 546 reviews, 10K+ | Argüelles "Thirteen Moon 28-day" calendar + Tzolkin; contains ads; updated Sep 9, 2026 | VERIFIED |
| [Dekatrian](https://play.google.com/store/apps/details?id=pindi.flutter.dekatrian&hl=en_US) | Pindí Consultoria e Sistemas | Android | 10+ downloads, no rating | Brazilian 13-month proposal (Scicast podcast); listing name-checks Cotsworth's "Yearal" | VERIFIED |
| [Roots Calendar](https://apps.apple.com/us/app/roots-calendar/id6771264156) | Sebastiaan Castenmiller | iOS | 0 ratings; v1.0.2 2026-06-24 | Beth-Luis-Nion "tree" calendar, 13×28 + Year Day, local events, **optional Google Calendar sync mapped by Gregorian date**, moon phases, Celtic festivals | VERIFIED (iTunes API) |

### 2c. Adjacent alternative-calendar apps studied for UX (section 5)

| Name | Platform | Rating / installs | Why it matters | Status |
|------|----------|-------------------|----------------|--------|
| [Ethiopian Calendar & Converter](https://play.google.com/store/apps/details?id=com.shalom.calendar&hl=en_US) (Mekete Nimaga) | Android | 4.6★ / 10.2K reviews, 1M+ | Best-in-class dual-calendar app for another 13-month calendar: dual view, converter, day + month widgets, events with alarms, offline, no ads | VERIFIED |
| [HebDate Hebrew Calendar](https://play.google.com/store/apps/details?id=com.lionscribe.hebdate&hl=en_US) (Lionscribe) | Android + Wear OS | 4.8★ / 30.1K reviews, 1M+ | Full scheduling calendar on a non-Gregorian grid, widget, Wear complication, recurring events on Hebrew dates synced to Google Calendar (premium). Also a cautionary tale on ads/permissions | VERIFIED |
| [French Revolutionary Calendar](https://play.google.com/store/apps/details?id=ca.rmen.android.frenchcalendar&hl=en_US) (Carmen Alvarez; also on [F-Droid](https://f-droid.org/en/packages/ca.rmen.android.frenchcalendar/)) | Android | 5.0★ / 239 reviews, 10K+ | Proof that a *widget-first* novelty-calendar app can be loved: resizable widget, 3 styles, share date, two-way converter; no ads; last updated 2018 and still 5.0★ | VERIFIED |
| [Hijri Widget](https://play.google.com/store/apps/details?id=me.amrbashir.hijriwidget&hl=en_US) (amrbashir) | Android | 4.7★ / 142 reviews, 10K+ | Open-source, ad-free single-purpose date widget; reviews show the #1 widget failure mode (date not auto-updating) | VERIFIED |
| [Calendar Converter](https://play.google.com/store/apps/details?id=com.ramdroid.calendarconverter.full&hl=en_US) (Rémy Pialat) | Android | 4.6★ / 726 reviews, 100K+ | Multi-calendar converter (Gregorian, Julian, Republican, Jewish, Islamic, Indian, Persian) + Julian Day. **Does not list IFC** | VERIFIED |

---

## 3. Per-competitor notes

### 3.1 erkantr — *13 Month Fixed Calendar* (the incumbent) — VERIFIED

Source: [Play listing](https://play.google.com/store/apps/details?id=com.bysoftware.fixedcalendar&hl=en_US); reviews from the same listing's public review feed (51 reviews retrieved).

- **Numbers:** 3.0★ (184 reviews) in header, 2.8★ (179) on the Phone tab, 10K+ downloads, category Productivity, updated May 13, 2025. (Third-party trackers show it lower in the past: [AppRecs](https://apprecs.com/android/com.bysoftware.fixedcalendar/13-month-fixed-calendar) lists 2.6★ / 92 ratings with 40% one-star — so v2.2.3 improved things somewhat.)
- **What it does:** shows all 13 months on one screen, highlights today, shows the Gregorian date. v2.2.3 changelog: UI update, "Fixed issues related to Day of the Year and Leap Day", settings screen, dark mode + theme colour, info/history screen, 9 added languages.
- **Listing quality issue:** the description itself is confused — it says the leap day "is inserted between June 28 and July 1, known as 'Sol'", conflating Leap Day with the month Sol. An accurate, well-written listing is a cheap differentiator.
- **Data safety:** declares sharing "App activity and Device or other IDs" with third parties; "Data can't be deleted". One reviewer: "I would rather buy this. Hate the adds and a widget would be nice..." (Play review, 2026-02-05).
- **Developer responsiveness:** one reply found — to a widget request (Play review, 2025-09-04): "Sure😊". No widget has shipped in the 12 months since; no update since May 2025.

**What users complain about / ask for (counts are from the 51 reviews retrieved; themes overlap).** Quotes are verbatim from the public review feed; reviewer display names are deliberately omitted throughout this document.

| Theme | Approx. mentions | Representative quote |
|-------|------------------|----------------------|
| **No events / reminders / notes** | ~10 | "I was hoping for a fully functional calendar app that happened to be based on the 13 month fixed calendar, and what I got was an app with one screen that just shows the fixed calendar and highlights what day it is currently." — 1★, 2024-07-31 (22 found helpful — the most-upvoted review) |
| **"It's just an image" / does nothing / can't open a month** | ~7 | "Just do an image search and you have all this app gives you. Literally can't do anything, including zooming in on a certain month." — 1★, 2024-07-14 |
| **Wants a widget** | 6 | "This app would be PERFECT if it had a widget, then it would be 5 stars!" — 4★, 2026-03-10 |
| **Wants a converter / tap a date to see Gregorian** | 4 | "I can't select a specific date to see the translation, like my birthday for example." — 3★, 2026-05-28. "maybe some converter -- like tapping on any day to see the Gregorian calendar equivalent" — 4★, 2025-10-08 |
| **Weekday labels missing / Saturday column cut off / font size** | 5 | "latest update doesn't show day of the week anymore" — 2★, 2025-07-22. "it needs to be able to adjust with the font size... also, the 7th day column is missing" — 3★, 2025-10-18 |
| **Wrong date shown (older versions) / distrust of accuracy** | 3–4 | "You are ahead a few days." — 2025-04-14 (v1.1.3). "Conflicting Information..." — 2024-11-22 |
| **Custom month names / renaming** | 3 | "Allow for users to change the names of the months." — 4★, 2025-08-18 (13 helpful) |
| **Year should start in spring/April; Year Day placement** | 5 | "year day is supposed to happen between March 28th and April 1st" — 2026-05-01. "The first month needs to be April." — 2025-07-23 |
| **Expects lunar alignment / moon phases** | 3 | "Shouldn't the lunar months coincide with the lunar cycle?" — 3★, 2026-05-29 |
| **Align weekdays with the real-world week** | 1–2 | "I want to match weekdays each year. e.g. 30th december 2024 gregorian = 1. january 2025 in fixed." — 2025-10-25 |
| **Integration with system calendar** | 1–2 | "Wish I could have it on my Galaxy Calendar." — 2025-12-24 |
| **Ads / would rather pay** | 1–2 | see above |

**What users praise:** that it exists at all ("its the only one i can find on here"), that it shows the Gregorian date alongside (review, 2026-04-07), the May 2025 redesign, simplicity.

**Takeaway:** every one of the owner's planned features (month grid you can open, tap-to-convert, events, converter, widget, proper intercalary days) maps 1:1 onto a documented complaint. This is the rare case where the roadmap is already validated by the competitor's review page.

### 3.2 Eizuberg — *13 Month Fixed Calendar* — VERIFIED (listing only)

Source: [Play listing](https://play.google.com/store/apps/details?id=eizu.kodaCalendar&hl=en_US).

- 10+ downloads, updated Sep 3, 2026, category Tools, **Contains ads**, Data safety: no data collected/shared. Package id `eizu.kodaCalendar` (probably a nod to Kodak).
- Listing claims agenda/plans/events on a 13-month calendar. Emoji-heavy, generic copy; never names the IFC, Sol, Year Day or Leap Day. No mention of widget, converter, or holidays.
- The developer's other apps ("Lock Practice", "Dice Calculator Counter", "Six Seven 6 7 Simulator", "Toilet Timer") suggest a high-volume small-app publisher rather than a calendar enthusiast.
- Only review (3★, 2026-09-12): "A different perspective but still wrong, names of the months are related to numbers that wasn't taken into consideration..."
- **Why it matters:** (a) it proves someone else noticed the same gap this month; (b) it uses the *identical* title to the incumbent, so "13 Month Fixed Calendar" is now doubly taken. Not hands-on tested — worth a 5-minute install by the owner to see how good its events feature really is.

### 3.3 ApplicationStation — *International Fixed Calendar* (Android) — PARTIAL / delisted

- Play URL `https://play.google.com/store/apps/details?id=io.github.hidroh.calendar` returns **HTTP 404** (checked twice, 2026-09-17). It still appears in web search indexes and on [APKCombo](https://apkcombo.com/international-fixed-calendar/io.github.hidroh.calendar/): v1.0, May 26, 2018, 500+ installs, "Gregorian to International Fixed Calendar that lets you enter events."
- The package id (`io.github.hidroh.*`) looks like the namespace of an unrelated open-source developer's calendar project, which hints it may have been a re-skinned fork — this is an inference, **not verified**. Rating/reviews: **not verified**.
- Relevance: none as a competitor; useful only as evidence that the exact title "International Fixed Calendar" has been used on Play before and is currently free there (it is still in use on iOS).

### 3.4 Darren Maxwell — *International Fixed Calendar* (iOS) — VERIFIED

Sources: [App Store page](https://apps.apple.com/us/app/international-fixed-calendar/id1107327564), iTunes Lookup API, [public reviews RSS](https://itunes.apple.com/us/rss/customerreviews/id=1107327564/sortBy=mostRecent/json).

- Free, no IAP; 3.17★ from 12 ratings; first released 2016-04-27; v2.2 dated 2024-09-12 (notes mention widget-system improvements and a StandBy widget). Requires iOS 17+. Humorous listing copy ("If you're a time traveling Kodak employee who owns an Apple Watch then this is the app for you!").
- Strength: **widgets + Watch complications** — the only IFC product anywhere with first-class glanceable surfaces.
- Complaints (verbatim from the RSS feed):
  - Widget reliability: "after installing the widget I've found that it just stays on whatever date it was when the widget was added to the Home Screen... This is the only fixed calendar app in the App Store so the fact that the widget isn't really functional is disappointing." (3★, 2023-05-28)
  - Accuracy: "The date is one day behind the correct international fixed calendar. I have checked several websites, an Android app and made a Python program." (2★, 2024-07-17)
  - Thin features: "Seemed to be an app with some promise for the IFC heads in the world (there are dozens of us --dozens!!) but the threadbare functionality of the app, infrequency of updates..." — review titled "Lacking integrations, customizations for format, and week start" (1★, 2025-01-29)
  - Wants year overview + explainer: "an overview showing the whole year and current highlighted date... an about/info screen that gives basic IFC/Eastman/Cotsworth information." (4★, 2026-01-14)
  - Same April/equinox belief as on Android: "I'm almost positive the spring equinox is April 1st." (2★, 2026-03-09)
- Not an Android competitor, but it sets the bar for widgets, and its reviews tell us the two things that kill trust: **a widget that doesn't roll over at midnight** and **an off-by-one date**.

### 3.5 George Tsipolitis — *Moon Year Calendar* (iOS) — VERIFIED

Source: [App Store](https://apps.apple.com/us/app/moon-year-calendar/id6761673829) via iTunes API. v1.0 (2026-04-08), free, zero ratings. Feature list is close to our plan (year view, two-way converter, dark mode) plus moon phase and zodiac per day. It explicitly says it is "Based on the International Fixed Calendar" but moves Year Day to 31 March and Leap Day to 16 September — i.e. it serves the "April new year" folk variant. Shows that variant has enough pull for someone to build for it.

### 3.6 Open source & web

- **[SuperiorTimeSystem](https://github.com/vivian-dai/SuperiorTimeSystem)** — the only Android IFC *widget* I could find anywhere. Kotlin, MIT, 4 stars, APK from GitHub releases only. Not discoverable by normal users. VERIFIED.
- **[13 Calendar](https://13calendar.pages.dev/)** — the most feature-complete IFC product overall, but web-only: holidays for 251 countries (built on the `date-holidays` dataset "with reviewed official sources"), moon phases computed locally, ICS export, 12 languages, side-by-side comparison ("Rotate your screen or use a larger display to compare both calendars side by side"). MIT-licensed — its approach to holiday data is directly reusable knowledge. States it is "Not affiliated with 13months.net or any standards body." VERIFIED.
- **[fixedcalendar.org](https://www.fixedcalendar.org/)** — polished bilingual (EN/ES) advocacy site, "An open initiative", with converter, year view and charts, and open-source `core`/`website`/`charts` repos under [github.com/fixedcalendar](https://github.com/fixedcalendar). Good, precise language for intercalary days: "Year Day and, in leap years, Leap Day belong to no week." VERIFIED (home page).
- **[13cal.net](https://13cal.net/)** — SEO-driven tool site that monetizes with a **$9 printable 2026–2027 PDF calendar** (65 pages, moon phases marked) and an X account (@13Calendar). Evidence that someone believes this niche will pay small amounts. VERIFIED (home page).
- **[13months.net](https://13months.net/)** — page title "13 Months — A Better Calendar"; the page is a JS shell and no content could be read. NOT VERIFIED beyond the title.
- **Algorithm reference:** [Wikibooks — Conversion of a Gregorian date to an IFC date](https://en.wikibooks.org/wiki/Algorithm_Implementation/Date_and_time/Conversion_of_a_Gregorian_date_to_an_International_Fixed_Calendar_date); background: [Wikipedia — International Fixed Calendar](https://en.wikipedia.org/wiki/International_Fixed_Calendar).

---

## 4. Gaps and opportunities

1. **No Android IFC widget exists on Play.** Six of 51 incumbent reviews ask for one; the developer said "Sure" a year ago and hasn't shipped. The iOS app has widgets but they're reported as not updating. A *reliable* widget is the single biggest opening.
2. **No maintained Android app lets you tap a date and see its Gregorian equivalent, or convert arbitrary dates.** Users literally describe this feature in reviews. General converter apps (e.g. [Calendar Converter](https://play.google.com/store/apps/details?id=com.ramdroid.calendarconverter.full&hl=en_US), 100K+) don't include the IFC.
3. **Events on IFC dates.** The most-upvoted complaint. Only the 10-download Eizuberg app claims it. Recurring events "on Sol 14 every year" is something no Gregorian calendar can express — a true unique capability.
4. **Trust/accuracy.** Both incumbents have public "wrong date" reviews. A visibly tested conversion engine (and saying so in the listing), plus a "how is this calculated?" screen, is a differentiator in itself.
5. **Basic polish and accessibility.** Missing weekday headers, clipped 7th column, no font scaling, single red theme — all reported. Material 3, dynamic colour, large-font support, TalkBack labels would already be "clearly better".
6. **Intercalary days handled explicitly.** Nobody explains or displays Year Day / Leap Day well; users are confused about where they are and what weekday they have. Opportunity to make them a delightful feature (special cell, special widget state, optional notification "Happy Year Day").
7. **Education.** Reviews show widespread confusion (IFC vs lunar months, April new year, Sol vs Leap Day, "it's just the normal calendar"). A short, well-sourced explainer/FAQ reduces 1★ "this is wrong" reviews.
8. **Holidays.** No Android IFC app has them; the 13 Calendar web app shows it's feasible with open data.
9. **Ad-free / privacy-first positioning.** Incumbent shares device IDs and draws an ads complaint; the Eizuberg app has ads; HebDate's worst reviews are all about intrusive ads and over-broad permissions. The best-loved adjacent apps (Ethiopian Calendar, French Revolutionary Calendar, Hijri Widget) are all ad-free. "No ads, no tracking, works offline" is both cheap to deliver and a proven rating driver.
10. **System-calendar integration** ("Wish I could have it on my Galaxy Calendar"). Showing device-calendar events on the IFC grid (read-only first) would make this a daily driver rather than a curiosity. Nobody does it on Android; Roots Calendar on iOS does a Google sync.

---

## 5. UX patterns worth borrowing

**From Ethiopian Calendar & Converter** ([listing](https://play.google.com/store/apps/details?id=com.shalom.calendar&hl=en_US)) — the closest structural analogue (also 13 months):
- **Dual date in every cell**: primary-calendar day number large, secondary (Gregorian) small in the corner; header shows both month names / ranges. "View both calendars simultaneously on one screen."
- **Two widget sizes with distinct jobs**: a small *today* widget showing both dates, and a *month-grid* widget with holidays highlighted. Widget options are configurable (their reviews show users wanting to hide rows they don't use — make every widget element optional).
- **Widget anti-pattern to avoid**: a toast on every widget refresh was their top-voted complaint (72 helpful) until fixed. Widgets must update silently.
- Offline-first, no ads, events with alarms, multiple languages — and the reviews credit exactly those things.

**From HebDate** ([listing](https://play.google.com/store/apps/details?id=com.lionscribe.hebdate&hl=en_US)):
- **Toggle which calendar drives the grid** ("Full month views for Hebrew and Secular calendars") — for us: IFC-primary grid with Gregorian sub-labels, and optionally a Gregorian grid with IFC sub-labels.
- **Recurring events anchored to the alternative calendar, synced out to Google Calendar** as concrete Gregorian instances (their premium feature). This is the right model for "birthday on Sol 9".
- **Persistent notification / status-bar date** (users noticed and complained when it vanished) — cheap glanceable surface besides the widget.
- Wear OS complication as a later add-on.
- **Anti-patterns**: requiring calendar/contacts permission just to open the app ("you can't even open the app without those permissions" — 1★, 2019); full-screen ads. Ask for calendar permission only at the moment the user turns on device-calendar integration, and keep the core app permission-free.

**From French Revolutionary Calendar** ([listing](https://play.google.com/store/apps/details?id=ca.rmen.android.frenchcalendar&hl=en_US)):
- Widget-first product; **resizable; multiple visual styles** (ornate + minimalist); per-month colour; **share today's date** as text (great for the enthusiast audience — free marketing); two-way converter added later by demand; lock-screen/watch display. 5.0★ with 239 reviews and no update since 2018 shows a small, correct, charming widget ages well.

**From Hijri Widget** ([listing](https://play.google.com/store/apps/details?id=me.amrbashir.hijriwidget&hl=en_US)):
- Single-purpose, open-source, ad-free, highly customizable text widget. Its top critical review is "the date doesn't automatically update. It only changes when I click the widget" and its changelog includes "Fixed widget not working on Realme and Infinix devices" — i.e. **midnight rollover and OEM battery-killer behaviour are the hard part of a date widget**. Plan for: update on `ACTION_DATE_CHANGED` / `TIME_SET` / `TIMEZONE_CHANGED`, a scheduled update just after local midnight, a WorkManager fallback, and recompute-on-every-render so a stale process can never show yesterday.

**From the web tools (13 Calendar, fixedcalendar.org):**
- Side-by-side year comparison in landscape / on tablets.
- Converter with a direction switch (Gregorian → IFC / IFC → Gregorian) and result line that includes weekday and "month 8/13".
- "Find my fixed-calendar birthday" as a first-run hook (13cal.net, 13 Calendar) — it's the first thing users try (see the "like my birthday" review in §3.1).
- ICS export as a lightweight alternative to full calendar sync.

**Intercalary-day presentation (our own synthesis — nobody does this well):**
- Render Year Day (and Leap Day) as a **full-width special row/card** after December 28 (and June 28), visually outside the 7-column grid, labelled "no weekday".
- Converter must accept and return them as first-class values ("Year Day 2026" ⇄ 31 Dec 2026; "Leap Day 2028" ⇄ 17 Jun 2028), and the IFC date picker needs an explicit way to pick them.
- Be explicit in UI copy about the **real-world weekday vs IFC weekday** mismatch. In the pure IFC every 1st is a Sunday, which diverges from the civil week. Show the *actual* (Gregorian) weekday in the day-detail sheet so nobody misses a real Tuesday appointment. One reviewer explicitly asked for the opposite model (shift the IFC to match real weekdays); treat that as a possible later option, not default.

---

## 6. Prioritized differentiating features

Tags: **MVP** = first public release; **v1.x** = fast-follow; **Later** = only if traction.

| Pri | Feature | Tag | Reasoning |
|-----|---------|-----|-----------|
| 1 | **Correct, unit-tested conversion engine** (both directions, leap years, Year Day, Leap Day, proleptic range, time-zone-safe "today") | MVP | Both incumbents have public wrong-date reviews. Everything else depends on it. Publish test vectors against Wikibooks algorithm / other implementations. |
| 2 | **Today screen**: big IFC date, Gregorian beneath, day-of-year, week number | MVP | Core promise; what the incumbent does, but done properly. |
| 3 | **Month grid with weekday headers, swipe between 13 months, year overview, proper font scaling** | MVP | Directly fixes "just an image", "can't open a month", "7th column missing", "no weekday labels". |
| 4 | **Tap any date → detail sheet with Gregorian equivalent (and real weekday)** | MVP | Explicitly requested in reviews; trivial once engine exists. |
| 5 | **Two-way converter with date pickers (incl. Year Day/Leap Day) + "find my IFC birthday"** | MVP | Requested by 4+ reviewers; first-run hook; shareable result. |
| 6 | **Home-screen widget: "today" (small, resizable) that reliably rolls over at midnight** | MVP | The #1 unmet ask on Android and the #1 broken thing on iOS. Even a minimal widget makes the app categorically better than everything on Play. |
| 7 | **First-class Year Day / Leap Day rendering** in grid, converter, widget | MVP | Owner requirement; zero competitors do it well; small scope. |
| 8 | **No ads, no tracking, no permissions at install, works offline**; accurate Data safety form | MVP | Proven rating driver among adjacent apps; incumbent weakness. Costs nothing. |
| 9 | **Short explainer / FAQ** (what the IFC is, Cotsworth/Eastman/Kodak history, "months are not lunar", "why January not April", where intercalary days go) | MVP | Pre-empts the most common 1–3★ "this is wrong" reviews. Requested on iOS too. |
| 10 | **Local events on IFC dates with reminders; yearly recurrence on an IFC date** | v1.0/v1.x (MVP if capacity allows) | Most-upvoted complaint. Keep it local-only first (no permissions). Could slip to v1.1 if it threatens launch quality, but the listing will be compared on this. |
| 11 | **Month-grid widget** (with event/holiday dots) and widget theming (Material You, transparent, minimalist) | v1.x | Ethiopian Calendar's most-praised feature; French calendar's style options. |
| 12 | **Built-in holidays** (start with a few countries from an open dataset such as `date-holidays`; show on both grids) | v1.x | Owner requirement; only the web competitor has it. Licence/accuracy review needed. |
| 13 | **Import holidays / events from ICS; export ICS** | v1.x | Avoids needing calendar permission; mirrors 13 Calendar's ICS export. |
| 14 | **Read-only overlay of device calendar events on the IFC grid** (runtime `READ_CALENDAR`, opt-in) | v1.x | Turns novelty into daily driver; "Wish I could have it on my Galaxy Calendar". Opt-in keeps core permission-free. |
| 15 | **Share today's date / conversion as text or image** | v1.x | Enthusiast audience evangelises; free marketing. |
| 16 | **Custom month names** (and optional numeric month display, e.g. "Month 7") | v1.x | Requested by 3 reviewers (13-helpful review). Cheap. Also defuses the "names don't match numbers" crowd. |
| 17 | **Persistent notification / status-bar date; Quick Settings tile** | v1.x | HebDate users rely on it; low effort. |
| 18 | **Localization** (incumbent has 10 languages; fixedcalendar.org has ES; a 3★ Spanish review exists) | v1.x | Table stakes to match incumbent; Play ranks localized listings. |
| 19 | **Optional moon-phase indicator** | Later | Frequently requested by the spiritual audience and shipped by Moon Year Calendar / 13cal.net; but risks reinforcing "IFC = lunar" confusion. Off by default, with explainer. |
| 20 | **Write-back / two-way sync of IFC-recurring events to Google Calendar** | Later | HebDate's premium feature; complex (WRITE_CALENDAR, recurrence expansion). |
| 21 | **Wear OS tile / complication** | Later | iOS competitor has Watch; HebDate has Wear. Niche within a niche. |
| 22 | **Alternative variants** (spring/April year start à la Moon Year Calendar; real-weekday-aligned mode; ISO-week-based 13×28) | Later / maybe never | ~5 reviewers want April start, 1–2 want real-week alignment. Supporting variants multiplies test surface and muddies "correctness". If done, keep the canonical Cotsworth/Eastman IFC as the clearly-labelled default. |
| 23 | **Side-by-side year comparison on tablets/landscape; printable PDF export** | Later | 13 Calendar / 13cal.net pattern; 13cal.net charges $9 for a PDF — possible tip-jar-style monetization. |

**Suggested monetization stance:** free, no ads, at launch (the niche is tiny and ratings matter more than pennies). If ever monetized: a one-time "supporter" unlock for cosmetic widget themes / PDF export. Reviewers have said they'd "rather buy this" than see ads.

---

## 7. Audience and communities

**Evidence-based (from store reviews — VERIFIED):**

1. **Calendar-reform / rationalist enthusiasts.** Know the terms IFC, Cotsworth, Eastman, Kodak; care about correctness, conversions, integrations, week-start options. Self-described as tiny: "the IFC heads in the world (there are dozens of us --dozens!!)" (iOS review, 2025-01-29). They want: accurate math, widgets, system-calendar integration, format customization, a history/info screen.
2. **"Natural time" / spiritual / anti-Gregorian audience.** Reviews reference the moon, menstrual cycles, taxes, "NWO", "archons", Earth "actually being 2033". They want: moon phases, April/spring new year, renamed/renumbered months, lunar alignment. They also leave many of the 5★ reviews. They overlap with users of [Lightbody](https://play.google.com/store/apps/details?id=com.light.body.technology.app&hl=en_US) and [DreamSpell](https://play.google.com/store/apps/details?id=com.kurbetsoft.dreamspell&hl=en_US) (the latter has 546 reviews — this audience is *larger* than audience 1).
3. **Practical self-organisers.** Want to actually plan with 28-day months — "personal scheduling of housework, meds, etc." (Play review, 2026-02-02). They want events, reminders, notes. A 28-day cycle also suits shift/medication/cycle tracking.

**Where they gather:**

- **TikTok** — search surfaced multiple TikTok "discover" topic pages ([International Fixed Calendar](https://www.tiktok.com/discover/international-fixed-calendar), [What If There Were 13 Months](https://www.tiktok.com/discover/what-if-there-were-13-months), [How to Figure Out Your Age with A 13 Month Calendar](https://www.tiktok.com/discover/how-to-figure-out-your-age-with-a-13-month-calendar)). Existence of the pages is verified via search results; their content/view counts were **not verified**. This is the most plausible source of the incumbent's ~1K/month installs ([AppBrain snippet](https://www.appbrain.com/app/13-month-fixed-calendar/com.bysoftware.fixedcalendar): "960 in the last 30 days" — search-snippet only, page itself returned 403, so PARTIAL) and of the April-new-year folk beliefs.
- **X/Twitter** — [@13Calendar](https://13cal.net/) (linked from 13cal.net; follower count not verified).
- **Web hubs** — [fixedcalendar.org](https://www.fixedcalendar.org/) ("A community exploring a simpler, more regular way to organize time"; has a Community nav item, contents not verified), [13calendar.pages.dev](https://13calendar.pages.dev/) ("community profiles"), [Calendar Wiki on Fandom](https://calendars.fandom.com/wiki/International_Fixed_Calendar) (long-running calendar-reform hobbyist wiki; seen in search results, not opened).
- **Facebook / podcasts** — the Dekatrian listing points to a "Dekatrian Calendar Facebook group" and the Brazilian Scicast podcast (not verified).
- **Reddit — NOT VERIFIED.** Reddit was completely inaccessible to this research agent. I cannot confirm the existence, size, or content of any subreddit. **To-do for a human:** search Reddit for "International Fixed Calendar" and check likely homes such as r/ISO8601, r/calendars, r/CrazyIdeas, r/Showerthoughts, r/androidapps (names are guesses — none verified), and note what app features people ask for. Periodic viral "why don't we have 13 months" posts are plausible but unconfirmed here.
- **Standards-adjacent interest:** W3C i18n tracked an issue on "[13 month calendars](https://www.w3.org/International/track/issues/98)" — seen in search results only.

**What they ask for (consolidated, all from verified reviews):** events/reminders; widget; tap-to-convert & birthday lookup; weekday labels & legibility; custom month names; moon phases; April/spring year start; system calendar integration; no ads; whole-year overview; info/history screen; correct dates.

---

## 8. Naming observations

**Already taken / crowded (avoid exact matches and near-duplicates):**

| Name | Where | Source |
|------|-------|--------|
| "13 Month Fixed Calendar" | Play — **two different apps** with this exact title | [erkantr](https://play.google.com/store/apps/details?id=com.bysoftware.fixedcalendar&hl=en_US), [Eizuberg](https://play.google.com/store/apps/details?id=eizu.kodaCalendar&hl=en_US) |
| "International Fixed Calendar" | iOS (active); Play (delisted 2018 app) | [App Store](https://apps.apple.com/us/app/international-fixed-calendar/id1107327564) |
| "Moon Year Calendar", "Roots Calendar" | iOS | section 2 |
| "Lightbody: 13 Moon Calendar", "DreamSpell: Tzolkin Calendar", "Dekatrian" | Play | section 2b |
| "13 Calendar", "13cal" / "13 Month Calendar", "Fixed Calendar", "JoyTempo", "13 Months" | Web brands | section 2a |
| "Perpetual Calendar" | Play (unrelated app ranks for "perennial/perpetual") | Play search 2026-09-17 |
| "Sol" | Crowded word on Play: "Sol - Inner Growth & Community", "Sol Flower App", solitaire apps | Play search "Sol 13" |
| "IFC" | Dominated by unrelated orgs on Play: "The IFC", "IFC Markets Forex Trading", "IFC Events", etc. | Play search "IFC Today" / "IFC calendar" |
| "Thirteenth Month" | Play — a Philippine 13th-month-pay calculator (`com.sweldongpinoy.thirteenthmonth`) | Play search |
| "Kodak …" / "Eastman …" | Trademarks of active companies ("KODAK Calendar Frame" is on Play). **Do not use in the title**; fine as historical mention in the description. | Play search "Kodak calendar" |

**Observations**
- Play titles are limited to 30 characters; the pattern *Brand: descriptor* lets a distinctive brand coexist with search keywords ("13 month", "fixed calendar", "IFC").
- Every existing name is purely descriptive, which is why two apps collided. A short distinctive brand word is an easy win.
- Leading with "IFC" is bad ASO (acronym collision); keep it in the short description instead.
- Avoid "moon"/"lunar" in the title — it attracts the audience most likely to 1★ the app for not being lunar.

**Candidate names** (Play US search on 2026-09-17 showed no app with these exact brand words; **trademark and domain checks NOT done**):

1. **Sol28** — "Sol28: 13-Month Calendar". Zero Play results for "Sol28". Encodes the two signature facts (month Sol, 28 days). Short, widget-label friendly.
2. **Cotsworth** — "Cotsworth: 13-Month Calendar". Honours the inventor; Play search for "Cotsworth" returns only unrelated apps (Cotswold walks etc.). Enthusiasts will get it instantly; others need the subtitle.
3. **Yearal** — Cotsworth's own name for the system (the Dekatrian listing even cites "the Yearal"). Play search returned nothing relevant. Distinctive, pronounceable, historically grounded.
4. **Thirteen28** (or **13×28**) — "Thirteen28: Fixed Calendar". Zero Play results. Purely descriptive-numeric; "×" may be awkward in search/typing, so prefer the spelled form.
5. **Year Day** — "Year Day: 13-Month Calendar". Names the most charming feature of the IFC; friendly tone. Slight risk of blending with "Day of Year" utilities seen in search.
6. **Solmonth** / **Month of Sol** — "Month of Sol: IFC Calendar". Evocative; avoids bare "Sol" collisions.
7. **Evenmonth** — "Evenmonth: 13×28 Calendar". Echoes the historical "Equal Month calendar" name; Play search showed no match.
8. **Fixed Thirteen** — "Fixed Thirteen Calendar". Keeps both top keywords in the brand while not duplicating the incumbent string.

Recommendation: **Sol28** or **Yearal** as brand + "13-Month Calendar" descriptor; put "International Fixed Calendar (IFC)" in the short description and first line of the long description for search.

---

## 9. Risks

| Risk | Detail | Mitigation |
|------|--------|------------|
| **Tiny niche** | Incumbent: 10K+ lifetime downloads (earliest review seen: July 2024); iOS app: 12 ratings in 10 years; an iOS reviewer jokes "there are dozens of us". Web/GitHub projects have 0–5 stars. Revenue potential ≈ nil. | Treat as a craft/portfolio project; optimise for ratings and low maintenance, not monetisation. Keep scope tight. |
| **New clone-ish entrants** | Eizuberg shipped a same-named app with events in Sep 2026; low-effort publishers can copy features quickly. | Compete on correctness, widget reliability, polish, no-ads — things volume publishers don't do. Distinctive brand name. |
| **Incumbent wakes up** | erkantr promised a widget (Sep 2025) and did a big update in May 2025. | Ship widget + converter in MVP so parity isn't enough for them. |
| **Audience expectation mismatch → 1★ reviews** | Many users expect April new year, lunar months, renamed months, or think a Jan-1 IFC is "the normal calendar". | In-app explainer + FAQ; accurate store listing; optional moon overlay and custom month names later; reply to reviews. |
| **Correctness bugs are very public** | Off-by-one/ahead-by-days reviews on both platforms; time zones, DST, midnight rollover, leap years (incl. century rules), Leap Day (17 Jun) and Year Day (31 Dec). | Pure-Kotlin engine with exhaustive tests (every day across 400-year cycle, round-trip both directions); use `java.time.LocalDate` and device zone; never cache "today" in widgets. |
| **Widget reliability on OEM Android** | Hijri Widget had Realme/Infinix breakage and "doesn't auto-update" reviews; iOS IFC widget same complaint. | Multiple update triggers (date/time/timezone broadcasts, post-midnight alarm, WorkManager fallback); test on Samsung/Xiaomi/Realme; no toasts. |
| **Play policy — calendar permissions** | `READ_CALENDAR`/`WRITE_CALENDAR` are runtime "dangerous" permissions; calendar data is personal & sensitive under Play's [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311): needs privacy policy (in Console **and** in-app), prominent in-app disclosure + affirmative consent before access, accurate Data safety form, and data use limited to expected functionality. | Keep MVP permission-free (local events DB). Add calendar access later as opt-in with disclosure screen. Host a simple privacy policy from day one. Avoid contacts/location entirely (HebDate was 1★'d for over-asking). |
| **Play policy — exact alarms** | [`USE_EXACT_ALARM`](https://support.google.com/googleplay/android-developer/answer/13161072) is restricted to alarm/timer apps and "a calendar app that shows event notifications". We qualify **only once event reminders exist**; a widget-only app would not. | For midnight widget rollover use inexact alarms/WorkManager + broadcasts. Declare `USE_EXACT_ALARM` only when reminders ship, or use `SCHEDULE_EXACT_ALARM` with graceful fallback. |
| **Play policy — new developer account testing** | Personal developer accounts created after Nov 13, 2023 must run a closed test with **at least 12 testers opted-in for 14 continuous days** before production access ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465)). | If the owner's account is new, recruit 12 testers early (the enthusiast communities in §7 are the natural pool); factor ~3–4 weeks into the launch plan. |
| **Play metadata policy / impersonation** | Using the identical title as existing apps, or third-party trademarks (Kodak, Eastman) in title/icon, risks rejection or takedown. Title ≤ 30 chars; no emoji/keyword stuffing. | Distinctive brand name (section 8); mention Kodak only as history in the description. |
| **Holiday data licensing & accuracy** | Holiday datasets vary in licence and correctness by country/year (13 Calendar itself warns "Government holiday coverage varies by country and year"). | Start with a small curated set + ICS import; check dataset licences before bundling. |
| **Variant creep** | Supporting April-start/lunar/real-weekday variants multiplies complexity and undermines the "correct IFC" claim. | Canonical Cotsworth/Eastman IFC only through v1.x; revisit with usage data. |
| **Research blind spots** | Reddit inaccessible; Play search is US/English only; Eizuberg app and web tools not hands-on tested; TikTok/X reach unmeasured; no trademark/domain search for name candidates. | Owner to spot-check: install both Play apps, browse Reddit manually, run a trademark/domain search on the chosen name. |

---

## 10. Source index

**Google Play (fetched directly 2026-09-17):**
[erkantr 13 Month Fixed Calendar](https://play.google.com/store/apps/details?id=com.bysoftware.fixedcalendar&hl=en_US) ·
[Eizuberg 13 Month Fixed Calendar](https://play.google.com/store/apps/details?id=eizu.kodaCalendar&hl=en_US) ·
[io.github.hidroh.calendar (404)](https://play.google.com/store/apps/details?id=io.github.hidroh.calendar) ·
[Lightbody](https://play.google.com/store/apps/details?id=com.light.body.technology.app&hl=en_US) ·
[DreamSpell](https://play.google.com/store/apps/details?id=com.kurbetsoft.dreamspell&hl=en_US) ·
[Dekatrian](https://play.google.com/store/apps/details?id=pindi.flutter.dekatrian&hl=en_US) ·
[Ethiopian Calendar & Converter](https://play.google.com/store/apps/details?id=com.shalom.calendar&hl=en_US) ·
[HebDate](https://play.google.com/store/apps/details?id=com.lionscribe.hebdate&hl=en_US) ·
[French Revolutionary Calendar](https://play.google.com/store/apps/details?id=ca.rmen.android.frenchcalendar&hl=en_US) ·
[Hijri Widget](https://play.google.com/store/apps/details?id=me.amrbashir.hijriwidget&hl=en_US) ·
[Calendar Converter](https://play.google.com/store/apps/details?id=com.ramdroid.calendarconverter.full&hl=en_US)

Play search queries run (US/EN): international fixed calendar; 13 month calendar; cotsworth calendar; sol calendar 13 months; fixed calendar widget; 13 moon calendar; IFC calendar; equal month calendar; 13 months 28 days calendar; Eastman calendar 13 month; fixed calendar; calendar reform; new calendar 13 months sol; kodak calendar 13 month; perennial calendar; perpetual calendar 13 months; IFC date converter; Sol month calendar; 13 month calendar widget; 28 day month calendar; year day leap day calendar; French republican calendar; discordian calendar (no dedicated app surfaced); ethiopian calendar; dual calendar hijri gregorian widget; plus name-collision checks in §8. Only the two apps in §2a rows 1–2 are IFC apps.

**Third-party Play trackers:** [AppRecs](https://apprecs.com/android/com.bysoftware.fixedcalendar/13-month-fixed-calendar) · [AppBrain](https://www.appbrain.com/app/13-month-fixed-calendar/com.bysoftware.fixedcalendar) (403 on direct fetch; snippet only) · [APKCombo (delisted app)](https://apkcombo.com/international-fixed-calendar/io.github.hidroh.calendar/)

**Apple:** [International Fixed Calendar](https://apps.apple.com/us/app/international-fixed-calendar/id1107327564) · [reviews RSS](https://itunes.apple.com/us/rss/customerreviews/id=1107327564/sortBy=mostRecent/json) · [Moon Year Calendar](https://apps.apple.com/us/app/moon-year-calendar/id6761673829) · [Roots Calendar](https://apps.apple.com/us/app/roots-calendar/id6771264156) · [Lightbody iOS](https://apps.apple.com/us/app/lightbody-13-moon-calendar/id6478142281)

**F-Droid:** [search](https://search.f-droid.org/?q=international+fixed&lang=en) · [French Revolutionary Calendar package](https://f-droid.org/en/packages/ca.rmen.android.frenchcalendar/)

**GitHub:** [topic: international-fixed-calendar](https://github.com/topics/international-fixed-calendar) · [SuperiorTimeSystem](https://github.com/vivian-dai/SuperiorTimeSystem) · [13calendar-public](https://github.com/jean7rafael/13calendar-public) · [fixedcalendar org](https://github.com/fixedcalendar) · [PyryL/fixedcal](https://github.com/PyryL/fixedcal) · [ThisIsMissEm/gregorian-to-ifc-converter](https://github.com/ThisIsMissEm/gregorian-to-ifc-converter) · [comicsads/Gregorian-to-IFC](https://github.com/comicsads/Gregorian-to-IFC) · [gauravnumber/ifc-cli](https://github.com/gauravnumber/ifc-cli)

**Web:** [13calendar.pages.dev](https://13calendar.pages.dev/) · [fixedcalendar.org](https://www.fixedcalendar.org/) · [13cal.net](https://13cal.net/) · [joytempo.com](https://joytempo.com/) · [13months.net](https://13months.net/) (title only) · [Wikipedia: IFC](https://en.wikipedia.org/wiki/International_Fixed_Calendar) · [Wikibooks algorithm](https://en.wikibooks.org/wiki/Algorithm_Implementation/Date_and_time/Conversion_of_a_Gregorian_date_to_an_International_Fixed_Calendar_date)

**Google Play policy:** [User Data](https://support.google.com/googleplay/android-developer/answer/10144311) · [Exact alarm permission](https://support.google.com/googleplay/android-developer/answer/13161072) · [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465)
