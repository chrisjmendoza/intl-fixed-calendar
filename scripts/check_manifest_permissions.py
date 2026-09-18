#!/usr/bin/env python3
"""Merged-manifest permission allow-list gate (ROADMAP M0 T7).

A dependency bump can silently merge a new `<uses-permission>` into the app (CLAUDE.md rule 7: no
`INTERNET`, and no new permission without a `docs/security-and-privacy.md` update). This script reads
the merged manifest AGP writes for `:app:assembleDebug` and fails if it contains a permission that is
not on the allow-list table in `docs/security-and-privacy.md` — the doc is the source of truth
(docs/WORKFLOW.md §4.2 "Executable specs beat prose"); nothing here is a second copy of the list.

Run from anywhere, after `:app:assembleDebug` has produced a merged manifest:

    python scripts/check_manifest_permissions.py

Self-test (no build required; three embedded sample manifests: allowed, disallowed INTERNET,
`uses-permission-sdk-23` variant):

    python scripts/check_manifest_permissions.py --self-test

Exit codes: 0 on success (including a clean self-test run), 1 if a permission outside the allow-list is
found (or a self-test assertion fails). An allow-listed permission that is no longer present in the
manifest, and an exported non-launcher component, are reported but never fail the build (see `check()`
and the "Exported components" note below for why the latter is report-only).
"""
from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC_PATH = ROOT / "docs" / "security-and-privacy.md"

# AGP writes the merged manifest to more than one intermediates directory across its build phases
# (found by building :app:assembleDebug once and diffing them, 2026-09-18): singular
# `merged_manifest/<variant>/processDebugMainManifest/`, plural `merged_manifests/<variant>/
# processDebugManifest/`, and the final `packaged_manifests/<variant>/processDebugManifestForPackage/`.
# Their permission/component content was identical in that build, so any of them works. The patterns
# pin the `debug` variant directory on purpose: `check` also writes a `debugUnitTest` merged manifest
# next to it, and that one carries androidx.test's REORDER_TASKS and test-only activities, which are
# never packaged into the app. First pattern with a match wins.
DEFAULT_MANIFEST_GLOBS = (
    "app/build/intermediates/merged_manifests/debug/*/AndroidManifest.xml",
    "app/build/intermediates/merged_manifest/debug/*/AndroidManifest.xml",
    "app/build/intermediates/packaged_manifests/debug/*/AndroidManifest.xml",
)

ANDROID_NS = "http://schemas.android.com/apk/res/android"
NAME_ATTR = f"{{{ANDROID_NS}}}name"
EXPORTED_ATTR = f"{{{ANDROID_NS}}}exported"

PERMISSION_TAGS = ("uses-permission", "uses-permission-sdk-23")
COMPONENT_TAGS = ("activity", "activity-alias", "receiver", "service", "provider")

ALLOWLIST_BEGIN = "<!-- permission-allowlist:begin -->"
ALLOWLIST_END = "<!-- permission-allowlist:end -->"


class DocAllowlistError(Exception):
    """Raised when `docs/security-and-privacy.md` does not have a well-formed allow-list block."""


def _extract_allowlist(doc_text: str, source: str) -> set[str]:
    """Parses permission names out of the marker-delimited table in ``doc_text``.

    The table lives between ``<!-- permission-allowlist:begin -->`` and
    ``<!-- permission-allowlist:end -->`` in `docs/security-and-privacy.md`. Each data row's first
    column is a permission name in an inline code span, e.g. `` | `android.permission.FOO` | ... | ``.
    ``source`` is only used to make error messages point at the right file.
    """
    try:
        start = doc_text.index(ALLOWLIST_BEGIN) + len(ALLOWLIST_BEGIN)
        end = doc_text.index(ALLOWLIST_END, start)
    except ValueError as exc:
        raise DocAllowlistError(
            f"{source}: no '{ALLOWLIST_BEGIN}' ... '{ALLOWLIST_END}' block found. "
            "scripts/check_manifest_permissions.py reads the allow-list from that block; add it "
            "under security-and-privacy.md section 5 if it was removed."
        ) from exc
    block = doc_text[start:end]

    names: set[str] = set()
    for line in block.splitlines():
        line = line.strip()
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if not cells or not cells[0]:
            continue
        first = cells[0]
        if set(first) <= {"-", ":"}:  # the `|---|---|` separator row
            continue
        if first.lower() == "permission":  # header row
            continue
        if first.startswith("`") and first.endswith("`") and len(first) >= 2:
            names.add(first[1:-1])
        else:
            raise DocAllowlistError(
                f"{source}: allow-list row {first!r} is not an inline-code permission name; "
                "every data row's first column must be `like.this`."
            )
    if not names:
        raise DocAllowlistError(f"{source}: the allow-list block is empty.")
    return names


