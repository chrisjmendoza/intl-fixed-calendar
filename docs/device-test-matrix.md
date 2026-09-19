# Device test matrix

Status: **started at M6 T1 (2026-09-19); nothing here has been run yet.** These are the checks the automated
gate cannot make (Robolectric does not enforce exported receivers, Doze, launchers, the lock screen or OEM
battery management). [ROADMAP.md](ROADMAP.md) M5 T8 owns this file; tick a row with the device, Android
version and date when it passes. Target coverage: API 26, 33 and 36, plus one Samsung or Xiaomi device.

## Date correctness and the widgets (M5 T1–T3, T5–T6)

| # | Check | Result |
|---|---|---|
| W1 | Place the Today widget at each of its three sizes and the Month widget at both; the lines shown match the size. | |
| W2 | Dynamic colour on API 31+, the brand palette below; light and dark. | |
| W3 | Tapping either widget opens the app. | |
| W4 | With the app force-closed, wait past midnight (or change the device date): both widgets show the new date. | |
| W5 | Change the device time zone across a date line: both widgets follow. | |
| W6 | Reboot: the widgets are right after unlock. | |
| W7 | Add, edit and delete an event: the Month widget's dot appears or disappears within about a second, also after a burst of quick edits. | |
| W8 | An event on Year Day and one on Leap Day (2028) mark the band. | |
| W9 | Widget picker previews on API ≤ 30, 31–34 and 35+. | |
| W10 | TalkBack on the Month widget: what is actually read out (one description, or every day number). | |
| W11 | logcat shows no WorkManager complaint about the removed `ACCESS_NETWORK_STATE`. | |

## Reminders (M6 T1, T3)

| # | Check | Result |
|---|---|---|
| R1 | A timed event a few minutes out with a 1-minute reminder fires within a minute. | |
| R2 | Doze: `adb shell dumpsys deviceidle force-idle`, then a reminder a few minutes out still fires on time; `adb shell dumpsys deviceidle unforce`. | |
| R3 | Lock screen with sensitive content hidden: "Event reminder" and the time, **no title**. | |
| R4 | Notifications denied: nothing crashes; after re-enabling, the next reminder appears. | |
| R5 | First reminder chip on Android 13+ asks for the notification permission once; denying never blocks saving. | |
| R6 | Reboot with a reminder ~5 minutes out: it arrives shortly after unlock. One 2 hours past stays silent. | |
| R7 | Change the time zone, and set the clock forward and back, with a reminder armed: the next one is still right. | |
| R8 | An all-day event tomorrow reminds at 09:00 local, not at midnight. | |
| R9 | API 31/32: revoke "Alarms & reminders"; reminders still arrive (up to ~10 minutes late); re-grant restores punctuality without reopening the app. | |
| R10 | Force-stop the app, reopen it: the pending reminder is armed again. | |
| R11 | Samsung / Xiaomi battery management: the app is not put to sleep; note any OEM limit for the in-app help text. | |

## App

| # | Check | Result |
|---|---|---|
| A1 | "Every Sol 13" and "every Year Day" events appear on the right days, on the grid, in Day detail and on Today. | |
| A2 | "Delete this occurrence" removes only that day; undo restores it. | |
| A3 | "Delete all data" empties events, resets settings, clears widget dots and any pending reminder. | |
| A4 | `bmgr` backup → reinstall → restore round trip (security-and-privacy.md acceptance checklist). | |
