# Workflow

How work gets done in this repo so that the app is built to spec, tested as it goes, documented as it
goes, and the docs stay true. These rules bind every contributor — human or LLM agent. Rules marked
**[gate]** are enforced by the build or CI; the rest are enforced by review against the checklists here.

## 1. The loop for every task

1. **Read** the docs listed for the area in [CLAUDE.md](../CLAUDE.md) before writing anything.
2. **State the contract**: which spec sections and which FEATURES IDs the task implements. If the spec
   is silent or ambiguous, stop and resolve it first (§5) — never invent behaviour.
3. **Write tests and code together.** Tests land in the same change as the code they cover.
4. **Document inline**: KDoc with the code, doc updates with the behaviour change (§4).
5. **Run the gate** (§2) locally and read the output.
6. **Self-review** against the Definition of Done (§3), then write the completion report (§6).

One task = one branch = one PR = one owning module. Small PRs. `main` is always green.

## 2. The gate

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat check          # everything below, for every module
python scripts\check_docs.py # doc link and reference check
```

`check` currently runs, per module:

| Step | What it enforces |
|---|---|
| `compileKotlin` | `explicitApi()` and **all warnings as errors** **[gate]** |
| `test` | JUnit 6 + Kotest, including the spec-driven and exhaustive tests **[gate]** |
| `spotlessCheck` | ktlint formatting (`.\gradlew.bat spotlessApply` fixes it) **[gate]** |
| `dokkaGenerate` | **KDoc on every public declaration** — an undocumented one fails the build **[gate]** |

CI (`.github/workflows/ci.yml`) runs the same gate on every push and PR. Android modules will add
`lint`, Robolectric tests, and `verifyRoborazziDebug` when they land (ARCHITECTURE.md §6).

## 3. Definition of Done

A task is done only when **all** of these are true:

- [ ] Behaviour matches the cited spec sections; every FEATURES ID touched is named in the PR.
- [ ] New or changed behaviour has tests **in the same PR**. Bug fixes start with a failing test.
- [ ] Every `when` over `IfcDate` and every date UI handles Year Day and Leap Day, and a test proves it.
- [ ] Anything that shows "today" has a test that crosses midnight with a fake `Clock`.
- [ ] Every public declaration has KDoc that meets §4.1.
- [ ] Docs changed in the same PR wherever behaviour, structure, versions, or scope changed (§4.2).
- [ ] `CHANGELOG.md` has an entry under *Unreleased* for anything user-visible or architectural.
- [ ] The full gate passes locally, and the completion report quotes the real result.
- [ ] No leftovers: no `TODO()` or stub in `main`, no commented-out code, no debug logging, no unused
      dependency.

## 4. Documentation rules

### 4.1 KDoc standard

KDoc is mandatory on every public class, function, and property **[gate]**. Good KDoc here:

- Says **what the declaration means and guarantees**, not how it is implemented.
- States units, ranges, nullability meaning, and what is thrown (`@throws`) — e.g. "1..28", "`null` for
  intercalary days".
- **Cites the spec** for anything rule-driven: ``Spec: `docs/calendar-spec.md` §3.1``.
- Calls out traps in bold where a caller could plausibly get it wrong (nominal vs actual weekday, the
  numeric form looking like an ISO date).
- Links related symbols with `[brackets]`. No filler ("Gets the year").
- Non-obvious `internal`/`private` logic gets a short comment explaining *why*.

### 4.2 Keeping docs true (anti-drift)

1. **One authority per topic.** The table at the top of [ARCHITECTURE.md](ARCHITECTURE.md) says which
   doc owns what. Other docs link to the owner instead of restating it. When two docs disagree, the
   owner wins and the other is fixed in the same PR.
2. **Executable specs beat prose.** Where a doc contains checkable facts, a test reads the doc:
   - `SpecVectorsTest` parses the vector tables in `calendar-spec.md` §6 and fails if the code
     disagrees or if the row count changes **[gate]**.
   - The same pattern applies to future data: holiday tables vs published dates, the permission
     allow-list vs the merged manifest, the module list vs `settings.gradle.kts`.
3. **Code is the authority for versions.** `gradle/libs.versions.toml` is what the build uses.
   ARCHITECTURE.md §1 explains *why*; after the M0 toolchain ADR it links to the catalog rather than
   repeating numbers.
4. **Same-PR rule.** A PR that changes behaviour, a module boundary, a permission, a dependency, a
   release scope, or a priority must update the owning doc in the same PR. "Docs later" is not a state.
5. **Decisions are recorded, not remembered.** Anything that changes or fills a gap in the architecture
   or spec gets an ADR in [docs/adr/](adr/) and a pointer from the owning doc.
6. **Roadmap is a ledger.** Finishing a ROADMAP task ticks it (or strikes it) in the PR that finishes
   it. Scope moved between releases is edited in both ROADMAP.md and FEATURES.md.
7. **Links must resolve.** `scripts/check_docs.py` fails on any relative markdown link that does not
   point at an existing file **[gate]**. Link to files rather than naming them in prose, so renames and
   deletions are caught.
8. **Docs carry a status line** (planning baseline / current as of milestone X). A doc known to be stale
   says so at the top until it is fixed.
9. **Frozen contracts** live in `docs/contracts/`. Changing a frozen public API requires an ADR and an
   update to the contract doc.

## 5. When the spec is silent, ambiguous, or wrong

Do not guess and do not quietly diverge. In order of preference:

1. If the answer is derivable from another authoritative doc, use it and cite it.
2. Otherwise write a short ADR proposing the rule, update the owning doc, and implement that.
3. If it is a product decision (naming, scope, UX default), ask the owner; list it under
   "Open decisions" in ROADMAP.md if it can wait.

If a test fails and you believe the *spec* is wrong, keep the test, leave the code alone, and report it.

## 6. Rules for LLM agents

These exist because the common LLM failure modes are: claiming success without running anything,
weakening tests until they pass, inventing APIs from older library versions, and leaving docs behind.

**Before**

- Read the required docs. Quote the spec section numbers you are implementing in your plan.
- Check the version catalog before using any library API; this project uses Room 3, Navigation 3, and
  AGP 9 (CLAUDE.md, "API generations"). If unsure an API exists, look at the dependency's sources
  rather than recalling from memory.
- Stay inside your module. If the task needs a change elsewhere, report it; do not reach across.

**During**

- Never make a test pass by weakening it: no deleting assertions, loosening expected values,
  `@Disabled`, `@Suppress`, `assumeTrue`, or catching and ignoring. If a test is wrong, say why with a
  spec citation and fix it; if the code is wrong, fix the code.
- Never edit a spec table, golden file, or expected value to match your output. Those are inputs.
- Never bypass a gate: no `--no-verify`, no removing `allWarningsAsErrors`, no lowering Dokka or lint
  strictness, no skipping commit signing, no `-x test`.
- No placeholder implementations, fake data, or "simplified for now" logic presented as done. If you
  cannot finish something, leave it out and say so.
- No new dependency, permission, or module without the doc update and justification in the same PR.
- The implementation of correctness-critical logic and its oracle tests are written by **different
  agents**, and the test author works from the spec, not from the implementation.

**After — the completion report**

End every task with this, truthfully:

```text
Task:        <roadmap task / FEATURES IDs>
Spec:        <doc §sections implemented>
Changed:     <files, grouped by module>
Tests:       <new/changed test classes; what each proves>
Gate:        <exact commands run and their real results, including test counts>
Docs:        <docs updated, or "none needed because …">
Not done:    <anything skipped, deferred, or uncertain — never empty by default>
Found:       <spec ambiguities, bugs in other modules, drift noticed>
```

- "Gate" must be the output of commands actually run in this session. If a command was not run, write
  "not run" — never infer a pass.
- A reviewer (human or another agent) spot-checks the report against the diff. A report that claims
  something the diff does not show is treated as a failed task, whatever the code quality.

## 7. Review checklist (for the reviewer, human or agent)

1. Does the diff do what the cited spec sections say — including the floating days?
2. Would the tests fail if the feature were broken? (Mentally delete the core line.)
3. Is any test weaker than before? Any expected value changed without a spec citation?
4. Is every public symbol documented to §4.1, and is the KDoc *true*?
5. Did behaviour change without its owning doc changing?
6. Rules in CLAUDE.md: no `now()` outside `Clock`, no bare `dayOfWeek`, no dates computed outside
   `:core:calendar`, no event content in logs, strings in resources, module boundaries respected.
7. Is the completion report consistent with the diff and the CI result?
