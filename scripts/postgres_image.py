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


HUB_TAG_URL = "https://hub.docker.com/v2/repositories/library/postgres/tags/{tag}"
REGISTRY_TOKEN_URL = (
    "https://auth.docker.io/token"
    "?service=registry.docker.io&scope=repository:library/postgres:pull"
)
REGISTRY_MANIFEST_URL = "https://registry-1.docker.io/v2/library/postgres/manifests/{digest}"
INDEX_MEDIA_TYPES = {
    "application/vnd.docker.distribution.manifest.list.v2+json",
    "application/vnd.oci.image.index.v1+json",
}
SINGLE_MANIFEST_MEDIA_TYPES = {
    "application/vnd.docker.distribution.manifest.v2+json",
    "application/vnd.oci.image.manifest.v1+json",
}
REQUIRED_PLATFORMS = (("linux", "amd64"), ("linux", "arm64"))
USER_AGENT = {"User-Agent": "fabric-management-build-4-guard"}


def _fetch_json(url: str, headers: dict[str, str] | None = None) -> tuple[dict, str]:
    request = urllib.request.Request(url, headers={**USER_AGENT, **(headers or {})})
    with urllib.request.urlopen(request, timeout=30) as response:
        media_type = response.headers.get("Content-Type", "").split(";", 1)[0].strip()
        return json.load(response), media_type


def split_image(image: str) -> tuple[str, int, int, str]:
    """Return the tag, major version, minor version and digest of the governed image."""
    match = IMAGE_PATTERN.fullmatch(image)
    if match is None:
        raise ValueError("Cannot verify an invalid PostgreSQL image value")
    major, minor = match.group("version").split(".")
    tag = image.removeprefix("postgres:").split("@", 1)[0]
    return tag, int(major), int(minor), match.group("digest")


def evaluate_pinned_index(media_type: str, document: dict) -> list[str]:
    """Check the pinned digest itself.

    A digest names fixed content, so this result cannot change when Docker Hub republishes the
    tag. Tag drift is a separate, scheduled question (see evaluate_freshness).
    """
    errors: list[str] = []
    declared = document.get("mediaType") or media_type
    if declared not in INDEX_MEDIA_TYPES:
        errors.append(f"Pinned digest is not a multi-architecture manifest index: {declared!r}")
    platforms = set()
    for entry in document.get("manifests", []):
        platform = entry.get("platform") or {}
        platforms.add((platform.get("os"), platform.get("architecture")))
    for os_name, architecture in REQUIRED_PLATFORMS:
        if (os_name, architecture) not in platforms:
            errors.append(f"Pinned index has no {os_name}/{architecture} manifest")
    return errors


def verify_pinned_index(image: str) -> list[str]:
    _, _, _, digest = split_image(image)
    accept = ", ".join(sorted(INDEX_MEDIA_TYPES | SINGLE_MANIFEST_MEDIA_TYPES))
    try:
        token, _ = _fetch_json(REGISTRY_TOKEN_URL)
        document, media_type = _fetch_json(
            REGISTRY_MANIFEST_URL.format(digest=digest),
            {"Authorization": f"Bearer {token['token']}", "Accept": accept},
        )
    except (OSError, KeyError, json.JSONDecodeError) as error:
        return [f"Cannot read the pinned PostgreSQL index {digest} from the registry: {error}"]
    return evaluate_pinned_index(media_type, document)


def next_maintenance_tag(image: str) -> str:
    _, major, minor, _ = split_image(image)
    return f"{major}.{minor + 1}-alpine"


def evaluate_freshness(
    image: str, current_digest: str | None, next_tag_digest: str | None
) -> list[str]:
    """Compare the pin with what Docker Hub publishes now.

    current_digest is the pinned tag's index digest today (None when the tag is gone);
    next_tag_digest is the next maintenance tag's digest (None while it is unpublished).
    """
    tag, _, _, digest = split_image(image)
    notices: list[str] = []
    if current_digest is None:
        notices.append(f"Docker Hub no longer publishes {tag}.")
    elif current_digest != digest:
        notices.append(
            f"Docker Hub has republished {tag}: its index digest is now {current_digest}, "
            f"the pin is {digest}. Pin the new digest in pom.xml <postgres.image>, then run "
            "--write and --check."
        )
    if next_tag_digest is not None:
        notices.append(
            f"PostgreSQL {next_maintenance_tag(image)} is published. Pin it with its index "
            "digest (BUILD-4 rule 2), then run --write and --check."
        )
    return notices


def _tag_digest(tag: str) -> str | None:
    """The current index digest of a Docker Hub tag, or None when the tag is not published."""
    try:
        metadata, _ = _fetch_json(HUB_TAG_URL.format(tag=tag))
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise
    return metadata.get("digest")


def verify_freshness(image: str) -> list[str]:
    tag, _, _, _ = split_image(image)
    try:
        current_digest = _tag_digest(tag)
        next_tag_digest = _tag_digest(next_maintenance_tag(image))
    except (OSError, json.JSONDecodeError) as error:
        return [f"Cannot read Docker Hub metadata for {tag}: {error}"]
    return evaluate_freshness(image, current_digest, next_tag_digest)


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
    errors.extend(verify_pinned_index(image))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true", help="Generate the compose image field")
    mode.add_argument(
        "--check",
        action="store_true",
        help="Check generation, guard and the pinned multi-architecture index",
    )
    mode.add_argument(
        "--check-freshness",
        action="store_true",
        help="Report a republished tag or a newer maintenance release (scheduled, not a PR gate)",
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
        if args.check_freshness:
            notices = verify_freshness(image)
            if notices:
                print("\n".join(notices), file=sys.stderr)
                return 1
            print(f"PostgreSQL image pin is the newest published build: {image}")
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
