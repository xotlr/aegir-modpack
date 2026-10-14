#!/usr/bin/env python3
"""Minimal packwiz helper for when the packwiz CLI isn't installed.

  python tools/pw.py pin <mods/x.pw.toml> <modrinth version id>   repin a Modrinth mod, then reindex
  python tools/pw.py add <modrinth slug> <both|client|server>       add a Modrinth mod (latest NeoForge 1.21.1 build)
  python tools/pw.py reindex                                      rehash every file into index.toml + pack.toml

reindex tracks every file under the repo except pack.toml, index.toml, .git, docs/, tools/ and core/ (AEGIR Core source).
Files in PRESERVE are installed only when missing, never over a player's edits; other configs are enforced.
"""
import hashlib, json, pathlib, re, sys, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
SKIP_DIRS = {".git", "docs", "tools", "core", "dist"}
PRESERVE = {"config/DistantHorizons.toml", "config/coldsweat/client.toml", "config/iris.properties", "config/renourisheddelight/client.json5", "config/mca.json"}
SKIP_FILES = {"pack.toml", "index.toml", ".gitattributes", ".gitignore", ".packwizignore", "README.md", "FRIENDS.md"}


def sha256(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def api(path):
    req = urllib.request.Request(f"https://api.modrinth.com/v2/{path}", headers={"User-Agent": "aegir-pack/tools/pw.py"})
    return json.load(urllib.request.urlopen(req))


def add(slug, side):
    proj = api(f"project/{slug}")
    versions = api(f'project/{slug}/version?loaders=["neoforge"]&game_versions=["1.21.1"]'.replace('"', "%22"))
    if not versions:
        sys.exit(f"{slug}: no NeoForge 1.21.1 version")
    meta = f"mods/{slug}.pw.toml"
    (ROOT / meta).write_text(f'''name = "{proj["title"]}"
filename = ""
side = "{side}"

[download]
url = ""
hash-format = "sha512"
hash = ""

[update]
[update.modrinth]
mod-id = "{proj["id"]}"
version = ""
''', encoding="utf-8", newline="\n")
    pin(meta, versions[0]["id"])


def pin(meta, version_id):
    path = ROOT / meta
    v = api(f"version/{version_id}")
    f = next((x for x in v["files"] if x["primary"]), v["files"][0])
    text = path.read_text(encoding="utf-8")
    for key, val in (("filename", f["filename"]), ("url", f["url"]),
                     ("hash", f["hashes"]["sha512"]), ("version", version_id)):
        text = re.sub(rf'(?m)^{key} = ".*"$', f'{key} = "{val}"', text)
    path.write_text(text, encoding="utf-8", newline="\n")
    print(f"{meta} -> {f['filename']}")


def reindex():
    files = sorted(p for p in ROOT.rglob("*") if p.is_file()
                   and not (set(p.relative_to(ROOT).parts[:-1]) & SKIP_DIRS)
                   and p.relative_to(ROOT).parts[0] not in SKIP_DIRS
                   and p.name not in SKIP_FILES)
    out = ['hash-format = "sha256"', ""]
    for p in files:
        rel = p.relative_to(ROOT).as_posix()
        out += ["[[files]]", f'file = "{rel}"', f'hash = "{sha256(p)}"']
        if rel.endswith(".pw.toml"):
            out.append("metafile = true")
        if rel in PRESERVE:
            out.append("preserve = true")
        out.append("")
    (ROOT / "index.toml").write_text("\n".join(out), encoding="utf-8", newline="\n")
    pack = ROOT / "pack.toml"
    text = re.sub(r'(?m)^hash = ".*"$', f'hash = "{sha256(ROOT / "index.toml")}"',
                  pack.read_text(encoding="utf-8"), count=1)
    pack.write_text(text, encoding="utf-8", newline="\n")
    print(f"indexed {len(files)} files")


if __name__ == "__main__":
    if sys.argv[1:2] == ["pin"] and len(sys.argv) == 4:
        pin(sys.argv[2], sys.argv[3])
        reindex()
    elif sys.argv[1:2] == ["add"] and len(sys.argv) == 4 and sys.argv[3] in ("both", "client", "server"):
        add(sys.argv[2], sys.argv[3])
        reindex()
    elif sys.argv[1:] == ["reindex"]:
        reindex()
    else:
        sys.exit(__doc__)
