#!/usr/bin/env python3
"""Fail when Dependency-Check suppressions are expired, undated, or near expiry."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from datetime import date, datetime, timezone
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SUPPRESSION_FILE = REPOSITORY_ROOT / "dependency-check-suppression.xml"
UNTIL_FORMAT = "%Y-%m-%dZ"


def non_negative_int(value: str) -> int:
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be zero or greater")
    return parsed


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child_values(element: ET.Element, name: str) -> list[str]:
    return [
        child.text.strip()
        for child in element
        if local_name(child.tag) == name and child.text and child.text.strip()
    ]


def rule_label(suppression: ET.Element, index: int) -> str:
    cves = child_values(suppression, "cve")
    if cves:
        return ", ".join(cves)

    package_urls = child_values(suppression, "packageUrl")
    if package_urls:
        return ", ".join(package_urls)

    return f"suppression #{index}"


def parse_until(raw_until: str) -> date:
    parsed = datetime.strptime(raw_until, UNTIL_FORMAT)
    if parsed.strftime(UNTIL_FORMAT) != raw_until:
        raise ValueError("until must use YYYY-MM-DDZ")
    return parsed.date()


def load_suppressions(path: Path) -> list[ET.Element]:
    try:
        root = ET.parse(path).getroot()
    except FileNotFoundError:
        raise ValueError(f"suppression file not found: {path}") from None
    except OSError as error:
        raise ValueError(f"cannot read suppression file {path}: {error}") from None
    except ET.ParseError as error:
        raise ValueError(f"invalid suppression XML in {path}: {error}") from None

    suppressions = [
        element for element in root.iter() if local_name(element.tag) == "suppress"
    ]
    if not suppressions:
        raise ValueError(f"no <suppress> elements found in {path}")
    return suppressions


def check_suppressions(path: Path, warn_days: int, today: date) -> int:
    try:
        suppressions = load_suppressions(path)
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    findings: list[str] = []
    expiries: list[date] = []

    for index, suppression in enumerate(suppressions, start=1):
        label = rule_label(suppression, index)
        raw_until = suppression.get("until")
        if raw_until is None:
            findings.append(
                f"UNDATED: {label} — until missing, days remaining: n/a"
            )
            continue

        try:
            expiry = parse_until(raw_until)
        except ValueError:
            findings.append(
                f"ERROR: {label} — invalid until {raw_until!r}; expected YYYY-MM-DDZ"
            )
            continue

        expiries.append(expiry)
        days_remaining = (expiry - today).days
        if days_remaining <= 0:
            findings.append(
                f"EXPIRED: {label} — until {raw_until}, "
                f"days remaining: {days_remaining}"
            )
        elif days_remaining <= warn_days:
            findings.append(
                f"EXPIRING: {label} — until {raw_until}, "
                f"days remaining: {days_remaining}"
            )

    if findings:
        for finding in findings:
            print(finding)
        return 1

    next_expiry = min(expiries)
    days_remaining = (next_expiry - today).days
    print(
        f"clean: {len(suppressions)} rules, next expiry "
        f"{next_expiry.isoformat()} in {days_remaining} days"
    )
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check Dependency-Check suppression review dates."
    )
    parser.add_argument(
        "--file",
        type=Path,
        default=DEFAULT_SUPPRESSION_FILE,
        help="suppression XML file (default: repository suppression file)",
    )
    parser.add_argument(
        "--warn-days",
        type=non_negative_int,
        default=0,
        help="also report rules expiring within this many days (default: 0)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    today = datetime.now(timezone.utc).date()
    return check_suppressions(args.file, args.warn_days, today)


if __name__ == "__main__":
    raise SystemExit(main())
