"""
Design iteration 4 (hex map), from the player's notes on iteration 3:
  - Roads and paths at Block and Neighbourhood are drawn exactly as the app draws them (real curves, 0 A.D. dirt and
    cobble textures, edges, tree-lined avenues, light dashed unwalked streets) and take no part in the hex layout.
  - County: a settlement covers more hexes as its tier rises.
  - Beyond: region nodes are sized by their best settlement's tier.
  - Heat has five steps on the road stages' rule: 1, 3, 10, 30, 100 distinct days.
Invented places only. Run:  python3 -I tools/mockups/hextiers2.py → docs/art/mockup_hex_tiers2.png
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import hexmap as H  # noqa: E402
import hextiers as T  # noqa: E402
from PIL import Image, ImageDraw, ImageEnhance, ImageFont  # noqa: E402

OUT = H.ROOT / "docs/art/mockup_hex_tiers2.png"
W, HH = T.W, T.HH


# ---------- a street network that looks like a real one (curves, crescents, a cul-de-sac, winding trails) ----------
def curve(points, n=40):
    """Catmull-Rom through the given points."""
    pts = [points[0]] + points + [points[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for k in range(n):
            t = k / n
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t ** 3) for j in (0, 1)))
    out.append(points[-1])
    return out


ROADS = [
    (curve([(330, 300), (338, 420), (340, 470), (348, 640), (342, 900)]), 5),           # avenue toward home
    (curve([(0, 482), (120, 474), (240, 468), (340, 470), (420, 476), (452, 488)]), 4),  # main street to the shore
    (curve([(246, 330), (252, 470), (258, 600), (250, 760), (256, 900)]), 3),           # lane
    (curve([(250, 330), (200, 290), (150, 300), (110, 240), (70, 180), (60, 140)]), 2),  # woodland footpath
    (curve([(60, 140), (130, 115), (190, 140), (250, 110), (300, 120)]), 1),            # trail on through the woods
    (curve([(340, 470), (390, 560), (430, 640), (470, 700), (coast_x := H.coast_x(712) - 14, 712)]), 1),  # to the beach
    (curve([(258, 650), (320, 655), (348, 650), (400, 640), (440, 660)]), 1),
    # unwalked streets
    (curve([(150, 474), (130, 560), (150, 650), (130, 760), (150, 900)]), 0),
    (curve([(0, 390), (110, 380), (246, 384), (338, 380), (440, 392)]), 0),
    (curve([(0, 565), (120, 560), (252, 566)]), 0),
    (curve([(342, 560), (392, 566), (420, 590), (418, 615)]), 0),                        # cul-de-sac
    (curve([(252, 760), (300, 775), (346, 770)]), 0),
    (curve([(150, 300), (170, 230), (150, 170)]), 0),                                     # unwalked woodland trail
    (curve([(338, 380), (360, 330), (380, 260), (420, 230)]), 0),
]
H.ROADS = ROADS


def tex_scaled(name, scale):
    return H.tex(name, (W, HH), scale=max(16, int(scale)))


def draw_roads(im, explored_mask=None, wide=1.0):
    """The app's road look (MapStyle): trail = sand line with a soft edge; 2-3 dirt (3 with an edge); 4-5 cobbles with
    an edge; 5 tree-lined; unwalked = light dashes with a dark edge, only on explored ground."""
    rgb = im.convert("RGB")
    d = ImageDraw.Draw(rgb, "RGBA")
    k = wide * 1.25
    for poly, lvl in H.ROADS:
        if lvl != 0:
            continue
        acc = 0.0
        for a, b in zip(poly, poly[1:]):
            L = math.hypot(b[0] - a[0], b[1] - a[1])
            inside = explored_mask is None or explored_mask.getpixel((int(min(max(a[0], 0), W - 1)), int(min(max(a[1], 0), HH - 1)))) > 0
            if inside and (acc // (7 * k)) % 2 == 0:
                d.line([a, b], fill=(70, 56, 40, 200), width=max(2, int(4.2 * k)))
                d.line([a, b], fill=(240, 228, 196, 255), width=max(1, int(2.4 * k)))
            acc += L
    for lvl in (1, 2, 3, 4, 5):
        for poly, l in H.ROADS:
            if l != lvl:
                continue
            if lvl == 1:
                d.line(poly, fill=(156, 132, 88, 140), width=int(6 * k), joint="curve")
                d.line(poly, fill=(232, 212, 166, 255), width=max(2, int(3.5 * k)), joint="curve")
                continue
            if lvl >= 3:
                edge = {3: ((74, 58, 40, 190), 7.5), 4: ((94, 90, 82, 190), 9.5), 5: ((94, 90, 82, 190), 11)}[lvl]
                d.line(poly, fill=edge[0], width=int(edge[1] * k), joint="curve")
            width = {2: 3.5, 3: 5, 4: 6.5, 5: 8.5}[lvl]
            m = Image.new("L", (W, HH), 0)
            ImageDraw.Draw(m).line(poly, fill=255, width=max(2, int(width * k)), joint="curve")
            rgb.paste(tex_scaled("oad_road_dirt" if lvl < 4 else "oad_road_cobble", 40 * k), (0, 0), m)
            if lvl == 5:
                acc = 0.0
                for a, b in zip(poly, poly[1:]):
                    L = math.hypot(b[0] - a[0], b[1] - a[1]) or 1
                    nx, ny = -(b[1] - a[1]) / L, (b[0] - a[0]) / L
                    if int(acc / (19 * k)) != int((acc + L) / (19 * k)):
                        for s in (-1, 1):
                            x, y = a[0] + s * nx * 9 * k, a[1] + s * ny * 9 * k
                            rr = 3.2 * k
                            d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(58, 104, 50, 255), outline=(34, 64, 30, 255))
                    acc += L
    im.paste(rgb.convert("RGBA"))


H.draw_roads = draw_roads


# ---------- five heat steps, the road rule: 1, 3, 10, 30, 100 distinct days ----------
DAYS = [1, 3, 10, 30, 100]
STEP = [(1.30, 0.28, 1.0), (1.14, 0.6, 1.0), (1.0, 1.0, 1.0), (0.9, 1.25, 1.06), (0.8, 1.45, 1.14)]   # brightness, colour, contrast
TEXV = {}


def tiered(material, tier, scale):
    key = (material, tier, scale)
    if key not in TEXV:
        t = H.tex(H.TERRAIN_TEX[material], (W, HH), scale=scale)
        if material != "water":
            b, c, ct = STEP[tier]
            t = ImageEnhance.Contrast(ImageEnhance.Color(ImageEnhance.Brightness(t).enhance(b)).enhance(c)).enhance(ct)
        TEXV[key] = t
    return TEXV[key]


def heat_tier(f):
    """Mock familiarity → the five steps (stands in for distinct days walked: 1, 3, 10, 30, 100)."""
    return 0 if f < 1.2 else 1 if f < 2.4 else 2 if f < 3.8 else 3 if f < 5.5 else 4


T.tiered = tiered
T.heat_tier = heat_tier


def town_material(cx, cy):
    for (sx, sy), tier, _, _ in H.SETTLEMENTS:
        d = math.hypot(cx - sx, cy - sy)
        if d < 14 + 9 * tier:
            return ("town", 4 if d < 12 else 3 if d < 26 else 2)
    return None


T.town_material = town_material


def panel_neighbourhood():
    im = H.base_parchment(streets=False)
    allm, _ = T.hex_layer(im, 11.0, T.neighbourhood_sample, faint=False)
    draw_roads(im, allm, wide=0.8)
    H.draw_settlements(im)
    return im


# ---------- County: settlements cover more hexes as they grow ----------
T.HOT[0] = (300, 560, 9, 80, "Oakdale", 4, True)        # a town: centre + one ring
T.HOT[2] = (140, 300, 6, 60, "Brookfield", 3, False)     # a village: centre + part of a ring
TOWN_REACH = {0: 0, 1: 0.5, 2: 0.5, 3: 1.05, 4: 1.6, 5: 2.6, 6: 3.6}   # in hex widths from the centre


def panel_county():
    r = 26.0
    im = Image.new("RGBA", (W, HH), H.PARCH + (255,))
    d = ImageDraw.Draw(im)
    for y in range(HH):
        d.line([(T.county_coast(y), y), (W, y)], fill=(196, 214, 216))

    def sample(cx, cy):
        for hx, hy, s, rad, name, tier, cap in T.HOT:
            if name and math.hypot(cx - hx, cy - hy) <= TOWN_REACH[tier] * H.SQ3 * r and T.county_terrain(cx, cy) != "water":
                return ("town", min(4, tier))
        f = T.county_heat(cx, cy)
        return (T.county_terrain(cx, cy), heat_tier(f)) if f > 0 else None

    T.hex_layer(im, r, sample, faint=False, texscale=96)
    d = ImageDraw.Draw(im)
    for a, b, lvl in T.LINKS:
        d.line([a, b], fill=(70, 56, 40), width=2 + lvl + 2)
        d.line([a, b], fill=(206, 182, 136), width=2 + lvl)
    for hx, hy, s, rad, name, tier, cap in T.HOT:
        if name:
            H.put(im, H.icon(f"oad_medal_tier_{tier}", 40), hx, hy)
            if cap:
                H.put(im, H.icon("badge_capital", 16), hx + 16, hy - 16)
            H.label(im, name, hx, hy + 24)
    H.put(im, H.icon("medal_waystone", 34), 110, 760)
    H.label(im, "Waystone", 110, 780)
    H.label(im, "THE OAKDALE REACH", W / 2, 16, ImageFont.load_default(size=22))
    return im


# ---------- Beyond: node size by best settlement tier ----------
TIER_NAMES = ["Ruins", "Camp", "Hamlet", "Village", "Town", "City", "Metropolis"]
T.REGIONS[:] = [
    ("The Oakdale Reach", 300, 470, 1240, "grass", 4, True),
    ("Pine Hollow", 130, 760, 310, "forest", 3, False),
    ("Gull Island", 500, 300, 140, "sand", 2, False),
    ("Northfield", 260, 210, 90, "farm", 1, False),
    ("Stonebridge", 470, 790, 40, "town", 0, False),
]


def panel_nodes():
    im = Image.new("RGBA", (W, HH), (232, 222, 196, 255))
    d = ImageDraw.Draw(im, "RGBA")
    for y in range(HH):
        d.line([(560 + 40 * math.sin(y / 160), y), (W, y)], fill=(205, 218, 214, 255))
    for a, b, kind in T.ROUTES:
        A, B = T.REGIONS[a], T.REGIONS[b]
        p0, p1 = (A[1], A[2]), (B[1], B[2])
        if kind == "waystone":
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
    for name, x, y, heat, mat, tier, cap in T.REGIONS:
        rad = 20 + 11 * tier
        disc = Image.new("L", (W, HH), 0)
        ImageDraw.Draw(disc).polygon(H.hex_corners(x, y, rad), fill=255)
        im.paste(tiered(mat, min(4, tier), 96), (0, 0), disc)
        d.polygon(H.hex_corners(x, y, rad), outline=(70, 56, 40, 255), width=3)
        H.put(im, H.icon(f"oad_medal_tier_{tier}", int(18 + rad * 0.7)), x, y)
        if cap:
            H.put(im, H.icon("badge_capital", 20), x + rad * 0.45, y - rad * 0.45)
        H.label(im, name, x, y + rad + 4)
        H.label(im, TIER_NAMES[tier], x, y + rad + 22, ImageFont.load_default(size=13))
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
    d.text((16, ly + 66), "Size = best settlement's tier", font=leg, fill=H.INK)
    return im


def main():
    panels = [("1  Block", "~20 m hexes under the real streets; village\nas paved hexes; roads exactly as on the map", T.panel_block()),
              ("2  Neighbourhood", "~80 m hexes merged; the same real roads,\nunwalked streets dashed on explored ground", panel_neighbourhood()),
              ("3  County", "~1 km hexes; settlements cover more hexes\nas they grow (village 7ish, town 19ish)", panel_county()),
              ("4  Beyond", "Nodes only, sized by the region's best\nsettlement tier", panel_nodes())]
    gap, top = 24, 110
    sheet = Image.new("RGB", (len(panels) * W + (len(panels) + 1) * gap, HH + top + 120), (244, 236, 216))
    d = ImageDraw.Draw(sheet)
    head = ImageFont.load_default(size=28); small = ImageFont.load_default(size=17); tiny = ImageFont.load_default(size=14)
    for i, (title, sub, im) in enumerate(panels):
        x = gap + i * (W + gap)
        d.text((x, 18), title, font=head, fill=H.INK)
        d.multiline_text((x, 56), sub, font=small, fill=(120, 98, 76), spacing=4)
        sheet.paste(im.convert("RGB"), (x, top))
        d.rectangle([x - 1, top - 1, x + W, top + HH], outline=(128, 100, 70), width=2)
    # five-step key, aligned with the road stages
    ky = top + HH + 16
    d.text((gap, ky + 26), "Heat (distinct days):", font=small, fill=H.INK)
    kx0 = gap + 190
    road_names = ["Trail", "Footpath", "Lane", "Cobbled road", "Avenue"]
    for t in range(5):
        x = kx0 + t * 175
        d.text((x, ky), f"{DAYS[t]} day{'s' if DAYS[t] > 1 else ''} · {road_names[t]}", font=tiny, fill=(120, 98, 76))
        for j, m in enumerate(("grass", "forest", "town")):
            sheet.paste(tiered(m, t, 128).crop((0, 0, 44, 40)), (x + j * 46, ky + 20))
    d.text((kx0 + 5 * 175 + 10, ky + 30), "Each step: a hex looks like its road would.\nGrass, woods and town shown; every ground type has the same five steps.",
           font=small, fill=(120, 98, 76))
    sheet.save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
