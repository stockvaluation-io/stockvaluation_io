#!/usr/bin/env python3
"""
Run all bullbeargpt tests for the current local-first code layout.
"""
import subprocess
import sys
from pathlib import Path


def main() -> int:
    tests_dir = Path(__file__).resolve().parent
    project_root = tests_dir.parent
    try:
        result = subprocess.run(
            ["pytest", "-q", str(tests_dir)],
            text=True,
            cwd=project_root,
        )
    except FileNotFoundError:
        result = subprocess.run(
            [sys.executable, "-m", "pytest", "-q", str(tests_dir)],
            text=True,
            cwd=project_root,
        )
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
