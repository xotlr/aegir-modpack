#!/usr/bin/env python3
"""Top-down map of a generated world, straight from its region files (no game needed).

  python -I tools/render_map.py <world/region dir> <out.png> [scale]

Land is shaded by height, water by depth (OCEAN_FLOOR vs WORLD_SURFACE heightmaps); structure
starts are drawn as white dots and listed. Also prints storage per region file and per 512x512 area,
and the water/land ratio, for sizing the finite world (spec section 8).
"""
import collections, gzip, io, math, os, pathlib, struct, sys, zlib

# ---- minimal NBT reader -------------------------------------------------------------------------
def nbt(buf):
    f = io.BytesIO(buf)
    def r(n): return f.read(n)
    def payload(t):
        if t == 1: return struct.unpack(">b", r(1))[0]
        if t == 2: return struct.unpack(">h", r(2))[0]
        if t == 3: return struct.unpack(">i", r(4))[0]
        if t == 4: return struct.unpack(">q", r(8))[0]
        if t == 5: return struct.unpack(">f", r(4))[0]
        if t == 6: return struct.unpack(">d", r(8))[0]
        if t == 7: n = struct.unpack(">i", r(4))[0]; return r(n)
        if t == 8: n = struct.unpack(">H", r(2))[0]; return r(n).decode("utf-8", "replace")
        if t == 9:
            et, n = struct.unpack(">bi", r(5)); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                et = r(1)[0]
                if et == 0: return d
                n = struct.unpack(">H", r(2))[0]; name = r(n).decode("utf-8", "replace")
                d[name] = payload(et)
        if t == 11: n = struct.unpack(">i", r(4))[0]; return struct.unpack(f">{n}i", r(4 * n))
        if t == 12: n = struct.unpack(">i", r(4))[0]; return struct.unpack(f">{n}q", r(8 * n))
        raise ValueError(t)
    t = r(1)[0]; n = struct.unpack(">H", r(2))[0]; r(n)
    return payload(t)


def chunks(region):
    data = region.read_bytes()
    for i in range(1024):
        off = int.from_bytes(data[i * 4:i * 4 + 3], "big") * 4096
        if not off: continue
        length = int.from_bytes(data[off:off + 4], "big")
        comp = data[off + 4]
        raw = data[off + 5:off + 4 + length]
        yield nbt(zlib.decompress(raw) if comp == 2 else gzip.decompress(raw) if comp == 1 else raw)


def heightmap(longs, bits=9):
    per = 64 // bits; mask = (1 << bits) - 1; out = []
    for v in longs:
        v &= (1 << 64) - 1
        for k in range(per):
            out.append((v >> (k * bits)) & mask)
            if len(out) == 256: return out
    return out


# ---- PNG writer -----------------------------------------------------------------------------------
def png(path, w, h, rows):
    def chunk(tag, body): return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body))
    raw = b"".join(b"\0" + bytes(r) for r in rows)
    pathlib.Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
                                   + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def colour(surface, floor, sea=63, min_y=-64):
    s, fl = surface + min_y, floor + min_y
    depth = s - fl
    if depth > 0 and s <= sea + 1:  # water column
        t = min(depth, 110) / 110
        return (int(70 - 55 * t), int(130 - 95 * t), int(220 - 120 * t))
    t = max(0, min(s - sea, 200)) / 200  # land: green -> brown -> white
    if t < 0.35: k = t / 0.35; return (int(60 + 80 * k), int(150 - 20 * k), int(60 - 10 * k))
    if t < 0.7: k = (t - 0.35) / 0.35; return (int(140 - 20 * k), int(130 - 40 * k), int(50 + 30 * k))
    k = (t - 0.7) / 0.3; return (int(120 + 135 * k), int(90 + 165 * k), int(80 + 175 * k))


def main(region_dir, out, scale=1):
    regions = sorted(pathlib.Path(region_dir).glob("r.*.mca"))
    cols, starts, xs, zs = {}, collections.Counter(), [], []
    struct_pos = []
    for reg in regions:
        for c in chunks(reg):
            if c.get("Status") not in ("minecraft:full", "full"): continue
            cx, cz = c["xPos"], c["zPos"]; xs.append(cx); zs.append(cz)
            hm = c.get("Heightmaps", {})
            if "WORLD_SURFACE" not in hm or "OCEAN_FLOOR" not in hm: continue
            cols[(cx, cz)] = (heightmap(hm["WORLD_SURFACE"]), heightmap(hm["OCEAN_FLOOR"]))
            for name, s in c.get("structures", {}).get("starts", {}).items():
                if s.get("id", "INVALID") != "INVALID":
                    starts[name] += 1; struct_pos.append((cx * 16 + 8, cz * 16 + 8, name))
    if not cols: sys.exit("no full chunks")
    x0, x1, z0, z1 = min(xs), max(xs) + 1, min(zs), max(zs) + 1
    W, H = (x1 - x0) * 16 // scale, (z1 - z0) * 16 // scale
    img = [[0] * (W * 3) for _ in range(H)]
    water = land = 0; deepest = 999
    for (cx, cz), (ws, of) in cols.items():
        for i in range(256):
            bx, bz = i % 16, i // 16
            px, pz = ((cx - x0) * 16 + bx) // scale, ((cz - z0) * 16 + bz) // scale
            r, g, b = colour(ws[i], of[i])
            if ws[i] - of[i] > 0 and ws[i] - 64 <= 64: water += 1; deepest = min(deepest, of[i] - 64)
            else: land += 1
            if (bx % scale == 0) and (bz % scale == 0):
                img[pz][px * 3:px * 3 + 3] = [r, g, b]
    for x, z, _ in struct_pos:
        px, pz = (x - x0 * 16) // scale, (z - z0 * 16) // scale
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if 0 <= px + dx < W and 0 <= pz + dz < H: img[pz + dz][(px + dx) * 3:(px + dx) * 3 + 3] = [255, 255, 255]
    png(out, W, H, img)
    size = sum(r.stat().st_size for r in regions)
    area = len(cols) * 256
    print(f"map {W}x{H} px ({scale} block/px) -> {out}")
    print(f"chunks {len(cols)}  region files {len(regions)}  {size / 2**20:.1f} MiB  = {size / area * 512 * 512 / 2**20:.1f} MiB per 512x512 blocks")
    print(f"water {100 * water / (water + land):.0f}%  deepest ocean floor y={deepest}")
    print("structure starts:", ", ".join(f"{n} x{k}" for n, k in starts.most_common()))


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4): sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) == 4 else 1)
