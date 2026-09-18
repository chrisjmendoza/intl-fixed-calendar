# 0004. Holiday pack format, schema 1: what the spec left open

- Status: accepted
- Date: 2026-09-17
- Owning doc updated: [holidays-and-import.md](../holidays-and-import.md) §2.4; [ARCHITECTURE.md](../ARCHITECTURE.md) §2 and §3.3

## Context

M1 T9 implements the holiday-pack JSON format of [holidays-and-import.md](../holidays-and-import.md) §2.4
as `:core:holidays` (package `io.github.chrisjmendoza.yearal.core.holidays`): `@Serializable` DTOs, a
mapper onto the `:core:domain` holiday model of [ADR 0003](0003-holiday-rule-model.md), `HolidayPackLoader`,
`BundledHolidayPacks`, and the bundled `ifc.json`, `US.json` and `religious-christian.json` packs. The spec
gives one worked example and the field semantics (§2.2) but leaves the exact wire format of several fields
open, and its example uses two constructs the engine does not model. Per [WORKFLOW.md](../WORKFLOW.md) §5
they are decided here.

## Decision

1. **`schema` is required and must be `1`.** Any other value fails to load with a message naming the
   version, so a future schema 2 can change anything below without silently misreading old code.
2. **The pack carries its own `id`** (required, e.g. `"us"`, `"ifc"`, `"religious-christian"`), which
   becomes `HolidaySet.id` — the value persisted as the user's "enabled sets" choice. The file name
   (`US.json`) is only how `HolidayPackLoader.loadBundled` finds the pack; it may differ in case from the
   id. `region` (ISO 3166-1 alpha-2) and `sources` are optional; `name` with an `"en"` entry is required.
3. **Rule polymorphism is on a `"type"` key** with the discriminator values `fixed`, `nthWeekday`,
   `weekdayRelative`, `offset`, `easter`, `table`, `ifc`, decoded by a hand-written `KSerializer` that
   reads the key, strips it and delegates to the matching DTO. `calendar` — the spec's rule type 6 — is
   **rejected with a message saying it is unsupported** (FEATURES H4 adds it or its table-based
   replacement); an unknown type is rejected naming the type.
4. **Field spellings.** `weekday` is `MON`..`SUN` (three letters, upper case; nothing else). `easter.calendar`
   is `western` | `orthodox`; `easter.offset` defaults to 0. `weekdayRelative.direction` is `onOrAfter` |
   `onOrBefore`. `observed` is `us_federal` | `next_monday` | `sunday_to_monday` | `none` (default).
   `category` is `public` | `bank` | `observance` | `religious` | `ifc` and is required. `yearFilter` is
   `{ "mod": n, "eq": m }`. `since`, `until`, `durationDays` (default 1), `startsEveBefore` and `approximate`
   (default false) map one-to-one onto `HolidayDefinition`.
5. **`ifc` rules have two shapes:** `{ "type": "ifc", "month": 1..13, "day": 1..28 }` (month numbers are
   IFC month numbers, Sol = 7) or `{ "type": "ifc", "special": "YEAR_DAY" | "LEAP_DAY" }`. Both or neither
   is an error.
6. **`table` rules are inline:** `{ "type": "table", "dates": { "2024": "2024-11-01", … } }` with year keys
   and ISO-8601 dates that must lie in their key year (ADR 0003 decision 4). The spec example's
   `"table": "diwali"` reference to a separate `tables/*.json` file is **not implemented**; it is the natural
   extension when the H4 tables arrive and can be added as an alternative key without a schema bump.
7. **Unknown keys are errors** at every level (`ignoreUnknownKeys = false`), so a typo in a pack fails the
   tests that load every bundled pack rather than silently dropping a modifier.
8. **All-or-nothing loading with one exception type.** Every failure — malformed JSON, an unknown key or
   type, a domain constructor's `IllegalArgumentException` — is rethrown as `HolidayPackException(packName,
   holidayId, message, cause)`; `holidayId` is set for anything inside a holiday entry and `null` for
   pack-level errors (including duplicate ids, which the message names).
9. **Bundled packs live at classpath `/holidays/<name>.json` in `:core:holidays`** (not `assets/` as §2.4
   says: the module is pure JVM and must be testable without Android), enumerated by
   `BundledHolidayPacks.all`. Adding a pack means adding the file and the constant; a test loads every
   listed pack.
10. **Data decisions in the bundled packs** beyond what §2.5 states: Inauguration Day has `since: 1937`
    (the Twentieth Amendment moved the date to January 20 from 1937), Patriot Day `since: 2002` (Pub. L.
    107–89), Kwanzaa `since: 1966` (first celebrated 1966); Columbus Day's two display names are one
    `en` string, "Columbus Day / Indigenous Peoples' Day", because schema 1 has no alternate-name field;
    Tax Day is omitted (§2.5 defers it); Christmas Eve and New Year's Eve are separate entries; Sol 1 is
    named "Sol Day" (§2.2). The Easter family is its own pack, `religious-christian`, with ids `x.*` as in
    the §2.4 example.

## Consequences

- A pack is validated completely on load; the engine can assume every `HolidaySet` it receives is
  well-formed, and a bad bundled pack is caught by `BundledPacksTest`, never on a device.
- Schema 1 cannot express: lunisolar holidays (until H4's tables or the `calendar` type), alternate
  display names, `subdivisions`, or file-referenced tables. Each is an additive change; only a change to
  existing field semantics needs a schema bump.
- The holidays-and-import.md §2.4 example is now partly aspirational (the `calendar` and `"table":
  "diwali"` entries do not load); the owning doc should say so until H4 lands.
