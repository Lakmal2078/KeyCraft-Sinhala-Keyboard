#!/usr/bin/env python3
"""
gen_sinhala_dict.py

Compiles a comprehensive Sinhala dictionary in libcdict (Cdict) binary format
for KeyCraft Sinhala Keyboard.
"""

import argparse
import csv
import hashlib
from pathlib import Path
import re
import struct
import unicodedata

# Core high-frequency Sinhala words supplementing the bundled source dictionary
CORE_SINHALA_WORDS = [
    ("මම", 15), ("ඔයා", 15), ("අපි", 15), ("එයා", 15), ("ඔබ", 15),
    ("මට", 15), ("ඔයාට", 15), ("අපිට", 15), ("එයාට", 15), ("ඔබට", 15),
    ("මගේ", 15), ("ඔයාගේ", 15), ("අපේ", 15), ("එයාගේ", 15), ("ඔබගේ", 15),
    ("මේ", 15), ("මේක", 15), ("එක", 15), ("අරක", 15), ("ඒක", 15),
    ("ගෙදර", 15), ("වැඩ", 15), ("වැඩක්", 15), ("වැඩේ", 15), ("සල්ලි", 15),
    ("පොත", 15), ("පොත්", 15), ("කෑම", 15), ("වතුර", 15), ("රට", 15),
    ("ලංකා", 15), ("ශ්‍රී", 15), ("ලංකාව", 15), ("සිංහල", 15), ("සිංහලය", 15),
    ("ස්තූතියි", 15), ("බොහොම", 15), ("ආයුබෝවන්", 15), ("කරුණාකර", 15),
    ("සුබ", 15), ("උදෑසනක්", 15), ("දවසක්", 15), ("රාත්‍රියක්", 15),
    ("යනවා", 15), ("යන්න", 15), ("ගියා", 15), ("එනවා", 15), ("එන්න", 15),
    ("ආවා", 15), ("කරනවා", 15), ("කරන්න", 15), ("කළා", 15), ("බලනවා", 15),
    ("බලන්න", 15), ("කනවා", 15), ("කන්න", 15), ("බොනවා", 15), ("බොන්න", 15),
    ("හිතනවා", 15), ("හිතන්න", 15), ("දන්නවා", 15), ("දන්න", 15),
    ("කියන්න", 15), ("කියලා", 15), ("කිව්වා", 15), ("තියෙනවා", 15), ("තියෙන්නේ", 15),
    ("ලස්සන", 15), ("හොඳ", 15), ("නරක", 15), ("ගොඩක්", 15), ("ටිකක්", 15),
    ("පොඩ්ඩක්", 15), ("හරි", 15), ("පුළුවන්", 15), ("පුළුවන්ද", 15), ("බැහැ", 15),
    ("බෑ", 15), ("නැහැ", 15), ("නෑ", 15), ("ඔව්", 15), ("හෙට", 15),
    ("අද", 15), ("ඊයේ", 15), ("දැන්", 15), ("පස්සේ", 15), ("කලින්", 15),
    ("මොකද", 15), ("කොහොමද", 15), ("කොහෙද", 15), ("කවදාද", 15), ("මොකක්ද", 15),
    ("මොනවද", 15), ("ඇයි", 15), ("කවුද", 15), ("කාටද", 15), ("කාගේද", 15),
    ("පාසල", 14), ("ඉස්කෝලේ", 14), ("විශ්වවිද්‍යාලය", 14), ("කැම්පස්", 14),
    ("දුරකථනය", 14), ("ෆෝන්", 14), ("පරිගණකය", 14), ("කීබෝඩ්", 14),
    ("පණිවිඩය", 14), ("මැසේජ්", 14), ("ඡායාරූපය", 14), ("ෆොටෝ", 14),
    ("ක්‍රිකට්", 14), ("මැච්", 14), ("චිත්‍රපටය", 14), ("සින්දුව", 14),
    ("තෙරුවන්", 15), ("සරණයි", 15), ("බුදු", 15), ("දහම්", 15), ("සඟ", 15),
    ("පරිස්සමෙන්", 15), ("කාරුණික", 14), ("ඉල්ලීමක්", 14), ("දැනුම්දීමක්", 14),
    ("ප්‍රශ්නය", 14), ("විසඳුම", 14), ("ගැටලුව", 14), ("සාකච්ඡාව", 14),
]


SINHALA_WORD_RE = re.compile(r"[\u0D80-\u0DFF\u200D]+")


