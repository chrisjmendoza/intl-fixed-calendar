Yes—this is worth continuing as a hobby and portfolio project. Yearal has a distinctive idea, substantial implementation, and unusually careful date handling. Its main risk is scope: a polished IFC companion is achievable; a dependable replacement calendar brings much more ongoing work.
I reviewed through commit 6175108, including the Year view, Month widget, Learn, and Privacy screens added during this review. I made no source changes.
The engineering foundation is strong.
- The calendar model represents the difficult cases explicitly. Regular dates, Leap Day, and Year Day are separate types. Nominal IFC weekdays and actual Gregorian weekdays are distinct properties. That prevents whole categories of accidental mistakes. Calendar implementation
- The architecture separates useful responsibilities. Pure Kotlin calendar/domain logic, Room storage, feature screens, and Android scheduling have clear boundaries. Storing ordinary dates in Gregorian form while preserving IFC recurrence rules is a sensible design. Architecture
- Testing goes beyond self-confirming round trips. The calendar oracle independently enumerates the 13 months and intercalary days across all 3,652,059 supported dates. That is substantially stronger than merely proving that two conversion functions undo each other. There are also recurrence property/oracle tests and shared repository contract tests. Calendar oracle
- The offline design is concrete. The generated debug manifest passed the permission allow-list check and contains no Internet permission.
I ran the full check and debug build successfully. The test reports contained 1,604 tests, zero failures/errors, and zero skipped tests. I also freshly reran the calendar, domain, and holiday suites without cached results; all passed. Documentation-link and manifest checks passed too. I did not run the app on a physical device, so visual polish and device behavior remain unverified by this review.
The most actionable problems are at the integration and UX boundaries.
1. Time and time-zone changes can leave the open app stale.
   RealDateTicker sleeps until the previously calculated midnight. Its documentation expects Android resume/time-change handling above it, but the app directly binds that ticker. Separately, the agenda reads the current zone only when its other inputs emit; the zone itself is not an observed input.
   Changing zones while a screen remains active can therefore leave “today,” displayed event times, or event-day placement stale. The widget’s broadcast handling does not resolve those screen streams. Introduce an observable clock/zone invalidation source and test a zone change that crosses a date boundary. Ticker binding, agenda implementation
2. Rapid Save taps can create duplicate new events.
   save() launches a coroutine without setting an in-progress guard. Each new-event save generates another UID, so repository uniqueness checks would not deduplicate them. This is a high-confidence source-level risk, rather than a device-reproduced finding. Disable Save immediately during persistence and keep a stable identity for the draft. Save implementation
3. Changing a monthly event to an intercalary date silently removes recurrence.
   Select monthly IFC recurrence, then change the start to Year Day or Leap Day. The monthly option disappears, but the draft retains its selection; building the event falls back to Recurrence.None. The resulting one-off event may surprise the user. Require a replacement recurrence choice or explicitly explain the change before saving. Recurrence construction
4. Widget correctness claims exceed the current delivery guarantee.
   The rollover scheduler uses a ten-minute window and explicitly acknowledges additional Doze delays. Recomputing the correct date when rendered is good, but the widget can still display yesterday until it refreshes. “Always right” and “never goes stale” are too strong. Test overnight behavior, reboot, clock changes, and battery restrictions on actual devices. Scheduler
5. The new Privacy screen contradicts its own backup explanation.
   It says stored information cannot leave the device, then correctly explains that Android may upload encrypted backups. The backup configuration permits that. Describe the distinction clearly: Yearal has no app-operated server or direct network access; Android backup may transfer its data. Privacy text, backup configuration
