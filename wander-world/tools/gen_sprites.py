"""
Generates Wander World's placeholder pixel-art sprites (original work, no third-party assets).

Top-down, Orna-style: each sprite is drawn at 16x16 (or 24x24) with a fixed palette, a 1 px dark
outline and a soft drop shadow, then scaled up 4x with nearest-neighbour so it stays crisp.

Run:  python3 -I tools/gen_sprites.py   (writes app/src/main/res/drawable-nodpi/*.png)
"""
import random
import sys
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"
SCALE = 4

INK = (59, 42, 32, 255)
WOOD = (141, 110, 99, 255)
WOOD_L = (188, 154, 118, 255)
ROOF = (181, 82, 59, 255)
ROOF_L = (210, 122, 92, 255)
STONE = (158, 158, 158, 255)
STONE_L = (207, 207, 207, 255)
MOSS = (106, 142, 71, 255)
LEAF = (79, 125, 58, 255)
LEAF_L = (127, 174, 88, 255)
CANVAS = (232, 217, 176, 255)
CANVAS_D = (201, 183, 138, 255)
WINDOW = (245, 215, 110, 255)
FLAG = (192, 57, 43, 255)
BLUE = (63, 111, 168, 255)
SKIN = (233, 190, 150, 255)


def canvas(size=16):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Soft shadow under everything ("shading").
    d.ellipse([2, size - 5, size - 3, size - 1], fill=(40, 30, 20, 70))
    return img, d


