# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Planning baseline: calendar specification, feature catalog, architecture, roadmap, holiday and import
  strategy, security and privacy plan, competitive analysis.
- Workflow rules ([docs/WORKFLOW.md](docs/WORKFLOW.md)): definition of done, KDoc standard, anti-drift
  rules, rules for LLM agents, completion report, PR template, ADR template.
- Gradle build skeleton: wrapper 9.7.1, version catalog, `build-logic` with the `ifc.jvm.library`
  convention (explicit API, warnings as errors, ktlint via Spotless, Dokka KDoc gate, JUnit 6 + Kotest).
- `:core:calendar` — pure Kotlin/JVM IFC model: `IfcMonth`, `IfcDate` (`Regular`, `LeapDay`, `YearDay`),
  Gregorian ↔ IFC conversion, canonical numeric parse/format, and `IfcYearMonth` for month-grid layout.
- Spec-driven tests that read their vectors from `docs/calendar-spec.md` §6, plus an exhaustive
  definitional oracle over every day of years 1–9999.
- CI workflow running the full gate and the doc link check.
