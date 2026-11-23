"""
Design: the Atlas, wayfinding first (player, 2026-11-23). Settlements only for now, plus areas becoming settlements.
  1 List     capital card with progress and next step; settlements by distance with direction and Go; areas close to
             founding a camp; sort chips
  2 Detail   one settlement opened: tier progress, its buildings (hex icons), the request and what it needs, history
Reuses the shop mockup's window style. Invented place names only.
Run:  python3 -I tools/mockups/atlas.py → docs/art/mockup_atlas.png
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import hexicons as HX  # noqa: E402
import shop as SH  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

OUT = SH.ROOT / "docs/art/mockup_atlas.png"
PW, PH = SH.PW, SH.PH
F, INK, SOFT = SH.F, SH.INK, SH.SOFT


def arrow(d, x, y, bearing, r=16, color=(91, 70, 54)):
    a = math.radians(bearing - 90)
    tip = (x + r * math.cos(a), y + r * math.sin(a))
    l = (x + r * 0.7 * math.cos(a + 2.5), y + r * 0.7 * math.sin(a + 2.5))
    rr = (x + r * 0.7 * math.cos(a - 2.5), y + r * 0.7 * math.sin(a - 2.5))
    d.polygon([tip, l, (x, y), rr], fill=color)


def go(d, x, y):
    d.rounded_rectangle((x, y, x + 84, y + 46), 12, fill=(126, 96, 40), outline=SH.FRAME_GOLD, width=2)
    d.text((x + 24, y + 7), "Go", font=F(26), fill=(255, 246, 220))


def bar(d, x, y, w, frac, color=(110, 82, 50)):
    d.rounded_rectangle((x, y, x + w, y + 12), 6, fill=(226, 214, 190))
    d.rounded_rectangle((x, y, x + max(12, w * frac), y + 12), 6, fill=color)


def settlement_row(S, d, y, name, tier, prog, need, dist, bearing, last, capital=False):
    h = HX.settlement_hex(tier, 84, capital)
    S.alpha_composite(h, (60, y))
    d.text((160, y + 4), name, font=F(28), fill=INK)
    d.text((160, y + 42), f"{SH_T[tier]} · {need}", font=F(20), fill=SOFT)
    bar(d, 160, y + 74, 300, prog)
    arrow(d, PW - 196, y + 30, bearing)
    d.text((PW - 176, y + 14), dist, font=F(22), fill=INK)
    d.text((PW - 176, y + 44), last, font=F(18), fill=SOFT)
    return y + 120


SH_T = HX.TIERS


def screen_list():
    S, d = SH.base()
    SH.window(d, (24, 90, PW - 24, PH - 60), "Atlas")
    # summary line
    d.text((60, 140), "141 roads · 10 places · 14.1 km walked", font=F(22), fill=SOFT)
    # search
    d.rounded_rectangle((50, 180, PW - 50, 236), 12, fill=(255, 252, 240), outline=SH.FRAME_DARK, width=2)
    d.text((76, 192), "Search settlements, places, streets", font=F(22), fill=SOFT)
    # capital card
    SH.window(d, (50, 266, PW - 50, 560))
    S.alpha_composite(HX.settlement_hex(1, 110, True), (70, 290))
    d.text((200, 292), "Oakdale", font=F(32), fill=INK)
    d.text((200, 334), "Capital · Camp", font=F(22), fill=SOFT)
    d.text((200, 370), "4 / 10 visit-days to Hamlet", font=F(22), fill=INK)
    bar(d, 200, 402, 420, 0.4)
    d.text((70, 440), "Next: Signpost", font=F(26), fill=INK)
    d.text((70, 476), "needs 3 stone (you have 17) · 10 gold", font=F(20), fill=SOFT)
    for i, b in enumerate(["campfire"]):
        c = HX.developed_cell(b, HX.CELLS[0][1], "bldg_campfire", 70)
        S.alpha_composite(c, (PW - 190, 300))
    d.text((PW - 190, 380), "Built", font=F(18), fill=SOFT)
    SH.buy_button(d, PW - 220, 470, "Build")
    # chips
    y = 590
    for i, (t, on) in enumerate([("Nearest", True), ("Recent", False), ("Needs a visit", False)]):
        w = d.textlength(t, font=F(22)) + 36
        x = 60 + sum(d.textlength(tt, font=F(22)) + 50 for tt, _ in [("Nearest", 1), ("Recent", 1), ("Needs a visit", 1)][:i])
        d.rounded_rectangle((x, y, x + w, y + 44), 22, fill=SH.FRAME_DARK if on else SH.PANEL, outline=SH.FRAME_DARK, width=2)
        d.text((x + 18, y + 9), t, font=F(22), fill=(246, 230, 190) if on else INK)
    y = 660
    d.text((60, y), "Settlements", font=F(28), fill=(91, 70, 54)); y += 46
    y = settlement_row(S, d, y, "Elm Hollow", 2, 0.36, "9 / 25 to Village", "1.2 km", 40, "2 days ago")
    y = settlement_row(S, d, y, "Ridgefield", 1, 0.2, "2 / 10 to Hamlet", "3.4 km", 250, "last week")
    d.text((60, y + 4), "Becoming a settlement", font=F(28), fill=(91, 70, 54)); y += 50
    for name, days, dist, br in [("Willett Acres", 2, "0.8 km", 120), ("West Grove", 1, "2.1 km", 300), ("Melrose", 1, "4.0 km", 200)]:
        # an empty camp hex with day pips
        cx, cy = 102, y + 40
        d.polygon(HX.hexpts(cx, cy, 38), fill=(236, 226, 202), outline=SH.FRAME_DARK)
        for k in range(3):
            px = cx - 16 + k * 16
            d.ellipse((px - 6, cy - 6, px + 6, cy + 6), fill=(126, 96, 40) if k < days else (226, 214, 190), outline=SH.FRAME_DARK)
        d.text((160, y + 8), name, font=F(26), fill=INK)
        d.text((160, y + 44), f"{days} / 3 walking days · 5 min on foot each, within 30 days", font=F(18), fill=SOFT)
        arrow(d, PW - 196, y + 30, br)
        d.text((PW - 176, y + 18), dist, font=F(22), fill=INK)
        y += 96
    return S


def screen_detail():
    S, d = SH.base()
    SH.window(d, (24, 90, PW - 24, PH - 60), "Elm Hollow")
    d.ellipse((PW - 96, 104, PW - 44, 156), fill=SH.FRAME_DARK, outline=SH.FRAME_GOLD, width=2)
    d.line((PW - 82, 118, PW - 58, 142), fill=(246, 230, 190), width=5)
    d.line((PW - 58, 118, PW - 82, 142), fill=(246, 230, 190), width=5)
    S.alpha_composite(HX.settlement_hex(2, 150), (60, 150))
    d.text((240, 160), "Hamlet", font=F(32), fill=INK)
    d.text((240, 204), "9 / 25 visit-days to Village", font=F(22), fill=INK)
    bar(d, 240, 238, 400, 0.36)
    d.text((240, 268), "1.2 km NE · last walked 2 days ago", font=F(20), fill=SOFT)
    go(d, PW - 150, 160)
    d.text((60, 340), "Buildings", font=F(28), fill=(91, 70, 54))
    for i, (nm, ground, art, lab) in enumerate([HX.CELLS[0], HX.CELLS[3], HX.CELLS[7]]):
        c = HX.developed_cell(nm, ground, art, 120)
        S.alpha_composite(c, (60 + i * 150, 386))
        lw = d.textlength(lab.split(":")[0], font=F(18))
        d.text((60 + i * 150 + 64 - lw / 2, 516), lab.split(":")[0], font=F(18), fill=SOFT)
    SH.window(d, (50, 570, PW - 50, 820))
    d.text((76, 594), "The hamlet asks for a Woodshed", font=F(26), fill=INK)
    d.text((76, 632), "Trail wood here +25%", font=F(20), fill=SOFT)
    y = 676
    for res, have, need in [("wood", 2, 10), ("stone", 17, 5)]:
        S.alpha_composite(SH.img(f"oad_res_{res}", 40), (76, y))
        d.text((128, y + 6), f"{have} / {need}", font=F(24), fill=SH.RED if have < need else INK)
        y += 50
    d.text((300, 682), "Nearest wood: Ridge Trail, 600 m", font=F(20), fill=SOFT)
    SH.buy_button(d, 300, 730, "Go")
    SH.buy_button(d, PW - 200, 730, "Build", enabled=False)
    d.text((60, 860), "History", font=F(28), fill=(91, 70, 54))
    for i, ln in enumerate(["Founded Nov 15 · Camp", "Built the campfire Nov 18", "Grew to Hamlet Dec 2", "Built the well Dec 9"]):
        d.text((76, 906 + i * 38), "· " + ln, font=F(22), fill=INK)
    return S


def main():
    gap, top = 40, 110
    shots = [("1  Atlas", "Capital card with the next step; settlements by distance; areas becoming settlements", screen_list()),
             ("2  A settlement", "Progress, buildings as hex cells, its request with what's missing and where to get it", screen_detail())]
    sheet = Image.new("RGB", (len(shots) * (PW + gap) + gap, PH + top + 30), SH.PARCH)
    d = ImageDraw.Draw(sheet)
    for i, (t, sub, im) in enumerate(shots):
        x = gap + i * (PW + gap)
        d.text((x, 20), t, font=F(34), fill=(91, 70, 54))
        d.text((x, 66), sub, font=F(20), fill=SOFT)
        sheet.paste(im.convert("RGB"), (x, top))
        d.rounded_rectangle((x - 3, top - 3, x + PW + 3, top + PH + 3), 24, outline=(60, 50, 40), width=4)
    sheet.save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
