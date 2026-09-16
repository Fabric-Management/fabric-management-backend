"""Unit tests for BUILD-4 report comparison policy."""

from pathlib import Path
import unittest

from build_4_test_reports import (
    ReportSet,
    TestRecord,
    comparison_markdown,
    snapshot_markdown,
)


def report(*records: TestRecord, build_status: str = "BUILD SUCCESS") -> ReportSet:
    return ReportSet(
        root=Path("reports"),
        xml_files=1,
        records=tuple(records),
        build_status=build_status,
        environment="test environment",
    )


class Build4TestReportTest(unittest.TestCase):
    def test_explained_missing_identity_is_not_a_regression(self):
        missing = TestRecord("surefire", "ExampleTest", "(unnamed)", "ERROR", "startup")
        added = TestRecord("surefire", "ExampleTest", "runs", "PASSED", "")

        markdown, review_required = comparison_markdown(
            report(missing),
            report(added),
            {missing.identity: "The baseline failed before JUnit enumerated its test methods."},
        )

        self.assertFalse(review_required)
        self.assertIn("NO UNEXPLAINED REGRESSION", markdown)
        self.assertIn("The baseline failed before JUnit enumerated", markdown)

    def test_unexplained_missing_identity_requires_review(self):
        missing = TestRecord("surefire", "ExampleTest", "runs", "PASSED", "")

        _, review_required = comparison_markdown(report(missing), report())

        self.assertTrue(review_required)

    def test_stale_explanation_requires_review(self):
        existing = TestRecord("surefire", "ExampleTest", "runs", "PASSED", "")

        _, review_required = comparison_markdown(
            report(existing),
            report(existing),
            {existing.identity: "This explanation no longer corresponds to a missing identity."},
        )

        self.assertTrue(review_required)

    def test_new_skip_requires_review_even_when_total_skip_count_does_not_increase(self):
        old_skip = TestRecord("surefire", "OldTest", "old", "SKIPPED", "old reason")
        new_skip = TestRecord("surefire", "NewTest", "new", "SKIPPED", "new reason")

        markdown, review_required = comparison_markdown(
            report(old_skip),
            report(new_skip),
            {old_skip.identity: "The old test was intentionally replaced."},
        )

        self.assertTrue(review_required)
        self.assertIn("Newly skipped identities", markdown)
        self.assertIn("NewTest", markdown)

    def test_missing_build_log_is_not_reported_as_consistent(self):
        current = report(
            TestRecord("surefire", "ExampleTest", "runs", "PASSED", ""),
            build_status="NOT RECORDED",
        )

        markdown = snapshot_markdown(current, "Example")
        _, review_required = comparison_markdown(current, current)

        self.assertIn("NOT ASSESSABLE: build.log is missing", markdown)
        self.assertTrue(review_required)


if __name__ == "__main__":
    unittest.main()
