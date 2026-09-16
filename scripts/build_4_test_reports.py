#!/usr/bin/env python3
"""Extract and compare BUILD-4 Surefire/Failsafe XML reports by test identity."""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


REPORT_DIRECTORIES = ("surefire-reports", "failsafe-reports")


@dataclass(frozen=True, order=True)
class TestRecord:
    phase: str
    class_name: str
    test_name: str
    status: str
    reason: str

    @property
    def identity(self) -> tuple[str, str, str]:
        return (self.phase, self.class_name, self.test_name)

    def tsv(self) -> str:
        return "\t".join(
            clean(value)
            for value in (
                self.phase,
                self.status,
                self.class_name,
                self.test_name,
                self.reason,
            )
        )


@dataclass(frozen=True)
class ReportSet:
    root: Path
    xml_files: int
    records: tuple[TestRecord, ...]
    build_status: str
    environment: str


def clean(value: str | None) -> str:
    return re.sub(r"\s+", " ", value or "").strip()


def report_directory(root: Path, name: str) -> Path:
    direct = root / name
    nested = root / "target" / name
    matches = [candidate for candidate in (direct, nested) if candidate.is_dir()]
    if len(matches) != 1:
        raise ValueError(
            f"Expected exactly one non-ambiguous {name} under {root} or {root / 'target'}"
        )
    return matches[0]


def failure_reason(element: ET.Element) -> str:
    message = clean(element.get("message"))
    detail = clean(element.text)
    error_type = clean(element.get("type"))
    return " | ".join(value for value in (error_type, message, detail) if value)


def load_reports(root: Path) -> ReportSet:
    records: list[TestRecord] = []
    xml_count = 0
    for directory_name in REPORT_DIRECTORIES:
        directory = report_directory(root, directory_name)
        phase = directory_name.removesuffix("-reports")
        xml_files = sorted(directory.glob("TEST-*.xml"))
        if not xml_files:
            raise ValueError(f"No TEST-*.xml files found in {directory}")
        xml_count += len(xml_files)
        for xml_file in xml_files:
            try:
                suite = ET.parse(xml_file).getroot()
            except ET.ParseError as error:
                raise ValueError(f"Malformed XML report {xml_file}: {error}") from error
            suite_name = suite.get("name") or xml_file.stem.removeprefix("TEST-")
            for testcase in suite.findall(".//testcase"):
                class_name = testcase.get("classname") or suite_name
                test_name = testcase.get("name") or "(unnamed)"
                failure = testcase.find("failure")
                error = testcase.find("error")
                skipped = testcase.find("skipped")
                if failure is not None:
                    status = "FAILED"
                    reason = failure_reason(failure)
                elif error is not None:
                    status = "ERROR"
                    reason = failure_reason(error)
                elif skipped is not None:
                    status = "SKIPPED"
                    reason = failure_reason(skipped) or "(no reason recorded)"
                else:
                    status = "PASSED"
                    reason = ""
                records.append(
                    TestRecord(phase, class_name, test_name, status, reason)
                )
    if not records:
        raise ValueError(f"No test cases found below {root}")

    build_log = root / "build.log"
    build_status = "NOT RECORDED"
    if build_log.is_file():
        log = build_log.read_text(encoding="utf-8", errors="replace")
        if "[INFO] BUILD SUCCESS" in log:
            build_status = "BUILD SUCCESS"
        elif "[INFO] BUILD FAILURE" in log:
            build_status = "BUILD FAILURE"
        else:
            build_status = "UNKNOWN"
    environment_file = root / "environment.txt"
    environment = (
        environment_file.read_text(encoding="utf-8", errors="replace").strip()
        if environment_file.is_file()
        else "Not recorded"
    )
    return ReportSet(
        root=root,
        xml_files=xml_count,
        records=tuple(sorted(records)),
        build_status=build_status,
        environment=environment,
    )


def summary(report: ReportSet) -> dict[str, int]:
    statuses = Counter(record.status for record in report.records)
    return {
        "tests": len(report.records),
        "passed": statuses["PASSED"],
        "failures": statuses["FAILED"],
        "errors": statuses["ERROR"],
        "skipped": statuses["SKIPPED"],
    }


def identity_payload(report: ReportSet) -> str:
    return "\n".join(record.tsv() for record in report.records) + "\n"


def snapshot_markdown(report: ReportSet, title: str) -> str:
    counts = summary(report)
    payload = identity_payload(report)
    digest = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    if report.build_status == "NOT RECORDED":
        report_consistency = "NOT ASSESSABLE: build.log is missing"
    elif report.build_status != "BUILD SUCCESS":
        report_consistency = f"REVIEW REQUIRED: {report.build_status}"
    elif counts["failures"] or counts["errors"]:
        report_consistency = (
            "REVIEW REQUIRED: build.log says BUILD SUCCESS while XML records "
            "a failure or error"
        )
    else:
        report_consistency = "CONSISTENT"
    return f"""# {title}

This file is generated by `scripts/build_4_test_reports.py`. Do not type test totals or identities
by hand. The source report directory is intentionally kept outside Maven's `target/` tree.

## Result

| Field | Value |
| --- | ---: |
| Build status | {report.build_status} |
| Build/XML consistency | {report_consistency} |
| XML suites | {report.xml_files} |
| Test cases | {counts["tests"]} |
| Passed | {counts["passed"]} |
| Failures | {counts["failures"]} |
| Errors | {counts["errors"]} |
| Skipped | {counts["skipped"]} |
| Identity/status SHA-256 | `{digest}` |

## Environment

```text
{report.environment}
```

## Comparable test identities

Columns are phase, status, class, test name and skip/failure reason. Duplicate identities remain
duplicate rows, so a silently lost invocation changes both the count and digest.

```text
phase\tstatus\tclass\ttest\treason
{payload}```
"""


