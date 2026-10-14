#!/usr/bin/env python3
"""Build mods/aegir-tweaks-<version>.jar from tools/aegir-tweaks (version read from its neoforge.mods.toml).

  python -I tools/build_tweaks.py

Removes older aegir-tweaks jars from mods/. Entries get a fixed timestamp, so an unchanged source
always produces a byte-identical jar (stable hash in index.toml). Run tools/pw.py reindex afterwards.
"""
import pathlib, re, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "tools/aegir-tweaks"

version = re.search(r'version="([^"]+)"', (SRC / "META-INF/neoforge.mods.toml").read_text()).group(1)
out = ROOT / f"mods/aegir-tweaks-{version}.jar"
for old in (ROOT / "mods").glob("aegir-tweaks-*.jar"):
    if old != out:
        old.unlink()
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for p in sorted(SRC.rglob("*")):
        if p.is_file():
            zi = zipfile.ZipInfo(p.relative_to(SRC).as_posix(), date_time=(2026, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, p.read_bytes())
print(out.relative_to(ROOT))
