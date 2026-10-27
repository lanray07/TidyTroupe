import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("localization", Path(__file__).with_name("localization.py"))
loc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(loc)

class LocalisationTests(unittest.TestCase):
    def test_protection_survives_reordering(self):
        text = "TidyTroupe: %1$s earned %2$d stars & {reward}."
        encoded, parts = loc.protect(text)
        self.assertEqual(loc.restore(encoded, parts), text)
        self.assertEqual(loc.restore('<x id="1"/> for <x id="0"/>', ["%1$s", "%2$d"]), "%2$d for %1$s")

    def test_missing_or_duplicate_placeholder_is_rejected(self):
        for value in ('<x id="0"/>', '<x id="0"/><x id="0"/>', '<x id="9"/>'):
            with self.assertRaises(ValueError):
                loc.restore(value, ["%1$s", "%2$d"])

    def test_source_plural_other_is_required(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "strings.xml"
            source.write_text('<resources><plurals name="tasks"><item quantity="one">%d task</item></plurals></resources>')
            with self.assertRaises(ValueError):
                loc.extract(source)

    def test_placeholder_count_is_significant(self):
        self.assertNotEqual(loc.placeholders("%1$s %1$s"), loc.placeholders("%1$s"))

if __name__ == "__main__":
    unittest.main()
