#!/usr/bin/env python3
"""Generate and guard the repository-governed PostgreSQL container image."""

from __future__ import annotations

import argparse
import difflib
import json
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
POM = Path("pom.xml")
COMPOSE = Path("docker-compose.yml")
ACCESSOR = Path(
    "src/test/java/com/fabricmanagement/testsupport/PostgresImage.java"
)
GENERATED_COMMENT = (
    "    # GENERATED from pom.xml <postgres.image> by scripts/postgres_image.py; "
    "do not edit.\n"
)
IMAGE_PATTERN = re.compile(
    r"^postgres:(?P<version>16\.[0-9]+)-alpine@"
    r"(?P<digest>sha256:[0-9a-f]{64})$"
)
IMAGE_LITERAL_PATTERN = re.compile(
    r"postgres:[A-Za-z0-9][A-Za-z0-9_.-]*"
    r"(?:@sha256:[0-9a-f]{64})?"
)
CONSTRUCTOR_PATTERN = re.compile(
    r"new\s+(?:org\.testcontainers\.containers\.)?"
    r"PostgreSQLContainer\s*(?:<[^>]*>)?\s*\("
)
CONFIG_SUFFIXES = {
    ".env",
    ".json",
    ".properties",
    ".sh",
    ".toml",
    ".xml",
    ".yaml",
    ".yml",
}
CONFIG_NAMES = {"Makefile"}
SKIPPED_DIRECTORIES = {
    ".git",
    ".idea",
    ".mvn",
    "docs",
    "node_modules",
    "target",
}


def _local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def read_image(pom: Path) -> str:
    try:
        root = ET.parse(pom).getroot()
    except (OSError, ET.ParseError) as error:
        raise ValueError(f"Cannot read {pom}: {error}") from error
    property_blocks = [
        element for element in root if _local_name(element.tag) == "properties"
    ]
    if len(property_blocks) != 1:
        raise ValueError(f"Expected exactly one top-level <properties> in {pom}")
    values = [
        (element.text or "").strip()
        for element in property_blocks[0]
        if _local_name(element.tag) == "postgres.image"
    ]
    if len(values) != 1:
        raise ValueError(f"Expected exactly one <postgres.image> in {pom}")
    image = values[0]
    if not IMAGE_PATTERN.fullmatch(image):
        raise ValueError(
            "<postgres.image> must pin postgres:16.x-alpine to a sha256 index digest"
        )
    return image


def render_compose(contents: str, image: str) -> str:
    lines = contents.splitlines(keepends=True)
    service_starts = [
        index for index, line in enumerate(lines) if line.rstrip("\r\n") == "  postgres:"
    ]
    if len(service_starts) != 1:
        raise ValueError("Expected exactly one top-level postgres compose service")
    start = service_starts[0]
    end = len(lines)
    for index in range(start + 1, len(lines)):
        if re.fullmatch(r"  [A-Za-z0-9_-]+:\s*", lines[index].rstrip("\r\n")):
            end = index
            break
    image_lines = [
        index
        for index in range(start + 1, end)
        if re.match(r"^    image:\s*", lines[index])
    ]
    if len(image_lines) != 1:
        raise ValueError("Expected exactly one image field in the postgres compose service")
    image_index = image_lines[0]
    lines[image_index] = f"    image: {image}\n"
    if image_index == 0 or lines[image_index - 1] != GENERATED_COMMENT:
        if image_index > 0 and "GENERATED from pom.xml <postgres.image>" in lines[image_index - 1]:
            lines[image_index - 1] = GENERATED_COMMENT
        else:
            lines.insert(image_index, GENERATED_COMMENT)
    result = "".join(lines)
    return result.replace(
        "# DATABASE - PostgreSQL 15", "# DATABASE - PostgreSQL 16"
    )


def _is_executable_config(root: Path, path: Path) -> bool:
    relative = path.relative_to(root)
    if any(part in SKIPPED_DIRECTORIES for part in relative.parts[:-1]):
        return False
    if relative.parts[:2] == ("src", "test") and path.suffix == ".java":
        return False
    return path.name in CONFIG_NAMES or path.suffix in CONFIG_SUFFIXES or path.name.startswith(
        "Dockerfile"
    )


