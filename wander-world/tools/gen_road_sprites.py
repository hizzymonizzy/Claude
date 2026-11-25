"""
Roadside details for the 0.14.2 road look, original drawings (no outside art):
  roadside_trees.png   two tree canopies, one each side of an avenue (x runs along the road, y across it)
  roadside_cairns.png  two small stone cairns either side of a ranger trail
  roadside_buoys.png   two orange buoys either side of a marked water route
The map places them along the line (symbol-placement line, rotated with it) and scales them with the road.
Run: python3 -I tools/gen_road_sprites.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

RES = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable-nodpi"
LEN, ACROSS = 64, 200   # along the road × across it; the pair's centres are 144 px apart


def pair(draw_one, r):
    img = Image.new("RGBA", (LEN, ACROSS), (0, 0, 0, 0))
    for cy in (28, ACROSS - 28):
        draw_one(img, LEN // 2, cy, r)
    return img


def shadow(img, x, y, r, dx=4, dy=5, alpha=90):
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([x - r + dx, y - r + dy, x + r + dx, y + r + dy], fill=(20, 30, 10, alpha))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(3)))


def tree(img, x, y, r):
    shadow(img, x, y, r)
    d = ImageDraw.Draw(img)
    d.ellipse([x - r, y - r, x + r, y + r], fill=(58, 104, 50, 255), outline=(32, 62, 28, 255), width=3)
    for ox, oy, rr, c in [(-8, -6, .55, (78, 126, 62)), (7, 5, .45, (66, 114, 56)), (-4, -10, .3, (112, 158, 84))]:
        k = r * rr
        d.ellipse([x + ox - k, y + oy - k, x + ox + k, y + oy + k], fill=c + (255,))


def cairn(img, x, y, r):
    shadow(img, x, y, r, 3, 3, 70)
    d = ImageDraw.Draw(img)
    for k, c in [(1.0, (120, 116, 108)), (.7, (152, 148, 140)), (.4, (186, 182, 172))]:
        d.ellipse([x - r * k, y - r * k, x + r * k, y + r * k], fill=c + (255,), outline=(70, 66, 60, 255), width=2)


def buoy(img, x, y, r):
    shadow(img, x, y, r, 3, 3, 60)
    d = ImageDraw.Draw(img)
    d.ellipse([x - r, y - r, x + r, y + r], fill=(232, 131, 58, 255), outline=(120, 60, 24, 255), width=3)
    d.ellipse([x - r * .4, y - r * .4, x + r * .4, y + r * .4], fill=(250, 240, 226, 255))


def main():
    pair(tree, 26).save(RES / "roadside_trees.png", optimize=True)
    pair(cairn, 14).save(RES / "roadside_cairns.png", optimize=True)
    pair(buoy, 13).save(RES / "roadside_buoys.png", optimize=True)
    print("wrote roadside_trees, roadside_cairns, roadside_buoys")


if __name__ == "__main__":
    main()
