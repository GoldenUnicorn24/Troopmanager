"""Turns Gradle/Kotlin/lint failures into GitHub annotations (readable without raw log access)."""
import re
import sys
from pathlib import Path

lines = []
for name in sys.argv[1:]:
    path = Path(name)
    if path.exists():
        lines += path.read_text(errors="replace").splitlines()
interesting = [l for l in lines if re.match(r"^(e: |w: .*error)|.*(What went wrong|FAILED|Error:|error:|AssertionError|Exception)", l)]
if not interesting:
    interesting = lines[-30:]
# GitHub keeps 10 error annotations per step: pack several lines into each.
chunks = [interesting[i:i + 6] for i in range(0, min(len(interesting), 60), 6)]
for chunk in chunks[:10]:
    text = "%0A".join(c.replace("%", "%25").replace("\r", "")[:400] for c in chunk)
    print(f"::error title=build::{text}")
print("\n".join(lines[-40:]))
