"""
Design iteration 3 (hex map): four zoom tiers, hex size scaling with zoom, heat tiers within each material.

  1  Block          small hexes (~20 m), faint grid, village as paved hexes, every road and unwalked dashes
  2  Neighbourhood  medium hexes (~80 m), merged (no inner edges), walked roads only
  3  County         large hexes (~1 km), dominant ground, settlements and worn links
  4  Beyond         nodes only: regions sized by heat, linked by walks and waystones

Heat tiers: each material comes in three steps by heat (faded → normal → rich); a step change shows as a very
faint edge, a material change as a clear one, the fog edge strongest. Invented places only.
Run:  python3 -I tools/mockups/hextiers.py → docs/art/mockup_hex_tiers.png
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import hexmap as H  # noqa: E402
from PIL import Image, ImageDraw, ImageEnhance, ImageFont  # noqa: E402

OUT = H.ROOT / "docs/art/mockup_hex_tiers.png"
W, HH = H.PW, H.PH
TEXV = {}


def tiered(material, tier, scale):
    """Material texture at a heat tier: 0 faded, 1 normal, 2 rich."""
    key = (material, tier, scale)
    if key not in TEXV:
        t = H.tex(H.TERRAIN_TEX[material], (W, HH), scale=scale)
        if material != "water":
            if tier == 0:
                t = ImageEnhance.Color(ImageEnhance.Brightness(t).enhance(1.22)).enhance(0.4)
            elif tier == 2:
                t = ImageEnhance.Contrast(ImageEnhance.Brightness(ImageEnhance.Color(t).enhance(1.35)).enhance(0.84)).enhance(1.12)
        TEXV[key] = t
    return TEXV[key]


def heat_tier(f):
    return 0 if f < 1.6 else 1 if f < 4.5 else 2


def grid(r):
    w, h = H.SQ3 * r, 1.5 * r
    for row in range(-2, int(HH / h) + 3):
        for col in range(-2, int(W / w) + 3):
            yield row, col, col * w + (w / 2 if row % 2 else 0), row * h


def nbrs(row, col):
    odd = row % 2
    return [(row, col - 1), (row, col + 1), (row - 1, col - 1 + odd), (row - 1, col + odd), (row + 1, col - 1 + odd), (row + 1, col + odd)]


SIDE = {0: (3, 4), 1: (0, 1), 2: (4, 5), 3: (5, 0), 4: (2, 3), 5: (1, 2)}


def hex_layer(im, r, sample, faint, texscale=128):
    """sample(cx, cy) -> (material, tier) or None (fog). Draws tiles and edges onto im (RGBA); returns explored mask."""
    cells = {}
    for row, col, cx, cy in grid(r):
        s = sample(cx, cy)
        if s:
            cells[(row, col)] = (cx, cy) + s
    masks = {}
    for cx, cy, m, t in cells.values():
        k = (m, t)
        if k not in masks:
            masks[k] = Image.new("L", (W, HH), 0)
        ImageDraw.Draw(masks[k]).polygon(H.hex_corners(cx, cy, r + 0.7), fill=255)
    allm = Image.new("L", (W, HH), 0)
    for (m, t), mk in masks.items():
        im.paste(tiered(m, t, texscale), (0, 0), mk)
        allm.paste(255, (0, 0), mk)
    rgb = im.convert("RGB")
    d = ImageDraw.Draw(rgb, "RGBA")
    lw = max(1, int(r / 9))
    for (row, col), (cx, cy, m, t) in cells.items():
        cs = H.hex_corners(cx, cy, r)
        for i, nb in enumerate(nbrs(row, col)):
            o = cells.get(nb)
            seg = [cs[SIDE[i][0]], cs[SIDE[i][1]]]
            if o is None:
                d.line(seg, fill=(92, 74, 50, 220), width=lw + 1)
            elif o[2] != m:
                d.line(seg, fill=(60, 48, 34, 150), width=lw)
            elif o[3] != t:
                d.line(seg, fill=(40, 50, 26, 55), width=1)
            elif faint:
                d.line(seg, fill=(30, 40, 20, 26), width=1)
    im.paste(rgb.convert("RGBA"))
    return allm, cells


# ---------- 1 Block ----------
def town_material(cx, cy):
    for (sx, sy), tier, _, _ in H.SETTLEMENTS:
        d = math.hypot(cx - sx, cy - sy)
        if d < 14 + 9 * tier:
            return ("town", 2 if d < 12 else 1 if d < 26 else 0)
    return None


def neighbourhood_sample(cx, cy):
    t = town_material(cx, cy)
    if t:
        return t
    f = H.familiarity(cx, cy)
    return (H.terrain(cx, cy), heat_tier(f)) if f > 0 else None


def panel_block():
    zoom, focus, r = 3.2, (330, 500), 7.0

    def to_scene(x, y):
        return ((x - W / 2) / zoom + focus[0], (y - HH / 2) / zoom + focus[1])

    def to_screen(x, y):
        return ((x - focus[0]) * zoom + W / 2, (y - focus[1]) * zoom + HH / 2)

    im = Image.new("RGBA", (W, HH), H.PARCH + (255,))
    allm, _ = hex_layer(im, r * zoom, lambda x, y: neighbourhood_sample(*to_scene(x, y)), faint=True, texscale=int(128 * zoom * 0.7))
    saved = H.ROADS
    H.ROADS = [([to_screen(*p) for p in poly], lvl) for poly, lvl in saved]
    H.draw_roads(im, allm, wide=zoom * 0.75)
    H.ROADS = saved
    (sx, sy) = H.SETTLEMENTS[0][0]
    X, Y = to_screen(sx, sy)
    for i, b in enumerate(["bldg_campfire", "bldg_well", "oad_medal_bldg_market", "oad_medal_bldg_inn"]):
        a = math.radians(205 + 62 * i)
        H.put(im, H.icon(b, 46), X + 105 * math.cos(a), Y + 105 * math.sin(a))
    H.put(im, H.icon("oad_medal_tier_3", 76), X, Y)
    H.put(im, H.icon("badge_capital", 30), X + 30, Y - 30)
    H.label(im, "Oakdale", X, Y + 46, ImageFont.load_default(size=20))
    H.put(im, H.icon("sprite_wanderer", 64), *to_screen(300, 530))
    H.put(im, H.icon("sprite_trader", 56), *to_screen(372, 520))
    return im


# ---------- 2 Neighbourhood ----------
def panel_neighbourhood():
    im = H.base_parchment(streets=False)
    allm, _ = hex_layer(im, 11.0, neighbourhood_sample, faint=False)
    saved = H.ROADS
    H.ROADS = [(p, l) for p, l in saved if l > 0]
    H.draw_roads(im, allm, wide=0.75)
    H.ROADS = saved
    H.draw_settlements(im)
    return im


# ---------- 3 County (a wider invented world around the same home) ----------
HOT = [  # (x, y, strength, radius, name, tier, capital)
    (300, 560, 9, 80, "Oakdale", 3, True),
    (255, 500, 4, 40, "Elm Hollow", 1, False),
    (140, 300, 6, 60, "Brookfield", 2, False),
    (410, 250, 3, 40, "Mill Run", 1, False),
    (470, 720, 3, 45, None, 0, False),
    (110, 760, 2, 35, None, 0, False),
]
LINKS = [((300, 560), (255, 500), 5), ((255, 500), (140, 300), 4), ((300, 560), (410, 250), 2), ((300, 560), (470, 720), 3), ((140, 300), (110, 760), 1)]


def county_coast(y):
    return 500 + 50 * math.sin(y / 140.0)


def county_terrain(x, y):
    if x > county_coast(y):
        return "water"
    if x > county_coast(y) - 26:
        return "sand"
    if ((x - 150) / 110) ** 2 + ((y - 180) / 90) ** 2 < 1 or ((x - 330) / 70) ** 2 + ((y - 380) / 60) ** 2 < 1:
        return "forest"
    if x < 260 and y > 620:
        return "farm"
    return "grass"


def county_heat(x, y):
    f = 0.0
    for hx, hy, s, rad, *_ in HOT:
        d = math.hypot(x - hx, y - hy)
        if d < rad:
            f += s * (1 - d / rad) + 0.6
    for a, b, lvl in LINKS:
        d = H.seg_dist(x, y, [a, b])
        if d < 16:
            f += lvl * 0.5 + 0.7
    return f


def panel_county():
    im = Image.new("RGBA", (W, HH), H.PARCH + (255,))
    d = ImageDraw.Draw(im)
    for y in range(HH):
        d.line([(county_coast(y), y), (W, y)], fill=(196, 214, 216))

    def sample(cx, cy):
        for hx, hy, s, rad, name, tier, cap in HOT:
            if name and math.hypot(cx - hx, cy - hy) < 14 + 6 * tier:
                return ("town", min(2, tier))
        f = county_heat(cx, cy)
        return (county_terrain(cx, cy), heat_tier(f)) if f > 0 else None

    allm, _ = hex_layer(im, 26.0, sample, faint=False, texscale=96)
    d = ImageDraw.Draw(im)
    for a, b, lvl in LINKS:
        d.line([a, b], fill=(70, 56, 40), width=2 + lvl + 2)
        d.line([a, b], fill=(206, 182, 136), width=2 + lvl)
    for hx, hy, s, rad, name, tier, cap in HOT:
        if name:
            H.put(im, H.icon(f"oad_medal_tier_{tier}", 40), hx, hy)
            if cap:
                H.put(im, H.icon("badge_capital", 16), hx + 16, hy - 16)
            H.label(im, name, hx, hy + 24)
    H.put(im, H.icon("medal_waystone", 34), 110, 760)
    H.label(im, "Waystone", 110, 780)
    H.label(im, "THE OAKDALE REACH", W / 2, 16, ImageFont.load_default(size=22))
    return im


# ---------- 4 Beyond: nodes ----------
REGIONS = [  # name, x, y, heat (cells), material, tier icon, capital
    ("The Oakdale Reach", 300, 470, 1240, "grass", 3, True),
    ("Pine Hollow", 130, 760, 310, "forest", 2, False),
    ("Gull Island", 500, 300, 140, "sand", 1, False),
    ("Northfield", 260, 210, 90, "farm", 1, False),
    ("Stonebridge", 470, 790, 40, "town", 0, False),
]
ROUTES = [(0, 1, "walk"), (0, 2, "paddle"), (0, 3, "waystone"), (0, 4, "waystone")]


def panel_nodes():
    im = Image.new("RGBA", (W, HH), (232, 222, 196, 255))
    d = ImageDraw.Draw(im, "RGBA")
    # faint sea wash to the east, compass
    for y in range(HH):
        d.line([(560 + 40 * math.sin(y / 160), y), (W, y)], fill=(205, 218, 214, 255))
    for a, b, kind in ROUTES:
        A, B = REGIONS[a], REGIONS[b]
        p0, p1 = (A[1], A[2]), (B[1], B[2])
        if kind == "waystone":   # an arc of light
            mx, my = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
            nx, ny = -(p1[1] - p0[1]), p1[0] - p0[0]
            L = math.hypot(nx, ny)
            c = (mx + nx / L * 60, my + ny / L * 60)
            pts = [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]) for t in [i / 40 for i in range(41)]]
            for i in range(0, 40, 2):
                d.line([pts[i], pts[i + 1]], fill=(90, 130, 190, 220), width=4)
        elif kind == "paddle":
            d.line([p0, p1], fill=(70, 120, 160, 200), width=4)
        else:
            d.line([p0, p1], fill=(70, 56, 40, 255), width=8)
            d.line([p0, p1], fill=(206, 182, 136, 255), width=5)
    for name, x, y, heat, mat, tier, cap in REGIONS:
        rad = 22 + 10 * math.log1p(heat / 20)
        disc = Image.new("L", (W, HH), 0)
        ImageDraw.Draw(disc).polygon(H.hex_corners(x, y, rad), fill=255)
        im.paste(tiered(mat, 2 if heat > 500 else 1, 96), (0, 0), disc)
        d.polygon(H.hex_corners(x, y, rad), outline=(70, 56, 40, 255), width=3)
        H.put(im, H.icon(f"oad_medal_tier_{tier}", int(rad * 0.9)), x, y)
        if cap:
            H.put(im, H.icon("badge_capital", 20), x + rad * 0.45, y - rad * 0.45)
        H.label(im, name, x, y + rad + 4)
        H.label(im, f"{heat} hexes", x, y + rad + 22, ImageFont.load_default(size=13))
    H.label(im, "Your world", W / 2, 16, ImageFont.load_default(size=22))
    leg = ImageFont.load_default(size=14)
    ly = 60
    d.line([(16, ly + 8), (46, ly + 8)], fill=(70, 56, 40, 255), width=7); d.line([(16, ly + 8), (46, ly + 8)], fill=(206, 182, 136, 255), width=4)
    d.text((54, ly), "walked", font=leg, fill=H.INK)
    d.line([(16, ly + 30), (46, ly + 30)], fill=(70, 120, 160, 220), width=4)
    d.text((54, ly + 22), "paddled", font=leg, fill=H.INK)
    for k in range(0, 30, 10):
        d.line([(16 + k, ly + 52), (21 + k, ly + 52)], fill=(90, 130, 190, 220), width=4)
    d.text((54, ly + 44), "waystone link", font=leg, fill=H.INK)
    d.text((16, ly + 66), "Size = heat (hexes you know)", font=leg, fill=H.INK)
    return im


def main():
    panels = [("1  Block", "~20 m hexes, faint grid; village as paved\nhexes; every road, unwalked dashes", panel_block()),
              ("2  Neighbourhood", "~80 m hexes merged; heat tiers show as\nfaded-to-rich ground; walked roads only", panel_neighbourhood()),
              ("3  County", "~1 km hexes by main ground and heat;\nsettlements and worn links", panel_county()),
              ("4  Beyond", "Nodes only: each region sized by heat,\nlinked by walks, paddles and waystones", panel_nodes())]
    gap, top = 24, 110
    sheet = Image.new("RGB", (len(panels) * W + (len(panels) + 1) * gap, HH + top + 70), (244, 236, 216))
    d = ImageDraw.Draw(sheet)
    head = ImageFont.load_default(size=28); small = ImageFont.load_default(size=17)
    for i, (title, sub, im) in enumerate(panels):
        x = gap + i * (W + gap)
        d.text((x, 18), title, font=head, fill=H.INK)
        d.multiline_text((x, 56), sub, font=small, fill=(120, 98, 76), spacing=4)
        sheet.paste(im.convert("RGB"), (x, top))
        d.rectangle([x - 1, top - 1, x + W, top + HH], outline=(128, 100, 70), width=2)
    # heat tier key
    kx, ky = gap, top + HH + 18
    d.text((kx, ky + 8), "Heat tiers:", font=small, fill=H.INK)
    kx += 110
    for m in ("grass", "forest", "town"):
        for t in range(3):
            sw = tiered(m, t, 128).crop((0, 0, 44, 30))
            sheet.paste(sw, (kx, ky))
            kx += 48
        d.text((kx + 4, ky + 8), m if m != "town" else "town", font=small, fill=H.INK)
        kx += 90
    d.text((kx + 20, ky + 8), "faded, normal, rich: by how often you walk there", font=small, fill=(120, 98, 76))
    sheet.save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
