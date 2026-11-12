"""Red probes for the BUILD-4 PostgreSQL image guard."""

from pathlib import Path
import tempfile
import unittest

from postgres_image import (
    evaluate_freshness,
    evaluate_pinned_index,
    guard_tree,
    next_maintenance_tag,
    render_compose,
)


IMAGE = (
    "postgres:16.15-alpine@"
    "sha256:cf78e76683b9ca8c5733cbbdce6c9262b45b6767934dd0a95e671f9a0fc20685"
)


class PostgresImageGuardTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        (self.root / "src/test/java/com/fabricmanagement/testsupport").mkdir(
            parents=True
        )
        (self.root / "src/test/java/example").mkdir(parents=True)
        (self.root / "docs").mkdir()
        (self.root / "pom.xml").write_text(
            f"<project><properties><postgres.image>{IMAGE}</postgres.image>"
            "</properties></project>\n",
            encoding="utf-8",
        )
        (self.root / "docker-compose.yml").write_text(
            "services:\n"
            "  postgres:\n"
            "    # GENERATED from pom.xml <postgres.image> by "
            "scripts/postgres_image.py; do not edit.\n"
            f"    image: {IMAGE}\n",
            encoding="utf-8",
        )
        (self.root / "src/test/java/com/fabricmanagement/testsupport/PostgresImage.java").write_text(
            "final class PostgresImage { Object x() { "
            "return new PostgreSQLContainer<>(name()); } }\n",
            encoding="utf-8",
        )
        (self.root / "src/test/java/example/UsesPostgres.java").write_text(
            "final class UsesPostgres { Object x = PostgresImage.container(); }\n",
            encoding="utf-8",
        )

    def tearDown(self):
        self.directory.cleanup()

    def test_documented_history_is_ignored(self):
        (self.root / "docs/history.md").write_text(
            "Previously used postgres:15-alpine.\n", encoding="utf-8"
        )
        self.assertEqual([], guard_tree(self.root, IMAGE))

    def test_image_literal_in_test_source_fails(self):
        path = self.root / "src/test/java/example/UsesPostgres.java"
        path.write_text(
            'final class UsesPostgres { String image = "postgres:16.15-alpine"; }\n',
            encoding="utf-8",
        )
        self.assertTrue(
            any("image literal" in error for error in guard_tree(self.root, IMAGE))
        )

    def test_constructor_without_literal_still_fails(self):
        path = self.root / "src/test/java/example/UsesPostgres.java"
        path.write_text(
            "final class UsesPostgres { Object x = new PostgreSQLContainer<>(name()); }\n",
            encoding="utf-8",
        )
        self.assertTrue(
            any("bypasses" in error for error in guard_tree(self.root, IMAGE))
        )

    def test_fully_qualified_constructor_still_fails(self):
        path = self.root / "src/test/java/example/UsesPostgres.java"
        path.write_text(
            "final class UsesPostgres { Object x = new "
            "org.testcontainers.containers.PostgreSQLContainer<>(); }\n",
            encoding="utf-8",
        )
        self.assertTrue(
            any("bypasses" in error for error in guard_tree(self.root, IMAGE))
        )

    def test_compose_is_regenerated_from_the_authored_value(self):
        stale = (
            "services:\n"
            "  postgres:\n"
            "    image: postgres:15-alpine\n"
        )
        generated = render_compose(stale, IMAGE)
        self.assertIn(f"    image: {IMAGE}", generated)
        self.assertIn("GENERATED from pom.xml", generated)



PINNED = IMAGE.split("@", 1)[1]
OCI_INDEX = "application/vnd.oci.image.index.v1+json"


def _index(*platforms):
    return {
        "mediaType": OCI_INDEX,
        "manifests": [
            {"platform": {"os": os_name, "architecture": architecture}}
            for os_name, architecture in platforms
        ],
    }


class PinnedIndexTest(unittest.TestCase):
    def test_multi_architecture_index_passes(self):
        document = _index(("linux", "amd64"), ("linux", "arm64"), ("unknown", "unknown"))
        self.assertEqual([], evaluate_pinned_index(OCI_INDEX, document))

    def test_platform_specific_manifest_fails(self):
        document = {"mediaType": "application/vnd.oci.image.manifest.v1+json"}
        errors = evaluate_pinned_index("application/vnd.oci.image.manifest.v1+json", document)
        self.assertTrue(any("not a multi-architecture" in error for error in errors))

    def test_missing_architecture_fails(self):
        errors = evaluate_pinned_index(OCI_INDEX, _index(("linux", "amd64")))
        self.assertEqual(["Pinned index has no linux/arm64 manifest"], errors)

    def test_header_media_type_is_used_when_the_body_omits_it(self):
        document = _index(("linux", "amd64"), ("linux", "arm64"))
        del document["mediaType"]
        self.assertEqual([], evaluate_pinned_index(OCI_INDEX, document))


class FreshnessTest(unittest.TestCase):
    def test_next_maintenance_tag(self):
        self.assertEqual("16.16-alpine", next_maintenance_tag(IMAGE))

    def test_current_pin_reports_nothing(self):
        self.assertEqual([], evaluate_freshness(IMAGE, PINNED, None))

    def test_republished_tag_is_reported(self):
        notices = evaluate_freshness(IMAGE, "sha256:" + "a" * 64, None)
        self.assertEqual(1, len(notices))
        self.assertIn("republished 16.15-alpine", notices[0])

    def test_newer_maintenance_release_is_reported(self):
        notices = evaluate_freshness(IMAGE, PINNED, "sha256:" + "b" * 64)
        self.assertEqual(1, len(notices))
        self.assertIn("16.16-alpine is published", notices[0])

    def test_withdrawn_tag_is_reported(self):
        self.assertEqual(
            ["Docker Hub no longer publishes 16.15-alpine."],
            evaluate_freshness(IMAGE, None, None),
        )


if __name__ == "__main__":
    unittest.main()
