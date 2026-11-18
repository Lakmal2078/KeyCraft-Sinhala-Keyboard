#!/usr/bin/env python3
"""Run Sinhala Python tests and fail if discovery finds no tests."""

from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
TEST_DIR = ROOT / "test" / "sinhala"


def main() -> int:
    suite = unittest.defaultTestLoader.discover(
        start_dir=str(TEST_DIR),
        pattern="test_*.py",
    )
    count = suite.countTestCases()
    if count == 0:
        print(f"ERROR: no Python tests discovered under {TEST_DIR}", file=sys.stderr)
        return 2

    print(f"Discovered {count} Sinhala Python test(s).")
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
