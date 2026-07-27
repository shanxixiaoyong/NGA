#!/usr/bin/env python3

import re
import sys
from pathlib import Path


REQUIRED_HEADINGS = ("## 新增", "## 删除", "## 修复")
SECOND_LEVEL_HEADING = re.compile(r"^ {0,3}##(?:[ \t]+|$)")
LIST_ITEM = re.compile(r"^ {0,3}(?:[-+*]|\d+[.)])[ \t]+\S")
FENCE_START = re.compile(r"^ {0,3}(?P<fence>`{3,}|~{3,})")


def content_line_indexes(lines: list[str]) -> set[int]:
    indexes: set[int] = set()
    fence_character = ""
    fence_length = 0

    for line_number, line in enumerate(lines):
        if fence_character:
            closing_fence = re.compile(
                rf"^ {{0,3}}{re.escape(fence_character)}{{{fence_length},}}[ \t]*$"
            )
            if closing_fence.match(line):
                fence_character = ""
                fence_length = 0
            continue

        fence = FENCE_START.match(line)
        if fence:
            marker = fence.group("fence")
            fence_character = marker[0]
            fence_length = len(marker)
            continue

        indexes.add(line_number)

    return indexes


def validate_release_notes(path: Path) -> None:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as error:
        raise ValueError(f"cannot read release notes: {error}") from error

    content_indexes = content_line_indexes(lines)
    headings = [
        (line_number, line)
        for line_number, line in enumerate(lines)
        if line_number in content_indexes and SECOND_LEVEL_HEADING.match(line)
    ]
    actual_headings = tuple(line for _, line in headings)
    if actual_headings != REQUIRED_HEADINGS:
        expected = ", ".join(REQUIRED_HEADINGS)
        actual = ", ".join(actual_headings) if actual_headings else "none"
        raise ValueError(
            f"second-level headings must appear exactly once in this order: "
            f"{expected}; found: {actual}"
        )

    for index, (heading_line, heading) in enumerate(headings):
        section_end = headings[index + 1][0] if index + 1 < len(headings) else len(lines)
        if not any(
            line_number in content_indexes and LIST_ITEM.match(lines[line_number])
            for line_number in range(heading_line + 1, section_end)
        ):
            raise ValueError(f"{heading} must contain a non-empty Markdown list item")


def main(arguments: list[str]) -> int:
    if len(arguments) != 1:
        print("usage: validate_release_notes.py <notes-file>", file=sys.stderr)
        return 2

    notes_path = Path(arguments[0])
    try:
        validate_release_notes(notes_path)
    except ValueError as error:
        print(f"Invalid release notes {notes_path}: {error}", file=sys.stderr)
        return 1

    print(f"Validated release notes: {notes_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
