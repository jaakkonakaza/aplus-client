#!/usr/bin/env python3
"""Compare the Scala adapter's public JVM signatures with its reviewed API snapshot."""
import argparse
import difflib
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("classes", type=Path)
parser.add_argument("javap")
parser.add_argument("snapshot", type=Path)
parser.add_argument("--update", action="store_true")
args = parser.parse_args()
classes = sorted(".".join(p.relative_to(args.classes).with_suffix("").parts) for p in args.classes.rglob("*.class"))
if not classes:
    raise SystemExit("No compiled Scala classes found")
result = subprocess.run([args.javap, "-public", "-classpath", str(args.classes), *classes], check=True, capture_output=True, text=True)
actual = "\n".join(line for line in result.stdout.splitlines() if not line.startswith("Compiled from")) + "\n"
if args.update:
    args.snapshot.parent.mkdir(parents=True, exist_ok=True)
    args.snapshot.write_text(actual)
else:
    expected = args.snapshot.read_text()
    if actual != expected:
        print("".join(difflib.unified_diff(expected.splitlines(True), actual.splitlines(True), fromfile=str(args.snapshot), tofile="compiled API")))
        raise SystemExit("Scala JVM API changed. Review it and run :course-config-scala:updateScalaApi.")
