"""
Mockup: road stages now vs proposed (0.15 road look). Uses the app's 0 A.D. textures; trees are original drawings.
Run: python3 -I tools/mockups/roads.py  → docs/art/mockup_roads.png
"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res/drawable-nodpi"
W, ROW = 1500, 230
NAMES = ["1 Trail · 1 day", "2 Footpath · 3 days", "3 Lane · 10 days", "4 Cobbled road · 30 days", "5 Avenue · 100 days"]


def tex(name, size):
    t = Image.open(RES / f"{name}.png").convert("RGBA")
    out = Image.new("RGBA", size)
    for x in range(0, size[0], t.width):
        for y in range(0, size[1], t.height):
            out.paste(t, (x, y))
    return out


def curve(x0, x1, y):
    return [(x, y + 18 * math.sin((x - x0) / 140)) for x in range(x0, x1 + 1, 6)]


def stroke(size, pts, width, blur=0):
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    d.line(pts, fill=255, width=int(width), joint="curve")
    for p in (pts[0], pts[-1]):
        d.ellipse([p[0] - width / 2, p[1] - width / 2, p[0] + width / 2, p[1] + width / 2], fill=255)
    return m.filter(ImageFilter.GaussianBlur(blur)) if blur else m


def offset(pts, o):
    out = []
    for i, (x, y) in enumerate(pts):
        a = pts[max(i - 1, 0)]; b = pts[min(i + 1, len(pts) - 1)]
        dx, dy = b[0] - a[0], b[1] - a[1]
        l = math.hypot(dx, dy) or 1
        out.append((x - dy / l * o, y + dx / l * o))
    return out


def paint(img, mask, fill):
    layer = fill if isinstance(fill, Image.Image) else Image.new("RGBA", img.size, fill)
    img.paste(layer, (0, 0), mask)


def tree(img, x, y, r):
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([x - r + 4, y - r + 6, x + r + 4, y + r + 6], fill=(20, 30, 10, 90))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(3)))
    d = ImageDraw.Draw(img)
    d.ellipse([x - r, y - r, x + r, y + r], fill=(58, 104, 50, 255), outline=(34, 66, 30, 255), width=2)
    d.ellipse([x - r * .55, y - r * .7, x + r * .25, y + r * .05], fill=(92, 140, 70, 255))


def proposed(img, n, pts):
    size = img.size
    if n == 0:   # trail: trodden earth through the grass, soft and patchy
        paint(img, stroke(size, pts, 16, 3), (150, 120, 80, 150))
        paint(img, stroke(size, pts, 8, 1.5), tex("oad_road_dirt", size))
    elif n == 1:  # footpath: packed dirt with a worn, blurred margin
        paint(img, stroke(size, pts, 24, 3), (110, 90, 55, 140))
        paint(img, stroke(size, pts, 15, 1), tex("oad_road_dirt", size))
    elif n == 2:  # lane: dirt with grass verges and two wheel ruts
        paint(img, stroke(size, pts, 34, 2), (96, 76, 46, 200))
        paint(img, stroke(size, pts, 26, 1), tex("oad_road_dirt", size))
        for o in (-6, 6):
            paint(img, stroke(size, offset(pts, o), 4, 1), (90, 68, 40, 170))
    elif n == 3:  # cobbled road: cobbles between stone kerbs
        paint(img, stroke(size, pts, 44), (70, 64, 58, 255))
        paint(img, stroke(size, pts, 40), (196, 190, 176, 255))
        paint(img, stroke(size, pts, 32), tex("oad_road_cobble", size))
    else:         # avenue: wide paving, kerbs, trees both sides
        paint(img, stroke(size, pts, 58), (70, 64, 58, 255))
        paint(img, stroke(size, pts, 54), (206, 200, 186, 255))
        paint(img, stroke(size, pts, 44), tex("oad_road_cobble", size))
        for o in (-44, 44):
            side = offset(pts, o)
            for i in range(0, len(side), 9):
                tree(img, *side[i], 14)


def current(img, n, pts):
    size = img.size
    if n == 0:
        paint(img, stroke(size, pts, 6, 1.5), (156, 132, 88, 140)); paint(img, stroke(size, pts, 3.5), (232, 212, 166, 255))
    elif n in (1, 2):
        if n == 2: paint(img, stroke(size, pts, 7.5), (74, 58, 40, 190))
        paint(img, stroke(size, pts, 3.5 if n == 1 else 5), tex("oad_road_dirt", size))
    else:
        paint(img, stroke(size, pts, 9.5 if n == 3 else 11), (94, 90, 82, 190))
        paint(img, stroke(size, pts, 6.5 if n == 3 else 8.5), tex("oad_road_cobble", size))
        if n == 4:
            d = ImageDraw.Draw(img)
            for o in (-9, 9):
                for p in offset(pts, o)[::5]: d.ellipse([p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3], fill=(58, 104, 50, 255))


def main():
    img = tex("oad_ground_grass", (W, 90 + ROW * 5))
    d = ImageDraw.Draw(img)
    try:
        f = ImageFont.truetype("DejaVuSans.ttf", 26); fb = ImageFont.truetype("DejaVuSans-Bold.ttf", 30)
    except OSError:
        f = fb = ImageFont.load_default()
    head = Image.new("RGBA", (W, 70), (244, 236, 216, 235)); img.alpha_composite(head, (0, 0))
    d.text((40, 18), "Now (fixed thin lines)", font=fb, fill=(60, 44, 28))
    d.text((800, 18), "Proposed (real width, textured, details)", font=fb, fill=(60, 44, 28))
    for n in range(5):
        y = 90 + ROW * n + ROW // 2
        current(img, n, curve(60, 700, y))
        proposed(img, n, curve(800, 1440, y))
        label = Image.new("RGBA", (420, 40), (244, 236, 216, 220)); img.alpha_composite(label, (40, y - ROW // 2 + 6))
        d.text((52, y - ROW // 2 + 10), NAMES[n], font=f, fill=(60, 44, 28))
    out = ROOT / "docs/art/mockup_roads.png"
    img.convert("RGB").save(out, optimize=True)
    print(out)


if __name__ == "__main__":
    main()
