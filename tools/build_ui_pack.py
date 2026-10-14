#!/usr/bin/env python3
"""Build the UI part of the aegir-assets resource pack (Norse look for menus and inventories only).

  python -I tools/build_ui_pack.py <minecraft-1.21.1-client.jar>

Writes resourcepacks/aegir-assets/. Vanilla GUI textures are recoloured, not redrawn, so every
screen keeps its exact layout:
- inventories/containers/hotbar: grey -> birch/parchment panels with dark oak and iron edges
  (container titles are drawn in hard-coded dark grey, so panels must stay light)
- buttons and sliders: dark oak with an iron rim; hovered = gold rim
- title logo, edition line and loading-screen logo: AEGIR wordmark + the name in Elder Futhark
Only grey pixels are touched, so icons, arrows and modded colours stay as they were.
"""
import io, math, pathlib, random, sys, zipfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepacks/aegir-assets"
GUI = "assets/minecraft/textures/gui/"
FONT = r"C:\Windows\Fonts\georgiab.ttf"

# grey level -> colour, piecewise linear (vanilla GUI greys: 0 outline, 55/85 shadow, 139 slot, 198 panel, 255 light)
PANEL = [(0, (24, 17, 11)), (55, (64, 45, 27)), (85, (92, 66, 41)), (139, (139, 113, 80)),
         (198, (200, 178, 138)), (255, (240, 228, 196))]
OAK = [(0, (14, 10, 6)), (40, (34, 24, 15)), (110, (74, 52, 32)), (170, (112, 82, 52)), (255, (168, 132, 88))]
IRON, GOLD = (96, 98, 104), (212, 166, 64)

RECOLOUR_DIRS = ["container/", "sprites/container/", "sprites/recipe_book/", "sprites/advancements/",
                 "advancements/", "sprites/hud/hotbar", "sprites/popup/", "sprites/widget/tab"]
SKIP = ["sprites/hud/heart", "sprites/hud/food", "sprites/hud/armor", "sprites/hud/air", "sprites/icon/"]


def ramp(stops, v):
    for (a, ca), (b, cb) in zip(stops, stops[1:]):
        if v <= b:
            t = (v - a) / (b - a) if b > a else 0
            return tuple(round(x + (y - x) * t) for x, y in zip(ca, cb))
    return stops[-1][1]


def is_grey(r, g, b):
    return max(r, g, b) - min(r, g, b) <= 6


def recolour(im, stops, grain=True, seed=0):
    im = im.convert("RGBA"); px = im.load(); w, h = im.size
    rnd = random.Random(seed); rows = [rnd.uniform(-1, 1) for _ in range(h)]
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0 or not is_grey(r, g, b):
                continue
            c = ramp(stops, (r + g + b) / 3)
            if grain and 170 <= r <= 225:  # wood grain on panel faces: soft horizontal streaks
                d = 5 * rows[y] + 3 * math.sin(x / 9 + rows[y] * 3)
                c = tuple(max(0, min(255, round(v + d))) for v in c)
            px[x, y] = (*c, a)
    return im


def button(src, rim):
    """Dark oak button with a metal rim; src is the vanilla greyscale sprite (keeps its 9-slice border)."""
    im = recolour(src, OAK, grain=False); px = im.load(); w, h = im.size
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1):
                px[x, y] = (*rim, 255) if (0 < x < w - 1 or 0 < y < h - 1) else (0, 0, 0, 0)
            elif x in (1, w - 2) or y in (1, h - 2):
                r, g, b, a = px[x, y]; px[x, y] = (*(round(v * 0.85 + c * 0.15) for v, c in zip((r, g, b), rim)), a)
    rnd = random.Random(7)
    for y in range(2, h - 2):  # oak grain
        k = rnd.uniform(-1, 1)
        for x in range(2, w - 2):
            r, g, b, a = px[x, y]; d = round(6 * k + 3 * math.sin(x / 13 + k * 4))
            px[x, y] = (max(0, r + d), max(0, g + d), max(0, b + d), a)
    return im


# ---- runes (Elder Futhark ᚨᛖᚷᛁᚱ), drawn as strokes in a 0..1 box ----------------------------------
RUNES = {
    "A": [((0.2, 0), (0.2, 1)), ((0.2, 0), (0.8, 0.3)), ((0.2, 0.3), (0.8, 0.6))],            # ansuz
    "E": [((0.15, 0), (0.15, 1)), ((0.85, 0), (0.85, 1)), ((0.15, 0), (0.5, 0.35)), ((0.5, 0.35), (0.85, 0))],  # ehwaz
    "G": [((0.1, 0), (0.9, 1)), ((0.9, 0), (0.1, 1))],                                          # gebo
    "I": [((0.5, 0), (0.5, 1))],                                                                # isa
    "R": [((0.2, 0), (0.2, 1)), ((0.2, 0), (0.75, 0.22)), ((0.75, 0.22), (0.2, 0.45)), ((0.2, 0.45), (0.8, 1))],  # raidho
}