def grouped(records: tuple[TestRecord, ...]):
    result: dict[tuple[str, str, str], list[tuple[str, str]]] = defaultdict(list)
    for record in records:
        result[record.identity].append((record.status, record.reason))
    return {identity: sorted(values) for identity, values in result.items()}


def format_identity(identity: tuple[str, str, str]) -> str:
    return " / ".join(clean(part) for part in identity)


def load_missing_explanations(path: Path | None) -> dict[tuple[str, str, str], str]:
    if path is None:
        return {}
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"Cannot read comparison explanations from {path}: {error}") from error
    entries = document.get("missing")
    if not isinstance(entries, list):
        raise ValueError(f"Expected a 'missing' array in {path}")
    explanations: dict[tuple[str, str, str], str] = {}
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ValueError(f"Expected missing[{index}] in {path} to be an object")
        values = tuple(entry.get(field) for field in ("phase", "class", "test"))
        reason = entry.get("reason")
        if not all(isinstance(value, str) and value for value in values):
            raise ValueError(f"Expected missing[{index}] in {path} to identify phase/class/test")
        if not isinstance(reason, str) or not reason.strip():
            raise ValueError(f"Expected missing[{index}] in {path} to have a reason")
        identity = (values[0], values[1], values[2])
        if identity in explanations:
            raise ValueError(f"Duplicate missing identity in {path}: {format_identity(identity)}")
        explanations[identity] = clean(reason)
    return explanations


def comparison_markdown(
    before: ReportSet,
    after: ReportSet,
    missing_explanations: dict[tuple[str, str, str], str] | None = None,
) -> tuple[str, bool]:
    missing_explanations = missing_explanations or {}
    before_grouped = grouped(before.records)
    after_grouped = grouped(after.records)
    missing = sorted(set(before_grouped) - set(after_grouped))
    explained_missing = [identity for identity in missing if identity in missing_explanations]
    unexplained_missing = [identity for identity in missing if identity not in missing_explanations]
    unused_explanations = sorted(set(missing_explanations) - set(missing))
    added = sorted(set(after_grouped) - set(before_grouped))
    newly_skipped = sorted(
        identity
        for identity in added
        if any(status == "SKIPPED" for status, _ in after_grouped[identity])
    )
    changed = sorted(
        identity
        for identity in set(before_grouped) & set(after_grouped)
        if before_grouped[identity] != after_grouped[identity]
    )
    after_counts = summary(after)
    review_required = bool(
        after.build_status != "BUILD SUCCESS"
        or unexplained_missing
        or unused_explanations
        or newly_skipped
        or changed
        or after_counts["failures"]
        or after_counts["errors"]
    )

    def lines(identities):
        return (
            "\n".join(f"- `{format_identity(identity)}`" for identity in identities)
            or "- None"
        )

    explained_lines = (
        "\n".join(
            f"- `{format_identity(identity)}` — {missing_explanations[identity]}"
            for identity in explained_missing
        )
        or "- None"
    )

    changed_lines = (
        "\n".join(
            f"- `{format_identity(identity)}`: "
            f"{before_grouped[identity]!r} -> {after_grouped[identity]!r}"
            for identity in changed
        )
        or "- None"
    )
    result = "REVIEW REQUIRED" if review_required else "NO UNEXPLAINED REGRESSION"
    section = f"""## Baseline comparison

**Computed result:** {result}

**Post-change build gate:** {after.build_status}

### Missing identities

#### Explained

{explained_lines}

#### Unexplained

{lines(unexplained_missing)}

### Stale or unused explanations

{lines(unused_explanations)}

### Added identities

{lines(added)}

### Newly skipped identities

{lines(newly_skipped)}

### Changed status or reason

{changed_lines}

"""
    return section, review_required


def write(path: Path, contents: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    snapshot = subparsers.add_parser("snapshot")
    snapshot.add_argument("reports", type=Path)
    snapshot.add_argument("output", type=Path)
    snapshot.add_argument("--title", default="BUILD-4 test report snapshot")
    compare = subparsers.add_parser("compare")
    compare.add_argument("baseline", type=Path)
    compare.add_argument("after", type=Path)
    compare.add_argument("output", type=Path)
    compare.add_argument("--title", default="BUILD-4 PostgreSQL 16 verification")
    compare.add_argument("--explanations", type=Path)
    args = parser.parse_args()
    try:
        if args.command == "snapshot":
            report = load_reports(args.reports)
            write(args.output, snapshot_markdown(report, args.title))
            print(f"Wrote {args.output} from {len(report.records)} test cases")
            return 0
        before = load_reports(args.baseline)
        after = load_reports(args.after)
        comparison, review_required = comparison_markdown(
            before, after, load_missing_explanations(args.explanations)
        )
        write(
            args.output,
            snapshot_markdown(after, args.title).replace(
                "## Environment\n", comparison + "## Environment\n", 1
            ),
        )
        print(f"Wrote {args.output} from {len(after.records)} test cases")
        return 1 if review_required else 0
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