def clean_sinhala_word(word):
    """Normalize a single Sinhala token; return None for malformed/non-Sinhala input.

    This deliberately performs conservative structural checks rather than trying
    to encode every Sinhala orthographic rule. It catches leading/repeated
    dependent signs and repeated virama sequences without rejecting ZWJ conjuncts.
    """
    word = unicodedata.normalize("NFC", word.strip())
    if not word or not SINHALA_WORD_RE.fullmatch(word):
        return None
    if not any(unicodedata.category(ch).startswith("L") for ch in word):
        return None

    previous_was_vowel_sign = False
    previous_was_virama = False
    for ch in word:
        category = unicodedata.category(ch)
        is_vowel_sign = unicodedata.name(ch, "").startswith("SINHALA VOWEL SIGN")
        if is_vowel_sign and (previous_was_vowel_sign or previous_was_virama):
            return None
        if ch == "\u0DCA" and previous_was_virama:
            return None
        if category.startswith("L"):
            previous_was_vowel_sign = False
            previous_was_virama = False
        else:
            previous_was_vowel_sign = is_vowel_sign
            previous_was_virama = ch == "\u0DCA"
    if unicodedata.category(word[0]) in ("Mn", "Mc") or word[0] == "\u0DCA":
        return None
    return word


ROOT = Path(__file__).resolve().parent
SOURCE_DIR = ROOT / "srcs/dictionaries"
BUNDLED_DIR = ROOT / "assets/dict"
DIGEST_SOURCE = ROOT / "srcs/java/com/lakmal/keycraftsinhala/dict/SinhalaDictionaryDigest.java"
REVIEWED_FIELDS = ("word", "priority", "singlish_aliases", "source", "license", "review", "status")


def valid_reviewed_word(word):
    """Validate the single-token format used by reviewed additions."""
    def letter(ch):
        return "\u0D85" <= ch <= "\u0DC6" and unicodedata.category(ch) == "Lo"

    def consonant(ch):
        return ch >= "\u0D9A" and letter(ch)

    if clean_sinhala_word(word) != word or not word or not letter(word[0]):
        return False
    for i, ch in enumerate(word[1:], 1):
        previous = word[i - 1]
        if letter(ch):
            continue
        if ch == "\u200D":
            if previous != "\u0DCA" or i + 1 == len(word) or not consonant(word[i + 1]):
                return False
        elif ch == "\u0DCA" or unicodedata.name(ch, "").startswith("SINHALA VOWEL SIGN"):
            if not consonant(previous):
                return False
        elif ch in "\u0D81\u0D82\u0D83":
            if not letter(previous) and not unicodedata.name(previous, "").startswith("SINHALA VOWEL SIGN"):
                return False
        else:
            return False
    return True


def load_reviewed_words(path):
    """Read reviewed entries, keeping spelling and alias validation fail-closed."""
    words, aliases, seen = {}, {}, set()
    with path.open(encoding="utf-8", newline="") as source:
        rows = csv.DictReader(source, delimiter="\t", quoting=csv.QUOTE_NONE)
        if tuple(rows.fieldnames or ()) != REVIEWED_FIELDS:
            raise ValueError(f"{path}: expected header {' '.join(REVIEWED_FIELDS)}")
        for row in rows:
            location = f"{path}:{rows.line_num}"
            if None in row or any(value is None for value in row.values()):
                raise ValueError(f"{location}: expected {len(REVIEWED_FIELDS)} tab-separated fields")
            word = unicodedata.normalize("NFC", row["word"])
            if not valid_reviewed_word(word):
                raise ValueError(f"{location}: invalid Sinhala token {row['word']!r}")
            if word in seen:
                raise ValueError(f"{location}: duplicate NFC word {word!r}")
            priority = row["priority"] or "12"
            if not re.fullmatch(r"[1-9]|1[0-5]", priority):
                raise ValueError(f"{location}: priority must be 1–15")
            seen.add(word)
            status = row["status"]
            if status not in {"PENDING", "APPROVED", "REJECTED"}:
                raise ValueError(f"{location}: invalid review status {status!r}")
            approved = status == "APPROVED"
            for field in ("source", "review"):
                if (approved or row[field]) and not re.fullmatch(r"(?:https://|repo:)[^\s]+", row[field]):
                    raise ValueError(f"{location}: {field} must contain a reference")
            if (approved or row["license"]) and not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.+-]*", row["license"]):
                raise ValueError(f"{location}: an SPDX license identifier is required")
            row_aliases = row["singlish_aliases"].split(",") if row["singlish_aliases"] else []
            if len(set(row_aliases)) != len(row_aliases):
                raise ValueError(f"{location}: duplicate Singlish alias")
            for alias in row_aliases:
                if not re.fullmatch(r"[a-z]{2,30}", alias):
                    raise ValueError(f"{location}: invalid Singlish alias {alias!r}")
                if approved:
                    if alias in aliases:
                        raise ValueError(f"{location}: duplicate Singlish alias {alias!r}")
                    aliases[alias] = word
            if approved:
                words[word] = int(priority)
    return words, aliases