def runes(word, w, h, colour, width):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    gw = h * 0.55; gap = h * 0.45; total = len(word) * gw + (len(word) - 1) * gap; x0 = (w - total) / 2
    for i, ch in enumerate(word):
        ox = x0 + i * (gw + gap)
        for (ax, ay), (bx, by) in RUNES[ch]:
            d.line([(ox + ax * gw, ay * (h - 2 * width) + width), (ox + bx * gw, by * (h - 2 * width) + width)], fill=colour, width=width)
    return im


def wordmark(w, h, fill_top, fill_bottom, outline, size):
    font = ImageFont.truetype(FONT, size)
    mask = Image.new("L", (w, h), 0); d = ImageDraw.Draw(mask)
    text = "AEGIR"; spacing = size * 0.12
    widths = [d.textlength(c, font=font) for c in text]; total = sum(widths) + spacing * (len(text) - 1)
    x = (w - total) / 2; top = (h - size) / 2 - size * 0.12
    for c, cw in zip(text, widths):
        d.text((x, top), c, font=font, fill=255); x += cw + spacing
    grad = Image.new("RGBA", (w, h))
    for y in range(h):
        t = y / (h - 1); grad.paste(tuple(round(a + (b - a) * t) for a, b in zip(fill_top, fill_bottom)) + (255,), (0, y, w, y + 1))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    if outline:
        edge = mask.filter(ImageFilter.MaxFilter(9))
        out.paste(Image.new("RGBA", (w, h), (*outline, 255)), (0, 0), edge)
    out.paste(grad, (0, 0), mask)
    return out


def main(client_jar):
    z = zipfile.ZipFile(client_jar)
    get = lambda p: Image.open(io.BytesIO(z.read(GUI + p)))
    if OUT.exists():
        for p in sorted(OUT.rglob("*"), reverse=True):
            p.unlink() if p.is_file() else p.rmdir()
    tex = OUT / GUI
    count = 0
    for name in z.namelist():
        rel = name[len(GUI):]
        if not name.startswith(GUI) or not name.endswith(".png") or any(rel.startswith(s) for s in SKIP):
            continue
        if any(rel.startswith(d) for d in RECOLOUR_DIRS):
            out = tex / rel; out.parent.mkdir(parents=True, exist_ok=True)
            recolour(Image.open(io.BytesIO(z.read(name))), PANEL, seed=hash(rel) & 0xFFFF).save(out); count += 1
            meta = name + ".mcmeta"
            if meta in z.namelist(): (tex / (rel + ".mcmeta")).write_bytes(z.read(meta))
    w = tex / "sprites/widget"; w.mkdir(parents=True, exist_ok=True)
    for n, rim in [("button", IRON), ("button_highlighted", GOLD), ("button_disabled", (60, 58, 56)),
                   ("slider", IRON), ("slider_highlighted", GOLD), ("slider_handle", IRON), ("slider_handle_highlighted", GOLD)]:
        src = f"sprites/widget/{n}.png"
        if GUI + src not in z.namelist(): continue
        im = button(get(src), rim)
        if n == "button_disabled":
            im = Image.eval(im, lambda v: v)  # keep, it's already dulled by its darker rim
        im.save(w / f"{n}.png"); count += 1
        if GUI + src + ".mcmeta" in z.namelist(): (w / f"{n}.png.mcmeta").write_bytes(z.read(GUI + src + ".mcmeta"))
    t = tex / "title"; t.mkdir(parents=True, exist_ok=True)
    wordmark(1024, 256, (226, 200, 140), (138, 104, 52), (30, 20, 12), 200).save(t / "minecraft.png")
    runes("AEGIR", 512, 64, (214, 190, 140, 255), 7).save(t / "edition.png")
    logo = wordmark(1024, 256, (255, 255, 255), (255, 255, 255), None, 210)
    split = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    split.paste(logo.crop((0, 0, 512, 256)), (0, 0)); split.paste(logo.crop((512, 0, 1024, 256)), (0, 256))
    split.save(t / "mojangstudios.png")
    (OUT / "assets/minecraft/texts").mkdir(parents=True, exist_ok=True)
    (OUT / "assets/minecraft/texts/splashes.txt").write_text("\n".join([
        "The sea remembers!", "Aegir brewed for friends who never came!", "Sail far, come home!",
        "The world wraps around!", "Skål!", "Mead is warmer than wool!", "Mind the Leviathan!",
        "Every grave becomes a cairn!", "Three hearts and a stew!", "Raise the sail!", "Valhalla can wait!",
        "Ships, not walls!", "Ale for the hall!", "Wind in the rigging!", "Find the world tree!"]) + "\n",
        encoding="utf-8", newline="\n")
    (OUT / "pack.mcmeta").write_text('{"pack": {"pack_format": 34, "description": "AEGIR: Norse menus and inventories"}}\n', newline="\n")
    wordmark(128, 128, (226, 200, 140), (138, 104, 52), (30, 20, 12), 40).save(OUT / "pack.png")
    print(f"{count} textures recoloured or drawn -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