def house(d, x, y, w=6, h=4, roof=ROOF, roof_l=ROOF_L, wall=WOOD_L):
    """A small house: wall with a door and window, pitched roof."""
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=wall, outline=INK)
    d.rectangle([x + 1, y + h - 2, x + 2, y + h - 1], fill=WOOD)          # door
    if w >= 5:
        d.point((x + w - 2, y + 1), fill=WINDOW)                           # window
    d.polygon([(x - 1, y), (x + w // 2 - 0.5, y - h + 1), (x + w, y)], fill=roof, outline=INK)
    d.line([(x + w // 2, y - h + 2), (x + w - 1, y - 1)], fill=roof_l)


def tower(d, x, y, w=4, h=8, flag=False):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=STONE, outline=INK)
    for i in range(x, x + w, 2):
        d.point((i, y - 1), fill=STONE)                                    # crenellations
    d.line([(x + 1, y + 1), (x + 1, y + h - 2)], fill=STONE_L)
    d.point((x + w // 2, y + 2), fill=WINDOW)
    if flag:
        d.line([(x + w // 2, y - 1), (x + w // 2, y - 5)], fill=INK)
        d.polygon([(x + w // 2 + 1, y - 5), (x + w // 2 + 4, y - 4), (x + w // 2 + 1, y - 3)], fill=FLAG)


def tree(d, x, y, r=3):
    d.rectangle([x, y + r - 1, x, y + r + 1], fill=WOOD)
    d.ellipse([x - r, y - r, x + r, y + r - 1], fill=LEAF, outline=INK)
    d.point((x - 1, y - 1), fill=LEAF_L)


def ruins():
    img, d = canvas()
    d.rectangle([3, 8, 5, 12], fill=STONE, outline=INK)
    d.rectangle([7, 10, 12, 12], fill=STONE, outline=INK)
    d.rectangle([9, 6, 11, 9], fill=STONE, outline=INK)
    for p in [(4, 8), (8, 10), (11, 10), (10, 6)]:
        d.point(p, fill=MOSS)
    d.point((6, 13), fill=MOSS); d.point((13, 12), fill=MOSS)
    return img


def camp():
    img, d = canvas()
    d.polygon([(2, 12), (7, 4), (12, 12)], fill=CANVAS, outline=INK)
    d.polygon([(7, 4), (12, 12), (8, 12)], fill=CANVAS_D)
    d.line([(7, 4), (7, 12)], fill=INK)
    d.rectangle([12, 11, 14, 12], fill=WOOD)                               # log
    d.point((13, 10), fill=FLAG); d.point((13, 9), fill=WINDOW)            # campfire
    return img


def hamlet():
    img, d = canvas()
    house(d, 2, 9, w=6, h=4)
    tree(d, 12, 6)
    d.rectangle([11, 11, 13, 12], fill=WOOD_L, outline=INK)                # well
    return img


def village():
    img, d = canvas(24)
    house(d, 2, 13, w=7, h=5)
    house(d, 12, 9, w=6, h=4, roof=BLUE, roof_l=(110, 150, 200, 255))
    house(d, 13, 17, w=6, h=4)
    tree(d, 5, 5)
    return img


def town():
    img, d = canvas(24)
    d.rectangle([1, 19, 22, 20], fill=STONE, outline=INK)                  # low wall
    house(d, 3, 13, w=6, h=4)
    house(d, 11, 10, w=7, h=5, roof=BLUE, roof_l=(110, 150, 200, 255))
    house(d, 14, 15, w=6, h=4)
    tower(d, 19, 9, w=3, h=10)
    return img


def city():
    img, d = canvas(24)
    d.rectangle([2, 12, 21, 20], fill=STONE, outline=INK)                  # keep
    d.rectangle([9, 16, 13, 20], fill=WOOD)                                # gate
    tower(d, 1, 6, w=4, h=14)
    tower(d, 18, 6, w=4, h=14, flag=True)
    house(d, 7, 9, w=9, h=4)
    return img


def metropolis():
    img, d = canvas(24)
    d.rectangle([1, 13, 22, 21], fill=STONE, outline=INK)
    d.rectangle([10, 17, 13, 21], fill=WOOD)
    tower(d, 0, 6, w=4, h=15, flag=True)
    tower(d, 19, 6, w=4, h=15, flag=True)
    tower(d, 9, 2, w=6, h=12, flag=True)
    d.rectangle([5, 10, 8, 13], fill=ROOF, outline=INK)
    d.rectangle([15, 10, 18, 13], fill=ROOF, outline=INK)
    return img


def place_park():
    img, d = canvas()
    tree(d, 5, 6); tree(d, 11, 8); tree(d, 6, 11, r=2)
    return img


def place_store():
    img, d = canvas()
    d.rectangle([2, 6, 13, 12], fill=STONE_L, outline=INK)
    d.rectangle([2, 5, 13, 7], fill=FLAG, outline=INK)                     # awning
    for x in range(3, 13, 2):
        d.point((x, 6), fill=CANVAS)
    d.rectangle([7, 9, 8, 12], fill=WOOD)
    return img


def place_generic():
    img, d = canvas()
    d.line([(7, 3), (7, 12)], fill=INK)
    d.polygon([(8, 3), (13, 5), (8, 7)], fill=FLAG, outline=INK)
    return img


def wanderer():
    img, d = canvas()
    d.ellipse([5, 2, 10, 7], fill=SKIN, outline=INK)                       # head
    d.polygon([(4, 7), (11, 7), (12, 13), (3, 13)], fill=BLUE, outline=INK)  # cloak
    d.line([(12, 4), (12, 13)], fill=WOOD)                                 # walking staff
    return img


def waypoint():
    """Signpost: marks every area you've walked in."""
    img, d = canvas()
    d.rectangle([7, 4, 8, 13], fill=WOOD, outline=INK)                     # post
    d.polygon([(3, 4), (11, 4), (13, 5), (11, 7), (3, 7)], fill=WOOD_L, outline=INK)   # arrow sign
    d.line([(5, 5), (10, 5)], fill=WOOD)
    return img


def waystone():
    """Standing stone with a glowing rune: where travel set you down."""
    img, d = canvas()
    d.polygon([(5, 13), (4, 5), (7, 2), (10, 4), (11, 13)], fill=STONE, outline=INK)
    d.line([(6, 4), (6, 12)], fill=STONE_L)
    d.line([(8, 6), (8, 10)], fill=(120, 200, 255, 255))                   # rune
    d.line([(7, 8), (9, 8)], fill=(120, 200, 255, 255))
    return img


def ring_medallion(inner_img, size, ring, edge, glow=None, double=False):
    """Original medallion frame (no third-party art): a coloured ring around a sprite on a dark disc."""
    from PIL import ImageFilter
    pad = 10 if glow else 4
    out = Image.new("RGBA", (size + 2 * pad, size + 2 * pad), (0, 0, 0, 0))
    if glow:
        g = Image.new("RGBA", out.size, (0, 0, 0, 0))
        ImageDraw.Draw(g).ellipse([2, 2, size + 2 * pad - 3, size + 2 * pad - 3], fill=glow)
        out = Image.alpha_composite(out, g.filter(ImageFilter.GaussianBlur(5)))
    d = ImageDraw.Draw(out)
    o = pad
    d.ellipse([o + 2, o + 4, o + size + 1, o + size + 3], fill=(30, 22, 14, 160))          # shadow
    d.ellipse([o, o, o + size - 1, o + size - 1], fill=ring, outline=edge, width=3)
    w = max(6, size // 10)
    d.ellipse([o + w, o + w, o + size - w - 1, o + size - w - 1], fill=(52, 46, 40, 255), outline=edge, width=2)
    if double:
        d.ellipse([o + 3, o + 3, o + size - 4, o + size - 4], outline=(225, 245, 255, 255), width=2)
    inner = size - 2 * w - 4
    sprite = inner_img.resize((inner, inner), Image.NEAREST)
    out.alpha_composite(sprite, (o + w + 2, o + w + 2))
    return out


def hospital():
    """Red cross on white: hospital / clinic (original)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 1, 14, 14], fill=(246, 244, 238, 255))
    d.rectangle([6, 3, 9, 12], fill=(200, 40, 40, 255))
    d.rectangle([3, 6, 12, 9], fill=(200, 40, 40, 255))
    return img


# ---- The wanderer (avatar): hand-placed 24x24 pixel art ----
AVATAR_PALETTE = {
    "K": (43, 29, 20, 255),      # outline
    "H": (63, 94, 58, 255),      # hood / cloak (forest green)
    "h": (96, 132, 82, 255),     # hood highlight
    "G": (44, 70, 42, 255),      # cloak shadow
    "S": (232, 185, 142, 255),   # skin
    "s": (201, 146, 106, 255),   # skin shade
    "E": (43, 29, 20, 255),      # eyes
    "T": (168, 67, 47, 255),     # tunic (madder red)
    "t": (126, 47, 34, 255),     # tunic shade
    "B": (107, 68, 35, 255),     # belt / straps
    "Y": (226, 188, 90, 255),    # buckle
    "P": (84, 72, 60, 255),      # trousers
    "O": (58, 42, 30, 255),      # boots
    "R": (201, 164, 106, 255),   # bedroll
    "r": (160, 127, 72, 255),    # bedroll shade
    "W": (138, 106, 68, 255),    # staff
    "w": (176, 141, 94, 255),    # staff highlight
    "L": (255, 214, 107, 255),   # lantern glow
    "l": (90, 70, 48, 255),      # lantern frame
}

AVATAR = [
    "........................",
    "..........KKKK......lll.",
    ".........KhhhhK....lLLLl",
    "........KhhHHhhK...lLLLl",
    "........KhHSSHhK....lll.",
    "........KHSESEHK.....W..",
    "........KHSssSHK.....w..",
    ".......KRKHSSHKRK....W..",
    "......KRrKGHHGKrRK..KW..",
    "......KRrGHTTTHGRK.KSK..",
    ".....KGGHHTtTtTHHGKSSK..",
    ".....KGHHKTTYTTKHHGKWK..",
    ".....KGHKBBBYBBBKHGK.W..",
    ".....KGHKTtTTTtTKHGK.w..",
    ".....KGHKTTTTTTTKHGK.W..",
    "......KGKTtTTTtTKGK..W..",
    "......KGGKPPPPPKGGK..W..",
    ".......KKKPPKPPKKK...W..",
    ".........KPPKPPK.....W..",
    ".........KPPKPPK.....w..",
    ".........KOOKOOK.....W..",
    "........KOOOKOOOK....W..",
    "........KKKK.KKKK....K..",
    "........................",
]


def render_grid(rows, palette):
    img = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                px[x, y] = palette[ch]
    return img


def avatar_walk():
    fig = render_grid(AVATAR, AVATAR_PALETTE)
    out = Image.new("RGBA", (28, 28), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    d.ellipse([5, 22, 23, 27], fill=(30, 22, 14, 90))          # ground shadow
    d.ellipse([18, 0, 27, 8], fill=(255, 214, 107, 60))        # lantern glow
    out.alpha_composite(fig, (2, 2))
    return out


def avatar_paddle():
    """The wanderer standing on a paddleboard, paddle in hand."""
    fig = render_grid([r[:20] for r in AVATAR[:18]], AVATAR_PALETTE)   # no staff, upper body + legs
    out = Image.new("RGBA", (32, 28), (0, 0, 0, 0))
    d = ImageDraw.Draw(out)
    for y in (24, 26):                                          # ripples
        d.line([(1, y), (6, y)], fill=(225, 242, 250, 200)); d.line([(25, y), (31, y)], fill=(225, 242, 250, 200))
    d.ellipse([3, 19, 29, 26], fill=(43, 29, 20, 255))         # board outline
    d.ellipse([4, 20, 28, 25], fill=(64, 178, 186, 255))       # board (turquoise)
    d.line([(7, 22), (25, 22)], fill=(160, 226, 228, 255))     # board highlight
    out.alpha_composite(fig, (4, 3))
    d.line([(26, 2), (19, 22)], fill=(43, 29, 20, 255), width=2)   # paddle shaft
    d.line([(26, 2), (20, 20)], fill=(176, 141, 94, 255), width=1)
    d.polygon([(18, 20), (21, 21), (19, 26), (16, 25)], fill=(232, 120, 60, 255), outline=(43, 29, 20, 255))  # blade
    return out


def water_ripple(size=64, seed=9):
    """Tileable water texture: calm blue with soft ripple strokes (original)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size), (126, 178, 196, 255))
    d = ImageDraw.Draw(img)
    for _ in range(14):
        x, y = rnd.randrange(size), rnd.randrange(size)
        w = rnd.randrange(5, 11)
        for dx in (-size, 0, size):
            for dy in (-size, 0, size):
                d.arc([x + dx - w, y + dy - 2, x + dx + w, y + dy + 3], 200, 340, fill=(196, 228, 238, 255))
    return img


def capital_badge():
    """Gold star badge shown on the capital's medallion."""
    img = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    import math as m
    pts = []
    for i in range(10):
        r = 11 if i % 2 == 0 else 5
        a = -m.pi / 2 + i * m.pi / 5
        pts.append((12 + r * m.cos(a), 12 + r * m.sin(a)))
    d.polygon(pts, fill=(246, 200, 70, 255), outline=INK)
    return img


def parchment(size=64, seed=7):
    """Tileable fog texture: plain parchment with faint fibres (no blots, so it doesn't look patterned)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (size, size), (236, 228, 208, 255))
    px = img.load()
    for _ in range(500):
        x, y = rnd.randrange(size), rnd.randrange(size)
        r, g, b, a = px[x, y]
        k = rnd.choice([-6, -4, 3, 4])
        px[x, y] = (max(0, r + k), max(0, g + k), max(0, b + k), 255)
    return img


SPRITES = {
    # Settlement, waypoint and place markers now use 0 A.D. medallions (tools/build_0ad_assets.py).
    "waystone": waystone,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SPRITES.items():
        img = fn()
        img = img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)
        img.save(OUT / f"sprite_{name}.png", optimize=True)
    parchment().resize((128, 128), Image.NEAREST).save(OUT / "fog_parchment.png", optimize=True)
    stone = waystone()
    ring_medallion(stone, 88, (90, 140, 190, 255), (30, 60, 100, 255)).save(OUT / "medal_waystone.png", optimize=True)
    ring_medallion(stone, 112, (110, 170, 225, 255), (30, 60, 100, 255), glow=(120, 200, 255, 150), double=True) \
        .save(OUT / "medal_waystone_great.png", optimize=True)
    capital_badge().resize((48, 48), Image.NEAREST).save(OUT / "badge_capital.png", optimize=True)
    avatar_walk().resize((112, 112), Image.NEAREST).save(OUT / "sprite_wanderer.png", optimize=True)
    avatar_paddle().resize((128, 112), Image.NEAREST).save(OUT / "sprite_wanderer_paddle.png", optimize=True)
    water_ripple().resize((128, 128), Image.NEAREST).save(OUT / "water_ripple.png", optimize=True)
    ring_medallion(hospital(), 96, (196, 160, 82, 255), (110, 80, 40, 255)).save(OUT / "medal_poi_hospital.png", optimize=True)
    print(f"wrote {len(SPRITES) + 1} images to {OUT}", file=sys.stderr)


if __name__ == "__main__":
    main()
