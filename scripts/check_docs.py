#!/usr/bin/env python3
"""Doc-drift gate: every relative markdown link in the repo must resolve to an existing file.

Run from anywhere: `python scripts/check_docs.py`. Exits 1 and lists the broken links on failure.
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


def main() -> int:
    failures = 0
    files = markdown_files()
    for path in sorted(files):
        for number, target in broken_links(path):
            print(f"{path.relative_to(ROOT).as_posix()}:{number}: broken link -> {target}")
            failures += 1
    if failures:
        print(f"\n{failures} broken link(s) in {len(files)} markdown files.")
        return 1
    print(f"OK: {len(files)} markdown files, all relative links resolve.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
