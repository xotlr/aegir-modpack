#!/usr/bin/env python3
"""Build the Renourished Delight food-tier preset (spec section 6) from the mods' own item tags.

  python -I tools/build_food_tiers.py <mods dir> <neoforge universal jar>

Renourished presets take exact item ids (no tags), so tags are expanded here, from the jars,
and only ids that some jar actually defines (item model or lang entry) are kept.
Writes tools/aegir-tweaks/data/renourisheddelight/foodpresets/aegir.json.

Tier bonuses are split into +2 HP steps whose durations are spaced evenly down from the full
duration (T3: 100/66/33 %, as in the spec), so the bonus fades step by step.
Foods not listed keep Renourished's own bonus derived from nutrition and saturation.
"""
import json, pathlib, re, sys, zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "tools/aegir-tweaks/data/renourisheddelight/foodpresets/aegir.json"

TIERS = {  # name: (max health bonus in HP, full duration in ticks)
    "T1": (2, 18000), "T2": (4, 24000), "T3": (6, 30000), "T4": (10, 36000),
}

T1_ITEMS = [  # vanilla simple foods
    "minecraft:bread", "minecraft:baked_potato", "minecraft:apple", "minecraft:carrot",
    "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:melon_slice", "minecraft:cookie",
    "minecraft:dried_kelp", "minecraft:beetroot", "minecraft:cooked_beef", "minecraft:cooked_porkchop",
    "minecraft:cooked_chicken", "minecraft:cooked_mutton", "minecraft:cooked_rabbit",
    "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:pumpkin_pie",
]
T2_TAGS = ["farmersdelight:snacks", "farmersdelight:sweets", "farmersdelight:pies", "roasters_delight:coffees"]
T3_TAGS = ["farmersdelight:meals", "c:foods/soup", "brewinandchewin:fermented_drinks", "fishofthieves:cooked_thieves_fish"]
T3_NAMESPACES = ["brewinandchewin"]  # every Brewin' And Chewin' food in c:foods
T4_TAGS = ["farmersdelight:feasts"]
T4_ITEMS = [  # feast servings, End food, golden foods
    "farmersdelight:roast_chicken", "farmersdelight:stuffed_pumpkin", "farmersdelight:honey_glazed_ham",
    "farmersdelight:shepherds_pie", "farmersdelight:rice_roll_medley_block",
    "minecraft:chorus_fruit", "minecraft:golden_apple", "minecraft:enchanted_golden_apple", "minecraft:golden_carrot",
]
EXCLUDE = re.compile(r"(_block|_feast|raw_|rotten|spider_eye|poisonous|pufferfish|horror)")
FEAST_BLOCK_OK = {"farmersdelight:rice_roll_medley_block"}  # its block item is eaten directly? kept only if it exists


def load(mods_dir, neoforge_jar):
    tags, known = {}, set()
    jars = sorted(pathlib.Path(mods_dir).glob("*.jar")) + [pathlib.Path(neoforge_jar)]
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                m = re.match(r"data/([^/]+)/tags/items?/(.+)\.json$", n)
                if m:
                    try:
                        vals = json.loads(z.read(n)).get("values", [])
                    except ValueError:
                        continue
                    tags.setdefault(f"{m.group(1)}:{m.group(2)}", []).extend(vals)
                m = re.match(r"assets/([^/]+)/models/item/(.+)\.json$", n)
                if m:
                    known.add(f"{m.group(1)}:{m.group(2)}")
                if re.match(r"assets/[^/]+/lang/en_us\.json$", n):
                    try:
                        for k in json.loads(z.read(n)):
                            p = k.split(".")
                            if len(p) == 3 and p[0] in ("item", "block"):
                                known.add(f"{p[1]}:{p[2]}")
                    except ValueError:
                        pass
    return tags, known  # vanilla ids are not in mod jars; they are trusted as-is in main()


def expand(tag, tags, seen=None):
    seen = seen or set()
    out = []
    for v in tags.get(tag, []):
        if isinstance(v, dict):
            v = v.get("id", "")
        if v.startswith("#"):
            if v[1:] not in seen:
                seen.add(v[1:])
                out += expand(v[1:], tags, seen)
        elif v:
            out.append(v)
    return out


def steps(hp, duration):
    n = hp // 2
    return [{"attribute": "minecraft:generic.max_health", "operation": "add_value", "amount": 2.0,
             "duration": round(duration * (n - k) / n)} for k in range(n)]


def main(mods_dir, neoforge_jar):
    tags, known = load(mods_dir, neoforge_jar)
    tier_of = {}
    def put(items, tier):  # later (higher) tiers win
        for i in items:
            tier_of[i] = tier
    put(T1_ITEMS, "T1")
    fd_basics = [i for tg in tags if tg.startswith("c:foods/cooked_") for i in expand(tg, tags)
                 if i.startswith("farmersdelight:")]
    put(fd_basics + [i for t in T2_TAGS for i in expand(t, tags)], "T2")
    put([i for t in T3_TAGS for i in expand(t, tags)]
        + [i for i in expand("c:foods", tags) if i.split(":")[0] in T3_NAMESPACES], "T3")
    put([i for t in T4_TAGS for i in expand(t, tags)] + T4_ITEMS, "T4")
    entries, dropped = [], []
    for item, tier in sorted(tier_of.items(), key=lambda kv: (kv[1], kv[0])):
        if (item not in known and not item.startswith("minecraft:")) or (EXCLUDE.search(item.split(":")[1]) and item not in FEAST_BLOCK_OK):
            dropped.append(item)
            continue
        entries.append({"item": item, "attributes": steps(*TIERS[tier])})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(entries, indent=2) + "\n", encoding="utf-8", newline="\n")
    for t in TIERS:
        names = [e["item"] for e in entries if tier_of[e["item"]] == t]
        print(f"{t}: {len(names)}  {', '.join(names)}\n")
    print("dropped (unknown id, raw/feast block, or harmful):", ", ".join(sorted(dropped)) or "none")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
