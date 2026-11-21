"""
Design iteration 2 (hex map): micro hexes at street zoom. Terrain is built from small hexes; hexes of the same
material merge (shared edges vanish or go very faint) and edges show only where material changes or at the fog.
Roads and trails stay real lines on top; they are never hexes.

  A  Merged      no edges inside a material; a crisp hex edge where material changes and at the fog
  B  Faint grid  as A, plus a very faint grid inside materials so the board reads up close
  C  Close-up    B at higher zoom around the village

Same invented scene and textures as hexmap.py. Run:  python3 -I tools/mockups/hexmicro.py → docs/art/mockup_hex_micro.png
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import hexmap as H  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

OUT = H.ROOT / "docs/art/mockup_hex_micro.png"


def material(cx, cy):
    f = H.familiarity(cx, cy)
    for (sx, sy), tier, _, _ in H.SETTLEMENTS:
        if math.hypot(cx - sx, cy - sy) < 14 + 9 * tier:
            return "town"
    return H.terrain(cx, cy) if f > 0 else None


def render(r, faint, zoom=1.0, focus=(0, 0)):
    """Render the scene at a zoom factor around focus (scene coords), hex radius r in scene units."""
    W, Hh = H.PW, H.PH
    def tx(x, y):
        return ((x - focus[0]) * zoom + W / 2, (y - focus[1]) * zoom + Hh / 2) if zoom != 1 else (x, y)
    im = H.base_parchment()
    if zoom != 1:
        im = Image.new("RGBA", (W, Hh), H.PARCH + (255,))
        d0 = ImageDraw.Draw(im)
        for yy in range(Hh):
            sy = (yy - Hh / 2) / zoom + focus[1]
            d0.line([(tx(H.coast_x(sy), sy)[0], yy), (W, yy)], fill=(196, 214, 216))
    # cells in scene coords
    cells = {}
    w, h = H.SQ3 * r, 1.5 * r
    rows = int(H.PH / h) + 3; cols = int(H.PW / w) + 3
    for row in range(-1, rows):
        for col in range(-1, cols):
            cx = col * w + (w / 2 if row % 2 else 0); cy = row * h
            m = material(cx, cy)
            if m:
                cells[(row, col)] = (cx, cy, m)
    masks = {k: Image.new("L", (W, Hh), 0) for k in H.TERRAIN_TEX}
    for cx, cy, m in cells.values():
        ImageDraw.Draw(masks[m]).polygon([tx(*p) for p in H.hex_corners(cx, cy, r + 0.7)], fill=255)
    tiles = Image.new("RGB", (W, Hh))
    allm = Image.new("L", (W, Hh), 0)
    for k, m in masks.items():
        tiles.paste(H.tex(H.TERRAIN_TEX[k], (W, Hh), scale=int(128 * zoom)), (0, 0), m)
        allm.paste(255, (0, 0), m)
    im.paste(tiles, (0, 0), allm)
    im = im.convert("RGB")             # draw edges on RGB so translucent lines blend instead of overwriting
    d = ImageDraw.Draw(im, "RGBA")

    def nbrs(row, col):
        odd = row % 2
        return [(row, col - 1), (row, col + 1), (row - 1, col - 1 + odd), (row - 1, col + odd), (row + 1, col - 1 + odd), (row + 1, col + odd)]

    # edges: corner pairs for each neighbour direction (pointy-top corners at -30,30,90,150,210,270)
    side = {0: (3, 4), 1: (0, 1), 2: (4, 5), 3: (5, 0), 4: (2, 3), 5: (1, 2)}
    for (row, col), (cx, cy, m) in cells.items():
        cs = [tx(*p) for p in H.hex_corners(cx, cy, r)]
        for i, nb in enumerate(nbrs(row, col)):
            other = cells.get(nb)
            a, b = side[i]
            seg = [cs[a], cs[b]]
            if other is None:
                d.line(seg, fill=(92, 74, 50, 200), width=max(2, int(2 * zoom)))           # the fog edge
            elif other[2] != m:
                d.line(seg, fill=(60, 48, 34, 150), width=max(1, int(1.5 * zoom)))         # material change
            elif faint:
                d.line(seg, fill=(30, 40, 20, 22 if zoom == 1 else 40), width=1)                               # same material: barely there
    im = im.convert("RGBA")
    # roads stay real lines (draw in scene coords, transformed)
    if zoom == 1:
        H.draw_roads(im, allm)
        H.draw_settlements(im)
    else:
        saved = H.ROADS
        H.ROADS = [([tx(*p) for p in poly], lvl) for poly, lvl in saved]
        H.draw_roads(im, allm, wide=zoom * 0.8)
        H.ROADS = saved
        for (x, y), tier, cap, name in H.SETTLEMENTS:
            X, Y = tx(x, y)
            if 0 < X < W and 0 < Y < Hh:
                H.put(im, H.icon(f"oad_medal_tier_{tier}", 70), X, Y)
                if cap:
                    H.put(im, H.icon("badge_capital", 28), X + 28, Y - 28)
                H.label(im, name, X, Y + 44, H.ImageFont.load_default(size=20))
        for i, b in enumerate(["bldg_campfire", "bldg_well", "oad_medal_bldg_market"]):
            a = math.radians(200 + 70 * i)
            X, Y = tx(340 + 42 * math.cos(a), 470 + 42 * math.sin(a))
            H.put(im, H.icon(b, 44), X, Y)
        H.put(im, H.icon("sprite_wanderer", 64), *tx(300, 520))
    return im


def main():
    panels = [("A  Merged", "Same material = no edge; a hex edge only\nwhere ground changes and at the fog", render(7, faint=False)),
              ("B  Faint grid", "As A, plus a barely-there grid inside\nso the board still reads", render(7, faint=True)),
              ("C  Close-up of B", "Street zoom around the village: roads and\ntrails stay real lines, never hexes", render(7, faint=True, zoom=3.0, focus=(330, 500)))]
    gap, top = 24, 110
    sheet = Image.new("RGB", (len(panels) * H.PW + (len(panels) + 1) * gap, H.PH + top + gap), (244, 236, 216))
    d = ImageDraw.Draw(sheet)
    head = ImageFont.load_default(size=28); small = ImageFont.load_default(size=17)
    for i, (title, sub, im) in enumerate(panels):
        x = gap + i * (H.PW + gap)
        d.text((x, 18), title, font=head, fill=H.INK)
        d.multiline_text((x, 56), sub, font=small, fill=(120, 98, 76), spacing=4)
        sheet.paste(im.convert("RGB"), (x, top))
        d.rectangle([x - 1, top - 1, x + H.PW, top + H.PH], outline=(128, 100, 70), width=2)
    sheet.save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
