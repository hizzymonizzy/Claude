"""
Design sheet: hex shapes for map symbols (player, 2026-11-23). Everything round becomes a pointy-top hex, at about
2× today's size:
  A  Settlement hexes: the 0 A.D. medallion art cut to a hex, framed by tier (bronze → silver → gold), capital star
  B  Resource badges: hex outline colour = state (best, in zone, new, gathered, thinned), plus the edge arrow
  C  Developed cells (close zoom only): a building claims a block hex around its settlement; own ground + its art
  D  Close zoom scene: the settlement hex, its paved ring, developed cells, a resource badge and roads
  E  Size: today vs proposed, against a block hex at street zoom
Uses only the app's existing art (0 A.D. CC BY-SA 3.0 and original sprites). Invented names only.
Run:  python3 -I tools/mockups/hexicons.py → docs/art/mockup_hex_icons.png
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res/drawable-nodpi"
OUT = ROOT / "docs/art/mockup_hex_icons.png"
PARCH = (244, 236, 216)
INK = (91, 70, 54)
FRAMES = {  # tier → (outer, inner) frame colours
    "bronze": ((92, 58, 30), (176, 120, 64)),
    "silver": ((70, 70, 76), (196, 196, 204)),
    "gold": ((110, 78, 18), (232, 186, 74)),
}
TIER_FRAME = ["bronze", "bronze", "bronze", "silver", "silver", "gold", "gold"]
TIERS = ["Ruins", "Camp", "Hamlet", "Village", "Town", "City", "Metropolis"]


def img(name):
    return Image.open(RES / f"{name}.png").convert("RGBA")


def hexpts(cx, cy, r, rot=-30):
    return [(cx + r * math.cos(math.radians(60 * i + rot)), cy + r * math.sin(math.radians(60 * i + rot))) for i in range(6)]


def hexmask(size, r, scale=4):
    big = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(big).polygon(hexpts(size * scale / 2, size * scale / 2, r * scale), fill=255)
    return big.resize((size, size), Image.LANCZOS)


def tile(name, size, scale=96, enhance=None):
    t = img(name).convert("RGB").resize((scale, scale), Image.LANCZOS)
    out = Image.new("RGB", (size, size))
    for x in range(0, size, scale):
        for y in range(0, size, scale):
            out.paste(t, (x, y))
    if enhance:
        b, c = enhance
        out = ImageEnhance.Color(ImageEnhance.Brightness(out).enhance(b)).enhance(c)
    return out


def framed_hex(inner, size, frame, width=None, shadow=True):
    """inner: RGBA image to fill the hex; frame: (outer, inner) colours."""
    width = width or max(3, size // 14)
    out = Image.new("RGBA", (size + 8, size + 8), (0, 0, 0, 0))
    r = size / 2 - 1
    if shadow:
        sh = Image.new("RGBA", out.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).polygon(hexpts(out.width / 2 + 2, out.height / 2 + 3, r), fill=(0, 0, 0, 90))
        out.alpha_composite(sh.filter(ImageFilter.GaussianBlur(3)))
    m = hexmask(size, r - width * 0.6)
    body = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    body.paste(inner.resize((size, size), Image.LANCZOS), (0, 0), m)
    out.alpha_composite(body, (4, 4))
    d = ImageDraw.Draw(out)
    c = (out.width / 2, out.height / 2)
    d.polygon(hexpts(*c, r), outline=frame[0], width=width + 2)
    d.polygon(hexpts(*c, r - width * 0.5), outline=frame[1], width=max(2, width - 1))
    return out


def medal_inner(name, zoom=1.35):
    """The medallion's picture without its round frame: crop the middle and enlarge."""
    m = img(name)
    w, h = m.size
    k = int(min(w, h) / zoom / 2)
    return m.crop((w // 2 - k, h // 2 - k, w // 2 + k, h // 2 + k))


def settlement_hex(tier, size=120, capital=False):
    inner = Image.new("RGBA", (200, 200), (58, 46, 34, 255))
    art = medal_inner(f"oad_medal_tier_{tier}").resize((200, 200), Image.LANCZOS)
    inner.alpha_composite(art)
    h = framed_hex(inner, size, FRAMES[TIER_FRAME[tier]])
    d = ImageDraw.Draw(h)
    # Tier pips along the bottom edge (camp 1 … metropolis 6).
    for i in range(tier):
        x = h.width / 2 + (i - (tier - 1) / 2) * 11
        d.ellipse([x - 4, h.height - 22, x + 4, h.height - 14], fill=FRAMES[TIER_FRAME[tier]][1], outline=(40, 30, 20))
    if capital:
        star = img("badge_capital").resize((size // 3, size // 3), Image.LANCZOS)
        h.alpha_composite(star, (h.width - star.width - 2, 0))
    return h


STATE = {  # state → (outline colour, width, fill alpha, label)
    "best": ((217, 165, 32), 7, 235, "best: the arrow's target"),
    "near": ((63, 157, 74), 8, 235, "in its zone: gathering"),
    "new": ((184, 154, 90), 4, 235, "new: pays double"),
    "gathered": ((110, 143, 104), 4, 150, "gathered today"),
    "thinned": ((150, 140, 120), 3, 120, "thinned"),
}


def resource_hex(res, state, size=96):
    col, w, alpha, _ = STATE[state]
    out = Image.new("RGBA", (size + 8, size + 8), (0, 0, 0, 0))
    c = (out.width / 2, out.height / 2)
    d = ImageDraw.Draw(out)
    d.polygon(hexpts(*c, size / 2 - 2), fill=(244, 236, 216, alpha))
    icon = img(f"oad_res_{res}").resize((int(size * 0.58), int(size * 0.58)), Image.LANCZOS)
    if state in ("gathered", "thinned"):
        icon.putalpha(icon.getchannel("A").point(lambda a: a * 55 // 100))
    out.alpha_composite(icon, (int(c[0] - icon.width / 2), int(c[1] - icon.height / 2)))
    d.polygon(hexpts(*c, size / 2 - 2), outline=col, width=w)
    if state == "gathered":   # a drawn tick (the font has no ✓)
        x, y = c[0] + size * 0.16, c[1] + size * 0.12
        d.line([(x, y + 8), (x + 7, y + 15), (x + 20, y - 4)], fill=(63, 107, 58), width=5)
    return out


def arrow_hex(res, size=96):
    h = resource_hex(res, "best", size)
    out = Image.new("RGBA", (h.width + 28, h.height), (0, 0, 0, 0))
    out.alpha_composite(h)
    d = ImageDraw.Draw(out)
    cy = out.height / 2
    d.polygon([(h.width - 4, cy - 16), (h.width + 24, cy), (h.width - 4, cy + 16)], fill=(91, 70, 54))
    return out


CELLS = [  # building → (ground texture, tint (brightness, colour), art, label)
    ("campfire", ("oad_road_dirt", (1.0, 1.0)), "bldg_campfire", "Campfire: packed earth"),
    ("meeting_hall", ("oad_road_dirt", (0.95, 1.1)), "oad_medal_bldg_meeting_hall", "Meeting hall"),
    ("town_hall", ("oad_road_cobble", (1.0, 1.0)), "oad_medal_bldg_town_hall", "Town hall: paved"),
    ("well", ("oad_road_cobble", (1.05, 0.9)), "bldg_well", "Well: stone"),
    ("fountain", ("oad_road_cobble", (1.1, 0.9)), "bldg_fountain", "Fountain"),
    ("market", ("oad_road_cobble", (1.0, 1.1)), "oad_medal_bldg_market", "Market: cobbles"),
    ("market_hall", ("oad_road_cobble", (0.95, 1.2)), "oad_medal_bldg_market_hall", "Market hall"),
    ("gardens", ("oad_ground_farm", (1.0, 1.2)), None, "Gardens: field rows"),
]


def developed_cell(name, ground, art, size=150):
    """A block hex owned by a building: its ground texture edge to edge, the building drawn on it, a thin stone rim."""
    tex, (b, c) = ground
    base = tile(tex, size * 2, enhance=(b, c)).convert("RGBA").resize((size, size))
    if art:
        # Every building's art is round today (0 A.D. medallions and our own badges): cut its picture to a smaller hex
        # so it sits on the cell's ground with no circle left.
        a = medal_inner(art, 1.25 if art.startswith("oad_medal_bldg") else 1.45)
        a = a.resize((int(size * 0.72), int(size * 0.72)), Image.LANCZOS)
        m = hexmask(a.width, a.width / 2 - 2)
        cut = Image.new("RGBA", a.size, (0, 0, 0, 0)); cut.paste(a, (0, 0), m); a = cut
        base.alpha_composite(a, ((size - a.width) // 2, (size - a.height) // 2))
    return framed_hex(base, size, ((84, 70, 52), (170, 152, 120)), width=4)


def label(d, x, y, text, size=17, color=INK, center=True):
    f = ImageFont.load_default(size=size)
    w = d.textlength(text, font=f)
    d.text((x - w / 2 if center else x, y), text, fill=color, font=f, stroke_width=3, stroke_fill=(244, 236, 216))


def scene(w=760, h=620):
    """Close zoom: grass hexes, the settlement hex on its paved ring, developed cells, a resource badge, roads."""
    S = Image.new("RGBA", (w, h), (232, 222, 196, 255))
    r = 46                                   # a block hex at street zoom (~20 m)
    grass = tile("oad_ground_grass", 1024).convert("RGBA")
    cob = tile("oad_road_cobble", 1024, enhance=(0.9, 1.2)).convert("RGBA")
    cx, cy = w / 2, h / 2
    hw, hh = math.sqrt(3) * r, 1.5 * r
    cells = {}
    for row in range(-6, 7):
        for col in range(-6, 7):
            x = cx + col * hw + (hw / 2 if row % 2 else 0)
            y = cy + row * hh
            ring = max(abs(row), abs(col + (row - (row & 1)) // 2 - 0), 0)
            cells[(row, col)] = (x, y)
    for (row, col), (x, y) in cells.items():
        dist = math.hypot(x - cx, y - cy) / hw
        if dist > 3.6:
            continue
        m = Image.new("L", (w, h), 0)
        ImageDraw.Draw(m).polygon(hexpts(x, y, r + 0.6), fill=255)
        src = cob if dist < 1.1 else grass
        if dist >= 1.1:
            src = ImageEnhance.Color(grass).enhance(1.3 - 0.2 * dist)
        S.paste(src.crop((0, 0, w, h)), (0, 0), m)
    d = ImageDraw.Draw(S)
    for (row, col), (x, y) in cells.items():
        if math.hypot(x - cx, y - cy) / hw <= 3.6:
            d.polygon(hexpts(x, y, r), outline=(30, 40, 20, 40))
    # roads on top, as lines
    d.line([(0, cy + 6), (w, cy - 20)], fill=(120, 116, 108), width=16)
    d.line([(0, cy + 6), (w, cy - 20)], fill=(150, 146, 136), width=12)
    d.line([(cx + 30, 0), (cx - 40, h)], fill=(156, 132, 88), width=7)
    d.line([(cx + 30, 0), (cx - 40, h)], fill=(232, 212, 166), width=4)
    # developed cells: buildings claim ring-2 hexes
    picks = [((-2, -1), CELLS[0]), ((-2, 1), CELLS[3]), ((0, 2), CELLS[5]), ((2, 1), CELLS[7]), ((0, -2), CELLS[1])]
    for (row, col), (nm, ground, art, _) in picks:
        x, y = cells[(row, col)]
        c = developed_cell(nm, ground, art, size=int(r * 2))
        S.alpha_composite(c, (int(x - c.width / 2), int(y - c.height / 2)))
    sh = settlement_hex(3, size=int(r * 2.1), capital=True)
    S.alpha_composite(sh, (int(cx - sh.width / 2), int(cy - sh.height / 2)))
    label(d, cx, cy + r + 14, "Oakdale · Village", 22)
    rb = resource_hex("wood", "best", 84)
    S.alpha_composite(rb, (w - 150, 60))
    label(d, w - 150 + rb.width / 2, 60 + rb.height + 2, "Elm Woods · new", 16)
    me = img("sprite_wanderer").resize((54, 76))
    S.alpha_composite(me, (int(cx - 170), int(cy + 90)))
    trader = img("sprite_trader").resize((110, 77))
    S.alpha_composite(trader, (int(cx + 150), int(cy + 120)))
    banner = img("banner_crimson").resize((40, 60))
    S.alpha_composite(banner, (int(cx + 28), int(cy - 92)))
    return S


def main():
    W, H = 1900, 1080
    sheet = Image.new("RGBA", (W, H), PARCH + (255,))
    d = ImageDraw.Draw(sheet)
    head = ImageFont.load_default(size=30)
    sub = ImageFont.load_default(size=18)

    def section(x, y, title, note):
        d.text((x, y), title, font=head, fill=INK)
        d.text((x, y + 38), note, font=sub, fill=(120, 98, 76))

    # A settlements
    section(30, 20, "A  Settlement hexes", "Medallion art cut to a hex; frame by tier (bronze, silver, gold) and pips; capital star")
    for t in range(7):
        h = settlement_hex(t, 112, capital=(t == 6))
        x = 30 + t * 134
        sheet.alpha_composite(h, (x, 90))
        label(d, x + h.width / 2, 90 + h.height + 4, TIERS[t])
    # B resources
    section(30, 290, "B  Resource badges", "Hex outline colour is the state; the edge arrow gets the same hex")
    for i, st in enumerate(STATE):
        b = resource_hex(["wood", "food", "metal", "wood", "food"][i], st, 96)
        x = 40 + i * 150
        sheet.alpha_composite(b, (x, 360))
        label(d, x + b.width / 2, 360 + b.height + 4, STATE[st][3], 15)
    a = arrow_hex("metal", 96)
    sheet.alpha_composite(a, (800, 360))
    label(d, 800 + a.width / 2, 360 + a.height + 4, "edge arrow", 15)
    # C developed cells
    section(30, 530, "C  Developed cells (close zoom only)", "A building claims a block hex next to its settlement: its own ground and its art; upgrades change it")
    for i, (nm, ground, art, lab) in enumerate(CELLS):
        c = developed_cell(nm, ground, art, 130)
        x = 40 + i * 118 if i < 4 else 40 + (i - 4) * 118
        y = 600 if i < 4 else 800
        x = 40 + (i % 4) * 230
        sheet.alpha_composite(c, (x, y))
        label(d, x + c.width / 2, y + c.height + 4, lab, 15)
    # D scene
    section(1000, 20, "D  Close zoom", "The settlement hex on its paved ring, developed cells in ring 2, roads on top")
    sc = scene()
    sheet.alpha_composite(sc, (1020, 90))
    d.rectangle([1019, 89, 1020 + sc.width, 90 + sc.height], outline=(128, 100, 70), width=2)
    # E size
    section(1000, 740, "E  Size", "Today (left) vs proposed (right), against a ~20 m block hex at street zoom")
    ref = Image.new("RGBA", (320, 200), (232, 222, 196, 255))
    rd = ImageDraw.Draw(ref)
    for k in range(2):
        rd.polygon(hexpts(80 + 160 * k, 100, 46), fill=(120, 140, 70), outline=(60, 70, 40))
    old = img("bldg_campfire").resize((26, 26)); ref.alpha_composite(old, (67, 87))
    new = developed_cell("campfire", CELLS[0][1], "bldg_campfire", 92); ref.alpha_composite(new, (240 - new.width // 2, 100 - new.height // 2))
    sheet.alpha_composite(ref, (1020, 810))
    label(d, 1100, 1015, "today: 26 px icon", 15); label(d, 1260, 1015, "proposed: fills its hex", 15)
    oldm = img("oad_medal_tier_3").resize((44, 44)); sheet.alpha_composite(oldm, (1400, 860))
    newm = settlement_hex(3, 96); sheet.alpha_composite(newm, (1480, 830))
    label(d, 1422, 1015, "today", 15); label(d, 1532, 1015, "proposed", 15)
    sheet.convert("RGB").save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