Calendar edge cases are a strength, with some remaining product decisions.
The implementation explicitly handles century leap-year rules, Year Day, IFC Leap Day, clamped month/year arithmetic, exclusive event ends, DST gaps/overlaps, and events crossing dates between zones. I found no basic conversion defect.
The harder question is whether users understand those semantics. “Weekly” means seven real days, so it does not preserve the nominal IFC weekday across an intercalary day. Birthdays recurring on an IFC date and birthdays recurring on a Gregorian date can also behave differently. Those distinctions deserve short explanations at the recurrence choice, even with the Learn screen available.
Before adding import, address the editor’s limited recurrence representation: it reconstructs Gregorian rules into its simpler weekly/yearly choices and can discard additional qualifiers. That is a documented current limitation, but it must not silently change imported schedules later. Event draft model
The product idea works best as an IFC companion.
My judgment is that the strongest everyday use is: see today in IFC, keep a useful widget, explore the year, and convert dates effortlessly. That makes the unusual calendar tangible without requiring anyone else to adopt it.
Events add depth, but they also create expectations around reminders, recovery, interoperability, and reliability. Reminders currently store preferences without delivering notifications; the UI honestly explains that. Device-calendar overlay and import/export remain future work. Until those exist, users must maintain another calendar alongside this one. Current roadmap
The dual-date presentation, explicit intercalary bands, and accessibility descriptions are good choices. Still, two weekday systems and two day numbers create cognitive load. Watch a few unfamiliar users answer: “What Gregorian date is this?” and “When will this event happen?” That will teach you more than adding another view.
Maintainability is good, but the process is heavier than the product currently needs.
Module boundaries, injectable clocks, and documented contracts make the code easier to reason about. Keep those. However, the volume of specifications, milestone bookkeeping, and repeated commentary creates maintenance overhead—and drift is already visible.
The README still says the Android app has not started and the name is undecided. It undersells the project badly. Replace that with current screenshots, implemented versus planned features, accurate setup requirements, and a practical way to try it. README
Also, screenshot infrastructure is present, but visual regression coverage is not: the recording workflow explicitly describes recording zero images, and CI does not yet enforce screenshot verification. Passing Compose tests cannot establish that every layout looks good. Screenshot workflow
I would prioritize further effort in this order:
1. Fix time-zone refresh, duplicate saves, and silent recurrence changes.
2. Correct README/privacy claims and make the current app easy to evaluate.
3. Validate widgets, accessibility, large fonts, and event persistence on devices; establish a small screenshot baseline.
4. Deliver reliable reminders before presenting it as a daily event manager.
5. Let actual usage determine whether calendar integration, more holidays, or further customization comes next.
As a portfolio piece, this already has substance: meaningful domain modeling, independent correctness testing, persistence, reactive UI, and Android lifecycle work. A finished, understandable release would demonstrate those strengths better than a larger roadmap. I would keep building—but put the next effort into trust, usability, and shipping a bounded version.

# UI / UX Improvement Notes — International Fixed Calendar App

## Overall Direction

The app already has a strong functional foundation. The main opportunity is not adding more features immediately, but making the IFC system easier to understand at a glance and giving the app a more distinctive visual identity.

The app should feel like an intentional International Fixed Calendar experience, not simply a normal Android calendar displaying unusual dates.

The strongest visual idea to lean into is the structure of the IFC itself:

- 13 months
- 28 days per month
- 4 weeks per month
- Every month starts on Sunday
- Year Day and Leap Day exist outside the normal weekly cycle

That regular 4x7 structure can become part of the app's visual identity.

---

# Main Calendar Screen

## Current Structure

The current calendar is logically correct:

- Large numbers = IFC dates
- Small numbers = corresponding Gregorian dates
- Large weekday header = IFC weekday columns
- Smaller weekday row = Gregorian weekday for the mapped Gregorian date

Example:

IFC:
Sun Mon Tue Wed Thu Fri Sat
1   2   3   4   5   6   7

Gregorian equivalent:
Thu 10, Fri 11, Sat 12, etc.

The IFC month correctly begins on Sunday.

## Main Problem

The relationship between IFC and Gregorian information is not immediately obvious to a new user.

The two weekday rows can look like competing calendar headers:

Sun Mon Tue Wed Thu Fri Sat
Thu Fri Sat Sun Mon Tue Wed

This requires the user to reverse-engineer what the screen means.

## Recommended Improvement

Keep the existing hierarchy:

- IFC dates large and primary
- Gregorian dates small and secondary

Do NOT swap these.

Instead, make the Gregorian information more obviously secondary.

Possible cell layout:

    Sun
     1
   Sep 10

    Mon
     2
   Sep 11

    Tue
     3
   Sep 12

The IFC weekday is already communicated by the column header, so repeating the Gregorian weekday may not be necessary.

For a selected date:

    Tue
   [10]
 Sat · Sep 19

This makes the relationship immediately understandable:

IFC:
Tuesday, September 10

Gregorian:
Saturday, September 19

## Month Header

Consider adding contextual information beneath the month name:

