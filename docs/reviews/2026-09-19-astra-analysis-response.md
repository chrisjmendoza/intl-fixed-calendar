# Response to the 2026-09-19 external review

Status: **current as of M6 T1 (2026-09-19).** The review is [2026-09-19-astra-analysis.md](2026-09-19-astra-analysis.md)
(an outside model's read of the repo at commit `6175108`, pasted in by the owner, unedited). This file
records which findings were checked against the code, what we agree with, and where each one went.
[ROADMAP.md](../ROADMAP.md) owns the resulting tasks; this file only points at them.

## Engineering findings — all five verified in the source

| # | Finding | Verdict | Where it went |
|---|---|---|---|
| 1 | An open screen can go stale after a clock or time-zone change: `:app` binds `RealDateTicker` directly (it sleeps until the midnight it computed), and `DefaultObserveAgendaUseCase` reads the zone only when its other inputs emit. | **Agree — real.** ARCHITECTURE §4 already specified the Android layer (re-emit on resume and on `TIME_SET` / `TIMEZONE_CHANGED` / `DATE_CHANGED`); it was never built, and two task reports had flagged it. | ROADMAP **R1** |
| 2 | Rapid Save taps can create duplicate events: `save()` launches without an in-progress guard and each new save draws a fresh UID. | **Agree — real**, source-level. | ROADMAP **R2** |
| 3 | Moving a monthly-IFC event's start to Year Day or Leap Day silently drops the recurrence (`monthlyOn(...) ?: Recurrence.None`). | **Agree — real.** The option is hidden but the stale choice is kept and saved as a one-off. | ROADMAP **R3** |
| 4 | "Always right" / "never stale" overstate what a widget can guarantee. | **Agree on the wording.** Since the review, M6 T3 gave the rollover an exact alarm (the 10-minute window is now only the fallback), but Doze, force-stop and OEM task killers remain. FEATURES now states the mechanism instead of an absolute; [device-test-matrix.md](../device-test-matrix.md) is where the claim gets earned. | Fixed in this change; M5 T8 |
| 5 | The Privacy screen says nothing stored "could leave your device" and then describes Android's encrypted cloud backup. | **Agree — contradiction.** Reworded: the app has no server and no network permission, so *the app* sends nothing; Android's own encrypted backup is the one copy that can leave the phone. | Fixed in this change |

Also raised, and already handled by the time of this response: the stale README (rewritten in the same wave,
with a `check_docs.py` guard), reminders that stored but did not fire (M6 T1 and T3 are done), and widget-picker
previews that showed a loading spinner (M5 T5 is done; the API 35+ path still needs a device).

## Product and process points

| Point | Verdict |
|---|---|
| The product works best as an IFC **companion**; events raise expectations (reminders, recovery, interop). | **Agree**, and the release map already reflects it: 1.0 has no calendar permission and no import. We keep events in 1.0 because "every Sol 13" is the one thing no other calendar can express, and reminders now fire. Whether 1.0 should shrink further is an owner decision (listed under ROADMAP "Open decisions"). |
| Explain recurrence semantics at the point of choice ("weekly" is seven real days; an IFC-date birthday and a Gregorian-date birthday drift apart). | **Agree.** ROADMAP **R4**. |
| The editor rewrites Gregorian rules into its own weekly / yearly text; that must not silently change imported schedules. | **Agree**, already recorded: ADR 0005 Amendment 1 and the M7 T1 import task. |
| No screenshot baseline: the workflow records zero images and CI verifies none. | **Agree.** ROADMAP **R6** (the rest of M2 T10). |
| The process is heavier than the product needs; drift is visible. | **Partly agree.** The drift was real (README, status lines) and is fixed, with a standing docs step in every task brief (WORKFLOW §3, §4.2, §6). We keep the specs and contracts: they are what lets parallel agents build against each other without talking, and what caught the bugs above being *findable*. What we drop is restating one doc in another — link to the owner instead (WORKFLOW §4.2 rule 1). |
| Watch a few unfamiliar users answer "what Gregorian date is this?" and "when will this event happen?". | **Agree**; nothing to build. Noted under M2 T13 as the acceptance test for the design pass. |

## UI / UX notes

All of these are design-pass material and are folded into ROADMAP **M2 T13**, which the owner has sequenced
after the foundation. Verdicts:

- **Agree:** make the Gregorian information visibly secondary in the month cell; a selected-date summary in the
  dead space under the grid; an intentional, non-chip treatment for Year Day and Leap Day with its own accent;
  tighter Year-view tiles with the highlight on the cells rather than a large outline; large widgets should show
  more (today's details, year progress) instead of empty space; a swap-arrows icon for Convert (the current one
  reads as "refresh"); restraint — typography, spacing and the 4×7 grid as the design language.
- **Agree, already planned:** IFC-primary / Gregorian-primary display preference (FEATURES W3, 🟡); the
  Gregorian grid with IFC inset (FEATURES C10, owner-requested).
- **Already true:** the converter has no submit button; the result updates as the input changes.
- **Open question, not adopted yet:** renaming "Actual weekday" to "Gregorian weekday". calendar-spec §4.1 uses
  *actual* on purpose — the seven-day cycle is not a property of the Gregorian calendar, and it is what real life
  runs on — but the reviewer's point that the label reads as a value judgement is fair. Decide it with the
  design pass and test it on the unfamiliar users above; if it changes, it changes in the spec's glossary first.
- **Disagree:** dropping the actual-weekday header row from the month grid by default. Reading "Sunday" on a real
  Thursday is the single most dangerous misreading this app can cause (FEATURES T2, C2); the `BOTH` default stays
  until testers say otherwise, and the setting already lets a user turn it off.