def guard_tree(root: Path, image: str) -> list[str]:
    errors: list[str] = []
    test_root = root / "src/test"
    accessor = root / ACCESSOR
    java_files = sorted(test_root.rglob("*.java"))
    if not java_files:
        errors.append("No Java test sources found")
    for path in java_files:
        contents = path.read_text(encoding="utf-8")
        relative = path.relative_to(root)
        for match in IMAGE_LITERAL_PATTERN.finditer(contents):
            errors.append(f"{relative}: PostgreSQL image literal {match.group(0)!r}")
        constructors = list(CONSTRUCTOR_PATTERN.finditer(contents))
        if path == accessor:
            if len(constructors) != 1:
                errors.append(
                    f"{relative}: accessor must contain exactly one PostgreSQLContainer construction"
                )
        elif constructors:
            errors.append(
                f"{relative}: PostgreSQLContainer construction bypasses PostgresImage.container()"
            )
    if not accessor.is_file():
        errors.append(f"Missing PostgreSQL image accessor: {ACCESSOR}")

    for path in sorted(candidate for candidate in root.rglob("*") if candidate.is_file()):
        if not _is_executable_config(root, path):
            continue
        relative = path.relative_to(root)
        contents = path.read_text(encoding="utf-8")
        searchable = contents
        if relative == POM:
            authored = f"<postgres.image>{image}</postgres.image>"
            if contents.count(authored) != 1:
                errors.append(f"{relative}: authored PostgreSQL image field is missing or duplicated")
            searchable = searchable.replace(authored, "", 1)
        elif relative == COMPOSE:
            generated = f"    image: {image}"
            if contents.count(generated) != 1:
                errors.append(f"{relative}: generated PostgreSQL image field is missing or duplicated")
            searchable = searchable.replace(generated, "", 1)
        for match in IMAGE_LITERAL_PATTERN.finditer(searchable):
            line = searchable.count("\n", 0, match.start()) + 1
            errors.append(
                f"{relative}:{line}: unexpected PostgreSQL image literal {match.group(0)!r}"
            )
    return errors


def verify_registry_index(image: str) -> list[str]:
    match = IMAGE_PATTERN.fullmatch(image)
    if match is None:
        return ["Cannot verify an invalid PostgreSQL image value"]
    tag = image.removeprefix("postgres:").split("@", 1)[0]
    url = f"https://hub.docker.com/v2/repositories/library/postgres/tags/{tag}"
    request = urllib.request.Request(
        url, headers={"User-Agent": "fabric-management-build-4-guard"}
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            metadata = json.load(response)
    except (OSError, urllib.error.URLError, json.JSONDecodeError) as error:
        return [f"Cannot verify Docker Hub metadata for {tag}: {error}"]

    errors: list[str] = []
    if metadata.get("digest") != match.group("digest"):
        errors.append(
            "Docker Hub index digest differs from <postgres.image>: "
            f"{metadata.get('digest')!r}"
        )
    if metadata.get("media_type") not in {
        "application/vnd.docker.distribution.manifest.list.v2+json",
        "application/vnd.oci.image.index.v1+json",
    }:
        errors.append(
            "Pinned digest is not a multi-architecture manifest index: "
            f"{metadata.get('media_type')!r}"
        )
    platforms = {
        (entry.get("os"), entry.get("architecture"), entry.get("variant"))
        for entry in metadata.get("images", [])
    }
    if not any(
        os_name == "linux" and architecture == "amd64"
        for os_name, architecture, _ in platforms
    ):
        errors.append("Docker Hub index has no linux/amd64 manifest")
    if not any(
        os_name == "linux" and architecture == "arm64"
        for os_name, architecture, _ in platforms
    ):
        errors.append("Docker Hub index has no linux/arm64 manifest")
    return errors


def check(root: Path, image: str) -> list[str]:
    errors: list[str] = []
    compose = root / COMPOSE
    actual = compose.read_text(encoding="utf-8")
    expected = render_compose(actual, image)
    if actual != expected:
        diff = "".join(
            difflib.unified_diff(
                actual.splitlines(keepends=True),
                expected.splitlines(keepends=True),
                fromfile=str(COMPOSE),
                tofile=f"{COMPOSE} (regenerated)",
            )
        )
        errors.append("Generated compose image is stale:\n" + diff)
    errors.extend(guard_tree(root, image))
    errors.extend(verify_registry_index(image))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true", help="Generate the compose image field")
    mode.add_argument(
        "--check", action="store_true", help="Check generation, guard and index metadata"
    )
    mode.add_argument("--print-image", action="store_true", help="Print the governed image")
    args = parser.parse_args()
    try:
        image = read_image(ROOT / POM)
        if args.print_image:
            print(image)
            return 0
        if args.write:
            compose = ROOT / COMPOSE
            generated = render_compose(compose.read_text(encoding="utf-8"), image)
            compose.write_text(generated, encoding="utf-8")
            print(f"Generated {COMPOSE} from {POM}")
            return 0
        errors = check(ROOT, image)
        if errors:
            print("\n".join(errors), file=sys.stderr)
            return 1
        print(f"PostgreSQL image governance is current: {image}")
        return 0
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
