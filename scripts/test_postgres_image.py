"""Red probes for the BUILD-4 PostgreSQL image guard."""

from pathlib import Path
import tempfile
import unittest

from postgres_image import guard_tree, render_compose


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


if __name__ == "__main__":
    unittest.main()
