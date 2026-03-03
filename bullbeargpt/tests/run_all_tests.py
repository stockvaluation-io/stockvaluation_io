#!/usr/bin/env python3
"""
Run all bullbeargpt tests for the current local-first code layout.
"""
import subprocess
import sys


def main() -> int:
    try:
        result = subprocess.run(["pytest", "-q", "tests"], text=True)
    except FileNotFoundError:
        result = subprocess.run([sys.executable, "-m", "pytest", "-q", "tests"], text=True)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