September 2026
IFC Month 10 · Gregorian Sep 10 – Oct 7

This helps users understand where the IFC month falls within the Gregorian calendar.

## Selected Date Details

There is currently significant unused vertical space underneath the month grid.

Use some of it for a selected-date summary card.

Example:

Tuesday · September 10

Gregorian
Saturday · September 19

Day 262 · Week 38

This gives tapping dates more purpose and makes the calendar feel interactive rather than static.

---

# Today Screen

This is currently one of the strongest screens.

The hierarchy is already useful:

- IFC date
- Gregorian equivalent
- weekday comparison
- day/week/year information
- year progress
- upcoming special dates

## Weekday Card

The current weekday card takes a relatively large amount of space for two pieces of information.

Current:

IFC weekday: Tuesday
Actual weekday: Saturday

Possible compact version:

IFC                 Gregorian
Tuesday             Saturday

Or:

Tuesday · IFC
Saturday · Gregorian

"Gregorian weekday" may also be clearer than "Actual weekday," because both weekdays are technically valid within their respective calendar systems.

## Year Progress

The following information is very useful:

Day 262 · Week 38 of 52 · Q3

This should remain prominent.

The year progress visualization could better explain the IFC structure.

For example:

Day 262 of 364
████████████████░░░░ 72%

Year Day
●

This visually reinforces that Year Day exists separately from the normal 364-day weekly calendar.

---

# Year View

The miniature 4x7 month grids are one of the strongest visual ideas in the app.

They immediately communicate that every IFC month is structurally identical.

Keep this concept.

## Improvements

The current year layout has relatively large empty areas around each miniature calendar.

Possible improvements:

- tighten spacing between months
- slightly increase mini-calendar size
- strengthen month labels
- reduce the size/intensity of the selected-month border
- highlight the selected day or month using the cells themselves rather than a very large outline

Possible 3-column layout if readability allows:

Jan   Feb   Mar
Apr   May   Jun
Sol   Jul   Aug
Sep   Oct   Nov
Dec

Then place Year Day beneath the grid.

A 2-column layout is also acceptable if it remains more readable on phones.

---

# Year Day / Leap Day

These are important features of the International Fixed Calendar and should receive intentional visual treatment.

Currently Year Day looks somewhat like a floating Material chip.

Instead, make it a special calendar element.

Example:

YEAR DAY

December 31, 2026
Outside the weekly cycle

Likewise on leap years:

LEAP DAY

Outside the weekly cycle

Use a dedicated accent or visual treatment for these days that is different from normal date selection.

This reinforces one of the most interesting aspects of the IFC.

---

# Widgets

## Current Widget Issue

The large widget currently uses only a small portion of its available area.

The calendar occupies the top section while a large amount of empty space remains below it.

The solution should not simply be making the calendar larger.

Instead, widget content should adapt based on available widget size.

## Suggested Widget Sizes

### 2x1 — Today Widget

September 10
Tuesday · IFC
Sep 19 Gregorian

Simple and glanceable.

### 2x2 — Compact Month

September
S M T W T F S
1 2 3 4 5 6 7
8 9 10 11...
15...
22...

Highlight today.

### 4x2 — Detailed Month

September 2026
IFC Month 10
Sep 10 – Oct 7

Sun Mon Tue Wed Thu Fri Sat
 1   2   3   4   5   6   7
 8   9  [10] 11 12 13 14
15...
22...

### 4x4 — Month + Today Information

September 2026

[calendar]

Today

September 10 · Tuesday
Gregorian: Sep 19 · Saturday

Day 262 · Week 38

████████████░░░ 72%

Larger widgets should display additional useful information rather than simply adding whitespace.

---

# Widget Preview Problem

The Android widget picker currently fails to render useful previews.

The widget picker appears to show loading/progress indicators rather than the actual widget.

Investigate:

- previewImage
- previewLayout
- AppWidgetProviderInfo configuration
- Jetpack Glance preview behavior, if Glance is being used
- whether preview rendering depends on runtime state
- whether the preview requires database/preferences/state unavailable to the launcher

The widget preview should not require live application state.

Use representative static preview data if necessary.

Example preview:

September 2026
10 highlighted
Sep 10 – Oct 7

The preview's job is to demonstrate the widget layout, not necessarily display the current date.

Test preview behavior on both Samsung and Pixel launchers if possible.

