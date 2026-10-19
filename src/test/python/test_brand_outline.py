"""Font resolution regression tests; no real fonts or Inkscape invocation required."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("outline", Path(__file__).resolve().parents[2] / "main/resources/branding/outline.py")
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)


class BrandOutlineTests(unittest.TestCase):
    def setUp(self):
        self.style = {"font-family": "'Brand Font'", "font-weight": "700"}

    @patch.object(worker.subprocess, "run")
    def test_exact_face_and_all_glyphs(self, run):
        run.return_value = SimpleNamespace(stdout="Brand Font\n200\n0\n20-7e\n")
        worker.require_font(self.style, "AKKAYALAR", "fc-match")
        self.assertIn("Brand Font:weight=200:slant=0", run.call_args.args[0])

    @patch.object(worker.subprocess, "run")
    def test_fallback_font_and_synthetic_weight_are_rejected(self, run):
        for answer in ["Another Font\n200\n0\n20-7e\n", "Brand Font\n80\n0\n20-7e\n"]:
            run.return_value = SimpleNamespace(stdout=answer)
            with self.assertRaises(worker.MissingFont):
                worker.require_font(self.style, "Brand", "fc-match")

    @patch.object(worker.subprocess, "run")
    def test_missing_glyph_does_not_use_another_font(self, run):
        run.return_value = SimpleNamespace(stdout="Brand Font\n200\n0\n20-7e\n")
        with self.assertRaises(worker.MissingFont):
            worker.require_font(self.style, "İplik", "fc-match")

    @patch.object(worker.subprocess, "run")
    def test_family_pattern_injection_never_reaches_a_process(self, run):
        with self.assertRaises(worker.MissingFont):
            worker.require_font({"font-family": "Bad:family=Other"}, "Brand", "fc-match")
        run.assert_not_called()

    @patch.object(worker, "require_font")
    def test_inherited_font_and_tspan_overrides(self, require):
        root = ET.fromstring('<svg xmlns="http://www.w3.org/2000/svg" font-family="Brand Font"><text>A<tspan font-weight="700">B</tspan>C</text></svg>')
        worker.check_fonts(root, {}, "fc-match")
        self.assertEqual(require.call_count, 3)
        self.assertEqual(require.call_args.args[0]["font-weight"], "700")


if __name__ == "__main__":
    unittest.main()
