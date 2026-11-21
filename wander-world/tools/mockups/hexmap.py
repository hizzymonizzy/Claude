"""
Design exercise: the hex board, top-down (the isometric diorama was rejected, ADR 0004), in the map's current
0 A.D. look. One invented scene (coast, woods, a field, a street grid, a woodland trail, two settlements), drawn
three ways side by side:

  A  Hex fog       today's map, but fog clears cell by cell and cells grow lusher with familiarity
  B  Hex board     every explored cell is one terrain tile; settlements spread onto neighbouring cells
  C  Region board  zoomed out: big cells by dominant terrain, settlements and worn links between them

Textures are the app's own (0 A.D., CC BY-SA 3.0, see docs/art/0AD_ASSETS.md). Nothing here is a real place.
Run:  python3 -I tools/mockups/hexmap.py   → docs/art/mockup_hex.png
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res/drawable-nodpi"
OUT = ROOT / "docs/art/mockup_hex.png"
PW, PH = 600, 900
PARCH = (239, 230, 207)
INK = (91, 70, 54)
SQ3 = math.sqrt(3)


# ---------- the invented scene (panel pixel coordinates) ----------
def coast_x(y):
    return 480 + 34 * math.sin(y / 90.0)


def terrain(x, y):
    cx = coast_x(y)
    if x > cx:
        return "water"
    if x > cx - 22:
        return "sand"
    if ((x - 170) / 125) ** 2 + ((y - 250) / 95) ** 2 < 1:
        return "forest"
    if 60 < x < 230 and 610 < y < 780:
        return "farm"
    return "grass"


def wiggle(p0, p1, n=24, amp=0.0, f=3.0):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = p0[0] + (p1[0] - p0[0]) * t
        y = p0[1] + (p1[1] - p0[1]) * t
        nx, ny = -(p1[1] - p0[1]), p1[0] - p0[0]
        L = math.hypot(nx, ny) or 1
        o = amp * math.sin(t * math.pi * f)
        pts.append((x + nx / L * o, y + ny / L * o))
    return pts


# (polyline, wear level 0=unwalked, 1..5)
ROADS = [
    ([(0, 470), (600, 470)], 4),                 # main street, well worn
    ([(0, 560), (470, 560)], 0),
    ([(250, 330), (250, 900)], 2),
    ([(340, 330), (340, 900)], 5),               # avenue to home
    ([(420, 380), (420, 900)], 0),
    ([(150, 470), (150, 900)], 0),
    ([(250, 650), (470, 650)], 1),
    ([(0, 380), (470, 380)], 0),
    (wiggle((250, 330), (60, 140), amp=28), 2),  # woodland trail
    (wiggle((60, 140), (300, 120), amp=-18), 1),
    ([(340, 470), (coast_x(700) - 10, 700)], 1),  # down to the beach
]
SETTLEMENTS = [((340, 470), 3, True, "Oakdale"), ((210, 300), 1, False, "Elm Hollow")]


def seg_dist(px, py, poly):
    a = np.array(poly[:-1], float); b = np.array(poly[1:], float)
    ab = b - a; ap = np.stack([px - a[:, 0], py - a[:, 1]], 1)
    t = np.clip((ap * ab).sum(1) / np.maximum((ab * ab).sum(1), 1e-9), 0, 1)
    d = np.hypot(ap[:, 0] - t * ab[:, 0], ap[:, 1] - t * ab[:, 1])
    return d.min()


def familiarity(x, y):
    """Sum of wear of walked roads near the point (0 = never walked near)."""
    f = 0.0
    for poly, lvl in ROADS:
        if lvl == 0:
            continue
        d = seg_dist(x, y, poly)
        reach = 22 + 9 * lvl
        if d < reach:
            f += lvl * (1 - d / reach) + 0.6
    for (sx, sy), tier, _, _ in SETTLEMENTS:
        if math.hypot(x - sx, y - sy) < 30 + 18 * tier:
            f += 3
    return f


# ---------- drawing helpers ----------
def tex(name, size=(PW, PH), scale=128):
    t = Image.open(RES / f"{name}.png").convert("RGB").resize((scale, scale), Image.LANCZOS)
    out = Image.new("RGB", size)
    for x in range(0, size[0], scale):
        for y in range(0, size[1], scale):
            out.paste(t, (x, y))
    return out


TEX = {}


def T(name):
    if name not in TEX:
        TEX[name] = tex(name)
    return TEX[name]


TERRAIN_TEX = {"grass": "oad_ground_grass", "forest": "oad_ground_forest", "sand": "oad_ground_sand",
               "farm": "oad_ground_farm", "water": "oad_water_sea", "town": "oad_road_cobble", "meadow": "oad_ground_meadow"}


def hex_corners(cx, cy, r):
    return [(cx + r * math.cos(math.radians(60 * i - 30)), cy + r * math.sin(math.radians(60 * i - 30))) for i in range(6)]


def hex_centers(r, pad=1):
    w, h = SQ3 * r, 1.5 * r
    out = []
    row = -pad
    while row * h < PH + h * pad:
        off = (w / 2) if row % 2 else 0
        col = -pad
        while col * w < PW + w * pad:
            out.append((col * w + off, row * h))
            col += 1
        row += 1
    return out


def icon(name, h):
    im = Image.open(RES / f"{name}.png").convert("RGBA")
    return im.resize((int(h * im.width / im.height), h), Image.LANCZOS)


def put(base, im, x, y):
    base.alpha_composite(im, (int(x - im.width / 2), int(y - im.height / 2)))


def base_parchment(streets=True):
    im = Image.new("RGBA", (PW, PH), PARCH + (255,))
    d = ImageDraw.Draw(im)
    for y in range(PH):
        cx = coast_x(y)
        d.line([(cx, y), (PW, y)], fill=(196, 214, 216))
    if streets:
        for poly, _ in ROADS:
            d.line(poly, fill=(226, 214, 186), width=5)
    return im


def draw_roads(im, explored_mask=None, wide=1.0):
    d = ImageDraw.Draw(im)
    style = {1: ((216, 196, 150), 4), 2: ((170, 134, 88), 5), 3: ((150, 112, 70), 6), 4: ((140, 136, 128), 8), 5: ((128, 124, 118), 10)}
    for poly, lvl in sorted(ROADS, key=lambda r: r[1]):
        if lvl == 0:
            # unwalked: dashed, only on explored ground
            for (x0, y0), (x1, y1) in zip(poly, poly[1:]):
                L = math.hypot(x1 - x0, y1 - y0); n = int(L / 6)
                for i in range(0, n, 2):
                    a = (x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n)
                    b = (x0 + (x1 - x0) * (i + 1) / n, y0 + (y1 - y0) * (i + 1) / n)
                    if explored_mask is None or explored_mask.getpixel((int(min(max(a[0], 0), PW - 1)), int(min(max(a[1], 0), PH - 1)))) > 0:
                        d.line([a, b], fill=(60, 48, 34), width=int(4 * wide))
                        d.line([a, b], fill=(236, 222, 186), width=int(2 * wide))
            continue
        col, w = style[lvl]
        d.line(poly, fill=(70, 56, 40), width=int((w + 3) * wide), joint="curve")
        d.line(poly, fill=col, width=int(w * wide), joint="curve")
        if lvl == 5:  # avenue trees
            for (x0, y0), (x1, y1) in zip(poly, poly[1:]):
                L = math.hypot(x1 - x0, y1 - y0)
                for k in range(0, int(L), 26):
                    x = x0 + (x1 - x0) * k / L; y = y0 + (y1 - y0) * k / L
                    nx, ny = -(y1 - y0) / L, (x1 - x0) / L
                    for s in (-1, 1):
                        d.ellipse([x + s * nx * 11 - 4, y + s * ny * 11 - 4, x + s * nx * 11 + 4, y + s * ny * 11 + 4], fill=(62, 96, 48))


def draw_settlements(im, scale=1.0):
    for (x, y), tier, cap, name in SETTLEMENTS:
        put(im, icon(f"oad_medal_tier_{tier}", int(44 * scale)), x, y)
        if cap:
            put(im, icon("badge_capital", int(18 * scale)), x + 18 * scale, y - 18 * scale)
        label(im, name, x, y + 30 * scale)


FONT = ImageFont.load_default(size=15)


def label(im, text, x, y, font=FONT):
    d = ImageDraw.Draw(im)
    w = d.textlength(text, font=font)
    d.text((x - w / 2, y), text, font=font, fill=INK, stroke_width=3, stroke_fill=(244, 236, 216))


# ---------- panel A: hex fog ----------
def panel_a():
    r = 15
    im = base_parchment()
    mask = Image.new("L", (PW, PH), 0); md = ImageDraw.Draw(mask)
    lush = Image.new("L", (PW, PH), 0); ld = ImageDraw.Draw(lush)
    cells = []
    for cx, cy in hex_centers(r):
        f = familiarity(cx, cy)
        if f > 0:
            poly = hex_corners(cx, cy, r + 0.6)
            md.polygon(poly, fill=255)
            ld.polygon(poly, fill=int(min(255, 70 + f * 28)))
            cells.append((cx, cy, f))
    ground = Image.new("RGB", (PW, PH))
    for y in range(0, PH, 2):
        pass
    # terrain under explored cells (current look: grass, woods, sand; water stays water)
    terr = Image.new("RGB", (PW, PH))
    tm = {k: Image.new("L", (PW, PH), 0) for k in TERRAIN_TEX}
    for cx, cy, f in cells:
        ImageDraw.Draw(tm[terrain(cx, cy)]).polygon(hex_corners(cx, cy, r + 0.6), fill=255)
    for k, m in tm.items():
        terr.paste(T(TERRAIN_TEX[k]), (0, 0), m)
    # familiarity: dull → lush
    dull = ImageEnhance.Color(ImageEnhance.Brightness(terr).enhance(1.18)).enhance(0.45)
    terr = Image.composite(terr, dull, lush)
    im.paste(terr, (0, 0), mask)
    d = ImageDraw.Draw(im, "RGBA")
    for cx, cy, f in cells:
        d.polygon(hex_corners(cx, cy, r), outline=(40, 60, 30, 55))
    # peek ring: the cells next to explored ones show a faint outline in the fog
    for cx, cy in hex_centers(r):
        if familiarity(cx, cy) == 0 and any(math.hypot(cx - a, cy - b) < 2 * r for a, b, _ in cells):
            d.polygon(hex_corners(cx, cy, r), outline=(140, 120, 90, 90))
    draw_roads(im, mask)
    draw_settlements(im)
    put(im, icon("sprite_wanderer", 40), 300, 650 - 10)
    return im


# ---------- panel B: hex board ----------
def panel_b():
    r = 22
    im = base_parchment(streets=False)
    d = ImageDraw.Draw(im, "RGBA")
    mask = Image.new("L", (PW, PH), 0)
    tm = {k: Image.new("L", (PW, PH), 0) for k in TERRAIN_TEX}
    cells = []
    for cx, cy in hex_centers(r):
        f = familiarity(cx, cy)
        kind = terrain(cx, cy)
        town = None
        for (sx, sy), tier, _, _ in SETTLEMENTS:
            ring = math.hypot(cx - sx, cy - sy) / (SQ3 * r)
            if ring < 0.6 or (ring < 1.6 and tier >= 3 and kind != "water"):
                town = "town"
            elif ring < 2.6 and tier >= 3 and kind not in ("water", "sand"):
                town = "meadow"
        if f > 0 or town:
            k = town or kind
            ImageDraw.Draw(tm[k]).polygon(hex_corners(cx, cy, r - 1.2), fill=255)
            ImageDraw.Draw(mask).polygon(hex_corners(cx, cy, r), fill=255)
            cells.append((cx, cy, k, f))
    tiles = Image.new("RGB", (PW, PH), (88, 74, 52))
    for k, m in tm.items():
        tiles.paste(T(TERRAIN_TEX[k]), (0, 0), m)
    im.paste(tiles, (0, 0), mask)
    # fog cells: parchment tiles with a dotted rim
    for cx, cy in hex_centers(r):
        if familiarity(cx, cy) == 0:
            d.polygon(hex_corners(cx, cy, r - 1.5), outline=(190, 172, 140, 120))
    # props: trees on forest tiles, crops on farm tiles, buildings ring the town
    tree = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); td = ImageDraw.Draw(tree)
    td.ellipse([1, 1, 15, 15], fill=(48, 84, 40, 255), outline=(30, 52, 26, 255))
    for cx, cy, k, f in cells:
        if k == "forest":
            for a in range(3):
                put(im, tree, cx + 8 * math.cos(a * 2.1), cy + 8 * math.sin(a * 2.1))
    blds = ["bldg_campfire", "bldg_well", "oad_medal_bldg_market", "oad_medal_bldg_inn", "oad_medal_bldg_meeting_hall"]
    (sx, sy) = SETTLEMENTS[0][0]
    for i, b in enumerate(blds):
        a = math.radians(30 + 60 * i)
        put(im, icon(b, 34), sx + SQ3 * r * math.cos(a), sy + SQ3 * r * math.sin(a))
    draw_roads(im, mask, wide=0.8)
    draw_settlements(im)
    put(im, icon("sprite_trader", 34), SETTLEMENTS[1][0][0] + 34, SETTLEMENTS[1][0][1] + 4)
    return im


# ---------- panel C: region board (zoomed out) ----------
def panel_c():
    r = 48
    im = base_parchment(streets=False)
    tm = {k: Image.new("L", (PW, PH), 0) for k in TERRAIN_TEX}
    cells = []
    for cx, cy in hex_centers(r):
        # dominant terrain over a few samples, explored if any sample is familiar
        samples = [(cx + dx, cy + dy) for dx in (-20, 0, 20) for dy in (-20, 0, 20)]
        kinds = [terrain(x, y) for x, y in samples]
        k = max(set(kinds), key=kinds.count)
        fam = sum(familiarity(x, y) for x, y in samples) / len(samples)
        for (sx, sy), tier, _, _ in SETTLEMENTS:
            if math.hypot(cx - sx, cy - sy) < r:
                k = "town"
        cells.append((cx, cy, k, fam))
        ImageDraw.Draw(tm[k]).polygon(hex_corners(cx, cy, r - 2), fill=255)
    tiles = Image.new("RGB", (PW, PH))
    m_all = Image.new("L", (PW, PH), 0)
    for k, m in tm.items():
        tiles.paste(tex(TERRAIN_TEX[k], scale=96), (0, 0), m)
        m_all.paste(255, (0, 0), m)
    im.paste(tiles, (0, 0), m_all)
    d = ImageDraw.Draw(im, "RGBA")
    for cx, cy, k, fam in cells:
        if fam < 0.4:   # fog over cells you barely know
            d.polygon(hex_corners(cx, cy, r - 2), fill=PARCH + (205,))
        d.polygon(hex_corners(cx, cy, r - 2), outline=(60, 50, 36, 110))
    # worn links between settlement cells and the trail's end, thickness by wear
    pts = {"Oakdale": SETTLEMENTS[0][0], "Elm Hollow": SETTLEMENTS[1][0], "beach": (coast_x(700) - 10, 700), "woods": (60, 140)}
    for a, b, w in (("Oakdale", "Elm Hollow", 7), ("Elm Hollow", "woods", 5), ("Oakdale", "beach", 4)):
        d.line([pts[a], pts[b]], fill=(70, 56, 40), width=w + 3)
        d.line([pts[a], pts[b]], fill=(206, 182, 136), width=w)
    draw_settlements(im, scale=1.25)
    put(im, icon("medal_waystone", 40), 60, 140)
    label(im, "Waystone", 60, 166)
    label(im, "THE OAKDALE REACH", PW / 2, 14, ImageFont.load_default(size=22))
    return im


def main():
    panels = [("A  Hex fog", "Today's map; fog clears cell by cell,\ncells grow lusher the more you walk them", panel_a()),
              ("B  Hex board", "Each explored cell is one terrain tile;\nsettlements spread onto neighbouring cells", panel_b()),
              ("C  Region board", "Zoomed out: big cells by main terrain,\nsettlements and worn links between them", panel_c())]
    gap, top = 24, 110
    sheet = Image.new("RGB", (len(panels) * PW + (len(panels) + 1) * gap, PH + top + gap), (244, 236, 216))
    d = ImageDraw.Draw(sheet)
    head = ImageFont.load_default(size=28); small = ImageFont.load_default(size=17)
    for i, (title, sub, im) in enumerate(panels):
        x = gap + i * (PW + gap)
        d.text((x, 18), title, font=head, fill=INK)
        d.multiline_text((x, 56), sub, font=small, fill=(120, 98, 76), spacing=4)
        sheet.paste(im.convert("RGB"), (x, top))
        d.rectangle([x - 1, top - 1, x + PW, top + PH], outline=(128, 100, 70), width=2)
    sheet.save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