---

# Navigation

Current bottom navigation:

Today
Calendar
Events
Convert
More

This structure is sensible and should remain for now.

## Convert Icon

The circular-arrow icon currently reads more like:

- refresh
- reload
- sync

rather than date conversion.

Consider using:

- opposing arrows
- swap arrows
- bidirectional arrows

This would communicate conversion more clearly.

## More

Keep More for now.

If it eventually contains only:

- settings
- about
- licenses

then it may be possible to move these into a settings icon and reduce the bottom navigation to four primary destinations.

This is not currently a priority.

---

# Convert Screen

The conversion workflow should be extremely fast.

Suggested design:

Gregorian → IFC

September 19, 2026

        ↓

September 10, 2026
Tuesday

Include a swap direction control:

⇅

Switching direction should immediately change the interface to:

IFC → Gregorian

Avoid unnecessary submit buttons.

Changing the date should update the converted result immediately.

Useful actions:

- Today
- Copy
- Share
- Swap calendar direction

---

# Events

Events have the potential to become one of the most useful parts of the app.

The most practical problem with using IFC is that the rest of the world still schedules things using Gregorian dates.

The app can act as the translation layer.

Example:

Dentist

September 14 · Thursday
Gregorian Sep 23 · Wednesday

2:00 PM

Potential display modes:

- IFC primary
- Gregorian primary

This would allow a user to live primarily in IFC while remaining compatible with normal calendars.

If Android calendar events can be integrated cleanly, this could become one of the app's strongest practical features.

---

# Date Display Preference

Consider eventually adding:

## IFC Primary

September 10, 2026
Saturday, Sep 19 Gregorian

## Gregorian Primary

Saturday, September 19
IFC September 10

## IFC Only

September 10, 2026
Tuesday

This could make the app useful both to:

- IFC enthusiasts
- curious users experimenting with the calendar

This is a later feature and not required for the first polished release.

---

# Visual Design Direction

Avoid over-designing the app.

The IFC itself already provides a strong visual concept.

Recommended direction:

Material 3
+
clean geometric calendar design
+
scientific/instrument-like organization

Avoid excessive:

- gradients
- glass effects
- animations
- decoration
- visual gimmicks

Use typography, spacing, alignment, and the 4x7 grid as the main design language.

The current dark theme works well.

Consider:

- one primary accent for selection/current date
- one special accent exclusively for Year Day / Leap Day
- consistent rounded corners
- consistent cell spacing
- stronger typography hierarchy

---

# App Identity

The app icon already uses a grid motif.

Continue using the IFC's mathematical structure as the app's identity:

13 months
4 weeks
7 days

This can appear subtly throughout:

- icon
- calendar cells
- year overview
- widgets
- progress visualizations
- onboarding/about screen

The app should visually communicate:

"This calendar is unusually regular."

That regularity is one of the most appealing characteristics of the IFC.

---

# Development Priority

Recommended order:

1. Clarify IFC vs Gregorian information on the main calendar.
2. Improve month cell layout while keeping IFC dates primary.
3. Fix responsive widget sizing.
4. Fix widget picker previews.
5. Add selected-date information beneath the calendar.
6. Improve Year Day / Leap Day presentation.
7. Polish the year-view miniature calendars.
8. Improve conversion screen workflow.
9. Improve event display and Gregorian interoperability.
10. Add customization/settings after the core UI is polished.

Avoid spending significant time yet on:

- multiple themes
- complex animations
- large numbers of settings
- decorative UI effects
- excessive customization

First make the core IFC experience exceptionally clear and pleasant.

---

# Overall Assessment

The app is worth finishing to Play Store quality.

It probably will not have a massive mainstream audience because the International Fixed Calendar itself is niche, but that does not make the project low-value.

It has several strengths as a hobby and portfolio project:

- immediately understandable core concept
- interesting date conversion logic
- calendar edge cases
- Android widget integration
- non-standard calendar UX problems
- responsive layouts
- system calendar/event integration possibilities
- a clear opportunity for thoughtful UI design

It demonstrates considerably more software-development thinking than a generic CRUD app or standard calendar clone.

The current functionality already appears substantial enough that the main goal should be refinement rather than expansion.

The biggest opportunity is making the app feel intentionally designed around the International Fixed Calendar instead of feeling like a normal Android calendar that happens to display IFC data.