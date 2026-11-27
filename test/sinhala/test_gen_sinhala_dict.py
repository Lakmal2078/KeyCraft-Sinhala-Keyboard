import importlib.util
from pathlib import Path
import tempfile
import struct
import unicodedata
import unittest

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "gen_sinhala_dict.py"
SPEC = importlib.util.spec_from_file_location("sinhala_dictionary_generator", SCRIPT)
assert SPEC and SPEC.loader

generator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(generator)


class SinhalaDictionaryGeneratorTest(unittest.TestCase):
    def reviewed_rows(self, directory, *rows):
        source = Path(directory) / "reviewed.tsv"
        source.write_text("\t".join(generator.REVIEWED_FIELDS) + "\n" + "\n".join(rows) + "\n",
                          encoding="utf-8")
        return source

    def test_reviewed_rows_normalize_and_default_priority(self):
        with tempfile.TemporaryDirectory() as tmp:
            spelling = unicodedata.normalize("NFD", "කෝමෝ")
            path = self.reviewed_rows(
                tmp, f"{spelling}\t\tkomo,koomo\trepo:fixture\tCC0-1.0\trepo:test-review\tAPPROVED")
            words, aliases = generator.load_reviewed_words(path)
        self.assertEqual({"කෝමෝ": 12}, words)
        self.assertEqual({"komo": "කෝමෝ", "koomo": "කෝමෝ"}, aliases)

    def test_reviewed_rows_reject_duplicate_normalized_word_and_alias(self):
        with tempfile.TemporaryDirectory() as tmp:
            approved = "කෝමෝ\t13\tkomo\trepo:fixture\tCC0-1.0\trepo:test-review\tAPPROVED"
            for duplicate, message in (
                (unicodedata.normalize("NFD", approved), "duplicate NFC word"),
                (approved.replace("කෝමෝ", "කෑම"), "duplicate Singlish alias"),
            ):
                with self.subTest(message=message):
                    path = self.reviewed_rows(tmp, approved, duplicate)
                    with self.assertRaisesRegex(ValueError, message):
                        generator.load_reviewed_words(path)

    def test_reviewed_rows_reject_invalid_characters_and_malformed_metadata(self):
        valid = "කෝමෝ\t13\tkomo\trepo:fixture\tCC0-1.0\trepo:test-review\tAPPROVED"
        with tempfile.TemporaryDirectory() as tmp:
            invalid = [
                valid.replace("කෝමෝ", token)
                for token in ("ක A", "ක෧", "ක\u0DE0", "ික", "කීි", "ක්්", "ක‍ම", "ක්‍", "ක‌ම", "ක‍ක")
            ]
            invalid += [
                valid.replace("13", "16"), valid.replace("komo", "ko mo"),
                valid.replace("repo:fixture", ""), valid.replace("repo:test-review", ""),
                valid.replace("CC0-1.0", ""), valid.replace("APPROVED", "approved"), valid.rsplit("\t", 1)[0], valid + "\textra",
            ]
            for row in invalid:
                with self.subTest(row=row):
                    with self.assertRaises(ValueError):
                        generator.load_reviewed_words(self.reviewed_rows(tmp, row))

    def test_only_approved_rows_change_generated_vocabulary_and_aliases(self):
        with tempfile.TemporaryDirectory() as tmp:
            empty = self.reviewed_rows(tmp)
            baseline = generator.build_outputs(empty)
            waiting = self.reviewed_rows(tmp,
                "කෝමෝ\t13\tkomo\t\t\t\tPENDING",
                "කේමෝ\t13\tkemo\t\t\t\tREJECTED")
            self.assertEqual(baseline, generator.build_outputs(waiting))
            approved = self.reviewed_rows(tmp,
                "කෝමෝ\t13\tkomo\trepo:fixture\tCC0-1.0\trepo:test-review\tAPPROVED",
                "කේමෝ\t13\tkomo\t\t\t\tPENDING")
            outputs = generator.build_outputs(approved)
            self.assertEqual("komo\tකෝමෝ\n".encode(), outputs["si_aliases.tsv"])
            self.assertNotEqual(baseline["si.dict"], outputs["si.dict"])

    def test_priority_file_cannot_admit_new_entries(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "priority.tsv"
            path.write_text("කෝමෝ\t15\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "existing dictionary entry"):
                generator.apply_existing_priorities({"කෑම": 8}, path)
            path.write_text("කෑම\t15\n", encoding="utf-8")
            words = {"කෑම": 8}
            generator.apply_existing_priorities(words, path)
            self.assertEqual({"කෑම": 15}, words)

    def test_rebuild_includes_reviewed_word_at_exact_and_prefix_nodes(self):
        # Exercise a reviewed fixture without modifying the shipped dictionary.
        with tempfile.TemporaryDirectory() as tmp:
            path = self.reviewed_rows(
                tmp, "කෝමෝ\t13\tkomo\trepo:fixture\tCC0-1.0\trepo:test-review\tAPPROVED")
            outputs = generator.build_outputs(path)
        self.assertEqual("komo\tකෝමෝ\n".encode(), outputs["si_aliases.tsv"])
        data = outputs["si.dict"]
        _, node, _ = struct.unpack_from(">III", data, 5)
        final = False
        for symbol in "කෝමෝ".encode():
            branches = data[node + 1]
            label_offset = node + 2
            labels = data[label_offset:label_offset + branches]
            self.assertIn(symbol, labels, "Each byte of the word must extend the trie prefix")
            pointer = label_offset + branches + 3 * labels.index(symbol)
            relative = int.from_bytes(data[pointer:pointer + 3], "big", signed=True)
            final = bool(relative & 1)
            node += relative & ~1
        self.assertTrue(final, "The reviewed word must be a terminal dictionary entry")

    def test_cleaner_keeps_valid_sinhala_and_conjuncts(self):
        for word in ("සිංහල", "ක්‍රමය", "විද්‍යාව", "ආයුබෝවන්", "සවුදි"):
            with self.subTest(word=word):
                self.assertEqual(word, generator.clean_sinhala_word(word))

    def test_cleaner_rejects_malformed_repeated_marks(self):
        for word in ("කිිසිසේත්", "අවස්්ථාවක", "ආර්්ථික", "කෑෑමට"):
            with self.subTest(word=word):
                self.assertIsNone(generator.clean_sinhala_word(word))

    def test_cleaner_rejects_non_sinhala_and_leading_marks(self):
        self.assertIsNone(generator.clean_sinhala_word("hello"))
        self.assertIsNone(generator.clean_sinhala_word("\u0DD2සිංහල"))
        self.assertIsNone(generator.clean_sinhala_word("\u0DCA"))

    def test_trie_builder_cleans_malformed_entries(self):
        data = generator.build_trie_dict({"සිංහල": 15, "කිිසිසේත්": 8}, "main")
        self.assertTrue(data.startswith(b"Dic"))
        self.assertGreater(len(data), 30)


if __name__ == "__main__":
    unittest.main()
