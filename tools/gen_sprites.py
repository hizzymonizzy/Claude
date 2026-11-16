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
    "tier_0": ruins, "tier_1": camp, "tier_2": hamlet, "tier_3": village,
    "tier_4": town, "tier_5": city, "tier_6": metropolis,
    "place_park": place_park, "place_store": place_store, "place_generic": place_generic,
    "wanderer": wanderer, "waypoint": waypoint, "waystone": waystone,
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in SPRITES.items():
        img = fn()
        img = img.resize((img.width * SCALE, img.height * SCALE), Image.NEAREST)
        img.save(OUT / f"sprite_{name}.png", optimize=True)
    parchment().resize((128, 128), Image.NEAREST).save(OUT / "fog_parchment.png", optimize=True)
    print(f"wrote {len(SPRITES) + 1} images to {OUT}", file=sys.stderr)


if __name__ == "__main__":
    main()