def load_words_from_libreoffice():
    """Load the vendored, licensed Sinhala word list without network access."""
    source_path = SOURCE_DIR / "si_LK.dic"
    words = set()
    with open(source_path, "r", encoding="utf-8") as source:
        next(source, None)  # Hunspell word-count header
        for line in source:
            line = line.strip()
            if not line:
                continue
            word = line.split("/", 1)[0].strip()
            cleaned = clean_sinhala_word(word)
            if cleaned:
                words.add(cleaned)
    print(f"Loaded {len(words)} words from the vendored LibreOffice dictionary.")
    return words


def build_trie_dict(word_freq_map, dict_name="main"):
    # Normalize and conservatively clean every source before building the trie.
    cleaned_freq = {}
    for raw_word, freq in word_freq_map.items():
        word = clean_sinhala_word(raw_word)
        if word:
            cleaned_freq[word] = max(freq, cleaned_freq.get(word, 0))
    items = sorted(cleaned_freq.items(), key=lambda x: x[0].encode("utf-8"))

    class TrieNode:
        __slots__ = ("is_final", "freq", "children", "pos", "total_words", "word_index")
        def __init__(self):
            self.is_final = False
            self.freq = 0
            self.children = {}
            self.pos = 0
            self.total_words = 0
            self.word_index = -1

    root = TrieNode()

    for w, f in items:
        cur = root
        for b in w.encode("utf-8"):
            if b not in cur.children:
                cur.children[b] = TrieNode()
            cur = cur.children[b]
        cur.is_final = True
        cur.freq = f

    # Assign alphabetical word indexes
    words_in_order = []
    def assign_indexes(n, current_bytes):
        if n.is_final:
            n.word_index = len(words_in_order)
            words_in_order.append((bytes(current_bytes), n.freq))
        for b in sorted(n.children.keys()):
            current_bytes.append(b)
            assign_indexes(n.children[b], current_bytes)
            current_bytes.pop()

    assign_indexes(root, bytearray())

    # Count reachable words
    def count_words(n):
        c = 1 if n.is_final else 0
        for b in sorted(n.children.keys()):
            c += count_words(n.children[b])
        n.total_words = c
        return c

    count_words(root)

    BRANCHES_FMT = 6  # 24-bit signed
    NUMBERS_FMT = 6   # 24-bit unsigned

    def calc_node_size(n):
        L = len(n.children)
        raw_size = 2 + 7 * L
        # 2-byte alignment ensures all offsets are even
        return (raw_size + 1) & ~1

    nodes_order = []
    def layout_postorder(n):
        for b in sorted(n.children.keys()):
            layout_postorder(n.children[b])
        nodes_order.append(n)

    layout_postorder(root)

    # 5 bytes file header + 24 bytes dict header + 1 byte pad = 30 bytes
    current_offset = 30
    for n in nodes_order:
        n.pos = current_offset
        current_offset += calc_node_size(n)

    root_ptr = root.pos

    def make_complete_tree(branches_list):
        L = len(branches_list)
        if L == 0:
            return []
        tree = [None] * L
        def width_of_last_full_level(len_):
            s = 1
            while (s * 2 + 1) <= len_:
                s = s * 2 + 1
            return (s // 2) + 1, len_ - s

        def pivot(len_):
            width, last_level = width_of_last_full_level(len_)
            if last_level <= width:
                return (len_ + last_level) // 2
            else:
                return (width * 2) - 1

        def loop(lo, hi, dsti):
            if lo > hi:
                return
            mid = lo + pivot(hi - lo + 1)
            tree[dsti] = branches_list[mid]
            loop(lo, mid - 1, dsti * 2 + 1)
            loop(mid + 1, hi, dsti * 2 + 2)

        loop(0, L - 1, 0)
        return tree

    nodes_bytes = bytearray()
    for n in nodes_order:
        L = len(n.children)
        sorted_branches = []
        cum_words = 0
        for b in sorted(n.children.keys()):
            child = n.children[b]
            sorted_branches.append((b, child, cum_words))
            cum_words += child.total_words

        tree = make_complete_tree(sorted_branches)

        header = (NUMBERS_FMT << 4) | (BRANCHES_FMT << 1) | 0
        node_buf = bytearray()
        node_buf.append(header)
        node_buf.append(L)

        # Labels (complete binary tree)
        for item in tree:
            node_buf.append(item[0])

        # Branches
        for item in tree:
            child = item[1]
            offset = child.pos - n.pos
            assert offset % 2 == 0, f"Offset {offset} must be even"
            ptr = offset | (1 if child.is_final else 0)
            ptr_u = ptr & 0xFFFFFF
            node_buf.append((ptr_u >> 16) & 0xFF)
            node_buf.append((ptr_u >> 8) & 0xFF)
            node_buf.append(ptr_u & 0xFF)

        # Numbers
        for item in tree:
            num = item[2]
            num_u = num & 0xFFFFFF
            node_buf.append((num_u >> 16) & 0xFF)
            node_buf.append((num_u >> 8) & 0xFF)
            node_buf.append(num_u & 0xFF)

        if len(node_buf) < calc_node_size(n):
            node_buf.append(0)

        assert len(node_buf) == calc_node_size(n)
        nodes_bytes.extend(node_buf)

    freq_off = current_offset
    total_w = len(words_in_order)
    freq_size = (total_w + 1) // 2
    freq_buf = bytearray(freq_size)
    for i, (w_bytes, f) in enumerate(words_in_order):
        nibble = min(15, max(0, f)) & 0x0F
        if (i & 1) == 0:
            freq_buf[i >> 1] |= nibble
        else:
            freq_buf[i >> 1] |= (nibble << 4)

    current_offset += freq_size

    name_off = current_offset
    name_bytes = dict_name.encode("latin1") + b"\0"
    current_offset += len(name_bytes)

    # File Header
    hdr = bytearray()
    hdr.extend(b"Dic")
    hdr.append(1)  # Version 1
    hdr.append(1)  # Dict count 1
    hdr.extend(struct.pack(">III", name_off, root_ptr, freq_off))
    hdr.append(0)  # Aliases header
    hdr.extend(b"\0\0\0")  # Aliases length
    hdr.extend(struct.pack(">II", 0, 0))  # Aliases keys, values
    hdr.append(0)  # Pad to 30 bytes

    final_data = bytes(hdr) + bytes(nodes_bytes) + bytes(freq_buf) + name_bytes
    return final_data


def apply_existing_priorities(word_freq, path):
    """Raise editorial priority without admitting any new vocabulary."""
    seen = set()
    for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split("\t")
        if len(fields) != 2 or not re.fullmatch(r"[1-9]|1[0-5]", fields[1]):
            raise ValueError(f"{path}:{i}: expected an existing word and priority 1–15")
        word, priority = fields
        if word not in word_freq or word in seen:
            raise ValueError(f"{path}:{i}: not a unique existing dictionary entry: {word!r}")
        seen.add(word)
        word_freq[word] = max(int(priority), word_freq[word])


def build_outputs(reviewed_path):
    word_freq = dict.fromkeys(load_words_from_libreoffice(), 8)
    word_freq.update(CORE_SINHALA_WORDS)
    legacy_words = (SOURCE_DIR / "si_legacy_keycraft.txt").read_text(encoding="utf-8").splitlines()
    word_freq.update(dict.fromkeys(legacy_words, 15))
    apply_existing_priorities(word_freq, SOURCE_DIR / "si_existing_priorities.tsv")
    reviewed_words, aliases = load_reviewed_words(reviewed_path)
    for word, priority in reviewed_words.items():
        word_freq[word] = max(priority, word_freq.get(word, 0))
    return {
        "si.dict": build_trie_dict(word_freq),
        "si_aliases.tsv": "".join(f"{alias}\t{aliases[alias]}\n" for alias in sorted(aliases)).encode("utf-8"),
    }


def main():
    parser = argparse.ArgumentParser(description="Build or check the offline KeyCraft Sinhala dictionary.")
    parser.add_argument("--reviewed", type=Path, default=SOURCE_DIR / "si_reviewed.tsv")
    parser.add_argument("--output-dir", type=Path, default=BUNDLED_DIR)
    parser.add_argument("--check", action="store_true", help="Compare generated files without writing them.")
    args = parser.parse_args()
    try:
        assets = build_outputs(args.reviewed)
        outputs = {args.output_dir / name: data for name, data in assets.items()}
        if args.output_dir.resolve() == BUNDLED_DIR:
            digest = hashlib.sha256(assets["si.dict"]).hexdigest()
            outputs[DIGEST_SOURCE] = (
                "// Generated by gen_sinhala_dict.py.\n"
                "package com.lakmal.keycraftsinhala.dict;\n\n"
                "final class SinhalaDictionaryDigest\n"
                "{\n"
                f'  static final String SHA256 = "{digest}";\n'
                "}\n"
            ).encode("ascii")
        if not args.check:
            args.output_dir.mkdir(parents=True, exist_ok=True)
        for destination, data in outputs.items():
            if args.check:
                if not destination.is_file() or destination.read_bytes() != data:
                    raise ValueError(f"{destination} is out of date. Run python3 gen_sinhala_dict.py.")
            else:
                destination.write_bytes(data)
                print(f"Generated {destination} ({len(data)} bytes)")
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")


if __name__ == "__main__":
    main()