def load_allowlist(doc_path: Path) -> set[str]:
    """Reads and parses the allow-list table from the doc file at ``doc_path``."""
    return _extract_allowlist(doc_path.read_text(encoding="utf-8"), str(doc_path))


def find_manifest(explicit: str | None) -> Path:
    """Resolves the merged manifest to check: an explicit path/glob, or the newest default match."""
    if explicit:
        direct = Path(explicit)
        if direct.is_file():
            return direct
        matches = sorted(Path().glob(explicit), key=lambda p: p.stat().st_mtime, reverse=True)
        if matches:
            return matches[0]
        raise FileNotFoundError(f"no file matches --manifest {explicit!r}")

    for pattern in DEFAULT_MANIFEST_GLOBS:
        matches = sorted(ROOT.glob(pattern), key=lambda p: p.stat().st_mtime, reverse=True)
        if matches:
            return matches[0]
    raise FileNotFoundError(
        "no merged manifest found under app/build/intermediates/. Run "
        "'.\\gradlew.bat :app:assembleDebug' first, or pass --manifest explicitly."
    )


def _is_launcher(activity: ET.Element) -> bool:
    for intent_filter in activity.findall("intent-filter"):
        actions = {a.get(NAME_ATTR) for a in intent_filter.findall("action")}
        categories = {c.get(NAME_ATTR) for c in intent_filter.findall("category")}
        if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
            return True
    return False


def parse_manifest(xml_text: str) -> tuple[set[str], list[str], list[tuple[str, str]]]:
    """Returns (uses-permission names, self-declared <permission> names, exported non-launcher components).

    Each exported component is `(tag, android:name)`. The launcher activity (an <activity> whose
    intent-filter has action MAIN and category LAUNCHER) is excluded — it is expected to be exported.
    """
    root = ET.fromstring(xml_text)

    used = {
        el.get(NAME_ATTR)
        for tag in PERMISSION_TAGS
        for el in root.findall(tag)
        if el.get(NAME_ATTR)
    }
    declared = [el.get(NAME_ATTR) for el in root.findall("permission") if el.get(NAME_ATTR)]

    application = root.find("application")
    exported: list[tuple[str, str]] = []
    if application is not None:
        for tag in COMPONENT_TAGS:
            for el in application.findall(tag):
                if el.get(EXPORTED_ATTR) != "true":
                    continue
                name = el.get(NAME_ATTR, "<unnamed>")
                if tag == "activity" and _is_launcher(el):
                    continue
                exported.append((tag, name))
    return used, declared, exported


def check(manifest_path: Path, doc_path: Path) -> int:
    allowlist = load_allowlist(doc_path)
    used, declared, exported = parse_manifest(manifest_path.read_text(encoding="utf-8"))

    disallowed = sorted(used - allowlist)
    unused_allowlisted = sorted(allowlist - used)

    print(f"Manifest:   {manifest_path}")
    print(f"Allow-list: {doc_path} ({len(allowlist)} permission(s))")
    print(f"Permissions found ({len(used)}): {', '.join(sorted(used)) or '(none)'}")
    if declared:
        print(f"Self-declared <permission> element(s): {', '.join(sorted(declared))}")

    if unused_allowlisted:
        print(
            "WARNING: allow-listed permission(s) not present in this manifest "
            f"(informational only): {', '.join(unused_allowlisted)}"
        )

    # Report-only: the security doc's rule ("exported=false except the launcher activity, widget
    # receivers, and the .ics import activity", security-and-privacy.md §9.2 item 2) is written for a
    # release manifest. A *debug* merged manifest — the only one this script is asked to check, per
    # CLAUDE.md's assembleDebug-only build command — legitimately carries extra exported debug-only
    # components (Compose UI tooling's PreviewActivity, the compose-ui-test-manifest host Activity) plus
    # androidx.profileinstaller's permission-guarded ProfileInstallReceiver, none of which the doc's rule
    # was written to describe. Turning this into a hard failure would require either a second allow-list
    # or hand parsing "is this component debug-only", so it stays a
    # printed report for human review until a mechanical rule for the debug manifest exists.
    print(f"\nExported non-launcher components ({len(exported)}) -- report only, see comment above:")
    if exported:
        for tag, name in exported:
            print(f"  <{tag}> {name}")
    else:
        print("  (none)")

    if disallowed:
        print(
            f"\nFAIL: {len(disallowed)} permission(s) not on the docs/security-and-privacy.md "
            f"allow-list: {', '.join(disallowed)}"
        )
        print(
            "Add the permission to the allow-list table (with a release and justification) in the "
            "same push that introduces it, per docs/WORKFLOW.md section 4.2 'Same-push rule' -- or, "
            "if it arrived through a dependency bump you did not intend, pin/replace that dependency."
        )
        return 1

    print("\nOK: every permission in the manifest is on the allow-list.")
    return 0


