#!/usr/bin/env python3
"""Doc-drift gate: every relative markdown link in the repo must resolve to an existing file, and
README.md must not regress into stating a handful of specific claims that have been false since 2026-09.

Run from anywhere: `python scripts/check_docs.py`. Exits 1 and lists the problems on failure.
See docs/WORKFLOW.md §4.2.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKIP_DIRS = {".git", ".gradle", ".kotlin", ".idea", "build", "node_modules"}
LINK = re.compile(r"(?<!\!)\[[^\]\n]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
FENCE = re.compile(r"^\s*(```|~~~)")
EXTERNAL = ("http://", "https://", "mailto:", "#")

# Literal phrases that were true in the very first commit of README.md and have been false ever since an
# Android module existed and the app was named. If one of these reappears, someone reverted the status
# section instead of updating it in the same push as a behaviour change (docs/WORKFLOW.md §4.2 rule 4).
README_STALE_PHRASES = (
    "The Android app itself has not been started",
    "The working name is a placeholder",
    "Tech stack (planned)",
)


def markdown_files() -> list[Path]:
    return [
        p
        for p in ROOT.rglob("*.md")
        if not any(part in SKIP_DIRS for part in p.relative_to(ROOT).parts)
    ]


def broken_links(path: Path) -> list[tuple[int, str]]:
    problems = []
    in_fence = False
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if FENCE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        for target in LINK.findall(re.sub(r"`[^`]*`", "", line)):
            if target.startswith(EXTERNAL):
                continue
            file_part = target.split("#", 1)[0]
            if file_part and not (path.parent / file_part).exists():
                problems.append((number, target))
    return problems


def stale_readme_phrases(readme: Path) -> list[str]:
    if not readme.exists():
        return []
    text = readme.read_text(encoding="utf-8")
    return [phrase for phrase in README_STALE_PHRASES if phrase in text]


def main() -> int:
    failures = 0
    files = markdown_files()
    for path in sorted(files):
        for number, target in broken_links(path):
            print(f"{path.relative_to(ROOT).as_posix()}:{number}: broken link -> {target}")
            failures += 1

    for phrase in stale_readme_phrases(ROOT / "README.md"):
        print(f'README.md: contains a known-stale phrase -> "{phrase}"')
        failures += 1

    if failures:
        print(f"\n{failures} problem(s) in {len(files)} markdown files.")
        return 1
    print(f"OK: {len(files)} markdown files, all relative links resolve, README.md has no stale phrases.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
