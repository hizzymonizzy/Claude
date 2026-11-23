"""
Design: the shop, JRPG-style (player, 2026-11-23). Three phone screens:
  1 Wares     greeting from the trader, a trade request, item list with flavour lines, the selected row's stepper
  2 Quantity  tapping the number opens a number pad to type the amount
  3 Looks     banners and outfits: price, Wear or Take off; the worn one starred
No rules text: prices and totals show the mechanics. ✕ top right. Invented place names; art from the app.
Run:  python3 -I tools/mockups/shop.py → docs/art/mockup_shop.png
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res/drawable-nodpi"
OUT = ROOT / "docs/art/mockup_shop.png"
PW, PH = 720, 1480
PARCH = (246, 238, 220)
PANEL = (250, 244, 230)
FRAME_DARK = (74, 54, 34)
FRAME_GOLD = (196, 158, 86)
INK = (52, 38, 26)
SOFT = (120, 98, 76)
GOLD = (176, 128, 30)
RED = (170, 52, 40)


def F(size):
    return ImageFont.load_default(size=size)


def img(name, h=None):
    im = Image.open(RES / f"{name}.png").convert("RGBA")
    if h:
        im = im.resize((int(h * im.width / im.height), h), Image.NEAREST if im.width <= 160 else Image.LANCZOS)
    return im


def window(d, box, title=None):
    """A JRPG window: dark outer line, gold inner line, parchment fill; optional title plate."""
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, 14, fill=PANEL, outline=FRAME_DARK, width=5)
    d.rounded_rectangle((x0 + 7, y0 + 7, x1 - 7, y1 - 7), 10, outline=FRAME_GOLD, width=2)
    if title:
        w = d.textlength(title, font=F(30)) + 40
        d.rounded_rectangle((x0 + 24, y0 - 22, x0 + 24 + w, y0 + 22), 10, fill=FRAME_DARK, outline=FRAME_GOLD, width=2)
        d.text((x0 + 44, y0 - 17), title, font=F(30), fill=(246, 230, 190))


def coin(d, x, y, r=11):
    d.ellipse((x - r, y - r, x + r, y + r), fill=(232, 186, 74), outline=(140, 98, 24), width=2)
    d.ellipse((x - r + 5, y - r + 5, x + r - 5, y + r - 5), outline=(176, 128, 30), width=2)


def gold_text(d, x, y, n, size=26, color=INK, right=False):
    t = f"{n}"
    w = d.textlength(t, font=F(size))
    if right:
        x -= w + 30
    coin(d, x + 11, y + size * 0.55)
    d.text((x + 28, y), t, font=F(size), fill=color)


def base(title_right_gold=66):
    """Phone screen: dimmed map behind, the shop window with ✕ and gold."""
    S = Image.new("RGBA", (PW, PH), (0, 0, 0, 255))
    try:
        bg = Image.open(ROOT / "docs/art/mockup_hex_icons.png").convert("RGBA").crop((1020, 90, 1780, 710)).resize((PW, PH))
    except Exception:
        bg = Image.new("RGBA", (PW, PH), (200, 200, 180, 255))
    S.alpha_composite(bg.filter(ImageFilter.GaussianBlur(3)))
    S.alpha_composite(Image.new("RGBA", (PW, PH), (20, 14, 8, 120)))
    d = ImageDraw.Draw(S)
    return S, d


def header(S, d, name, gold):
    window(d, (24, 90, PW - 24, PH - 60), name)
    # ✕ top right
    d.ellipse((PW - 96, 104, PW - 44, 156), fill=FRAME_DARK, outline=FRAME_GOLD, width=2)
    d.line((PW - 82, 118, PW - 58, 142), fill=(246, 230, 190), width=5)
    d.line((PW - 58, 118, PW - 82, 142), fill=(246, 230, 190), width=5)
    gold_text(d, PW - 110, 112, gold, 30, right=True)


def greeting(S, d, y, lines, portrait="sprite_trader"):
    window(d, (50, y, PW - 50, y + 150))
    p = img(portrait, 96)
    p = p.crop((0, 0, min(p.width, 70), p.height))
    S.alpha_composite(p, (70, y + 28))
    for i, ln in enumerate(lines):
        d.text((160, y + 30 + i * 34), ln, font=F(24), fill=INK)
    return y + 150


def tabs(d, y, active):
    for i, t in enumerate(["Wares", "Looks"]):
        x0 = 60 + i * 180
        on = t == active
        d.rounded_rectangle((x0, y, x0 + 160, y + 52), 10, fill=FRAME_DARK if on else PANEL, outline=FRAME_DARK, width=3)
        w = d.textlength(t, font=F(26))
        d.text((x0 + 80 - w / 2, y + 11), t, font=F(26), fill=(246, 230, 190) if on else INK)
    return y + 70


def stepper(d, x, y, qty, edit=False):
    for i, sym in enumerate(["−", None, "+"]):
        bx = x + i * 78
        if sym is None:
            d.rounded_rectangle((bx - 6, y, bx + 70, y + 54), 8, fill=(255, 252, 240), outline=GOLD if edit else FRAME_DARK, width=4 if edit else 2)
            t = f"{qty}" + ("|" if edit else "")
            w = d.textlength(t, font=F(30))
            d.text((bx + 32 - w / 2, y + 9), t, font=F(30), fill=INK)
        else:
            d.rounded_rectangle((bx, y, bx + 56, y + 54), 10, fill=FRAME_DARK, outline=FRAME_GOLD, width=2)
            c = (246, 230, 190)
            d.line((bx + 16, y + 27, bx + 40, y + 27), fill=c, width=5)          # drawn: the font has no minus sign
            if sym == "+":
                d.line((bx + 28, y + 15, bx + 28, y + 39), fill=c, width=5)


def buy_button(d, x, y, label="Buy", enabled=True):
    d.rounded_rectangle((x, y, x + 130, y + 54), 12, fill=(126, 96, 40) if enabled else (190, 176, 150), outline=FRAME_GOLD, width=3)
    w = d.textlength(label, font=F(28))
    d.text((x + 65 - w / 2, y + 10), label, font=F(28), fill=(255, 246, 220))


WARES = [  # resource, unit price (trader = 2× base, first unit), flavour
    ("wood", 20, "Seasoned oak. Splinters sold separately."),
    ("food", 16, "Trail rations. Tastes like adventure, mostly salt."),
    ("stone", 12, "Quarried fresh. Great for walls, terrible for pillows."),
    ("metal", 50, "Ingots of questionable origin. Don't ask."),
]


def climb(unit, n, start=0):
    return math.ceil(sum(unit * 1.1 ** (start + i) for i in range(n)))


def ware_rows(S, d, y, selected, qty, edit=False, gold=66):
    for res, unit, flavour in WARES:
        sel = res == selected
        h = 190 if sel else 110
        if sel:
            d.rounded_rectangle((52, y - 6, PW - 52, y + h - 10), 12, fill=(240, 228, 198), outline=FRAME_GOLD, width=2)
            d.polygon([(60, y + 32), (80, y + 44), (60, y + 56)], fill=FRAME_DARK)   # ▶ cursor
        S.alpha_composite(img(f"oad_res_{res}", 64), (92, y + 10))
        d.text((176, y + 8), res.capitalize(), font=F(30), fill=INK)
        d.text((176, y + 48), flavour, font=F(20), fill=SOFT)
        if sel:
            total = climb(unit, qty)
            stepper(d, 176, y + 100, qty, edit)
            gold_text(d, 420, y + 110, total, 30, color=RED if total > gold else INK)
            buy_button(d, PW - 200, y + 100, enabled=total <= gold)
        else:
            gold_text(d, PW - 100, y + 12, unit, 26, right=True)
        y += h + 6
    return y


def screen_wares():
    S, d = base()
    header(S, d, "Travelling trader", 66)
    y = greeting(S, d, 160, ["Fine morning for the road, traveller.", "Mild and dry: a good day for stone.", "Fresh stock, honest-ish prices."])
    # A trade request (occasional)
    window(d, (50, y + 20, PW - 50, y + 140))
    S.alpha_composite(img("oad_res_stone", 52), (74, y + 54))
    d.text((140, y + 40), "Wanted: 3 stone", font=F(26), fill=INK)
    d.text((140, y + 76), "“I'll trade a metal ingot for them.”", font=F(21), fill=SOFT)
    buy_button(d, PW - 200, y + 56, "Trade")
    y = tabs(d, y + 170, "Wares")
    ware_rows(S, d, y + 10, "wood", 2)
    d.text((64, PH - 120), "Purchased 2 food.", font=F(22), fill=(60, 110, 60))
    return S


def screen_qty():
    S, d = base()
    header(S, d, "Travelling trader", 66)
    y = tabs(d, 180, "Wares")
    ware_rows(S, d, y + 10, "stone", 5, edit=True)
    # number pad sliding up from the bottom
    top = PH - 520
    window(d, (40, top, PW - 40, PH - 80))
    d.text((70, top + 26), "How many stone?", font=F(26), fill=INK)
    keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "Max", "0", "OK"]
    for i, k in enumerate(keys):
        cx = 70 + (i % 3) * 200
        cy = top + 80 + (i // 3) * 82
        d.rounded_rectangle((cx, cy, cx + 180, cy + 68), 12, fill=FRAME_DARK if k == "OK" else (255, 250, 236), outline=FRAME_DARK, width=2)
        w = d.textlength(k, font=F(30))
        d.text((cx + 90 - w / 2, cy + 15), k, font=F(30), fill=(246, 230, 190) if k == "OK" else INK)
    return S


LOOKS = [  # (icon, name, flavour, state)
    ("banner_crimson", "Crimson banner", "Says 'we were here' in a very loud red.", "worn"),
    ("banner_royal", "Royal banner", "Blue blood not included.", 60),
    ("banner_gold", "Gold banner", "Subtle as a dragon's hoard.", 60),
    ("sprite_wanderer_crimson", "Crimson cloak", "Swish +5.", "owned"),
    ("sprite_wanderer_navy", "Navy coat", "For long marches and longer naps.", 120),
    ("sprite_wanderer_hat", "Wide hat", "+1 to looking like you know the way.", 120),
]


def screen_looks():
    S, d = base()
    header(S, d, "Travelling trader", 66)
    y = tabs(d, 180, "Looks")
    y += 10
    for icon, name, flavour, state in LOOKS:
        S.alpha_composite(img(icon, 72), (88, y + 6))
        nx = 176
        if state == "worn":   # a drawn star (the font has no ★)
            cx, cy, R, r = 190, y + 26, 14, 6
            d.polygon([(cx + (R if k % 2 == 0 else r) * math.cos(math.radians(-90 + 36 * k)), cy + (R if k % 2 == 0 else r) * math.sin(math.radians(-90 + 36 * k))) for k in range(10)], fill=(214, 160, 40), outline=(120, 80, 20))
            nx = 214
        d.text((nx, y + 8), name, font=F(30), fill=INK)
        d.text((176, y + 48), flavour, font=F(20), fill=SOFT)
        if state == "worn":
            buy_button(d, PW - 200, y + 18, "Take off")
        elif state == "owned":
            buy_button(d, PW - 200, y + 18, "Wear")
        else:
            d.rounded_rectangle((PW - 200, y + 18, PW - 70, y + 72), 12, outline=FRAME_DARK, width=3)
            gold_text(d, PW - 186, y + 28, state, 26, color=INK if state <= 66 else RED)
        y += 120
    return S


def main():
    gap, top = 40, 110
    shots = [("1  Wares", "Greeting, a trade request, flavour lines, stepper + total + Buy", screen_wares()),
             ("2  Typing a quantity", "Tap the number: a pad to type it (Max fills what you can afford)", screen_qty()),
             ("3  Looks", "Price, Wear or Take off; a star marks what you wear", screen_looks())]
    sheet = Image.new("RGB", (len(shots) * (PW + gap) + gap, PH + top + 30), PARCH)
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