# --------------------------------------------------------------------------------------------------
# Self-test: no build or repo checkout required. Covers an allowed manifest, one with a disallowed
# INTERNET permission, and one using <uses-permission-sdk-23> plus an unexpected exported receiver.
# --------------------------------------------------------------------------------------------------

_SAMPLE_DOC = f"""# Security & Privacy Plan (sample)

## 5. Permissions

### Current release allow-list (checked by CI)

{ALLOWLIST_BEGIN}
| Permission | Release | Justification |
|---|---|---|
| `io.github.chrisjmendoza.yearal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | 0.1 | sample |
| `android.permission.POST_NOTIFICATIONS` | 1.0 | sample |
{ALLOWLIST_END}
"""

_SAMPLE_MANIFEST_ALLOWED = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="io.github.chrisjmendoza.yearal">
    <permission android:name="io.github.chrisjmendoza.yearal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        android:protectionLevel="signature" />
    <uses-permission android:name="io.github.chrisjmendoza.yearal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
    <application android:name=".IfcApplication">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <provider android:name="androidx.startup.InitializationProvider" android:exported="false" />
    </application>
</manifest>
"""

_SAMPLE_MANIFEST_DISALLOWED = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="io.github.chrisjmendoza.yearal">
    <uses-permission android:name="io.github.chrisjmendoza.yearal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
    <uses-permission android:name="android.permission.INTERNET" />
    <application android:name=".IfcApplication">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""

_SAMPLE_MANIFEST_SDK23 = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="io.github.chrisjmendoza.yearal">
    <uses-permission android:name="io.github.chrisjmendoza.yearal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />
    <uses-permission-sdk-23 android:name="android.permission.POST_NOTIFICATIONS" />
    <application android:name=".IfcApplication">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <receiver android:name=".SneakyReceiver" android:exported="true" />
    </application>
</manifest>
"""


def self_test() -> int:
    allowlist = _extract_allowlist(_SAMPLE_DOC, "<embedded sample doc>")
    expected = {
        "io.github.chrisjmendoza.yearal.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        "android.permission.POST_NOTIFICATIONS",
    }
    assert allowlist == expected, f"allow-list parse mismatch: {allowlist}"
    print("PASS: allow-list table parses to the expected two permissions")

    used, _declared, exported = parse_manifest(_SAMPLE_MANIFEST_ALLOWED)
    assert (used - allowlist) == set(), "allowed sample must have no disallowed permissions"
    assert exported == [], f"allowed sample must have no non-launcher exported components: {exported}"
    print("PASS: allowed sample manifest reports no disallowed permissions")

    used, _declared, _exported = parse_manifest(_SAMPLE_MANIFEST_DISALLOWED)
    disallowed = used - allowlist
    assert disallowed == {"android.permission.INTERNET"}, f"expected INTERNET only, got {disallowed}"
    print("PASS: disallowed sample manifest flags android.permission.INTERNET")

    used, _declared, exported = parse_manifest(_SAMPLE_MANIFEST_SDK23)
    assert "android.permission.POST_NOTIFICATIONS" in used, "uses-permission-sdk-23 was not extracted"
    assert (used - allowlist) == set(), "sdk-23 sample must have no disallowed permissions"
    assert exported == [("receiver", ".SneakyReceiver")], f"expected SneakyReceiver reported: {exported}"
    print("PASS: uses-permission-sdk-23 is extracted and an exported receiver is reported")

    print("\nSelf-test OK: 4/4 checks passed.")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--manifest",
        help="Path or glob to the merged manifest. Defaults to the newest match under "
        "app/build/intermediates/ (build :app:assembleDebug first).",
    )
    parser.add_argument(
        "--docs",
        default=str(DOC_PATH),
        help="Path to the doc holding the allow-list table (default: docs/security-and-privacy.md).",
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run the embedded self-test (no build or manifest required) instead of a real check.",
    )
    args = parser.parse_args(argv)

    if args.self_test:
        try:
            return self_test()
        except AssertionError as exc:
            print(f"SELF-TEST FAILED: {exc}")
            return 1

    try:
        manifest_path = find_manifest(args.manifest)
        return check(manifest_path, Path(args.docs))
    except (FileNotFoundError, DocAllowlistError, ET.ParseError) as exc:
        print(f"ERROR: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
